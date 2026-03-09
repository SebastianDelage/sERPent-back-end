package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateInventoryAdjustmentRequest(

        @NotNull(message = "Product id cannot be null")
        Long productId,

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        @NotNull(message = "Counted quantity cannot be null")
        @PositiveOrZero(message = "Counted quantity cannot be negative")
        BigDecimal countedQuantity,

        String reason,

        @NotNull(message = "Created by user id cannot be null")
        Long createdByUserId

) {
}