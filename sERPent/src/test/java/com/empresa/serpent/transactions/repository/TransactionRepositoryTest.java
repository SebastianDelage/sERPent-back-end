package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.reports.repository.projection.SalesDailyProjection;
import com.empresa.serpent.reports.repository.projection.SalesSummaryProjection;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;

@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Should return sales by product report")
    void shouldReturnSalesByProductReport() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");

        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");
        ProductEntity pataMuslo = persistProduct("Pata muslo", "POLLO002");

        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0),
                new BigDecimal("9100.0000"),
                cash,
                user
        );

        persistDetail(sale, pollo, "Pollo entero", "1.000", "4500.0000");
        persistDetail(sale, pataMuslo, "Pata muslo", "1.000", "4600.0000");

        entityManager.flush();
        entityManager.clear();

        List<SalesByProductResponse> result = transactionRepository.getSalesByProductReport(null, null);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(0).quantitySold()).isEqualByComparingTo("1.000");
        assertThat(result.get(0).quantityReturned()).isEqualByComparingTo("0");
        assertThat(result.get(0).grossRevenue()).isEqualByComparingTo("4500.0000");
        assertThat(result.get(0).netRevenue()).isEqualByComparingTo("4500.0000");

        assertThat(result.get(1).productName()).isEqualTo("Pata muslo");
        assertThat(result.get(1).quantitySold()).isEqualByComparingTo("1.000");
        assertThat(result.get(1).netRevenue()).isEqualByComparingTo("4600.0000");
    }

    @Test
    @DisplayName("Should subtract returns from the product they belong to")
    void shouldSubtractReturnsInSalesByProductReport() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");
        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");

        // Sold 5 at 4500 = 22500.
        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("22500.0000"), cash, user);
        persistDetail(sale, pollo, "Pollo entero", "5.000", "4500.0000");

        // Returned 2 of them: -9000, stored negative.
        TransactionEntity returnTx = persistReturnTransaction(
                LocalDateTime.of(2026, 3, 13, 9, 0), new BigDecimal("-9000.0000"), user);
        persistDetail(returnTx, pollo, "Devolución", "2.000", "-4500.0000");

        entityManager.flush();
        entityManager.clear();

        List<SalesByProductResponse> result = transactionRepository.getSalesByProductReport(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(0).quantitySold()).isEqualByComparingTo("5.000");
        assertThat(result.get(0).quantityReturned()).isEqualByComparingTo("2.000");
        assertThat(result.get(0).grossRevenue()).isEqualByComparingTo("22500.0000");
        assertThat(result.get(0).returnedAmount()).isEqualByComparingTo("-9000.0000");
        assertThat(result.get(0).netRevenue()).isEqualByComparingTo("13500.0000");
    }

    @Test
    @DisplayName("Should return daily sales report grouped by date")
    void shouldReturnDailySalesReportGroupedByDate() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");

        persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0),
                new BigDecimal("5000.0000"),
                cash,
                user
        );

        persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 18, 30),
                new BigDecimal("4100.0000"),
                cash,
                user
        );

        entityManager.flush();
        entityManager.clear();

        List<SalesDailyProjection> result = transactionRepository.getSalesDailyReportRaw(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(result.get(0).getTransactions()).isEqualTo(2L);
        assertThat(result.get(0).getGrossSales()).isEqualByComparingTo("9100.0000");
        assertThat(result.get(0).getReturnsTotal()).isEqualByComparingTo("0");
        assertThat(result.get(0).getNetSales()).isEqualByComparingTo("9100.0000");
    }

    @Test
    @DisplayName("Should count a return on the day it was registered, not the day of the sale")
    void shouldCountReturnOnTheDayItWasRegistered() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");

        persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("9100.0000"), cash, user);

        // Registered the next day: it belongs to the 13th, not the 12th.
        persistReturnTransaction(
                LocalDateTime.of(2026, 3, 13, 9, 0), new BigDecimal("-9000.0000"), user);

        entityManager.flush();
        entityManager.clear();

        List<SalesDailyProjection> result = transactionRepository.getSalesDailyReportRaw(null, null);

        assertThat(result).hasSize(2);

        // Ordered by date DESC: the 13th first, carrying only the return.
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 3, 13));
        assertThat(result.get(0).getTransactions()).isEqualTo(0L);
        assertThat(result.get(0).getGrossSales()).isEqualByComparingTo("0");
        assertThat(result.get(0).getReturnsTotal()).isEqualByComparingTo("-9000.0000");
        // A day of returns with no sales nets negative. That is correct, not a bug.
        assertThat(result.get(0).getNetSales()).isEqualByComparingTo("-9000.0000");

        assertThat(result.get(1).getDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(result.get(1).getNetSales()).isEqualByComparingTo("9100.0000");
    }

    @Test
    @DisplayName("Should return sales by payment method report")
    void shouldReturnSalesByPaymentMethodReport() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");
        PaymentMethodEntity transfer = persistPaymentMethod("Transfer");

        persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0),
                new BigDecimal("9100.0000"),
                cash,
                user
        );

        persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 11, 0),
                new BigDecimal("3000.0000"),
                transfer,
                user
        );

        entityManager.flush();
        entityManager.clear();

        List<SalesByPaymentMethodResponse> result = transactionRepository.getSalesByPaymentMethodReport(null, null);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).paymentMethodName()).isEqualTo("Cash");
        assertThat(result.get(0).transactions()).isEqualTo(1L);
        assertThat(result.get(0).totalRevenue()).isEqualByComparingTo("9100.0000");

        assertThat(result.get(1).paymentMethodName()).isEqualTo("Transfer");
        assertThat(result.get(1).transactions()).isEqualTo(1L);
        assertThat(result.get(1).totalRevenue()).isEqualByComparingTo("3000.0000");
    }

    @Test
    @DisplayName("Should return sales summary report")
    void shouldReturnSalesSummaryReport() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");

        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");

        // Each sale carries the line that makes up its total: the list-price figure is
        // summed from the lines, so a total with no lines behind it would read as zero.
        TransactionEntity firstSale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0),
                new BigDecimal("9100.0000"),
                cash,
                user
        );
        persistDetail(firstSale, pollo, "Pollo entero", "1.000", "9100.0000");

        TransactionEntity secondSale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 12, 0),
                new BigDecimal("3000.0000"),
                cash,
                user
        );
        persistDetail(secondSale, pollo, "Pollo entero", "1.000", "3000.0000");

        entityManager.flush();
        entityManager.clear();

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw(null, null);

        assertThat(result).isNotNull();
        assertThat(result.getTransactions()).isEqualTo(2L);
        assertThat(result.getListPriceSales()).isEqualByComparingTo("12100.0000");
        assertThat(result.getPaymentMethodSurcharges()).isEqualByComparingTo("0");
        assertThat(result.getManualAdjustments()).isEqualByComparingTo("0");
        assertThat(result.getReturnsTotal()).isEqualByComparingTo("0");
        assertThat(result.getNetSales()).isEqualByComparingTo("12100.0000");
        assertThat(result.getAverageTicket()).isEqualByComparingTo("6050.0000");
    }

    @Test
    @DisplayName("Should split the summary into list price, returns and net")
    void shouldSplitSummaryIntoListPriceReturnsAndNet() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");
        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");

        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("22500.0000"), cash, user);
        persistDetail(sale, pollo, "Pollo entero", "5.000", "4500.0000");

        TransactionEntity returnTx = persistReturnTransaction(
                LocalDateTime.of(2026, 3, 13, 9, 0), new BigDecimal("-9000.0000"), user);

        entityManager.flush();
        entityManager.clear();

        // The negative total survives the round trip to the database.
        assertThat(transactionRepository.findById(returnTx.getId()))
                .get()
                .extracting(TransactionEntity::getTotal, as(BIG_DECIMAL))
                .isEqualByComparingTo("-9000.0000");

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw(null, null);

        // The return is not a sale, so it does not inflate the transaction count
        // nor the average ticket.
        assertThat(result.getTransactions()).isEqualTo(1L);
        assertThat(result.getListPriceSales()).isEqualByComparingTo("22500.0000");
        assertThat(result.getReturnsTotal()).isEqualByComparingTo("-9000.0000");
        assertThat(result.getNetSales()).isEqualByComparingTo("13500.0000");
        assertThat(result.getAverageTicket()).isEqualByComparingTo("22500.0000");
    }

    @Test
    @DisplayName("Should keep the parent transaction type mirrored on each detail row")
    void shouldMirrorTransactionTypeOnDetails() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");
        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");

        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("22500.0000"), cash, user);
        TransactionDetailEntity saleLine =
                persistDetail(sale, pollo, "Pollo entero", "5.000", "4500.0000");

        TransactionEntity returnTx = persistReturnTransaction(
                LocalDateTime.of(2026, 3, 13, 9, 0), new BigDecimal("-9000.0000"), user);
        TransactionDetailEntity returnLine =
                persistDetail(returnTx, pollo, "Devolución", "2.000", "-4500.0000");

        entityManager.flush();

        // The mirror column is what lets the sign CHECKs be scoped per type.
        assertThat(saleLine.getTransactionType()).isEqualTo(TransactionType.SALE);
        assertThat(returnLine.getTransactionType()).isEqualTo(TransactionType.RETURN);
        // Subtotal is derived, so the sign carried on unitPrice flows through.
        assertThat(returnLine.getSubtotal()).isEqualByComparingTo("-9000.0000");
    }

    /**
     * The identity the whole breakdown rests on. A sale that exercises every source at
     * once: 5 units listed at 4000 (20000), marked up 10% by the payment method (+2000,
     * so 22000 charged), then 2000 knocked off manually (20000 paid), with 9000 of it
     * later returned.
     */
    @Test
    @DisplayName("Should break the summary into parts that add up to the net")
    void shouldBreakSummaryIntoPartsThatAddUpToNet() {

        UserEntity user = persistUser();
        PaymentMethodEntity card = persistPaymentMethod("Tarjeta");
        ProductEntity cigarettes = persistProduct("Cigarrillos", "CIG001");

        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("20000.0000"), card, user);
        persistSurchargedDetail(sale, cigarettes, "5.000", "4000.0000", "4400.0000", "10.0000");
        persistSale(sale, "-2000.0000");

        persistReturnTransaction(
                LocalDateTime.of(2026, 3, 13, 9, 0), new BigDecimal("-9000.0000"), user);

        entityManager.flush();
        entityManager.clear();

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw(null, null);

        assertThat(result.getListPriceSales()).isEqualByComparingTo("20000.0000");
        assertThat(result.getPaymentMethodSurcharges()).isEqualByComparingTo("2000.0000");
        assertThat(result.getManualAdjustments()).isEqualByComparingTo("-2000.0000");
        assertThat(result.getReturnsTotal()).isEqualByComparingTo("-9000.0000");
        assertThat(result.getNetSales()).isEqualByComparingTo("11000.0000");

        // The four parts must reconstruct the net exactly — not to the cent, exactly.
        BigDecimal recomposed = result.getListPriceSales()
                .add(result.getPaymentMethodSurcharges())
                .add(result.getManualAdjustments())
                .add(result.getReturnsTotal());
        assertThat(recomposed).isEqualByComparingTo(result.getNetSales());
    }

    @Test
    @DisplayName("Should report list price equal to net when a sale carries no adjustment at all")
    void shouldReportListPriceEqualToNetWithoutAdjustments() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");
        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");

        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("9000.0000"), cash, user);
        persistDetail(sale, pollo, "Pollo entero", "2.000", "4500.0000");
        persistSale(sale, null);

        entityManager.flush();
        entityManager.clear();

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw(null, null);

        assertThat(result.getListPriceSales()).isEqualByComparingTo("9000.0000");
        assertThat(result.getPaymentMethodSurcharges()).isEqualByComparingTo("0");
        assertThat(result.getManualAdjustments()).isEqualByComparingTo("0");
        assertThat(result.getReturnsTotal()).isEqualByComparingTo("0");
        assertThat(result.getNetSales()).isEqualByComparingTo("9000.0000");
    }

    @Test
    @DisplayName("Should report a payment-method surcharge on its own line and nowhere else")
    void shouldReportPaymentMethodSurchargeOnly() {

        UserEntity user = persistUser();
        PaymentMethodEntity card = persistPaymentMethod("Tarjeta");
        ProductEntity cigarettes = persistProduct("Cigarrillos", "CIG001");

        // 2 x 1000 listed, +10% by card = 2200 charged.
        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("2200.0000"), card, user);
        persistSurchargedDetail(sale, cigarettes, "2.000", "1000.0000", "1100.0000", "10.0000");
        persistSale(sale, null);

        entityManager.flush();
        entityManager.clear();

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw(null, null);

        assertThat(result.getListPriceSales()).isEqualByComparingTo("2000.0000");
        assertThat(result.getPaymentMethodSurcharges()).isEqualByComparingTo("200.0000");
        assertThat(result.getManualAdjustments()).isEqualByComparingTo("0");
        assertThat(result.getNetSales()).isEqualByComparingTo("2200.0000");
    }

    @Test
    @DisplayName("Should report a manual adjustment on its own line and nowhere else")
    void shouldReportManualAdjustmentOnly() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Cash");
        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");

        // 10000 listed, 1000 knocked off manually.
        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("9000.0000"), cash, user);
        persistDetail(sale, pollo, "Pollo entero", "2.000", "5000.0000");
        persistSale(sale, "-1000.0000");

        entityManager.flush();
        entityManager.clear();

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw(null, null);

        assertThat(result.getListPriceSales()).isEqualByComparingTo("10000.0000");
        assertThat(result.getPaymentMethodSurcharges()).isEqualByComparingTo("0");
        assertThat(result.getManualAdjustments()).isEqualByComparingTo("-1000.0000");
        assertThat(result.getNetSales()).isEqualByComparingTo("9000.0000");
    }

    @Test
    @DisplayName("Should report a negative percentage as a discount in the surcharge line")
    void shouldReportPaymentMethodDiscountAsNegative() {

        UserEntity user = persistUser();
        PaymentMethodEntity cash = persistPaymentMethod("Efectivo");
        ProductEntity pollo = persistProduct("Pollo entero", "POLLO001");

        // 2 x 1000 listed, -5% for paying cash = 1900 charged.
        TransactionEntity sale = persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0), new BigDecimal("1900.0000"), cash, user);
        persistSurchargedDetail(sale, pollo, "2.000", "1000.0000", "950.0000", "-5.0000");
        persistSale(sale, null);

        entityManager.flush();
        entityManager.clear();

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw(null, null);

        assertThat(result.getListPriceSales()).isEqualByComparingTo("2000.0000");
        assertThat(result.getPaymentMethodSurcharges()).isEqualByComparingTo("-100.0000");
        assertThat(result.getNetSales()).isEqualByComparingTo("1900.0000");
    }

    private UserEntity persistUser() {
        UserEntity user = UserEntity.builder()
                .name("Admin")
                .lastName("System")
                .username("admin_test")
                .passwordHash("test-password")
                .email("admin@test.com")
                .active(true)
                .build();

        return entityManager.persistAndFlush(user);
    }

    private PaymentMethodEntity persistPaymentMethod(String name) {
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder()
                .name(name)
                .active(true)
                .build();

        return entityManager.persistAndFlush(paymentMethod);
    }

    private ProductEntity persistProduct(String name, String sku) {
        ProductEntity product = ProductEntity.builder()
                .name(name)
                .description(name)
                .price(new BigDecimal("1000.0000"))
                .sku(sku)
                .active(true)
                .build();

        return entityManager.persistAndFlush(product);
    }

    private TransactionEntity persistSaleTransaction(LocalDateTime date,
                                                     BigDecimal total,
                                                     PaymentMethodEntity paymentMethod,
                                                     UserEntity user) {
        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED)
                .total(total)
                .paymentMethod(paymentMethod)
                .createdByUserEntity(user)
                .description("Test sale")
                .build();

        transaction = entityManager.persistAndFlush(transaction);

        transaction.setDate(date);
        transaction = entityManager.persistAndFlush(transaction);

        return transaction;
    }

    /** A confirmed RETURN. Its total is negative: money going out. */
    private TransactionEntity persistReturnTransaction(LocalDateTime date,
                                                       BigDecimal total,
                                                       UserEntity user) {
        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.RETURN)
                .status(TransactionStatus.CONFIRMED)
                .total(total)
                .paymentMethod(null)
                .createdByUserEntity(user)
                .description("Test return")
                .build();

        transaction = entityManager.persistAndFlush(transaction);

        transaction.setDate(date);
        transaction = entityManager.persistAndFlush(transaction);

        return transaction;
    }

    private TransactionDetailEntity persistDetail(TransactionEntity transaction,
                                                  ProductEntity product,
                                                  String description,
                                                  String quantity,
                                                  String unitPrice) {
        TransactionDetailEntity detail = TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description(description)
                .quantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal(unitPrice))
                .build();

        return entityManager.persistAndFlush(detail);
    }

    /**
     * A line whose price a payment-method rule moved: unitPrice is the effective price,
     * baseUnitPrice the catalog one it started from.
     */
    private TransactionDetailEntity persistSurchargedDetail(TransactionEntity transaction,
                                                            ProductEntity product,
                                                            String quantity,
                                                            String baseUnitPrice,
                                                            String effectiveUnitPrice,
                                                            String percentage) {
        TransactionDetailEntity detail = TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description(product.getName())
                .quantity(new BigDecimal(quantity))
                .unitPrice(new BigDecimal(effectiveUnitPrice))
                .baseUnitPrice(new BigDecimal(baseUnitPrice))
                .appliedPercentage(new BigDecimal(percentage))
                .appliedMethodName("Tarjeta")
                .build();

        return entityManager.persistAndFlush(detail);
    }

    /** The sale header, which is where the sale-wide manual adjustment lives. */
    private SaleEntity persistSale(TransactionEntity transaction, String adjustmentAmount) {
        SaleEntity sale = SaleEntity.builder()
                .transaction(transaction)
                .taxTotal(BigDecimal.ZERO)
                .adjustmentType(adjustmentAmount == null ? AdjustmentType.NONE : AdjustmentType.FIXED)
                .adjustmentValue(adjustmentAmount == null ? BigDecimal.ZERO : new BigDecimal(adjustmentAmount))
                .adjustmentAmount(adjustmentAmount == null ? BigDecimal.ZERO : new BigDecimal(adjustmentAmount))
                .build();

        return entityManager.persistAndFlush(sale);
    }
}