package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateSaleRequest(

        Long customerId,
        String customerName,
        String customerDocument,
        String invoiceNumber,

        Long paymentMethodId,

        @NotNull
        Long createdByUserId,

        String description,

        @NotEmpty
        List<CreateSaleItemRequest> items

) {}
