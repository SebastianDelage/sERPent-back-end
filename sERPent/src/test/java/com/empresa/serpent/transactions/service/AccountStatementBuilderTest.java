package com.empresa.serpent.transactions.service;

import com.empresa.serpent.transactions.domain.enums.AccountMovementType;
import com.empresa.serpent.transactions.web.dto.response.AccountMovementResponse;
import com.empresa.serpent.transactions.web.dto.response.AccountStatementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Account statement ordering")
class AccountStatementBuilderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Test
    @DisplayName("On the same day: the sale, then the return against it, then the payment")
    void sameDayMovementsReadInTheOrderTheyHappened() {
        /*
         Ids deliberately run backwards against the intended order: the return is id 1 and
         the sale is id 2, which is perfectly normal because they live in different tables.
         Ordering by id alone would put goods coming back before they were ever sold.
        */
        AccountStatementResponse statement = AccountStatementBuilder.build(1L, "Vecino", List.of(
                new AccountStatementBuilder.Movement(DAY, AccountStatementBuilder.Order.PAYMENT,
                        1L, AccountMovementType.CUSTOMER_PAYMENT, "Cobro", new BigDecimal("-4000")),
                new AccountStatementBuilder.Movement(DAY, AccountStatementBuilder.Order.REVERSAL,
                        1L, AccountMovementType.SALE_RETURN, "Devolución", new BigDecimal("-5000")),
                new AccountStatementBuilder.Movement(DAY, AccountStatementBuilder.Order.CHARGE,
                        2L, AccountMovementType.CREDIT_SALE, "Venta", new BigDecimal("10000"))));

        assertThat(statement.movements())
                .extracting(AccountMovementResponse::type)
                .containsExactly(
                        AccountMovementType.CREDIT_SALE,
                        AccountMovementType.SALE_RETURN,
                        AccountMovementType.CUSTOMER_PAYMENT);

        // The running balance never goes through a state that did not happen.
        assertThat(statement.movements())
                .extracting(AccountMovementResponse::runningBalance)
                .containsExactly(
                        new BigDecimal("10000"),
                        new BigDecimal("5000"),
                        new BigDecimal("1000"));

        assertThat(statement.balance()).isEqualByComparingTo("1000");
        assertThat(statement.inFavour()).isFalse();
    }

    @Test
    @DisplayName("A negative balance is reported as being in the other party's favour")
    void negativeBalanceIsFlaggedInFavour() {
        AccountStatementResponse statement = AccountStatementBuilder.build(1L, "Vecino", List.of(
                new AccountStatementBuilder.Movement(DAY, AccountStatementBuilder.Order.CHARGE,
                        1L, AccountMovementType.CREDIT_SALE, "Venta", new BigDecimal("1000")),
                new AccountStatementBuilder.Movement(DAY, AccountStatementBuilder.Order.PAYMENT,
                        1L, AccountMovementType.CUSTOMER_PAYMENT, "Cobro", new BigDecimal("-3000"))));

        // They handed over 2000 more than they owed. That is their money, not a rounding
        // artefact, so it is reported rather than clamped to zero.
        assertThat(statement.balance()).isEqualByComparingTo("-2000");
        assertThat(statement.inFavour()).isTrue();
    }

    @Test
    @DisplayName("Movements on different days keep chronological order regardless of id")
    void olderDayComesFirst() {
        AccountStatementResponse statement = AccountStatementBuilder.build(1L, "Vecino", List.of(
                new AccountStatementBuilder.Movement(DAY.plusDays(5),
                        AccountStatementBuilder.Order.CHARGE, 1L,
                        AccountMovementType.CREDIT_SALE, "Venta nueva", new BigDecimal("500")),
                new AccountStatementBuilder.Movement(DAY,
                        AccountStatementBuilder.Order.CHARGE, 9L,
                        AccountMovementType.CREDIT_SALE, "Venta vieja", new BigDecimal("100"))));

        assertThat(statement.movements())
                .extracting(AccountMovementResponse::description)
                .containsExactly("Venta vieja", "Venta nueva");
    }
}
