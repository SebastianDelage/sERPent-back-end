package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateSaleRequest(

        Long customerId,
        String customerName,
        String customerDocument,
        String invoiceNumber,

        Long paymentMethodId,

        @NotNull(message = "Created by user id cannot be null")
        Long createdByUserId,

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        String description,

        @NotEmpty(message = "Items cannot be empty")
        List<@Valid CreateSaleItemRequest> items

) {}