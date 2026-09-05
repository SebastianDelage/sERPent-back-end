package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.shared.validation.MoneyLimits;
import com.empresa.serpent.shared.validation.QuantityLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateSaleItemRequest(

        @NotNull(message = "El producto es obligatorio.")
        Long productId,

        String description,

        /**
         * Counter ceiling: 999.999, three decimals. See {@link QuantityLimits} for why a sale
         * line is capped lower than a warehouse line, and why the column is not narrowed to
         * match. The cashier never reads this message — the form's own validator does that —
         * but every caller that skips the form lands here.
         */
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad tiene que ser mayor a cero.")
        @Digits(
                integer = QuantityLimits.COUNTER_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "La cantidad de una línea no puede superar 999,999 y admite hasta tres decimales."
        )
        BigDecimal quantity,

        @NotNull(message = "El precio unitario es obligatorio.")
        @PositiveOrZero(message = "El precio unitario no puede ser negativo.")
        @Digits(
                integer = MoneyLimits.INTEGER_DIGITS,
                fraction = MoneyLimits.FRACTION_DIGITS,
                message = "El precio unitario no puede superar 9.999.999,99 y admite hasta dos decimales."
        )
        BigDecimal unitPrice

) {}
