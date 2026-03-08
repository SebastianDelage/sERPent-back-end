package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank String name,
        String description,
        @NotNull BigDecimal price,
        String sku,
        Boolean active
) {
}