package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

public record CreateSaleReturnResponse(
        Long transactionId,
        Long saleId,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal quantity,
        String message
) {
}