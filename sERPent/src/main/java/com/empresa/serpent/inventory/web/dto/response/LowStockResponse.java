package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

public record LowStockResponse(
        Long productId,
        String productName,
        BigDecimal currentStock,
        BigDecimal minimumStock
) {
}