package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Links a supplier to a product, or edits an existing link.
 *
 * <p>Carries no price on purpose. What we last paid is derived from the purchases
 * themselves, so there is nothing here that could disagree with them.
 */
public record UpsertProductSupplierRequest(

        @NotNull(message = "Supplier id cannot be null")
        Long supplierId,

        /** The supplier's own code for this product. Optional: many small suppliers have none. */
        @Size(max = 80, message = "Supplier product code cannot be longer than 80 characters")
        String supplierProductCode,

        /** The one the replenishment report proposes. Only one active supplier per product may have it. */
        Boolean preferred,

        @PositiveOrZero(message = "Lead time cannot be negative")
        Integer leadTimeDays,

        Boolean active
) {
}
