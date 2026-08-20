package com.empresa.serpent.transactions.web.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * A current account: the balance and everything that makes it up.
 *
 * @param balance  positive means a debt is owed; negative is a credit in the other party's
 *                 favour, which is real money and is reported as such rather than clamped
 * @param inFavour convenience flag for the UI, true when the balance is negative
 */
public record AccountStatementResponse(
        Long partyId,
        String partyName,
        BigDecimal balance,
        boolean inFavour,
        List<AccountMovementResponse> movements
) {}
