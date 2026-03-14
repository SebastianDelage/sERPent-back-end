package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record InventoryMovementsByProductResponse(
        Long productId,
        String productName,
        Long movements,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal netQuantity
) {
}