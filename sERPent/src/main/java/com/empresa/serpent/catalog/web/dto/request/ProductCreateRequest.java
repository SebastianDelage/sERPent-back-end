package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,
        String description,
        @NotNull(message = "Price cannot be null")
        BigDecimal price,
        String sku,
        Boolean active
) {
}