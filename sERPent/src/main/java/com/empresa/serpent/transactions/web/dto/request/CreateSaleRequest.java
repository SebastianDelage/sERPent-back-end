package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateSaleRequest(

        Long customerId,

        @Size(max = 150, message = "Customer name cannot be longer than 150 characters")
        String customerName,

        @Size(max = 60, message = "Customer document cannot be longer than 60 characters")
        String customerDocument,

        @Size(max = 60, message = "Invoice number cannot be longer than 60 characters")
        String invoiceNumber,

        @NotNull(message = "Payment method id cannot be null")
        Long paymentMethodId,

        @NotNull(message = "Created by user id cannot be null")
        Long createdByUserId,

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        String description,

        @NotEmpty(message = "Items cannot be empty")
        List<@Valid CreateSaleItemRequest> items,

        /** Omit (or NONE) for a sale with no manual adjustment. */
        AdjustmentType adjustmentType,

        /**
         * Signed: negative discounts, positive surcharges. -10 with PERCENTAGE is a
         * 10% discount; 500 with FIXED is a $500 surcharge.
         */
        BigDecimal adjustmentValue

) {

    /** Convenience overload for callers that do not apply an adjustment. */
    public CreateSaleRequest(Long customerId,
                             String customerName,
                             String customerDocument,
                             String invoiceNumber,
                             Long paymentMethodId,
                             Long createdByUserId,
                             Long warehouseId,
                             String description,
                             List<CreateSaleItemRequest> items) {
        this(customerId, customerName, customerDocument, invoiceNumber, paymentMethodId,
                createdByUserId, warehouseId, description, items, null, null);
    }
}
