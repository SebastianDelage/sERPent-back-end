package com.empresa.serpent.transactions.web.dto.response;

public record PaymentMethodResponse(
        Long id,
        String name,
        Boolean active
) {
}