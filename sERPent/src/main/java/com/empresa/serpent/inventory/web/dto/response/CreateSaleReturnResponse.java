package com.empresa.serpent.inventory.web.dto.response;

import java.math.BigDecimal;

/**
 * @param lowersCustomerBalance the original sale was taken on account, so this return
 *                              reduces what the customer owes instead of handing money
 *                              back. The cashier has to be told, or they will pay out cash
 *                              for goods that were never paid for.
 */
public record CreateSaleReturnResponse(
        Long transactionId,
        Long saleId,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal quantity,
        boolean lowersCustomerBalance,
        String message
) {
}