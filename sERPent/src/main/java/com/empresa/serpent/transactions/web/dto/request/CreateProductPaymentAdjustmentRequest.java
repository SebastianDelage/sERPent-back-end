package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductPaymentAdjustmentRequest(

        @NotNull(message = "Product id cannot be null")
        Long productId,

        @NotNull(message = "Payment method id cannot be null")
        Long paymentMethodId,

        /** Signed: negative discounts, positive surcharges. Never below -100. */
        @NotNull(message = "Adjustment percentage cannot be null")
        BigDecimal adjustmentPercentage,

        /** Defaults to true when omitted. */
        Boolean active

) {}
