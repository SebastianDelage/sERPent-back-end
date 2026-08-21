package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.reports.repository.projection.SalesSummaryProjection;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.transactions.domain.entity.CustomerPaymentEntity;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.PurchaseEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import com.empresa.serpent.transactions.domain.entity.SupplierPaymentEntity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Current accounts, and the line they must never cross into the reports.
 *
 * <p>Fixture: one customer buys 10000 on account and 5000 in cash, then pays 3000. One
 * supplier is owed 8000 on account and gets 2000. The numbers are chosen so every figure
 * below is checkable by hand.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Current accounts")
class CurrentAccountTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleReturnRepository saleReturnRepository;

    @Autowired
    private CustomerPaymentRepository customerPaymentRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private SupplierPaymentRepository supplierPaymentRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    private static final BigDecimal CREDIT_SALE = new BigDecimal("10000.0000");
    private static final BigDecimal CASH_SALE = new BigDecimal("5000.0000");
    private static final BigDecimal COLLECTED = new BigDecimal("3000.0000");
    private static final BigDecimal CREDIT_PURCHASE = new BigDecimal("8000.0000");
    private static final BigDecimal PAID_TO_SUPPLIER = new BigDecimal("2000.0000");

    private UserEntity user;
    private PaymentMethodEntity cash;
    private WarehouseEntity warehouse;
    private ProductEntity product;
    private CustomerEntity customer;
    private SupplierEntity supplier;
    private SaleEntity creditSale;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(UserEntity.builder()
                .name("Admin").username("admin_account").passwordHash("hash").active(true).build());
        cash = entityManager.persistAndFlush(
                PaymentMethodEntity.builder().name("Efectivo").active(true).build());
        warehouse = entityManager.persistAndFlush(
                WarehouseEntity.builder().name("Central").active(true).build());
        product = entityManager.persistAndFlush(ProductEntity.builder()
                .name("Pollo entero").description("Pollo entero")
                .price(new BigDecimal("1000.0000")).sku("POLLO_ACC").active(true).build());
        customer = entityManager.persistAndFlush(CustomerEntity.builder()
                .name("Vecino del barrio").active(true).build());
        supplier = entityManager.persistAndFlush(SupplierEntity.builder()
                .name("Distribuidora Norte").active(true).build());

        // Sold on account: no payment method, the customer owes it.
        TransactionEntity creditTx = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 10, 10, 0), CREDIT_SALE, null);
        persistDetail(creditTx, "10.000", "1000.0000");
        creditSale = persistSale(creditTx, true);

        // Sold and collected the same moment: the ordinary case.
        TransactionEntity cashTx = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 10, 11, 0), CASH_SALE, cash);
        persistDetail(cashTx, "5.000", "1000.0000");
        persistSale(cashTx, false);

        persistCustomerPayment(LocalDate.of(2026, 3, 15), COLLECTED);

        TransactionEntity purchaseTx = persistPurchaseTransaction(
                LocalDateTime.of(2026, 3, 11, 9, 0), CREDIT_PURCHASE);
        persistPurchase(purchaseTx, true);
        persistSupplierPayment(LocalDate.of(2026, 3, 16), PAID_TO_SUPPLIER);
    }

    @Nested
    @DisplayName("customer balance")
    class CustomerBalance {

        @Test
        @DisplayName("Only the credit sale counts, and the payment lowers it")
        void balanceIsCreditSalesMinusPayments() {
            // The cash sale never enters the balance: it was paid on the spot.
            assertThat(saleRepository.sumCreditSalesByCustomerId(customer.getId()))
                    .isEqualByComparingTo(CREDIT_SALE);
            assertThat(customerPaymentRepository.sumByCustomerId(customer.getId()))
                    .isEqualByComparingTo(COLLECTED);

            assertThat(balance()).isEqualByComparingTo("7000.0000");
        }

        @Test
        @DisplayName("A return against a credit sale lowers the balance")
        void returnAgainstCreditSaleLowersTheBalance() {
            persistReturnAgainst(creditSale, new BigDecimal("-1500.0000"),
                    LocalDateTime.of(2026, 3, 12, 10, 0));

            // 10000 owed, 1500 given back in goods, 3000 paid.
            assertThat(saleReturnRepository.sumAgainstCreditSalesByCustomerId(customer.getId()))
                    .isEqualByComparingTo("-1500.0000");
            assertThat(balance()).isEqualByComparingTo("5500.0000");
        }

        @Test
        @DisplayName("Paying more than owed leaves a credit in the customer's favour")
        void overpaymentGoesNegative() {
            persistCustomerPayment(LocalDate.of(2026, 3, 20), new BigDecimal("9000.0000"));

            // 10000 owed against 12000 collected: they are 2000 up, and that is real money.
            assertThat(balance()).isEqualByComparingTo("-2000.0000");
        }

        private BigDecimal balance() {
            return saleRepository.sumCreditSalesByCustomerId(customer.getId())
                    .add(saleReturnRepository.sumAgainstCreditSalesByCustomerId(customer.getId()))
                    .subtract(customerPaymentRepository.sumByCustomerId(customer.getId()));
        }
    }

    @Nested
    @DisplayName("supplier balance")
    class SupplierBalance {

        @Test
        @DisplayName("Only the credit purchase counts, and the payment lowers it")
        void balanceIsCreditPurchasesMinusPayments() {
            assertThat(purchaseRepository.sumCreditPurchasesBySupplierId(supplier.getId()))
                    .isEqualByComparingTo(CREDIT_PURCHASE);
            assertThat(supplierPaymentRepository.sumBySupplierId(supplier.getId()))
                    .isEqualByComparingTo(PAID_TO_SUPPLIER);

            assertThat(purchaseRepository.sumCreditPurchasesBySupplierId(supplier.getId())
                    .subtract(supplierPaymentRepository.sumBySupplierId(supplier.getId())))
                    .isEqualByComparingTo("6000.0000");
        }

        @Test
        @DisplayName("Paying a supplier is not an expense")
        void supplierPaymentIsNotAnExpense() {
            // The purchase already hit the result when the goods came in. If the payment
            // were also booked as an expense the same money would count twice.
            assertThat(expenseRepository.findAll()).isEmpty();

            assertThat(transactionRepository.findAll())
                    .noneMatch(t -> t.getType() == TransactionType.EXPENSE);
        }
    }

    @Nested
    @DisplayName("the sales reports")
    class Reports {

        @Test
        @DisplayName("A credit sale is a sale: it counts in net sales")
        void creditSaleCountsAsRevenue() {
            SalesSummaryProjection row = summary();

            // Both sales, whether collected or not.
            assertThat(row.getTransactions()).isEqualTo(2L);
            assertThat(row.getNetSales()).isEqualByComparingTo(CREDIT_SALE.add(CASH_SALE));
        }

        @Test
        @DisplayName("The breakdown identity still holds with a credit sale in the period")
        void identityHolds() {
            SalesSummaryProjection row = summary();

            BigDecimal parts = row.getListPriceSales()
                    .add(row.getPaymentMethodSurcharges())
                    .add(row.getManualAdjustments())
                    .add(row.getReturnsTotal());

            assertThat(parts).isEqualByComparingTo(row.getNetSales());
        }

        @Test
        @DisplayName("Collecting a debt is not a sale: net sales do not move")
        void collectingDoesNotAddRevenue() {
            BigDecimal before = summary().getNetSales();
            long transactionsBefore = summary().getTransactions();

            persistCustomerPayment(LocalDate.of(2026, 3, 18), new BigDecimal("4000.0000"));

            // The sale counted when it happened. Counting the collection too would report
            // the same money twice, which is the whole reason payments are not transactions.
            assertThat(summary().getNetSales()).isEqualByComparingTo(before);
            assertThat(summary().getTransactions()).isEqualTo(transactionsBefore);
        }

        @Test
        @DisplayName("A credit sale is absent from the by-payment-method rows, and reported apart")
        void creditSaleIsNotAttributedToAnyMethod() {
            List<SalesByPaymentMethodResponse> methods =
                    transactionRepository.getSalesByPaymentMethodReport(null, null, true, List.of());

            // Only the cash sale: money that never arrived must not be shown as if it had.
            assertThat(methods).hasSize(1);
            assertThat(methods.get(0).paymentMethodName()).isEqualTo("Efectivo");
            assertThat(methods.get(0).totalRevenue()).isEqualByComparingTo(CASH_SALE);

            BigDecimal creditSales = saleRepository.sumCreditSales(null, null, true, List.of());
            assertThat(creditSales).isEqualByComparingTo(CREDIT_SALE);

            // The invariant that replaced "the rows add up to total sales".
            assertThat(methods.get(0).totalRevenue().add(creditSales))
                    .isEqualByComparingTo(summary().getNetSales());
        }

        private SalesSummaryProjection summary() {
            return transactionRepository.getSalesSummaryReportRaw(null, null, true, List.of());
        }
    }

    // --- fixture helpers ---

    private TransactionEntity persistSaleTransaction(LocalDateTime date,
                                                     BigDecimal total,
                                                     PaymentMethodEntity paymentMethod) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED)
                .total(total)
                .paymentMethod(paymentMethod)
                .createdByUserEntity(user)
                .description("Venta de prueba")
                .build());

        // date is @CreationTimestamp, so it is overwritten on insert and set afterwards.
        transaction.setDate(date);
        return entityManager.persistAndFlush(transaction);
    }

    private TransactionEntity persistPurchaseTransaction(LocalDateTime date, BigDecimal total) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(TransactionType.PURCHASE)
                .status(TransactionStatus.CONFIRMED)
                .total(total)
                .paymentMethod(null)
                .createdByUserEntity(user)
                .description("Compra de prueba")
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

    private SaleEntity persistSale(TransactionEntity transaction, boolean onCredit) {
        return entityManager.persistAndFlush(SaleEntity.builder()
                .transaction(transaction)
                .warehouse(warehouse)
                .customer(onCredit ? customer : null)
                .onCredit(onCredit)
                .taxTotal(BigDecimal.ZERO)
                .adjustmentType(AdjustmentType.NONE)
                .adjustmentValue(BigDecimal.ZERO)
                .adjustmentAmount(BigDecimal.ZERO)
                .build());
    }

    private void persistPurchase(TransactionEntity transaction, boolean onCredit) {
        entityManager.persistAndFlush(PurchaseEntity.builder()
                .transaction(transaction)
                .supplier(supplier)
                .warehouse(warehouse)
                .onCredit(onCredit)
                .build());
    }

    private void persistReturnAgainst(SaleEntity originalSale, BigDecimal total, LocalDateTime date) {
        TransactionEntity transaction = entityManager.persistAndFlush(TransactionEntity.builder()
                .type(TransactionType.RETURN)
                .status(TransactionStatus.CONFIRMED)
                .total(total)
                .paymentMethod(null)
                .createdByUserEntity(user)
                .description("Devolución de prueba")
                .build());

        transaction.setDate(date);
        entityManager.persistAndFlush(transaction);

        entityManager.persistAndFlush(SaleReturnEntity.builder()
                .transaction(transaction)
                .originalSale(originalSale)
                .reason("Producto fallado")
                .build());
    }

    private void persistCustomerPayment(LocalDate date, BigDecimal amount) {
        entityManager.persistAndFlush(CustomerPaymentEntity.builder()
                .customer(customer)
                .paymentMethod(cash)
                .amount(amount)
                .paymentDate(date)
                .createdByUserEntity(user)
                .build());
    }

    private void persistSupplierPayment(LocalDate date, BigDecimal amount) {
        entityManager.persistAndFlush(SupplierPaymentEntity.builder()
                .supplier(supplier)
                .paymentMethod(cash)
                .amount(amount)
                .paymentDate(date)
                .createdByUserEntity(user)
                .build());
    }
}
