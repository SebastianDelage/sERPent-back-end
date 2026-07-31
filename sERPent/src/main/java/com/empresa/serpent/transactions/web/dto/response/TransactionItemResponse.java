package com.empresa.serpent.transactions.web.dto.response;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;

import java.math.BigDecimal;

public record TransactionItemResponse(
        Long id,
        Long productId,
        String productName,
        /** Read-only, from the product. Lets the UI pick a sensible quantity step. */
        UnitOfMeasure unitOfMeasure,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}