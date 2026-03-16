package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreatePurchaseRequest(

        @NotNull(message = "Created by user id cannot be null")
        Long createdByUserId,

        Long paymentMethodId,

        Long supplierId,

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        String receiptNumber,

        String description,

        String notes,

        @NotEmpty(message = "Items cannot be empty")
        List<@Valid CreatePurchaseItemRequest> items
) {
}