package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.shared.validation.QuantityLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateProductTransformationOutputRequest(

        @NotNull(message = "El producto es obligatorio.")
        Long productId,

        String description,

        /**
         * Warehouse ceiling: a production run is not a counter sale. See {@link QuantityLimits}.
         */
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad tiene que ser mayor a cero.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "La cantidad de una línea no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal quantity
) {
}
