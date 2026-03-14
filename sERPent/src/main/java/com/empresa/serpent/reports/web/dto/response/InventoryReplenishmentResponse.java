package com.empresa.serpent.reports.web.dto.response;

import java.math.BigDecimal;

public record InventoryReplenishmentResponse(

        Long productId,
        String productName,

        Long warehouseId,
        String warehouseName,

        BigDecimal currentStock,

        BigDecimal reorderPoint,
        BigDecimal reorderQuantity,

        BigDecimal suggestedOrderQuantity
) {}