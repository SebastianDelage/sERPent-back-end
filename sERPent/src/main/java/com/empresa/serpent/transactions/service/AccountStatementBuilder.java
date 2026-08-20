package com.empresa.serpent.transactions.service;

import com.empresa.serpent.transactions.domain.enums.AccountMovementType;
import com.empresa.serpent.transactions.web.dto.response.AccountMovementResponse;
import com.empresa.serpent.transactions.web.dto.response.AccountStatementResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a pile of account movements into an ordered statement with a running balance.
 *
 * <p>Shared by the customer and supplier sides, which differ only in where the movements
 * come from: both are balance accounts where charges raise the balance and payments lower
 * it.
 */
final class AccountStatementBuilder {

    private AccountStatementBuilder() {
    }

    /**
     * Sorts the movements and accumulates the balance across them.
     *
     * <p>The order is (date, kind, id). Without the last two terms, two movements on the
     * same day would come back in whatever order the database felt like and the running
     * balance column would change between two viewings of the same account.
     *
     * <p>{@link Order} matters for more than tidiness. Ids are only comparable WITHIN one
     * table, so each kind of movement gets its own position and ids never decide the order
     * across two different tables. Giving sales and returns the same position was a real
     * bug: a same-day return whose id happened to be lower than its sale's sorted ahead of
     * it, and the statement showed goods coming back before they were ever sold, with a
     * negative running balance on the first line.
     */
    static AccountStatementResponse build(Long partyId, String partyName, List<Movement> movements) {
        List<Movement> ordered = new ArrayList<>(movements);
        ordered.sort(Comparator
                .comparing(Movement::date)
                .thenComparing(Movement::order)
                .thenComparing(Movement::referenceId));

        List<AccountMovementResponse> lines = new ArrayList<>(ordered.size());
        BigDecimal running = BigDecimal.ZERO;

        for (Movement movement : ordered) {
            running = running.add(movement.amount());
            lines.add(new AccountMovementResponse(
                    movement.date(),
                    movement.type(),
                    movement.referenceId(),
                    movement.description(),
                    movement.amount(),
                    running));
        }

        return new AccountStatementResponse(
                partyId,
                partyName,
                running,
                running.signum() < 0,
                lines);
    }

    /**
     * Where each kind of movement sits within a day: first what was taken on account, then
     * what came back, then what was paid. Declaration order IS the sort order.
     *
     * <p>One position per source table, so the id tiebreaker only ever compares ids that
     * come from the same table and are therefore actually comparable.
     */
    enum Order {
        /** A credit sale or a credit purchase. */
        CHARGE,
        /** Goods returned against a credit sale. */
        REVERSAL,
        /** Money paid against the balance. */
        PAYMENT
    }

    /**
     * @param amount signed against the balance: positive raises the debt, negative lowers it
     */
    record Movement(
            LocalDate date,
            Order order,
            Long referenceId,
            AccountMovementType type,
            String description,
            BigDecimal amount
    ) {}
}
