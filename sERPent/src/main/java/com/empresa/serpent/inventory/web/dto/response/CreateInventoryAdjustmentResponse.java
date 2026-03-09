package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

public record CreateInventoryAdjustmentResponse(
        Long transactionId,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        String movementType,
        BigDecimal previousStock,
        BigDecimal countedQuantity,
        BigDecimal adjustmentQuantity,
        String message
) {
}