package com.empresa.serpent.cashcount.web.dto.request;

import com.empresa.serpent.shared.validation.MoneyLimits;
import jakarta.validation.constraints.Digits;
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

        @NotNull(message = "El método de pago es obligatorio.")
        Long paymentMethodId,

        @NotNull(message = "El importe contado es obligatorio.")
        @Digits(
                integer = MoneyLimits.INTEGER_DIGITS,
                fraction = MoneyLimits.FRACTION_DIGITS,
                message = "El importe contado no puede superar 9.999.999,99 y admite hasta dos decimales."
        )
        BigDecimal countedAmount
) {
}
