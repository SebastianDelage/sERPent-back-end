package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.web.dto.request.StockCheckItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockValidationService {

    private final StockQueryService stockQueryService;

    public void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    public void validateAvailableStock(Long productId, Long warehouseId, BigDecimal requestedQuantity) {
        validatePositiveQuantity(requestedQuantity);

        BigDecimal currentStock =
                stockQueryService.getStockByProductAndWarehouse(productId, warehouseId);

        if (currentStock.compareTo(requestedQuantity) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient stock for product " + productId +
                            " in warehouse " + warehouseId +
                            ". Current stock: " + currentStock +
                            ", requested: " + requestedQuantity
            );
        }
    }

    public void validateSaleItemsStock(List<StockCheckItemRequest> items, Long warehouseId) {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Sale must contain at least one item");
        }

        for (StockCheckItemRequest item : items) {

            if (item.productId() == null) {
                throw new IllegalArgumentException("Item productId cannot be null");
            }

            validateAvailableStock(item.productId(), warehouseId, item.quantity());
        }
    }
}