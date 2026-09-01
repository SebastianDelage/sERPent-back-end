package com.empresa.serpent.catalog.web.dto.request;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(

        @NotBlank(message = "Name cannot be blank")
        String name,

        String description,

        @NotNull(message = "Price cannot be null")
        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal price,

        @Size(max = 80, message = "SKU cannot be longer than 80 characters")
        String sku,

        @Pattern(
                regexp = "^(\\d{8}|\\d{12}|\\d{13})?$",
                message = "Barcode must be 8, 12, or 13 digits (EAN-8, UPC-A, or EAN-13)"
        )
        String barcode,

        @Pattern(
                regexp = "^(\\d{1,20})?$",
                message = "Scale code must be digits only"
        )
        String scaleCode,

        Boolean active,

        @PositiveOrZero(message = "Minimum stock cannot be negative")
        BigDecimal minimumStock,

        @PositiveOrZero(message = "Reorder point cannot be negative")
        BigDecimal reorderPoint,

        @PositiveOrZero(message = "Reorder quantity cannot be negative")
        BigDecimal reorderQuantity,

        @NotNull(message = "Unit of measure cannot be null")
        UnitOfMeasure unitOfMeasure
) {}