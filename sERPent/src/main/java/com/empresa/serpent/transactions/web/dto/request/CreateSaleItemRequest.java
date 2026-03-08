package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSaleItemRequest(

        @NotNull(message = "Product id cannot be null")
        Long productId,

        String description,

        @NotNull(message = "Quantity cannot be null")
        BigDecimal quantity,

        @NotNull(message = "Unit price cannot be null")
        BigDecimal unitPrice

) {}