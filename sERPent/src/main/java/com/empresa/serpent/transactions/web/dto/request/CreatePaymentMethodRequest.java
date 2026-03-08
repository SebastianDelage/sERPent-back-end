package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentMethodRequest(
        @NotBlank String name,
        Boolean active
) {
}
