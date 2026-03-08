package com.empresa.serpent.inventory.web.dto.request;

import java.math.BigDecimal;

public record StockCheckItemRequest(
        Long productId,
        BigDecimal quantity
) {
}
