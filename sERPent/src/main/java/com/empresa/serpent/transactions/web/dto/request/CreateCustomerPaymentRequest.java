package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.shared.validation.MoneyLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A customer paying down their balance. Not tied to any particular sale: the account is
 * kept by balance, so a partial payment is simply a smaller amount.
 */
public record CreateCustomerPaymentRequest(

        @NotNull(message = "El cliente es obligatorio.")
        Long customerId,

        @NotNull(message = "El método de pago es obligatorio.")
        Long paymentMethodId,

        /**
         * The branch whose till takes the money. Required — the shift count is per branch,
         * and a collection with no branch could not be counted anywhere.
         *
         * <p>Ignored when {@code terminalId} is set: the terminal decides the branch.
         */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the branch. */
        Long terminalId,

        @NotNull(message = "El importe es obligatorio.")
        @Positive(message = "El importe tiene que ser mayor a cero.")
        @Digits(
                integer = MoneyLimits.INTEGER_DIGITS,
                fraction = MoneyLimits.FRACTION_DIGITS,
                message = "El importe del pago no puede superar 9.999.999,99 y admite hasta dos decimales."
        )
        BigDecimal amount,

        @NotNull(message = "La fecha del pago es obligatoria.")
        LocalDate paymentDate,

        String note
) {}
