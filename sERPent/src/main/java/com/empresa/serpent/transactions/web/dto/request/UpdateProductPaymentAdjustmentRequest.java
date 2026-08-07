package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Only the percentage and the on/off flag can change. Pointing a rule at a different
 * product or payment method would make it a different rule — delete it and create the
 * new one, so the (product, payment method) uniqueness stays meaningful.
 */
public record UpdateProductPaymentAdjustmentRequest(

        @NotNull(message = "Adjustment percentage cannot be null")
        BigDecimal adjustmentPercentage,

        @NotNull(message = "Active cannot be null")
        Boolean active

) {}
