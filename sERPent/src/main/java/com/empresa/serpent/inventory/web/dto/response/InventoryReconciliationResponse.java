package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

public record InventoryReconciliationResponse(
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal ledgerStock,
        BigDecimal snapshotStock,
        BigDecimal difference,
        boolean consistent
) {
}