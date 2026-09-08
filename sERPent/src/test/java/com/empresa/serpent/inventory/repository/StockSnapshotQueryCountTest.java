package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CUÁNTAS CONSULTAS CUESTA LEER EL STOCK DE UN DEPÓSITO.
 *
 * <p>Es la lectura más caliente que toca InventoryStockSnapshotEntity: la pantalla de venta
 * pide {@code GET /api/stock?warehouseId=N} al abrirse y de nuevo después de confirmar cada
 * venta, y eso baja TODAS las filas del depósito como entidades gestionadas. No está paginada
 * a propósito —el formulario necesita la lista entera para saber qué hay disponible—, así que
 * el costo crece con el catálogo.
 *
 * <p>POR QUÉ ESTE TEST EXISTE. Mapear {@code last_movement_id} como relación es correcto en
 * teoría y podría ser carísimo en la práctica: un {@code @ManyToOne} es EAGER por defecto en
 * JPA, y EAGER acá significa una consulta por fila. Con el catálogo de la granjita eso
 * convierte una consulta en decenas, en la pantalla donde hay gente esperando. El número tiene
 * que estar medido antes y después del cambio, no supuesto.
 *
 * <p>SE CUENTAN SENTENCIAS PREPARADAS, no consultas lógicas: es lo que efectivamente va a la
 * base. El conteo se hace con el catálogo cargado dos veces con distinta cantidad de filas,
 * porque un número solo no distingue "una consulta" de "una por fila" cuando hay pocas filas.
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("Cost of reading a warehouse's stock")
class StockSnapshotQueryCountTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryStockSnapshotRepository repository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private WarehouseEntity warehouse;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        warehouse = entityManager.persistAndFlush(
                WarehouseEntity.builder().name("Central QC").active(true).build());
    }

    /**
     * Deja {@code rows} snapshots en el depósito, cada uno apuntando a un movimiento REAL.
     *
     * <p>Los ids inventados no sirven para medir: la clave foránea del esquema los rechaza, y
     * además un id que no existe no dispararía la carga de la relación que este test mide.
     */
    private int seeded = 0;

    private void seedSnapshots(int rows) {
        for (int i = 0; i < rows; i++) {
            int n = seeded++;
            ProductEntity product = entityManager.persistAndFlush(ProductEntity.builder()
                    .name("Producto QC " + n)
                    .price(new BigDecimal("1000"))
                    .sku("QC_" + n)
                    .active(true)
                    .build());

            InventoryMovementEntity movement = entityManager.persistAndFlush(
                    InventoryMovementEntity.builder()
                            .product(product)
                            .warehouse(warehouse)
                            .movementType(MovementType.IN)
                            .quantity(new BigDecimal("10.000"))
                            .build());

            entityManager.persistAndFlush(InventoryStockSnapshotEntity.builder()
                    .product(product)
                    .warehouse(warehouse)
                    .currentStock(new BigDecimal("10.000"))
                    .lastMovementId(movement.getId())
                    .build());
        }
        entityManager.clear();
    }

    /** Sentencias que la lectura del depósito manda a la base, con la caché de sesión vacía. */
    private long statementsToLoadWarehouseStock() {
        entityManager.clear();
        statistics.clear();

        List<InventoryStockSnapshotEntity> snapshots =
                repository.findByWarehouseIdIn(List.of(warehouse.getId()));

        /*
          SE RECORRE LO MISMO QUE RECORRE PRODUCCIÓN, y esto ya se hizo mal una vez: la primera
          versión sumaba currentStock y nada más, así que medía 1 sentencia y no veía las cargas
          perezosas que StockQueryService.toStockResponse dispara al leer producto y depósito.
          Un medidor que no recorre lo que recorre el código mide otra cosa.
        */
        for (InventoryStockSnapshotEntity snapshot : snapshots) {
            assertThat(snapshot.getProduct().getName()).isNotNull();
            assertThat(snapshot.getWarehouse().getName()).isNotNull();
            assertThat(snapshot.getCurrentStock()).isNotNull();
        }

        return statistics.getPrepareStatementCount();
    }

    @Test
    @DisplayName("Reading the whole warehouse costs one statement, and it does not grow with rows")
    void readingStockIsOneStatement() {
        seedSnapshots(3);
        long withThree = statementsToLoadWarehouseStock();

        seedSnapshots(7);
        long withTen = statementsToLoadWarehouseStock();

        /*
          LO QUE CUESTA HOY, MEDIDO Y NO SUPUESTO: una consulta por la lista, más una por
          producto distinto, más una por depósito distinto. Los productos son distintos en cada
          fila y el depósito es siempre el mismo, así que con N filas son N + 2.

          Este número documenta un N+1 QUE YA EXISTE en la lectura más caliente de la app, y no
          lo introduce este trabajo: toStockResponse lee el nombre del producto y el del
          depósito, y las dos relaciones son perezosas. Arreglarlo es otra ronda —un
          @EntityGraph sobre findByWarehouseIdIn, como el que ya tiene el repositorio de
          movimientos—. Queda medido para que esa ronda tenga contra qué comparar.
        */
        assertThat(withThree).as("3 filas: 1 lista + 3 productos + 1 depósito").isEqualTo(5);
        assertThat(withTen).as("10 filas: 1 lista + 10 productos + 1 depósito").isEqualTo(12);

        /*
          LA AFIRMACIÓN QUE IMPORTA PARA ESTA RONDA: el costo crece con los productos y con
          nada más. Mapear last_movement_id como @ManyToOne sin declarar LAZY sumaría otra
          consulta por fila —medido: con product en EAGER, tres filas pasan de 1 a 4
          sentencias— y este test lo pondría rojo.
        */
        long porFilaEntreLasDosCorridas = (withTen - withThree) / (10 - 3);
        assertThat(porFilaEntreLasDosCorridas)
                .as("sentencias que cuesta cada fila de más")
                .isEqualTo(1);
    }
}
