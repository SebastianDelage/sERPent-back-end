package com.empresa.serpent.cashcount.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * What was counted for one payment method.
 *
 * <p>Not constrained to be positive: a posnet batch is never negative, but refusing a
 * negative here would only turn a typo into an error message instead of into a difference
 * the owner can see. The count records what the person says they found.
 */
public record CashCountLineRequest(

        @NotNull(message = "Payment method id cannot be null")
        Long paymentMethodId,

        @NotNull(message = "Counted amount cannot be null")
        BigDecimal countedAmount
) {
}
