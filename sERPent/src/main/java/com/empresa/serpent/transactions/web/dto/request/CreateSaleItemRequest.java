package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSaleItemRequest(

        @NotNull
        Long productId,

        String description,

        @NotNull
        BigDecimal quantity,

        @NotNull
        BigDecimal unitPrice

) {}
