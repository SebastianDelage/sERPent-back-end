package com.empresa.serpent.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductUpdateRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        String description,

        @NotNull(message = "Price cannot be null")
        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal price,

        String sku,

        Boolean active
) {}