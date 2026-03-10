package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSaleReturnRequest(

        @NotNull(message = "Sale id cannot be null")
        Long saleId,

        @NotNull(message = "Product id cannot be null")
        Long productId,

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        @NotNull(message = "Quantity cannot be null")
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity,

        String reason,

        @NotNull(message = "Created by user id cannot be null")
        Long createdByUserId

) {
}