package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePurchaseRequest(

        /**
         * Legacy field. The acting user now comes from the authenticated session; sending a
         * different id here is rejected rather than silently honoured. Newer clients omit it.
         */
        Long createdByUserId,

        Long paymentMethodId,

        Long supplierId,

        /** Ignored when {@code terminalId} is set: the terminal decides the warehouse. */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the warehouse. */
        Long terminalId,

        @Size(max = 80, message = "Receipt number cannot be longer than 80 characters")
        String receiptNumber,

        String description,

        String notes,

        @NotEmpty(message = "Items cannot be empty")
        List<@Valid CreatePurchaseItemRequest> items
) {

    /** Convenience overload for callers that do not go through a terminal. */
    public CreatePurchaseRequest(Long createdByUserId,
                                 Long paymentMethodId,
                                 Long supplierId,
                                 Long warehouseId,
                                 String receiptNumber,
                                 String description,
                                 String notes,
                                 List<CreatePurchaseItemRequest> items) {
        this(createdByUserId, paymentMethodId, supplierId, warehouseId, null,
                receiptNumber, description, notes, items);
    }
}