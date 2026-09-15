package com.empresa.serpent.shared.dialect;

import com.empresa.serpent.cashcount.repository.CashCountRepository;
import com.empresa.serpent.catalog.repository.CustomerRepository;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.TerminalRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.transactions.repository.CustomerPaymentRepository;
import com.empresa.serpent.transactions.repository.ExpenseCategoryRepository;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.SupplierPaymentRepository;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CADA CONSULTA CON UN FILTRO OPCIONAL, EJECUTADA CON ESE FILTRO EN NULL.
 *
 * <p>No mira resultados: mira que la consulta CORRA. Suena a poco y es exactamente lo que
 * faltaba, por dos motivos distintos que se sumaron.
 *
 * <h2>Lo que pasó</h2>
 *
 * <p>Seis búsquedas por nombre reventaban en PostgreSQL con
 * {@code ERROR: no existe la funcion lower(bytea)} cada vez que se abría la pantalla sin
 * escribir nada en el buscador — o sea, siempre. Con la app instalada en el local, eso significó
 * no poder ni crear un depósito.
 *
 * <p>LA CAUSA ES EL TIPO DEL PARÁMETRO, NO EL MOTOR. Cuando el parámetro aparece SOLO dentro de
 * un {@code IS NULL} y de funciones ({@code CONCAT}, {@code LOWER}), Hibernate no tiene de dónde
 * deducir de qué tipo es. Con un valor adelante usa el tipo del valor; con null no hay valor que
 * mirar y cae en su tipo por defecto, que viaja al driver como BINARIO. PostgreSQL recibe
 * entonces una concatenación cuyo parámetro está declarado bytea, resuelve el operador de bytea,
 * y no existe lower() para ese tipo.
 *
 * <p>Ojo con la explicación fácil: NO es que PostgreSQL no pueda inferir el tipo de un null. Si
 * fuera eso, el error sería {@code 42P18 no se pudo determinar el tipo del parametro}, que es lo
 * que devuelve cuando el driver manda el tipo sin especificar. Acá se le dijo bytea, y le creyó.
 *
 * <h2>Por qué ningún test lo agarró: son dos agujeros, no uno</h2>
 *
 * <ol>
 *   <li>ESTAS CONSULTAS NO LAS EJECUTABA NINGÚN TEST, en ningún motor. Los tests de servicio que
 *       cubren el caso sin término mockean el repositorio, así que verifican que el servicio
 *       convierta vacío en null y mapee bien la respuesta — nunca que el JPQL corra.
 *   <li>Y aunque la ejecutaran, el suite corre contra H2, que acepta el parámetro binario sin
 *       chistar. Ésa es la divergencia de COMPORTAMIENTO —no de esquema— que el trabajo de
 *       paridad de migraciones no cubre: aquél compara DDL, y acá los dos esquemas son idénticos.
 * </ol>
 *
 * <p>Por eso esta clase tiene dos hijas: la de H2 corre siempre y tapa el agujero 1; la de
 * PostgreSQL corre a pedido contra el motor de la máquina y tapa el agujero 2.
 *
 * <p>QUÉ AGREGAR ACÁ: toda consulta nueva que reciba un filtro que pueda venir en null. Es una
 * línea, y es el único lugar del proyecto donde esa línea se ejecuta contra el motor real.
 */
abstract class NullableFilterQueriesTest {

    @Autowired WarehouseRepository warehouses;
    @Autowired CustomerRepository customers;
    @Autowired SupplierRepository suppliers;
    @Autowired TerminalRepository terminals;
    @Autowired PaymentMethodRepository paymentMethods;
    @Autowired ExpenseCategoryRepository expenseCategories;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired InventoryStockSnapshotRepository stock;
    @Autowired CashCountRepository cashCounts;
    @Autowired CustomerPaymentRepository customerPayments;
    @Autowired SupplierPaymentRepository supplierPayments;

    // --- Las que rompieron en el local ------------------------------------------------------

    @Test @DisplayName("warehouses: listado sin término de búsqueda")
    void warehousesSinTermino() {
        assertThatCode(() -> warehouses.search(null, false)).doesNotThrowAnyException();
    }

    @Test @DisplayName("customers: listado sin término de búsqueda")
    void customersSinTermino() {
        assertThatCode(() -> customers.search(null, false)).doesNotThrowAnyException();
    }

    @Test @DisplayName("suppliers: listado sin término de búsqueda")
    void suppliersSinTermino() {
        assertThatCode(() -> suppliers.search(null, false)).doesNotThrowAnyException();
    }

    @Test @DisplayName("terminals: listado sin término de búsqueda")
    void terminalsSinTermino() {
        assertThatCode(() -> terminals.search(null, false)).doesNotThrowAnyException();
    }

    @Test @DisplayName("payment methods: listado sin término de búsqueda")
    void paymentMethodsSinTermino() {
        assertThatCode(() -> paymentMethods.search(null, false)).doesNotThrowAnyException();
    }

    @Test @DisplayName("expense categories: listado sin término de búsqueda")
    void expenseCategoriesSinTermino() {
        assertThatCode(() -> expenseCategories.search(null, false)).doesNotThrowAnyException();
    }

    @Test @DisplayName("users: listado sin término de búsqueda")
    void usersSinTermino() {
        assertThatCode(() -> users.search(null, false)).doesNotThrowAnyException();
    }

    // --- Las que venían funcionando de casualidad -------------------------------------------
    // Funcionaban porque su WHERE compara el parámetro contra una columna mapeada
    // (p.barcode = :name), y ESA comparación es la que le daba el tipo. Andaban gracias a una
    // cláusula puesta por otro motivo: sacarla las habría roto sin que nadie lo asocie.

    @Test @DisplayName("products: listado sin término de búsqueda")
    void productsSinTermino() {
        assertThatCode(() -> products.search(null, false)).doesNotThrowAnyException();
    }

    @Test @DisplayName("stock: listado sin término de búsqueda")
    void stockSinTermino() {
        assertThatCode(() -> stock.searchGroupedByProduct(
                null, true, List.of(1L), false, false, false, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    // --- Filtros opcionales que no son texto, para fijar el límite del problema --------------
    // Éstos pasan, y la razón importa: su parámetro se compara contra una columna mapeada, así
    // que Hibernate tiene de dónde sacar el tipo aun con null. Están acá para que, si algún día
    // esa comparación desaparece, la caída se vea en un test y no en el mostrador.

    @Test @DisplayName("cash counts: sin filtro de depósito")
    void cashCountsSinDeposito() {
        assertThatCode(() -> cashCounts.search(true, List.of(1L), null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test @DisplayName("customer payments: sin filtros de fecha ni medio de pago")
    void customerPaymentsSinFiltros() {
        assertThatCode(() -> customerPayments.search(null, null, null)).doesNotThrowAnyException();
    }

    @Test @DisplayName("supplier payments: sin filtros de fecha ni medio de pago")
    void supplierPaymentsSinFiltros() {
        assertThatCode(() -> supplierPayments.search(null, null, null)).doesNotThrowAnyException();
    }
}
