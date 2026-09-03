package com.empresa.serpent.inventory.web.dto.request;

import com.empresa.serpent.shared.validation.QuantityLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateWarehouseTransferRequest(

        @NotNull(message = "El producto es obligatorio.")
        Long productId,

        @NotNull(message = "El depósito de origen es obligatorio.")
        Long sourceWarehouseId,

        @NotNull(message = "El depósito de destino es obligatorio.")
        Long targetWarehouseId,

        /**
         * Warehouse ceiling: what moves between warehouses came in through purchases, not out
         * through the counter. See {@link QuantityLimits}.
         */
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad tiene que ser mayor a cero.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "La cantidad de una transferencia no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal quantity,

        String reason,

        /**
         * Legacy field. The acting user now comes from the authenticated session; sending a
         * different id here is rejected rather than silently honoured. Newer clients omit it.
         */
        Long createdByUserId

) {
}
