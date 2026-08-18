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

        /**
         * Legacy field. The acting user now comes from the authenticated session; sending a
         * different id here is rejected rather than silently honoured. Newer clients omit it.
         */
        Long createdByUserId

) {
}