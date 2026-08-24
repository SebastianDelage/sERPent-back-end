package com.empresa.serpent.reports;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.domain.entity.ProductSupplierEntity;
import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.reports.service.InventoryReportService;
import com.empresa.serpent.reports.web.dto.response.InventoryReplenishmentResponse;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import com.empresa.serpent.transactions.domain.entity.PurchaseEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The replenishment report: what to reorder, per branch, and who to buy it from.
 *
 * <p>Fixture: two branches and one product whose reorder point is 30 at product level. The
 * central warehouse overrides it to 100 — it sells more, so it has to order earlier — and
 * both hold 50 units. That single number, 50, is below one branch's trigger and above the
 * other's, so any test that gets the cascade wrong reports the wrong branch rather than
 * merely the wrong figure.
 */
@DataJpaTest
@Import(InventoryReportService.class)
@ActiveProfiles("test")
@DisplayName("Replenishment report")
class ReplenishmentReportTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryReportService service;

    @MockitoBean
    private WarehouseScopeService warehouseScopeService;

    /** Not used by this report; only there to satisfy the constructor. */
    @MockitoBean
    private StockQueryService stockQueryService;

    private WarehouseEntity central;
    private WarehouseEntity north;
    private ProductEntity chicken;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(UserEntity.builder()
                .name("Admin").username("admin_repl").passwordHash("hash").active(true).build());

        central = persistWarehouse("Depósito Central");
        north = persistWarehouse("Sucursal Norte");

        // Product level: floor 20, order at 30, top up to 80.
        chicken = entityManager.persistAndFlush(ProductEntity.builder()
                .name("Pollo entero")
                .sku("POLLO_REPL")
                .price(new BigDecimal("2500.0000"))
                .active(true)
                .minimumStock(new BigDecimal("20.000"))
                .reorderPoint(new BigDecimal("30.000"))
                .reorderQuantity(new BigDecimal("80.000"))
                .build());

        // Central sells more: same floor, but it has to order much earlier.
        entityManager.persistAndFlush(ProductWarehouseMinimumStockEntity.builder()
                .product(chicken)
                .warehouse(central)
                .reorderPoint(new BigDecimal("100.000"))
                .build());

        stockOf(chicken, central, "50.000");
        stockOf(chicken, north, "50.000");

        unrestrictedScope();
    }

    @Nested
    @DisplayName("the cascade")
    class Cascade {

        @Test
        @DisplayName("Fires per branch on the reorder point that applies there")
        void firesOnTheResolvedReorderPoint() {
            List<InventoryReplenishmentResponse> rows = service.getReplenishmentReport(null);

            // 50 is under Central's own 100 and over Norte's inherited 30, so only one
            // branch is short even though both hold exactly the same stock.
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).warehouseName()).isEqualTo("Depósito Central");
            assertThat(rows.get(0).reorderPoint()).isEqualByComparingTo("100.000");
        }

        @Test
        @DisplayName("Reports the minimum that applies, inherited when the branch has none")
        void reportsTheResolvedMinimum() {
            // Central overrode only the reorder point, so its floor still comes from the
            // product. The two figures cascade independently.
            InventoryReplenishmentResponse row = service.getReplenishmentReport(null).get(0);

            assertThat(row.minimumStock()).isEqualByComparingTo("20.000");
            assertThat(row.reorderPoint()).isEqualByComparingTo("100.000");
        }

        @Test
        @DisplayName("Uses the branch's own reorder quantity when it has one")
        void usesTheResolvedReorderQuantity() {
            ProductWarehouseMinimumStockEntity override = entityManager.getEntityManager()
                    .createQuery("""
                            SELECT m FROM ProductWarehouseMinimumStockEntity m
                             WHERE m.product = :p AND m.warehouse = :w
                            """, ProductWarehouseMinimumStockEntity.class)
                    .setParameter("p", chicken)
                    .setParameter("w", central)
                    .getSingleResult();

            override.setReorderQuantity(new BigDecimal("200.000"));
            entityManager.persistAndFlush(override);
            entityManager.clear();

            InventoryReplenishmentResponse row = service.getReplenishmentReport(null).get(0);

            assertThat(row.reorderQuantity()).isEqualByComparingTo("200.000");
            // 200 target - 50 on hand.
            assertThat(row.suggestedOrderQuantity()).isEqualByComparingTo("150.000");
        }

        @Test
        @DisplayName("A product with no reorder point anywhere never reaches the report")
        void noReorderPointMeansNoReport() {
            ProductEntity untracked = entityManager.persistAndFlush(ProductEntity.builder()
                    .name("Sin seguimiento").sku("SIN_REPL")
                    .price(new BigDecimal("100.0000")).active(true)
                    .build());
            stockOf(untracked, central, "0.000");

            assertThat(service.getReplenishmentReport(null))
                    .extracting(InventoryReplenishmentResponse::productId)
                    .doesNotContain(untracked.getId());
        }
    }

    @Nested
    @DisplayName("the supplier")
    class Supplier {

        @Test
        @DisplayName("Proposes the preferred one when the product has several")
        void proposesThePreferred() {
            link(chicken, persistSupplier("Proveedor Malo"), "COD-MALO", false);
            link(chicken, persistSupplier("Proveedor Bueno"), "COD-BUENO", true);
            link(chicken, persistSupplier("Otro Más"), "COD-OTRO", false);
            entityManager.clear();

            InventoryReplenishmentResponse row = service.getReplenishmentReport(null).get(0);

            assertThat(row.preferredSupplierName()).isEqualTo("Proveedor Bueno");
            assertThat(row.supplierProductCode()).isEqualTo("COD-BUENO");
        }

        @Test
        @DisplayName("A product with no supplier still appears: it is short either way")
        void productWithoutSupplierStillAppears() {
            // Nothing linked. Not knowing who to buy from is not a reason to hide a shortage.
            InventoryReplenishmentResponse row = service.getReplenishmentReport(null).get(0);

            assertThat(row.productName()).isEqualTo("Pollo entero");
            assertThat(row.preferredSupplierName()).isNull();
            assertThat(row.supplierProductCode()).isNull();
        }

        @Test
        @DisplayName("An inactive link is never proposed")
        void inactiveLinkIsIgnored() {
            ProductSupplierEntity link = link(chicken, persistSupplier("Ya No Le Compramos"), "X", true);
            link.setActive(false);
            entityManager.persistAndFlush(link);
            entityManager.clear();

            assertThat(service.getReplenishmentReport(null).get(0).preferredSupplierName()).isNull();
        }
    }

    @Nested
    @DisplayName("the last purchase price")
    class LastPrice {

        @Test
        @DisplayName("Comes from the most recent confirmed purchase, not the oldest")
        void takesTheMostRecent() {
            SupplierEntity supplier = persistSupplier("Proveedor Único");

            purchaseOf(chicken, supplier, "1800.0000", LocalDateTime.of(2026, 1, 10, 9, 0));
            purchaseOf(chicken, supplier, "2200.0000", LocalDateTime.of(2026, 3, 15, 9, 0));
            purchaseOf(chicken, supplier, "2000.0000", LocalDateTime.of(2026, 2, 20, 9, 0));
            entityManager.clear();

            InventoryReplenishmentResponse row = service.getReplenishmentReport(null).get(0);

            assertThat(row.lastPurchaseUnitPrice()).isEqualByComparingTo("2200.0000");
            assertThat(row.lastPurchaseDate()).isEqualTo(LocalDateTime.of(2026, 3, 15, 9, 0));
        }

        @Test
        @DisplayName("Names the supplier of that purchase, which may not be the preferred one")
        void namesTheSupplierItCameFrom() {
            SupplierEntity preferred = persistSupplier("El Preferido");
            SupplierEntity occasional = persistSupplier("El De Esa Vez");

            link(chicken, preferred, "COD-PREF", true);
            purchaseOf(chicken, occasional, "1950.0000", LocalDateTime.of(2026, 4, 1, 9, 0));
            entityManager.clear();

            InventoryReplenishmentResponse row = service.getReplenishmentReport(null).get(0);

            // The two names differ on purpose: showing the price under the preferred
            // supplier's name would claim something that never happened.
            assertThat(row.preferredSupplierName()).isEqualTo("El Preferido");
            assertThat(row.lastPurchaseSupplierName()).isEqualTo("El De Esa Vez");
        }

        @Test
        @DisplayName("Is absent for a product never purchased")
        void absentWhenNeverPurchased() {
            InventoryReplenishmentResponse row = service.getReplenishmentReport(null).get(0);

            assertThat(row.lastPurchaseUnitPrice()).isNull();
            assertThat(row.lastPurchaseDate()).isNull();
        }
    }

    @Nested
    @DisplayName("branch scoping")
    class Scoping {

        @Test
        @DisplayName("An employee limited to one branch sees only that branch's shortages")
        void limitedScopeSeesOnlyItsOwn() {
            // Norte is short too once its stock drops under the inherited 30.
            stockOf(chicken, north, "10.000");
            entityManager.clear();

            Mockito.when(warehouseScopeService.resolve(Mockito.any()))
                    .thenReturn(new WarehouseScope(false, List.of(north.getId())));

            List<InventoryReplenishmentResponse> rows = service.getReplenishmentReport(null);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).warehouseName()).isEqualTo("Sucursal Norte");
        }

        @Test
        @DisplayName("A caller who may see nothing gets nothing, without querying")
        void emptyScopeReturnsEmpty() {
            Mockito.when(warehouseScopeService.resolve(Mockito.any()))
                    .thenReturn(new WarehouseScope(false, List.of()));

            assertThat(service.getReplenishmentReport(null)).isEmpty();
        }
    }

    // --- fixture helpers -------------------------------------------------------------

    private void unrestrictedScope() {
        Mockito.when(warehouseScopeService.resolve(Mockito.any()))
                .thenReturn(new WarehouseScope(true, List.of()));
    }

    private WarehouseEntity persistWarehouse(String name) {
        return entityManager.persistAndFlush(
                WarehouseEntity.builder().name(name).active(true).build());
    }

    private SupplierEntity persistSupplier(String name) {
        return entityManager.persistAndFlush(
                SupplierEntity.builder().name(name).active(true).build());
    }

    private ProductSupplierEntity link(ProductEntity product, SupplierEntity supplier,
                                       String code, boolean preferred) {
        return entityManager.persistAndFlush(ProductSupplierEntity.builder()
                .product(product)
                .supplierEntity(supplier)
                .supplierProductCode(code)
                .preferred(preferred)
                .active(true)
                .build());
    }

    private void stockOf(ProductEntity product, WarehouseEntity warehouse, String stock) {
        entityManager.getEntityManager()
                .createQuery("""
                        SELECT s FROM InventoryStockSnapshotEntity s
                         WHERE s.product = :p AND s.warehouse = :w
                        """, InventoryStockSnapshotEntity.class)
                .setParameter("p", product)
                .setParameter("w", warehouse)
                .getResultStream()
                .findFirst()
                .ifPresentOrElse(
                        existing -> {
                            existing.setCurrentStock(new BigDecimal(stock));
                            entityManager.persistAndFlush(existing);
                        },
                        () -> entityManager.persistAndFlush(InventoryStockSnapshotEntity.builder()
                                .product(product)
                                .warehouse(warehouse)
                                .currentStock(new BigDecimal(stock))
                                .build()));
    }

    /** A confirmed purchase of one product at a given price, dated explicitly. */
    private void purchaseOf(ProductEntity product, SupplierEntity supplier,
                            String unitPrice, LocalDateTime when) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(TransactionType.PURCHASE)
                .status(TransactionStatus.CONFIRMED)
                .total(new BigDecimal(unitPrice))
                .createdByUserEntity(user)
                .build());

        // date is a @CreationTimestamp, so it can only be moved after the insert — and
        // placing the purchases in time by hand is the whole point of these tests.
        entityManager.getEntityManager()
                .createQuery("UPDATE TransactionEntity t SET t.date = :when WHERE t.id = :id")
                .setParameter("when", when)
                .setParameter("id", transaction.getId())
                .executeUpdate();

        entityManager.persistAndFlush(PurchaseEntity.builder()
                .transaction(transaction)
                .warehouse(central)
                .supplier(supplier)
                .onCredit(false)
                .build());

        entityManager.persistAndFlush(TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description("Compra")
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal(unitPrice))
                .subtotal(new BigDecimal(unitPrice))
                .build());
    }
}
