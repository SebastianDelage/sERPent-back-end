package com.empresa.serpent.transactions.web.dto.response;

import java.math.BigDecimal;

public record TransactionItemResponse(
        Long id,
        Long productId,
        String productName,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}