package com.empresa.serpent.transactions.web.dto.request;

import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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

        /**
         * Required unless the sale goes on the customer's account: a credit sale collects
         * nothing, so there is no method to name. Validated in the service rather than with
         * {@code @NotNull}, because whether it is required depends on {@code onCredit}.
         */
        Long paymentMethodId,

        /**
         * Take the sale on account instead of collecting it. Requires {@code customerId}
         * and forbids {@code paymentMethodId}.
         */
        Boolean onCredit,

        /**
         * Legacy field. The acting user now comes from the authenticated session; sending a
         * different id here is rejected rather than silently honoured. Newer clients omit it.
         *
         * <p>The offline sync path is the one exception: it keeps honouring this field, so a
         * sale made by one cashier and synced by another stays attributed to whoever made it.
         */
        Long createdByUserId,

        /** Ignored when {@code terminalId} is set: the terminal decides the warehouse. */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the warehouse. */
        Long terminalId,

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

    /** True only when the caller explicitly asked for it; absent means a normal, collected sale. */
    public boolean isOnCredit() {
        return Boolean.TRUE.equals(onCredit);
    }

    /** Convenience overload for callers that do not go through a terminal. */
    public CreateSaleRequest(Long customerId,
                             String customerName,
                             String customerDocument,
                             String invoiceNumber,
                             Long paymentMethodId,
                             Long createdByUserId,
                             Long warehouseId,
                             String description,
                             List<CreateSaleItemRequest> items,
                             AdjustmentType adjustmentType,
                             BigDecimal adjustmentValue) {
        this(customerId, customerName, customerDocument, invoiceNumber, paymentMethodId,
                null, createdByUserId, warehouseId, null, description, items,
                adjustmentType, adjustmentValue);
    }

    /** Convenience overload for callers that do not apply an adjustment nor use a terminal. */
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
                null, createdByUserId, warehouseId, null, description, items, null, null);
    }
}
