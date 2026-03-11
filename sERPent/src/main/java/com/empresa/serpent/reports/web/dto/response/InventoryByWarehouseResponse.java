package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record InventoryByWarehouseResponse(
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal stock
) {
}