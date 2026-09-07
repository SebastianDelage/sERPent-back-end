package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Only the percentage and the on/off flag can change. Pointing a rule at a different
 * product or payment method would make it a different rule — delete it and create the
 * new one, so the (product, payment method) uniqueness stays meaningful.
 */
public record UpdateProductPaymentAdjustmentRequest(

        @NotNull(message = "El porcentaje del ajuste es obligatorio.")
        BigDecimal adjustmentPercentage,

        @NotNull(message = "Hay que indicar si está activo.")
        Boolean active

) {}
