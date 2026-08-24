package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Defines (or redefines) a product's reorder configuration at one warehouse.
 *
 * <p>All three figures are optional and INDEPENDENT: send only the ones this warehouse
 * overrides, and leave the rest null to keep inheriting the product's. Sending all three
 * null is how you say "inherit everything", and the service deletes the override row
 * rather than storing one that overrides nothing.
 */
public record UpsertProductWarehouseMinimumStockRequest(

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        /** This warehouse's floor, or null to inherit the product's. */
        @PositiveOrZero(message = "Minimum stock cannot be negative")
        BigDecimal minimumStock,

        /** When this warehouse reorders, or null to inherit the product's reorder point. */
        @PositiveOrZero(message = "Reorder point cannot be negative")
        BigDecimal reorderPoint,

        /** How much this warehouse orders, or null to inherit the product's quantity. */
        @PositiveOrZero(message = "Reorder quantity cannot be negative")
        BigDecimal reorderQuantity
) {
}
