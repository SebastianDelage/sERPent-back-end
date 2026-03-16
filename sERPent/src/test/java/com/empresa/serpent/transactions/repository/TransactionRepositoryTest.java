package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.reports.repository.projection.SalesDailyProjection;
import com.empresa.serpent.reports.repository.projection.SalesSummaryProjection;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
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

import static org.assertj.core.api.Assertions.assertThat;

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

        List<SalesByProductResponse> result = transactionRepository.getSalesByProductReport();

        assertThat(result).hasSize(2);

        assertThat(result.get(0).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(0).quantitySold()).isEqualByComparingTo("1.000");
        assertThat(result.get(0).totalRevenue()).isEqualByComparingTo("4500.0000");

        assertThat(result.get(1).productName()).isEqualTo("Pata muslo");
        assertThat(result.get(1).quantitySold()).isEqualByComparingTo("1.000");
        assertThat(result.get(1).totalRevenue()).isEqualByComparingTo("4600.0000");
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

        List<SalesDailyProjection> result = transactionRepository.getSalesDailyReportRaw();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(result.get(0).getTransactions()).isEqualTo(2L);
        assertThat(result.get(0).getTotalRevenue()).isEqualByComparingTo("9100.0000");
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

        List<SalesByPaymentMethodResponse> result = transactionRepository.getSalesByPaymentMethodReport();

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

        persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 10, 0),
                new BigDecimal("9100.0000"),
                cash,
                user
        );

        persistSaleTransaction(
                LocalDateTime.of(2026, 3, 12, 12, 0),
                new BigDecimal("3000.0000"),
                cash,
                user
        );

        entityManager.flush();
        entityManager.clear();

        SalesSummaryProjection result = transactionRepository.getSalesSummaryReportRaw();

        assertThat(result).isNotNull();
        assertThat(result.getTransactions()).isEqualTo(2L);
        assertThat(result.getTotalRevenue()).isEqualByComparingTo("12100.0000");
        assertThat(result.getAverageTicket()).isEqualByComparingTo("6050.0000");
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
}