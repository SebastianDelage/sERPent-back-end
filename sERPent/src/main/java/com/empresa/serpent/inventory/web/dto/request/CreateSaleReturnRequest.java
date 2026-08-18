package com.empresa.serpent.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateSaleReturnRequest(

        @NotNull(message = "Sale id cannot be null")
        Long saleId,

        @NotNull(message = "Product id cannot be null")
        Long productId,

        /** Ignored when {@code terminalId} is set: the terminal decides the warehouse. */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the warehouse. */
        Long terminalId,

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

    /** Convenience overload for callers that do not go through a terminal. */
    public CreateSaleReturnRequest(Long saleId,
                                   Long productId,
                                   Long warehouseId,
                                   BigDecimal quantity,
                                   String reason,
                                   Long createdByUserId) {
        this(saleId, productId, warehouseId, null, quantity, reason, createdByUserId);
    }
}