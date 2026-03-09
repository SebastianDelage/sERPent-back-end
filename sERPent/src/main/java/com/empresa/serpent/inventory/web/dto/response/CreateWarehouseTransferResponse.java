package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

public record CreateWarehouseTransferResponse(
        Long transactionId,
        Long productId,
        String productName,
        Long sourceWarehouseId,
        String sourceWarehouseName,
        Long targetWarehouseId,
        String targetWarehouseName,
        BigDecimal quantity,
        String message
) {
}