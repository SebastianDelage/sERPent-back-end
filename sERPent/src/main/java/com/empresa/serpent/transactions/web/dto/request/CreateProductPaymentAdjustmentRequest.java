package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.shared.validation.PercentageLimits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductPaymentAdjustmentRequest(

        @NotNull(message = "El producto es obligatorio.")
        Long productId,

        @NotNull(message = "El método de pago es obligatorio.")
        Long paymentMethodId,

        /*
          Firmado: negativo descuento, positivo recargo. El rango es simétrico —ver
          PercentageLimits— y por eso hay dos mensajes distintos para las dos puntas: el mismo
          número significa "el precio queda en cero" de un lado y "el precio se duplica" del
          otro, y un solo texto para los dos no diría ninguna de las dos cosas.
        */
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

        /** Defaults to true when omitted. */
        Boolean active

) {}
