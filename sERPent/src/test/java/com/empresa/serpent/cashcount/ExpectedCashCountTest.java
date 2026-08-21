package com.empresa.serpent.cashcount;

import com.empresa.serpent.cashcount.domain.entity.CashCountEntity;
import com.empresa.serpent.cashcount.service.ExpectedCashCountService;
import com.empresa.serpent.cashcount.web.dto.response.ExpectedCashCountMethodResponse;
import com.empresa.serpent.cashcount.web.dto.response.ExpectedCashCountResponse;
import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.transactions.domain.entity.*;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the till is expected to hold, across every source that moves money.
 *
 * <p>Fixture: one branch, two payment methods (cash and card), and one movement per source
 * with a round, distinct amount, so a wrong total says which source is wrong by how much it
 * is off. Everything is dated explicitly — {@code date} and {@code createdAt} are
 * {@code @CreationTimestamp} columns, so the test overwrites them after persisting to place
 * each movement inside or outside the shift on purpose.
 *
 * <p>CASH: 1000 float + 5000 sale + 700 collection − 300 refund − 400 expense − 900 supplier
 * payment − 2000 cash purchase = 3100.
 * <p>CARD: 8000 sale + 1500 collection − 600 refund = 8900. Outflows never touch it.
 */
@DataJpaTest
@Import(ExpectedCashCountService.class)
@ActiveProfiles("test")
@DisplayName("Expected till amounts")
class ExpectedCashCountTest {

    private static final LocalDateTime SHIFT_START = LocalDateTime.of(2026, 3, 10, 8, 0);
    private static final LocalDateTime DURING_SHIFT = LocalDateTime.of(2026, 3, 10, 12, 0);
    private static final LocalDateTime BEFORE_SHIFT = LocalDateTime.of(2026, 3, 9, 12, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 20, 0);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ExpectedCashCountService service;

    /** Only there to satisfy the constructor: the tests call build(), which never scopes. */
    @MockitoBean
    private WarehouseScopeService warehouseScopeService;

    private WarehouseEntity central;
    private WarehouseEntity north;
    private PaymentMethodEntity cash;
    private PaymentMethodEntity card;
    private UserEntity user;
    private ProductEntity product;
    private CustomerEntity customer;
    private SupplierEntity supplier;
    private ExpenseCategoryEntity category;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(UserEntity.builder()
                .name("Cajera").username("cajera_ecc").passwordHash("hash").active(true).build());

        central = persistWarehouse("Depósito Central");
        north = persistWarehouse("Sucursal Norte");

        cash = entityManager.persistAndFlush(PaymentMethodEntity.builder()
                .name("Efectivo").isCash(true).active(true).build());
        card = entityManager.persistAndFlush(PaymentMethodEntity.builder()
                .name("Tarjeta").isCash(false).active(true).build());

        product = entityManager.persistAndFlush(ProductEntity.builder()
                .name("Pollo").price(new BigDecimal("1000.0000")).sku("POLLO_ECC").active(true).build());
        customer = entityManager.persistAndFlush(CustomerEntity.builder()
                .name("Cliente ECC").active(true).build());
        supplier = entityManager.persistAndFlush(SupplierEntity.builder()
                .name("Proveedor ECC").active(true).build());
        category = entityManager.persistAndFlush(ExpenseCategoryEntity.builder()
                .name("Insumos ECC").active(true).build());
    }

    @Nested
    @DisplayName("the five sources")
    class Sources {

        @Test
        @DisplayName("A cash sale, a collection, a refund, an expense and a supplier payment all land")
        void everySourceCounts() {
            saleOf("5000.00", cash, central, DURING_SHIFT, false);
            collectionOf("700.00", cash, central, DURING_SHIFT);
            refundOf("300.00", cash, central, DURING_SHIFT, false);
            expenseOf("400.00", cash, central, DURING_SHIFT);
            supplierPaymentOf("900.00", cash, central, DURING_SHIFT);
            cashPurchaseOf("2000.00", cash, central, DURING_SHIFT);

            ExpectedCashCountMethodResponse row = cashRow(compute("1000.00"));

            assertThat(row.sales()).isEqualByComparingTo("5000.00");
            assertThat(row.customerPayments()).isEqualByComparingTo("700.00");
            assertThat(row.returns()).isEqualByComparingTo("-300.00");
            assertThat(row.expenses()).isEqualByComparingTo("400.00");
            assertThat(row.supplierPayments()).isEqualByComparingTo("900.00");
            assertThat(row.purchases()).isEqualByComparingTo("2000.00");

            // 1000 + 5000 + 700 - 300 - 400 - 900 - 2000
            assertThat(row.expectedAmount()).isEqualByComparingTo("3100.00");
        }

        @Test
        @DisplayName("A card shift only counts money in: the outflows never touch it")
        void nonCashIgnoresOutflows() {
            saleOf("8000.00", card, central, DURING_SHIFT, false);
            collectionOf("1500.00", card, central, DURING_SHIFT);
            refundOf("600.00", card, central, DURING_SHIFT, false);
            // Paid by card, so it left the bank and not the drawer: the posnet batch for the
            // shift does not shrink because of it.
            expenseOf("400.00", card, central, DURING_SHIFT);
            supplierPaymentOf("900.00", card, central, DURING_SHIFT);

            ExpectedCashCountMethodResponse row = rowFor(compute("1000.00"), card);

            assertThat(row.expenses()).isEqualByComparingTo("0.00");
            assertThat(row.supplierPayments()).isEqualByComparingTo("0.00");
            // 8000 + 1500 - 600, and no opening float either.
            assertThat(row.openingFloat()).isEqualByComparingTo("0.00");
            assertThat(row.expectedAmount()).isEqualByComparingTo("8900.00");
        }

        @Test
        @DisplayName("Another branch's movements stay out of this branch's count")
        void otherBranchesAreExcluded() {
            saleOf("5000.00", cash, central, DURING_SHIFT, false);
            saleOf("9999.00", cash, north, DURING_SHIFT, false);
            expenseOf("111.00", cash, north, DURING_SHIFT);

            assertThat(cashRow(compute("0.00")).expectedAmount()).isEqualByComparingTo("5000.00");
        }
    }

    @Nested
    @DisplayName("sales taken on credit")
    class CreditSales {

        @Test
        @DisplayName("Do not add to the till: nothing was collected")
        void creditSaleDoesNotCount() {
            saleOf("5000.00", cash, central, DURING_SHIFT, false);
            // A credit sale carries no payment method at all — the money never arrived.
            creditSaleOf("4000.00", central, DURING_SHIFT);

            ExpectedCashCountResponse expected = compute("0.00");

            assertThat(cashRow(expected).sales()).isEqualByComparingTo("5000.00");
            assertThat(cashRow(expected).expectedAmount()).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("A return against one does not take money out either: it lowers the balance")
        void creditReturnDoesNotSubtract() {
            saleOf("5000.00", cash, central, DURING_SHIFT, false);
            creditSaleOf("4000.00", central, DURING_SHIFT);
            refundOf("1200.00", null, central, DURING_SHIFT, true);

            ExpectedCashCountResponse expected = compute("0.00");

            assertThat(cashRow(expected).returns()).isEqualByComparingTo("0.00");
            assertThat(cashRow(expected).expectedAmount()).isEqualByComparingTo("5000.00");
        }
    }

    @Nested
    @DisplayName("the period")
    class Period {

        @Test
        @DisplayName("Starts at the last close: what the previous shift counted is not counted twice")
        void startsAtTheLastClose() {
            saleOf("7777.00", cash, central, BEFORE_SHIFT, false);
            saleOf("5000.00", cash, central, DURING_SHIFT, false);

            ExpectedCashCountResponse expected =
                    service.build(central, SHIFT_START, NOW, new BigDecimal("0.00"));

            assertThat(cashRow(expected).sales()).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("A movement exactly at the last close belongs to the shift that counted it")
        void theBoundaryBelongsToThePreviousShift() {
            // Strictly greater than periodFrom: the previous close already included this one,
            // and counting it again would make the same money appear in two shifts.
            saleOf("640.00", cash, central, SHIFT_START, false);

            assertThat(cashRow(service.build(central, SHIFT_START, NOW, new BigDecimal("0.00")))
                    .sales()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("With no previous close it covers everything, and says so")
        void firstCloseCoversEverything() {
            saleOf("7777.00", cash, central, BEFORE_SHIFT, false);
            saleOf("5000.00", cash, central, DURING_SHIFT, false);

            ExpectedCashCountResponse expected = service.build(central, null, NOW, new BigDecimal("0.00"));

            assertThat(cashRow(expected).sales()).isEqualByComparingTo("12777.00");
            assertThat(expected.periodFrom()).isNull();
            assertThat(expected.warnings())
                    .anySatisfy(warning -> assertThat(warning).contains("primer cierre"));
        }

        @Test
        @DisplayName("The anchor is the branch's own last close, not another branch's")
        void anchorIsPerBranch() {
            entityManager.persistAndFlush(CashCountEntity.builder()
                    .warehouse(north).createdByUserEntity(user)
                    .closedAt(DURING_SHIFT).openingFloat(BigDecimal.ZERO)
                    .unattributedAmount(BigDecimal.ZERO).unattributedCount(0)
                    .build());

            assertThat(service.lastCloseOf(central.getId())).isEmpty();
            assertThat(service.lastCloseOf(north.getId())).contains(DURING_SHIFT);
        }
    }

    @Nested
    @DisplayName("what the count cannot explain")
    class Unexplained {

        @Test
        @DisplayName("An expense with no payment method is reported apart, not folded in")
        void unattributedExpenseIsSurfaced() {
            saleOf("5000.00", cash, central, DURING_SHIFT, false);
            expenseOf("450.00", null, central, DURING_SHIFT);

            ExpectedCashCountResponse expected = compute("0.00");

            // Not subtracted anywhere: nobody knows which method paid it.
            assertThat(cashRow(expected).expenses()).isEqualByComparingTo("0.00");
            assertThat(cashRow(expected).expectedAmount()).isEqualByComparingTo("5000.00");

            assertThat(expected.unattributedCount()).isEqualTo(1);
            assertThat(expected.unattributedAmount()).isEqualByComparingTo("450.00");
            assertThat(expected.warnings())
                    .anySatisfy(warning -> assertThat(warning).contains("no dice"));
        }

        @Test
        @DisplayName("With no method marked as cash it says so instead of reporting zero")
        void missingCashMethodIsStated() {
            cash.setIsCash(false);
            entityManager.persistAndFlush(cash);
            entityManager.clear();

            saleOf("5000.00", cash, central, DURING_SHIFT, false);

            ExpectedCashCountResponse expected = compute("1000.00");

            assertThat(expected.cashConfigured()).isFalse();
            assertThat(expected.warnings())
                    .anySatisfy(warning -> assertThat(warning).contains("marcado como efectivo"));
            // The float belongs to a drawer nobody can identify, so it is nowhere.
            assertThat(expected.methods())
                    .allSatisfy(row -> assertThat(row.openingFloat()).isEqualByComparingTo("0.00"));
        }
    }

    // --- fixture helpers -------------------------------------------------------------

    private ExpectedCashCountResponse compute(String openingFloat) {
        return service.build(central, SHIFT_START, NOW, new BigDecimal(openingFloat));
    }

    private ExpectedCashCountMethodResponse cashRow(ExpectedCashCountResponse expected) {
        return rowFor(expected, cash);
    }

    private ExpectedCashCountMethodResponse rowFor(ExpectedCashCountResponse expected,
                                                   PaymentMethodEntity method) {
        return expected.methods().stream()
                .filter(row -> row.paymentMethodId().equals(method.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for " + method.getName()));
    }

    private WarehouseEntity persistWarehouse(String name) {
        return entityManager.persistAndFlush(
                WarehouseEntity.builder().name(name).active(true).build());
    }

    private SaleEntity saleOf(String total, PaymentMethodEntity method, WarehouseEntity warehouse,
                              LocalDateTime when, boolean onCredit) {
        TransactionEntity transaction = transactionOf(TransactionType.SALE, total, method, when);

        SaleEntity sale = SaleEntity.builder()
                .transaction(transaction)
                .warehouse(warehouse)
                .onCredit(onCredit)
                .customer(onCredit ? customer : null)
                .build();

        return entityManager.persistAndFlush(sale);
    }

    private void creditSaleOf(String total, WarehouseEntity warehouse, LocalDateTime when) {
        saleOf(total, null, warehouse, when, true);
    }

    /**
     * {@code amount} is given positive; returns are stored negative, as the app stores them.
     *
     * <p>The sale being returned is dated BEFORE the shift on purpose. It has to exist for
     * the return to hang off, but it belongs to an earlier shift — otherwise the fixture
     * would be adding a sale to the very total it is checking.
     */
    private void refundOf(String amount, PaymentMethodEntity method, WarehouseEntity warehouse,
                          LocalDateTime when, boolean againstCreditSale) {
        SaleEntity originalSale = againstCreditSale
                ? saleOf("9999.00", null, warehouse, BEFORE_SHIFT, true)
                : saleOf("9999.00", cash, warehouse, BEFORE_SHIFT, false);

        TransactionEntity transaction = transactionOf(
                TransactionType.RETURN, "-" + amount, method, when);

        entityManager.persistAndFlush(SaleReturnEntity.builder()
                .transaction(transaction)
                .originalSale(originalSale)
                .build());
    }

    private void expenseOf(String total, PaymentMethodEntity method, WarehouseEntity warehouse,
                           LocalDateTime when) {
        TransactionEntity transaction = transactionOf(TransactionType.EXPENSE, total, method, when);

        entityManager.persistAndFlush(ExpenseEntity.builder()
                .transaction(transaction)
                .warehouse(warehouse)
                .expenseCategory(category)
                .reimbursable(false)
                .build());
    }

    private void cashPurchaseOf(String total, PaymentMethodEntity method, WarehouseEntity warehouse,
                                LocalDateTime when) {
        TransactionEntity transaction = transactionOf(TransactionType.PURCHASE, total, method, when);

        entityManager.persistAndFlush(PurchaseEntity.builder()
                .transaction(transaction)
                .warehouse(warehouse)
                .supplier(supplier)
                .onCredit(false)
                .build());
    }

    private void collectionOf(String amount, PaymentMethodEntity method, WarehouseEntity warehouse,
                              LocalDateTime when) {
        CustomerPaymentEntity payment = entityManager.persistAndFlush(CustomerPaymentEntity.builder()
                .customer(customer)
                .warehouse(warehouse)
                .paymentMethod(method)
                .amount(new BigDecimal(amount))
                .paymentDate(when.toLocalDate())
                .createdByUserEntity(user)
                .build());

        stampCreatedAt("CustomerPaymentEntity", payment.getId(), when);
    }

    private void supplierPaymentOf(String amount, PaymentMethodEntity method, WarehouseEntity warehouse,
                                   LocalDateTime when) {
        SupplierPaymentEntity payment = entityManager.persistAndFlush(SupplierPaymentEntity.builder()
                .supplier(supplier)
                .warehouse(warehouse)
                .paymentMethod(method)
                .amount(new BigDecimal(amount))
                .paymentDate(when.toLocalDate())
                .createdByUserEntity(user)
                .build());

        stampCreatedAt("SupplierPaymentEntity", payment.getId(), when);
    }

    private TransactionEntity transactionOf(TransactionType type, String total,
                                            PaymentMethodEntity method, LocalDateTime when) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(type)
                .status(TransactionStatus.CONFIRMED)
                .total(new BigDecimal(total))
                .paymentMethod(method)
                .createdByUserEntity(user)
                .build());

        // date is a @CreationTimestamp, so it can only be moved after the insert. Placing
        // movements in time by hand is the whole point of the period tests.
        entityManager.getEntityManager()
                .createQuery("UPDATE TransactionEntity t SET t.date = :when WHERE t.id = :id")
                .setParameter("when", when)
                .setParameter("id", transaction.getId())
                .executeUpdate();
        entityManager.getEntityManager().refresh(transaction);

        return transaction;
    }

    private void stampCreatedAt(String entityName, Long id, LocalDateTime when) {
        entityManager.getEntityManager()
                .createQuery("UPDATE " + entityName + " p SET p.createdAt = :when WHERE p.id = :id")
                .setParameter("when", when)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

}
