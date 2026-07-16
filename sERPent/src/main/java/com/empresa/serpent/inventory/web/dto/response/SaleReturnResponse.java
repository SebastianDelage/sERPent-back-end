package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleReturnResponse(
        Long id,
        Long transactionId,
        LocalDateTime transactionDate,
        Long saleId,
        Long productId,
        String productName,
        BigDecimal quantity,
        String reason
) {
}