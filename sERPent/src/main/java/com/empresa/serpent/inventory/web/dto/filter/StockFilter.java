package com.empresa.serpent.inventory.web.dto.filter;

public record StockFilter(
        Long productId,
        Long warehouseId,
        Boolean onlyPositive
) {
}
