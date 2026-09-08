package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.shared.validation.PercentageLimits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Only the percentage and the on/off flag can change. Pointing a rule at a different
 * product or payment method would make it a different rule — delete it and create the
 * new one, so the (product, payment method) uniqueness stays meaningful.
 */
public record UpdateProductPaymentAdjustmentRequest(

        @NotNull(message = "El porcentaje del ajuste es obligatorio.")
        /*
          El mensaje NO dice "no puede superar el {value}%": {value} vale -100 y saldría
          "no puede superar el -100%". El valor firmado se nombra por lo que es, un mínimo.
        */
        @DecimalMin(value = PercentageLimits.FLOOR,
                message = "El porcentaje del ajuste no puede ser menor que {value}: un descuento mayor dejaría el precio en negativo.")
        @DecimalMax(value = PercentageLimits.CAP,
                message = "Un recargo no puede superar el {value}% del precio, que ya lo duplica.")
        @Digits(integer = PercentageLimits.INTEGER_DIGITS, fraction = PercentageLimits.FRACTION_DIGITS,
                message = "El porcentaje del ajuste admite hasta {integer} enteros y {fraction} decimales.")
        BigDecimal adjustmentPercentage,

        @NotNull(message = "Hay que indicar si está activo.")
        Boolean active

) {}
