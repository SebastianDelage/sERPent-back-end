package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.reports.repository.projection.SalesDailyProjection;
import com.empresa.serpent.reports.repository.projection.SalesSummaryProjection;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The warehouse filter on the sales reports.
 *
 * <p>Fixture, built once per test: branch A books a surcharged sale with a manual
 * discount and later takes a return against it; branch B books a plain sale. The numbers
 * are chosen so the breakdown identity is checkable by hand on each branch and on the
 * consolidated total.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Sales reports — warehouse filter")
class SalesReportWarehouseFilterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    private WarehouseEntity branchA;
    private WarehouseEntity branchB;
    private ProductEntity product;

    // Branch A: 2 x (base 1000 -> effective 1100) = 2200, minus a 100 manual discount.
    private static final BigDecimal A_LIST_PRICE = new BigDecimal("2000.0000");
    private static final BigDecimal A_SURCHARGE = new BigDecimal("200.0000");
    private static final BigDecimal A_MANUAL = new BigDecimal("-100.0000");
    private static final BigDecimal A_RETURN = new BigDecimal("-500.0000");
    private static final BigDecimal A_NET = new BigDecimal("1600.0000");

    // Branch B: 1 x 3000 at list price, nothing else.
    private static final BigDecimal B_LIST_PRICE = new BigDecimal("3000.0000");
    private static final BigDecimal B_NET = new BigDecimal("3000.0000");

    @BeforeEach
    void setUp() {
        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");
        PaymentMethodEntity card = persistPaymentMethod("Tarjeta");

        branchA = persistWarehouse("Sucursal A");
        branchB = persistWarehouse("Sucursal B");
        product = persistProduct();

        // --- Branch A: surcharged sale with a manual discount ---
        TransactionEntity saleA = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 10, 10, 0), new BigDecimal("2100.0000"), card, user);
        persistSurchargedDetail(saleA, "2.000", "1000.0000", "1100.0000");
        SaleEntity saleHeaderA = persistSale(saleA, branchA, A_MANUAL);

        // --- Branch A: a return against that sale ---
        // Physically processed at branch B (that is where the goods came back in, and where
        // the stock movement lives), but attributed to branch A because that is where the
        // revenue was booked. This query never looks at inventory_movements, by design.
        TransactionEntity returnTx = persistReturnTransaction(
                LocalDateTime.of(2026, 3, 11, 10, 0), A_RETURN, user);
        persistDetail(returnTx, "1.000", "-500.0000");
        persistSaleReturn(returnTx, saleHeaderA);

        // --- Branch B: plain sale at list price ---
        TransactionEntity saleB = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 10, 12, 0), B_NET, cash, user);
        persistDetail(saleB, "1.000", "3000.0000");
        persistSale(saleB, branchB, null);

        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("summary")
    class Summary {

        @Test
        @DisplayName("Filtered by a branch, returns only that branch's figures")
        void filtersByBranch() {
            SalesSummaryProjection a = summaryFor(branchA.getId());

            assertThat(a.getListPriceSales()).isEqualByComparingTo(A_LIST_PRICE);
            assertThat(a.getPaymentMethodSurcharges()).isEqualByComparingTo(A_SURCHARGE);
            assertThat(a.getManualAdjustments()).isEqualByComparingTo(A_MANUAL);
            assertThat(a.getReturnsTotal()).isEqualByComparingTo(A_RETURN);
            assertThat(a.getNetSales()).isEqualByComparingTo(A_NET);

            SalesSummaryProjection b = summaryFor(branchB.getId());

            assertThat(b.getListPriceSales()).isEqualByComparingTo(B_LIST_PRICE);
            assertThat(b.getPaymentMethodSurcharges()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(b.getManualAdjustments()).isEqualByComparingTo(BigDecimal.ZERO);
            // Branch B processed the return, but it belongs to branch A's revenue.
            assertThat(b.getReturnsTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(b.getNetSales()).isEqualByComparingTo(B_NET);
        }

        @Test
        @DisplayName("The breakdown identity holds on each branch and consolidated")
        void identityHoldsUnderFilter() {
            assertIdentityHolds(summaryFor(branchA.getId()));
            assertIdentityHolds(summaryFor(branchB.getId()));
            assertIdentityHolds(summaryFor(null));
        }

        @Test
        @DisplayName("Unfiltered, the consolidated total is the sum of both branches")
        void consolidatedSumsBothBranches() {
            SalesSummaryProjection all = summaryFor(null);

            assertThat(all.getListPriceSales()).isEqualByComparingTo(A_LIST_PRICE.add(B_LIST_PRICE));
            assertThat(all.getPaymentMethodSurcharges()).isEqualByComparingTo(A_SURCHARGE);
            assertThat(all.getManualAdjustments()).isEqualByComparingTo(A_MANUAL);
            assertThat(all.getReturnsTotal()).isEqualByComparingTo(A_RETURN);
            assertThat(all.getNetSales()).isEqualByComparingTo(A_NET.add(B_NET));
            assertThat(all.getTransactions()).isEqualTo(2L);
        }

        /** listPriceSales + paymentMethodSurcharges + manualAdjustments + returns == netSales. */
        private void assertIdentityHolds(SalesSummaryProjection row) {
            BigDecimal parts = row.getListPriceSales()
                    .add(row.getPaymentMethodSurcharges())
                    .add(row.getManualAdjustments())
                    .add(row.getReturnsTotal());

            assertThat(parts).isEqualByComparingTo(row.getNetSales());
        }

        private SalesSummaryProjection summaryFor(Long warehouseId) {
            return transactionRepository.getSalesSummaryReportRaw(null, null, warehouseId);
        }
    }

    @Nested
    @DisplayName("the other three reports")
    class OtherReports {

        @Test
        @DisplayName("by-product counts the return against the original sale's branch")
        void byProductAttributesReturnToOriginalBranch() {
            List<SalesByProductResponse> a =
                    transactionRepository.getSalesByProductReport(null, null, branchA.getId());

            assertThat(a).hasSize(1);
            // 2 sold at branch A, and the 1 returned unit counted here too.
            assertThat(a.get(0).quantitySold()).isEqualByComparingTo(new BigDecimal("2.000"));
            assertThat(a.get(0).quantityReturned()).isEqualByComparingTo(new BigDecimal("1.000"));

            List<SalesByProductResponse> b =
                    transactionRepository.getSalesByProductReport(null, null, branchB.getId());

            assertThat(b).hasSize(1);
            assertThat(b.get(0).quantitySold()).isEqualByComparingTo(new BigDecimal("1.000"));
            // The return was processed here, but it is not branch B's.
            assertThat(b.get(0).quantityReturned()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("daily reports each branch separately and both consolidated")
        void dailyFiltersByBranch() {
            List<SalesDailyProjection> a =
                    transactionRepository.getSalesDailyReportRaw(null, null, branchA.getId());

            // Branch A: the sale on the 10th, the return on the 11th.
            assertThat(a).hasSize(2);
            assertThat(a.stream().map(SalesDailyProjection::getNetSales)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(A_NET);

            List<SalesDailyProjection> b =
                    transactionRepository.getSalesDailyReportRaw(null, null, branchB.getId());

            assertThat(b).hasSize(1);
            assertThat(b.get(0).getNetSales()).isEqualByComparingTo(B_NET);

            List<SalesDailyProjection> all =
                    transactionRepository.getSalesDailyReportRaw(null, null, null);

            assertThat(all.stream().map(SalesDailyProjection::getNetSales)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(A_NET.add(B_NET));
        }

        @Test
        @DisplayName("by-payment-method only shows the methods used at that branch")
        void byPaymentMethodFiltersByBranch() {
            List<SalesByPaymentMethodResponse> a =
                    transactionRepository.getSalesByPaymentMethodReport(null, null, branchA.getId());

            assertThat(a).hasSize(1);
            assertThat(a.get(0).paymentMethodName()).isEqualTo("Tarjeta");

            List<SalesByPaymentMethodResponse> b =
                    transactionRepository.getSalesByPaymentMethodReport(null, null, branchB.getId());

            assertThat(b).hasSize(1);
            assertThat(b.get(0).paymentMethodName()).isEqualTo("Cash");

            List<SalesByPaymentMethodResponse> all =
                    transactionRepository.getSalesByPaymentMethodReport(null, null, null);

            assertThat(all).hasSize(2);
        }
    }

    // --- fixture helpers ---

    private UserEntity persistUser() {
        return entityManager.persistAndFlush(UserEntity.builder()
                .name("Admin").username("admin_wh_filter").passwordHash("hash").active(true).build());
    }

    private PaymentMethodEntity persistPaymentMethod(String name) {
        return entityManager.persistAndFlush(
                PaymentMethodEntity.builder().name(name).active(true).build());
    }

    private WarehouseEntity persistWarehouse(String name) {
        return entityManager.persistAndFlush(
                WarehouseEntity.builder().name(name).active(true).build());
    }

    private ProductEntity persistProduct() {
        return entityManager.persistAndFlush(ProductEntity.builder()
                .name("Pollo entero").description("Pollo entero")
                .price(new BigDecimal("1000.0000")).sku("POLLO_WH").active(true).build());
    }

    private TransactionEntity persistSaleTransaction(LocalDateTime date,
                                                     BigDecimal total,
                                                     PaymentMethodEntity paymentMethod,
                                                     UserEntity user) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED)
                .total(total)
                .paymentMethod(paymentMethod)
                .createdByUserEntity(user)
                .description("Test sale")
                .build());

        // date is @CreationTimestamp, so it is overwritten on insert and set afterwards.
        transaction.setDate(date);
        return entityManager.persistAndFlush(transaction);
    }

    private TransactionEntity persistReturnTransaction(LocalDateTime date,
                                                       BigDecimal total,
                                                       UserEntity user) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(TransactionType.RETURN)
                .status(TransactionStatus.CONFIRMED)
                .total(total)
                .paymentMethod(null)
                .createdByUserEntity(user)
                .description("Test return")
                .build());

        transaction.setDate(date);
        return entityManager.persistAndFlush(transaction);
    }

    private void persistDetail(TransactionEntity transaction, String quantity, String unitPrice) {
        entityManager.persistAndFlush(TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description(product.getName())
                .quantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal(unitPrice))
                .build());
    }

    private void persistSurchargedDetail(TransactionEntity transaction,
                                         String quantity,
                                         String baseUnitPrice,
                                         String effectiveUnitPrice) {
        entityManager.persistAndFlush(TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description(product.getName())
                .quantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal(effectiveUnitPrice))
                .baseUnitPrice(new BigDecimal(baseUnitPrice))
                .appliedPercentage(new BigDecimal("10.0000"))
                .appliedMethodName("Tarjeta")
                .build());
    }

    private SaleEntity persistSale(TransactionEntity transaction,
                                   WarehouseEntity warehouse,
                                   BigDecimal adjustmentAmount) {
        return entityManager.persistAndFlush(SaleEntity.builder()
                .transaction(transaction)
                .warehouse(warehouse)
                .taxTotal(BigDecimal.ZERO)
                .adjustmentType(adjustmentAmount == null ? AdjustmentType.NONE : AdjustmentType.FIXED)
                .adjustmentValue(adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount)
                .adjustmentAmount(adjustmentAmount == null ? BigDecimal.ZERO : adjustmentAmount)
                .build());
    }

    private void persistSaleReturn(TransactionEntity returnTransaction, SaleEntity originalSale) {
        entityManager.persistAndFlush(SaleReturnEntity.builder()
                .transaction(returnTransaction)
                .originalSale(originalSale)
                .reason("Producto fallado")
                .build());
    }
}
