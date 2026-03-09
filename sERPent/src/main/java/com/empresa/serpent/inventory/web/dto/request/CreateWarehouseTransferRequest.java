package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateWarehouseTransferRequest(

        @NotNull(message = "Product id cannot be null")
        Long productId,

        @NotNull(message = "Source warehouse id cannot be null")
        Long sourceWarehouseId,

        @NotNull(message = "Target warehouse id cannot be null")
        Long targetWarehouseId,

        @NotNull(message = "Quantity cannot be null")
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity,

        String reason,

        @NotNull(message = "Created by user id cannot be null")
        Long createdByUserId

) {
}