package com.empresa.serpent.transactions.web.dto.response;

public record PaymentMethodResponse(
        Long id,
        String name,

        /** True on the one method that represents the money in the drawer, if any. */
        Boolean isCash,

        Boolean active
) {
}
