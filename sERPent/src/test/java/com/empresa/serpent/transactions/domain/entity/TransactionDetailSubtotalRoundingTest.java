package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué subtotal termina EN LA BASE para un renglón de cantidad fraccionaria.
 *
 * <p>Va contra la base de verdad y no con mocks a propósito: lo que se rompía vivía en el
 * @PrePersist de la entidad y en el recorte de la columna NUMERIC(19,4), o sea en el tramo
 * que un test de Mockito no toca. Por eso @DataJpaTest.
 *
 * <h2>QUÉ SE ROMPÍA</h2>
 *
 * <p>calculateSubtotal() hacía unitPrice.multiply(quantity) sin redondear, y ese valor de
 * hasta 7 decimales lo recortaba la base al escribirlo. El total, en cambio, lo sumaba el
 * servicio con los renglones SIN recortar y se guardaba una sola vez. Resultado: la suma de
 * los subtotales guardados no daba el total guardado.
 *
 * <p>Antes de la venta por peso era inalcanzable: un precio de 4 decimales por una cantidad
 * ENTERA sigue teniendo 4 decimales y no hay nada que recortar.
 */
@DataJpaTest
@ActiveProfiles("test")
class TransactionDetailSubtotalRoundingTest {

    /** 9,5833 × 1,333 = 12,7745389 exacto: siete decimales en una columna que guarda cuatro. */
    private static final BigDecimal PRECIO_FRACCIONARIO = new BigDecimal("9.5833");
    private static final BigDecimal CANTIDAD_FRACCIONARIA = new BigDecimal("1.333");

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("El subtotal guardado es el producto redondeado a 4 decimales, no el recorte de la base")
    void subtotalGuardadoEsElProductoRedondeado() {
        TransactionDetailEntity linea = persistirRenglon(PRECIO_FRACCIONARIO, CANTIDAD_FRACCIONARIA);

        // Vuelta a la base: lo que se lee es lo que quedó escrito, no lo que hay en memoria.
        entityManager.flush();
        entityManager.clear();

        TransactionDetailEntity leida =
                entityManager.find(TransactionDetailEntity.class, linea.getId());

        assertThat(leida.getSubtotal()).isEqualByComparingTo(new BigDecimal("12.7745"));
    }

    @Test
    @DisplayName("Dos renglones fraccionarios suman exactamente, sin residuo escondido")
    void losRenglonesSumanSinResiduo() {
        TransactionEntity transaccion = persistirTransaccion();
        TransactionDetailEntity a = persistirRenglon(transaccion, PRECIO_FRACCIONARIO, CANTIDAD_FRACCIONARIA);
        TransactionDetailEntity b = persistirRenglon(transaccion, PRECIO_FRACCIONARIO, CANTIDAD_FRACCIONARIA);

        entityManager.flush();
        entityManager.clear();

        BigDecimal sumaGuardada = entityManager.find(TransactionDetailEntity.class, a.getId()).getSubtotal()
                .add(entityManager.find(TransactionDetailEntity.class, b.getId()).getSubtotal());

        assertThat(sumaGuardada).isEqualByComparingTo(new BigDecimal("25.5490"));

        /*
         Y acá está el bug, escrito como número: sin redondear por renglón la suma daba
         25,5491 mientras que lo guardado sumaba 25,5490. Un centavo de diferencia entre el
         total de una venta y sus propios renglones.
        */
        BigDecimal comoSeSumabaAntes = PRECIO_FRACCIONARIO.multiply(CANTIDAD_FRACCIONARIA)
                .add(PRECIO_FRACCIONARIO.multiply(CANTIDAD_FRACCIONARIA))
                .setScale(4, java.math.RoundingMode.HALF_UP);

        assertThat(comoSeSumabaAntes).isEqualByComparingTo(new BigDecimal("25.5491"));
        assertThat(sumaGuardada).isNotEqualByComparingTo(comoSeSumabaAntes);
    }

    @Test
    @DisplayName("Con cantidades enteras no cambió nada: es el caso normal del mostrador")
    void conCantidadesEnterasNoCambiaNada() {
        TransactionDetailEntity linea =
                persistirRenglon(new BigDecimal("3000.00"), new BigDecimal("2"));

        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(TransactionDetailEntity.class, linea.getId()).getSubtotal())
                .isEqualByComparingTo(new BigDecimal("6000"));
    }

    // ------------------------------------------------------------------ helpers

    private TransactionDetailEntity persistirRenglon(BigDecimal precio, BigDecimal cantidad) {
        return persistirRenglon(persistirTransaccion(), precio, cantidad);
    }

    private TransactionDetailEntity persistirRenglon(
            TransactionEntity transaccion, BigDecimal precio, BigDecimal cantidad) {

        TransactionDetailEntity detalle = TransactionDetailEntity.builder()
                .transaction(transaccion)
                .product(persistirProducto())
                .description("Renglón de prueba")
                .quantity(cantidad)
                .unitPrice(precio)
                .subtotal(BigDecimal.ZERO) // lo pisa calculateSubtotal(), que es lo que se prueba
                .build();

        return entityManager.persist(detalle);
    }

    private TransactionEntity persistirTransaccion() {
        UserEntity usuario = entityManager.persist(UserEntity.builder()
                .name("Admin")
                .username("admin_" + System.nanoTime())
                .passwordHash("test")
                .active(true)
                .build());

        return entityManager.persist(TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED)
                .description("Venta de prueba")
                .createdByUserEntity(usuario)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build());
    }

    private ProductEntity persistirProducto() {
        return entityManager.persist(ProductEntity.builder()
                .name("Producto " + System.nanoTime())
                .price(new BigDecimal("1000"))
                .active(true)
                .build());
    }
}
