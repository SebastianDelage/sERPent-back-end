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

        /**
         * Take the purchase on the supplier's account instead of paying it. Requires
         * {@code supplierId} and forbids {@code paymentMethodId}.
         *
         * <p>An explicit flag rather than "no payment method", which purchases have always
         * accepted with no defined meaning — treating that null as credit would rewrite
         * the meaning of rows already in the database.
         */
        Boolean onCredit,

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

    /** True only when the caller explicitly asked for it; absent means a normal, paid purchase. */
    public boolean isOnCredit() {
        return Boolean.TRUE.equals(onCredit);
    }

    /** Convenience overload for callers that do not go through a terminal. */
    public CreatePurchaseRequest(Long createdByUserId,
                                 Long paymentMethodId,
                                 Long supplierId,
                                 Long warehouseId,
                                 String receiptNumber,
                                 String description,
                                 String notes,
                                 List<CreatePurchaseItemRequest> items) {
        this(createdByUserId, paymentMethodId, supplierId, null, warehouseId, null,
                receiptNumber, description, notes, items);
    }
}