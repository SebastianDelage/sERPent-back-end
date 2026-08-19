package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

/**
 * @param warehouseActive so the UI can flag stock sitting in a deactivated warehouse.
 *                        Nothing can be sold from there, but the goods are still counted.
 */
public record StockResponse(
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal stock,
        Boolean warehouseActive
) {
}
