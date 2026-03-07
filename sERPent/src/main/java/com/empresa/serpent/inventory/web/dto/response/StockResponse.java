package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

public record StockResponse(
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal stock
) {
}
