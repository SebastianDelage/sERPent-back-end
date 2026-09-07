package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductPaymentAdjustmentRequest(

        @NotNull(message = "El producto es obligatorio.")
        Long productId,

        @NotNull(message = "El método de pago es obligatorio.")
        Long paymentMethodId,

        /** Signed: negative discounts, positive surcharges. Never below -100. */
        @NotNull(message = "El porcentaje del ajuste es obligatorio.")
        BigDecimal adjustmentPercentage,

        /** Defaults to true when omitted. */
        Boolean active

) {}
