package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateInventoryAdjustmentRequest(

        @NotNull(message = "Product id cannot be null")
        Long productId,

        /** Ignored when {@code terminalId} is set: the terminal decides the warehouse. */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the warehouse. */
        Long terminalId,

        @NotNull(message = "Counted quantity cannot be null")
        @PositiveOrZero(message = "Counted quantity cannot be negative")
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