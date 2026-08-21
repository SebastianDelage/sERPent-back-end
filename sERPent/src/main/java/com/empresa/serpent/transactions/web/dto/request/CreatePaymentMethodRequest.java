package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentMethodRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,

        /** Marks this as the money in the drawer. Omit for false; at most one method may have it. */
        Boolean isCash,

        Boolean active
) {
}
