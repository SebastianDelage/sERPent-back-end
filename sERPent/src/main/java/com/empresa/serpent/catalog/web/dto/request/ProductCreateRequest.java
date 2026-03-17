package com.empresa.serpent.catalog.web.dto.request;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductCreateRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        String description,

        @NotNull(message = "Price cannot be null")
        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal price,

        String sku,

        Boolean active,

        @PositiveOrZero(message = "Minimum stock cannot be negative")
        BigDecimal minimumStock,

        @PositiveOrZero(message = "Reorder point cannot be negative")
        BigDecimal reorderPoint,

        @PositiveOrZero(message = "Reorder quantity cannot be negative")
        BigDecimal reorderQuantity,

        @NotBlank(message = "Unit of measure cannot be null")
        UnitOfMeasure unitOfMeasure
) {}