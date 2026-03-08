package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePaymentMethodRequest(
        @NotBlank String name,
        Boolean active
) {
}
