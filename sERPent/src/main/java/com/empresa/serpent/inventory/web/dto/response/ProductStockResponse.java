package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

public record ProductStockResponse(
        Long productId,
        String productName,
        BigDecimal totalStock
) {
}
