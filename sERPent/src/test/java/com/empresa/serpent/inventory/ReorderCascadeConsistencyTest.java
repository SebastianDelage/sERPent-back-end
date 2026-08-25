package com.empresa.serpent.inventory;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.StockStatusFilter;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotSpecifications;
import com.empresa.serpent.inventory.repository.ProductWarehouseMinimumStockRepository;
import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockPageFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.reports.repository.projection.InventoryReplenishmentProjection;
import com.empresa.serpent.reports.repository.projection.ProductStockProjection;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * LA MISMA CASCADA, RESUELTA POR CUATRO IMPLEMENTACIONES, SOBRE LOS MISMOS DATOS.
 *
 * <p>El valor efectivo de un depósito —el propio si lo definió, el del producto si no— está
 * escrito cuatro veces en el backend, porque no hay forma de compartir una implementación
 * entre la JVM y el motor SQL:
 *
 * <ol>
 *   <li>Java, en {@code ReorderCascade} (lo usa {@code StockQueryService.getLowStock}).
 *   <li>Criteria API, en {@code InventoryStockSnapshotSpecifications.effectiveMinimum}.
 *   <li>JPQL, en {@code searchGroupedByProduct} (filtro BELOW_MINIMUM del listado por producto).
 *   <li>JPQL, en {@code getReplenishmentReportRaw} (reporte de reposición).
 * </ol>
 *
 * <p>Se evaluó unificarlas en una vista de base y se descartó a propósito: paga una migración
 * espejada en H2 y Postgres y toca tres consultas que funcionan, para una divergencia que
 * todavía no ocurrió. ESTE TEST ES LO QUE PAGA ESA DECISIÓN. Si alguna de las cuatro se va por
 * su lado, acá falla en CI en vez de aparecer en la pantalla de alguien; y si algún día falla
 * seguido, ahí la vista se justifica con evidencia.
 *
 * <p>El fixture está armado para que la cascada tenga que decidir en las dos direcciones sobre
 * UN MISMO PRODUCTO: un depósito sube el piso con un override propio, el otro lo hereda del
 * producto porque su override deja el mínimo en null y solo pisa el punto de reposición. Un
 * mínimo igual en los dos no probaría nada — pasaría incluso con la cascada rota.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Reorder cascade: Java and SQL agree")
class ReorderCascadeConsistencyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryStockSnapshotRepository snapshotRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductWarehouseMinimumStockRepository overrideRepository;

    private StockQueryService stockQueryService;

    private WarehouseEntity central;
    private WarehouseEntity branch;
    private ProductEntity pollo;

    /** El producto: piso 20, pide a los 25, trae de a 50. */
    private static final String PRODUCT_MINIMUM = "20.000";

    /** Central se exige más que el producto. */
    private static final String CENTRAL_OWN_MINIMUM = "50.000";

    @BeforeEach
    void setUp() {
        // El scope no es lo que este test mira: se responde "todo" para que la única variable
        // sea la resolución de la cascada.
        WarehouseScopeService scopeService = Mockito.mock(WarehouseScopeService.class);
        Mockito.lenient().when(scopeService.resolve(any()))
                .thenReturn(new WarehouseScope(true, List.of()));

        stockQueryService = new StockQueryService(
                snapshotRepository, productRepository, overrideRepository, scopeService);

        central = persistWarehouse("Depósito Central");
        branch = persistWarehouse("Sucursal Norte");

        pollo = persistProduct("Pollo entero", PRODUCT_MINIMUM, "25.000", "50.000");

        // Central: sube el piso a 50 y, para no romper el invariante del dominio (el punto de
        // reposición que aplica no puede quedar por debajo del mínimo que aplica), sube
        // también el punto a 60. La cantidad la hereda.
        persistOverride(pollo, central, CENTRAL_OWN_MINIMUM, "60.000", null);
        // Norte: mínimo en NULL (hereda 20) y solo pisa el punto de reposición. Este es el
        // caso que tiraba NPE del lado Java mientras las vías SQL lo resolvían bien.
        persistOverride(pollo, branch, null, "30.000", null);

        // Los dos quedan por debajo de su mínimo efectivo Y de su punto efectivo, para que
        // aparezcan en las dos pantallas y los valores se puedan comparar de verdad. Que
        // aparezcan en las dos NO es una regla del sistema: bajo mínimo dispara por el
        // mínimo y reposición por el punto, así que en general los conjuntos difieren. Acá
        // se los hace coincidir a propósito, porque comparar valores exige filas comunes.
        persistSnapshot(pollo, central, "40.000");  // 40 <= 50 (mín) y 40 <= 60 (punto)
        persistSnapshot(pollo, branch, "8.000");    // 8 <= 20 (mín) y 8 <= 30 (punto)

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("All four implementations resolve the same effective minimum per warehouse")
    void allFourAgreeOnTheEffectiveMinimum() {
        // 1) Java — StockQueryService.getLowStock
        Map<Long, LowStockResponse> java = stockQueryService.getLowStock().stream()
                .collect(Collectors.toMap(LowStockResponse::warehouseId, Function.identity()));

        assertThat(java).containsOnlyKeys(central.getId(), branch.getId());
        assertThat(java.get(central.getId()).minimumStock())
                .isEqualByComparingTo(CENTRAL_OWN_MINIMUM);
        assertThat(java.get(branch.getId()).minimumStock())
                .isEqualByComparingTo(PRODUCT_MINIMUM);

        // El origen del mínimo, que es lo que la pantalla muestra al usuario: propio en
        // Central, heredado en Norte aunque Norte TENGA una fila de override.
        assertThat(java.get(central.getId()).minimumFromWarehouse()).isTrue();
        assertThat(java.get(branch.getId()).minimumFromWarehouse()).isFalse();

        // 4) JPQL — reporte de reposición. Es la única de las cuatro que expone el mínimo
        // efectivo como dato, así que se compara valor contra valor con la vía Java.
        Map<Long, InventoryReplenishmentProjection> sql =
                snapshotRepository.getReplenishmentReportRaw(true, List.of(), null).stream()
                        .collect(Collectors.toMap(
                                InventoryReplenishmentProjection::getWarehouseId,
                                Function.identity()));

        assertThat(sql).containsOnlyKeys(central.getId(), branch.getId());
        assertThat(sql.get(central.getId()).getMinimumStock())
                .isEqualByComparingTo(java.get(central.getId()).minimumStock());
        assertThat(sql.get(branch.getId()).getMinimumStock())
                .isEqualByComparingTo(java.get(branch.getId()).minimumStock());
    }

    @Test
    @DisplayName("Java and the Criteria specification flag the same (product, warehouse) pairs")
    void javaAndCriteriaAgreeOnWhoIsLow() {
        List<String> java = stockQueryService.getLowStock().stream()
                .map(row -> row.productName() + " @ " + row.warehouseName())
                .sorted()
                .toList();

        List<String> criteria = snapshotRepository.findAll(
                        InventoryStockSnapshotSpecifications.fromFilter(
                                new StockPageFilter(null, null, StockStatusFilter.BELOW_MINIMUM)),
                        PageRequest.of(0, 20))
                .getContent().stream()
                .map(row -> row.getProduct().getName() + " @ " + row.getWarehouse().getName())
                .sorted()
                .toList();

        assertThat(criteria).isEqualTo(java);
    }

    @Test
    @DisplayName("The grouped-by-product JPQL agrees on which products are short somewhere")
    void groupedByProductAgrees() {
        List<Long> javaProductIds = stockQueryService.getLowStock().stream()
                .map(LowStockResponse::productId)
                .distinct()
                .sorted()
                .toList();

        Page<ProductStockProjection> grouped = snapshotRepository.searchGroupedByProduct(
                null, true, List.of(), false, false, true, PageRequest.of(0, 20));

        List<Long> sqlProductIds = grouped.getContent().stream()
                .map(ProductStockProjection::getProductId)
                .distinct()
                .sorted()
                .toList();

        assertThat(sqlProductIds).isEqualTo(javaProductIds);
    }

    /**
     * El override de Norte pisa el punto de reposición y deja el mínimo heredado, así que las
     * dos cifras salen de niveles distintos EN LA MISMA FILA. Es el caso que una cascada
     * resuelta a nivel de fila —en vez de por cifra— confundiría.
     */
    @Test
    @DisplayName("Each of the three figures cascades independently within one row")
    void thethreeFiguresCascadeIndependently() {
        Map<Long, InventoryReplenishmentProjection> sql =
                snapshotRepository.getReplenishmentReportRaw(true, List.of(), null).stream()
                        .collect(Collectors.toMap(
                                InventoryReplenishmentProjection::getWarehouseId,
                                Function.identity()));

        InventoryReplenishmentProjection norte = sql.get(branch.getId());

        assertThat(norte.getMinimumStock()).isEqualByComparingTo("20.000");   // heredado
        assertThat(norte.getReorderPoint()).isEqualByComparingTo("30.000");   // propio
        assertThat(norte.getReorderQuantity()).isEqualByComparingTo("50.000"); // heredado

        InventoryReplenishmentProjection centralRow = sql.get(central.getId());

        assertThat(centralRow.getMinimumStock()).isEqualByComparingTo("50.000");    // propio
        assertThat(centralRow.getReorderPoint()).isEqualByComparingTo("60.000");    // propio
        assertThat(centralRow.getReorderQuantity()).isEqualByComparingTo("50.000"); // heredado
    }

    // --- fixture ---

    private WarehouseEntity persistWarehouse(String name) {
        return entityManager.persist(
                WarehouseEntity.builder().name(name).active(true).build());
    }

    private ProductEntity persistProduct(String name,
                                         String minimum,
                                         String reorderPoint,
                                         String reorderQuantity) {
        return entityManager.persist(ProductEntity.builder()
                .name(name)
                .sku(name.replaceAll("\\s", "").toUpperCase())
                .active(true)
                .price(new BigDecimal("1000.0000"))
                .minimumStock(decimal(minimum))
                .reorderPoint(decimal(reorderPoint))
                .reorderQuantity(decimal(reorderQuantity))
                .build());
    }

    private void persistSnapshot(ProductEntity product, WarehouseEntity warehouse, String stock) {
        entityManager.persist(InventoryStockSnapshotEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .currentStock(new BigDecimal(stock))
                .build());
    }

    private void persistOverride(ProductEntity product,
                                 WarehouseEntity warehouse,
                                 String minimum,
                                 String reorderPoint,
                                 String reorderQuantity) {
        entityManager.persist(ProductWarehouseMinimumStockEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .minimumStock(decimal(minimum))
                .reorderPoint(decimal(reorderPoint))
                .reorderQuantity(decimal(reorderQuantity))
                .build());
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
