package com.empresa.serpent.inventory.web.dto.request;

import com.empresa.serpent.shared.validation.QuantityLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSaleReturnRequest(

        @NotNull(message = "La venta es obligatoria.")
        Long saleId,

        @NotNull(message = "El producto es obligatorio.")
        Long productId,

        /** Ignored when {@code terminalId} is set: the terminal decides the warehouse. */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the warehouse. */
        Long terminalId,

        /**
         * Counter ceiling, same as a sale line: a return is the inverse of one. The real
         * limit is what was sold, which the dialog enforces and the service re-checks; this
         * one only bites on lines recorded before any ceiling existed. See
         * {@link QuantityLimits}.
         */
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad tiene que ser mayor a cero.")
        @Digits(
                integer = QuantityLimits.COUNTER_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "La cantidad de una devolución no puede superar 999,999 y admite hasta tres decimales."
        )
        BigDecimal quantity,

        /**
         * How the money was handed back, so the till count can subtract it from the right
         * bucket.
         *
         * <p>Asked rather than inferred from the original sale, deliberately: refunding cash
         * for a card sale is an ordinary thing to do, so copying the sale's method would
         * assert something nobody recorded.
         *
         * <p>Required EXCEPT when the original sale was on credit, where it must be omitted:
         * no money moves at all, the customer's balance just drops.
         */
        Long refundPaymentMethodId,

        String reason,

        /**
         * Legacy field. The acting user now comes from the authenticated session; sending a
         * different id here is rejected rather than silently honoured. Newer clients omit it.
         */
        Long createdByUserId

) {

    /** Convenience overload for callers that do not go through a terminal. */
    public CreateSaleReturnRequest(Long saleId,
                                   Long productId,
                                   Long warehouseId,
                                   BigDecimal quantity,
                                   Long refundPaymentMethodId,
                                   String reason,
                                   Long createdByUserId) {
        this(saleId, productId, warehouseId, null, quantity, refundPaymentMethodId, reason, createdByUserId);
    }
}