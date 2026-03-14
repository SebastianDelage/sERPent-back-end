package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record InventoryMovementsByWarehouseResponse(
        Long warehouseId,
        String warehouseName,
        Long movements,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal netQuantity
) {
}