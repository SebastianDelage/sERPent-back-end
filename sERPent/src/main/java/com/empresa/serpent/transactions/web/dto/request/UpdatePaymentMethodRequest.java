package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePaymentMethodRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,

        /** Marks this as the money in the drawer. Omit to leave it unchanged. */
        Boolean isCash,

        Boolean active
) {
}
