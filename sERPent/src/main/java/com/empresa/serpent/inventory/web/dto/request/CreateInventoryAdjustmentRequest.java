package com.empresa.serpent.inventory.web.dto.request;

import com.empresa.serpent.shared.validation.QuantityLimits;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateInventoryAdjustmentRequest(

        @NotNull(message = "El producto es obligatorio.")
        Long productId,

        /** Ignored when {@code terminalId} is set: the terminal decides the warehouse. */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the warehouse. */
        Long terminalId,

        /**
         * Warehouse ceiling: a physical stocktake is among the largest numbers the app
         * handles, so it is capped well above a counter line. See {@link QuantityLimits}.
         */
        @NotNull(message = "La cantidad contada es obligatoria.")
        @PositiveOrZero(message = "La cantidad contada no puede ser negativa.")
        @Digits(
                integer = QuantityLimits.WAREHOUSE_INTEGER_DIGITS,
                fraction = QuantityLimits.FRACTION_DIGITS,
                message = "La cantidad contada no puede superar 99.999,999 y admite hasta tres decimales."
        )
        BigDecimal countedQuantity,

        String reason,

        /**
         * Legacy field. The acting user now comes from the authenticated session; sending a
         * different id here is rejected rather than silently honoured. Newer clients omit it.
         */
        Long createdByUserId

) {

    /** Convenience overload for callers that do not go through a terminal. */
    public CreateInventoryAdjustmentRequest(Long productId,
                                            Long warehouseId,
                                            BigDecimal countedQuantity,
                                            String reason,
                                            Long createdByUserId) {
        this(productId, warehouseId, null, countedQuantity, reason, createdByUserId);
    }
}