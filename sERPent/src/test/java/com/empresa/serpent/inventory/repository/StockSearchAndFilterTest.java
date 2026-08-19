package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.StockStatusFilter;
import com.empresa.serpent.inventory.web.dto.filter.StockPageFilter;
import com.empresa.serpent.reports.repository.projection.ProductStockProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stock screen's filters, exercised against the real queries.
 *
 * <p>Fixture: two warehouses and three products, arranged so the cascade has something
 * to decide in each direction — one product inherits its minimum, one has an override
 * that RAISES the bar, one has none at all.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Stock search and filters")
class StockSearchAndFilterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryStockSnapshotRepository repository;

    private WarehouseEntity central;
    private WarehouseEntity branch;
    private ProductEntity pollo;
    private ProductEntity milanesa;
    private ProductEntity bolsas;

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        central = persistWarehouse("Depósito Central");
        branch = persistWarehouse("Sucursal Norte");

        // minimum 20 product-wide.
        pollo = persistProduct("Pollo entero", "POLLO001", "7791234567890", "20.000");
        // minimum 10 product-wide, but the central warehouse demands 30.
        milanesa = persistProduct("Milanesa de pollo", "MILA002", null, "10.000");
        // no minimum anywhere: never "below minimum", even at zero.
        bolsas = persistProduct("Bolsas", "BOLSA003", null, null);

        persistSnapshot(pollo, central, "100.000");   // fine
        persistSnapshot(pollo, branch, "8.000");      // below (inherited 20)
        persistSnapshot(milanesa, central, "25.000"); // below (override 30)
        persistSnapshot(milanesa, branch, "40.000");  // fine (inherited 10)
        persistSnapshot(bolsas, central, "0.000");    // out of stock, but never "below"

        persistOverride(milanesa, central, "30.000");

        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("per-warehouse view")
    class PerWarehouse {

        private Page<InventoryStockSnapshotEntity> search(StockPageFilter filter) {
            return repository.findAll(
                    InventoryStockSnapshotSpecifications.fromFilter(filter), FIRST_PAGE);
        }

        @Test
        @DisplayName("BELOW_MINIMUM honours the cascade: inherited, overridden, and none")
        void belowMinimumHonoursTheCascade() {
            Page<InventoryStockSnapshotEntity> result = search(
                    new StockPageFilter(null, null, StockStatusFilter.BELOW_MINIMUM));

            assertThat(result.getContent())
                    .extracting(r -> r.getProduct().getName() + " @ " + r.getWarehouse().getName())
                    .containsExactlyInAnyOrder(
                            // 8 <= 20, inherited from the product.
                            "Pollo entero @ Sucursal Norte",
                            // 25 <= 30, from the warehouse override — the product's own 10
                            // would have said this was fine.
                            "Milanesa de pollo @ Depósito Central");

            // "Bolsas" sits at zero but has no minimum at either level, so it is never low.
            assertThat(result.getContent())
                    .noneMatch(r -> r.getProduct().getName().equals("Bolsas"));
        }

        @Test
        @DisplayName("OUT_OF_STOCK and IN_STOCK split on zero")
        void outOfStockAndInStock() {
            Page<InventoryStockSnapshotEntity> out = search(
                    new StockPageFilter(null, null, StockStatusFilter.OUT_OF_STOCK));

            assertThat(out.getContent())
                    .extracting(r -> r.getProduct().getName())
                    .containsExactly("Bolsas");

            Page<InventoryStockSnapshotEntity> in = search(
                    new StockPageFilter(null, null, StockStatusFilter.IN_STOCK));

            assertThat(in.getTotalElements()).isEqualTo(4);
        }

        @Test
        @DisplayName("Search matches partial name, exact SKU and exact barcode")
        void searchMatchesNameSkuAndBarcode() {
            assertThat(search(new StockPageFilter("pollo", null, null)).getTotalElements())
                    .isEqualTo(4); // "Pollo entero" x2 and "Milanesa de pollo" x2

            assertThat(search(new StockPageFilter("MILA002", null, null)).getContent())
                    .allMatch(r -> r.getProduct().getName().equals("Milanesa de pollo"));

            assertThat(search(new StockPageFilter("7791234567890", null, null)).getContent())
                    .allMatch(r -> r.getProduct().getName().equals("Pollo entero"));
        }

        @Test
        @DisplayName("Paging is stable: consecutive pages do not repeat or drop rows")
        void pagingIsStable() {
            Sort byProductThenWarehouse = Sort.by("product.name", "warehouse.name");

            List<String> firstPage = repository.findAll(
                            InventoryStockSnapshotSpecifications.fromFilter(
                                    new StockPageFilter(null, null, null)),
                            PageRequest.of(0, 3, byProductThenWarehouse))
                    .getContent().stream()
                    .map(r -> r.getProduct().getName() + " @ " + r.getWarehouse().getName())
                    .toList();

            List<String> secondPage = repository.findAll(
                            InventoryStockSnapshotSpecifications.fromFilter(
                                    new StockPageFilter(null, null, null)),
                            PageRequest.of(1, 3, byProductThenWarehouse))
                    .getContent().stream()
                    .map(r -> r.getProduct().getName() + " @ " + r.getWarehouse().getName())
                    .toList();

            assertThat(firstPage).hasSize(3);
            assertThat(secondPage).hasSize(2);
            // The two pages together are the whole set, with nothing seen twice.
            assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
            assertThat(firstPage.size() + secondPage.size()).isEqualTo(5);
        }

        @Test
        @DisplayName("Filters combine, and paging happens over the filtered set")
        void filtersCombineAndPage() {
            Page<InventoryStockSnapshotEntity> result = repository.findAll(
                    InventoryStockSnapshotSpecifications.fromFilter(
                            new StockPageFilter("pollo", branch.getId(), StockStatusFilter.BELOW_MINIMUM)),
                    PageRequest.of(0, 1));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getProduct().getName()).isEqualTo("Pollo entero");
        }
    }

    @Nested
    @DisplayName("per-product view")
    class PerProduct {

        private Page<ProductStockProjection> search(String term, Long warehouseId, StockStatusFilter status) {
            return repository.searchGroupedByProduct(
                    term,
                    warehouseId,
                    status == StockStatusFilter.OUT_OF_STOCK,
                    status == StockStatusFilter.IN_STOCK,
                    status == StockStatusFilter.BELOW_MINIMUM,
                    FIRST_PAGE);
        }

        @Test
        @DisplayName("Sums across warehouses, one row per product")
        void sumsAcrossWarehouses() {
            Page<ProductStockProjection> result = search(null, null, null);

            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getContent())
                    .filteredOn(r -> r.getProductName().equals("Pollo entero"))
                    .singleElement()
                    .satisfies(r -> assertThat(r.getTotalStock()).isEqualByComparingTo("108.000"));
        }

        @Test
        @DisplayName("BELOW_MINIMUM shows a product short in AT LEAST ONE warehouse")
        void belowMinimumInAtLeastOneWarehouse() {
            Page<ProductStockProjection> result = search(null, null, StockStatusFilter.BELOW_MINIMUM);

            // Pollo is fine at central (100) but short at the branch (8) — it still shows,
            // with its consolidated total, which is exactly the product-level reading.
            assertThat(result.getContent())
                    .extracting(ProductStockProjection::getProductName)
                    .containsExactlyInAnyOrder("Pollo entero", "Milanesa de pollo");
        }

        @Test
        @DisplayName("The status filter selects products without rewriting their total")
        void statusFilterDoesNotRewriteTheTotal() {
            Page<ProductStockProjection> result = search(null, null, StockStatusFilter.BELOW_MINIMUM);

            // Pollo qualifies because of the branch's 8, but "Stock total" must still be
            // 100 + 8: the filter picks the product, it does not redefine how much there is.
            assertThat(result.getContent())
                    .filteredOn(r -> r.getProductName().equals("Pollo entero"))
                    .singleElement()
                    .satisfies(r -> assertThat(r.getTotalStock()).isEqualByComparingTo("108.000"));
        }

        @Test
        @DisplayName("A product with no minimum never shows as below minimum")
        void productWithoutMinimumNeverShows() {
            Page<ProductStockProjection> result = search("Bolsas", null, StockStatusFilter.BELOW_MINIMUM);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("low-stock alert count")
    class AlertCount {

        private long count(StockPageFilter filter) {
            return repository.count(
                    InventoryStockSnapshotSpecifications.fromFilterIgnoringStatus(filter));
        }

        @Test
        @DisplayName("Counts pairs over the whole filtered set, not the page")
        void countsOverTheFilteredSetNotThePage() {
            // Two alerts overall; a page of size 1 must not change that.
            assertThat(count(new StockPageFilter(null, null, null))).isEqualTo(2);

            Page<InventoryStockSnapshotEntity> onePage = repository.findAll(
                    InventoryStockSnapshotSpecifications.fromFilter(
                            new StockPageFilter(null, null, StockStatusFilter.BELOW_MINIMUM)),
                    PageRequest.of(0, 1));

            assertThat(onePage.getContent()).hasSize(1);
            assertThat(count(new StockPageFilter(null, null, null))).isEqualTo(2);
        }

        @Test
        @DisplayName("Respects the text and warehouse filters")
        void respectsTextAndWarehouseFilters() {
            assertThat(count(new StockPageFilter(null, branch.getId(), null))).isEqualTo(1);
            assertThat(count(new StockPageFilter("Milanesa", null, null))).isEqualTo(1);
            assertThat(count(new StockPageFilter("Bolsas", null, null))).isZero();
        }

        @Test
        @DisplayName("Ignores the status filter, so it never just echoes the paginator")
        void ignoresTheStatusFilter() {
            long unfiltered = count(new StockPageFilter(null, null, null));

            assertThat(count(new StockPageFilter(null, null, StockStatusFilter.IN_STOCK)))
                    .isEqualTo(unfiltered);
            assertThat(count(new StockPageFilter(null, null, StockStatusFilter.OUT_OF_STOCK)))
                    .isEqualTo(unfiltered);
            assertThat(count(new StockPageFilter(null, null, StockStatusFilter.BELOW_MINIMUM)))
                    .isEqualTo(unfiltered);
        }
    }

    // --- fixture helpers ---

    private WarehouseEntity persistWarehouse(String name) {
        return entityManager.persistAndFlush(
                WarehouseEntity.builder().name(name).active(true).build());
    }

    private ProductEntity persistProduct(String name, String sku, String barcode, String minimum) {
        return entityManager.persistAndFlush(ProductEntity.builder()
                .name(name).description(name).sku(sku).barcode(barcode).active(true)
                .price(new BigDecimal("1000.0000"))
                .minimumStock(minimum == null ? null : new BigDecimal(minimum))
                .build());
    }

    private void persistSnapshot(ProductEntity product, WarehouseEntity warehouse, String stock) {
        entityManager.persistAndFlush(InventoryStockSnapshotEntity.builder()
                .product(product).warehouse(warehouse)
                .currentStock(new BigDecimal(stock)).build());
    }

    private void persistOverride(ProductEntity product, WarehouseEntity warehouse, String minimum) {
        entityManager.persistAndFlush(ProductWarehouseMinimumStockEntity.builder()
                .product(product).warehouse(warehouse)
                .minimumStock(new BigDecimal(minimum)).build());
    }
}
