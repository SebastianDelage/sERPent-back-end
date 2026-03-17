package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateProductTransformationOutputRequest(

        @NotNull(message = "Product id cannot be null")
        Long productId,

        String description,

        @NotNull(message = "Quantity cannot be null")
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity
) {
}