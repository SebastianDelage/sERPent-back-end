package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Defines (or redefines) a product's minimum stock at one warehouse. */
public record UpsertProductWarehouseMinimumStockRequest(

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        @NotNull(message = "Minimum stock cannot be null")
        @PositiveOrZero(message = "Minimum stock cannot be negative")
        BigDecimal minimumStock
) {
}
