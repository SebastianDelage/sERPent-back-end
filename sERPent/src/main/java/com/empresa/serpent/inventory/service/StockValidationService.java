package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.web.dto.request.StockCheckItemRequest;
import com.empresa.serpent.shared.exception.InsufficientStockException;
import com.empresa.serpent.shared.exception.ValidationException;
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
    private final ProductRepository productRepository;

    public void validatePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("La cantidad debe ser mayor a cero.");
        }
    }

    public void validateAvailableStock(Long productId, Long warehouseId, BigDecimal requestedQuantity) {
        validatePositiveQuantity(requestedQuantity);

        BigDecimal currentStock =
                stockQueryService.getStockByProductAndWarehouse(productId, warehouseId);

        if (currentStock.compareTo(requestedQuantity) < 0) {
            String productName = productRepository.findById(productId)
                    .map(ProductEntity::getName)
                    .orElse("el producto");

            throw new InsufficientStockException(
                    "No hay stock suficiente de \"" + productName + "\". " +
                            "Disponible: " + currentStock + ", solicitado: " + requestedQuantity + "."
            );
        }
    }

    public void validateSaleItemsStock(List<StockCheckItemRequest> items, Long warehouseId) {

        if (items == null || items.isEmpty()) {
            throw new ValidationException("La venta debe tener al menos un ítem.");
        }

        for (StockCheckItemRequest item : items) {

            if (item.productId() == null) {
                throw new ValidationException("Falta indicar el producto de un ítem.");
            }

            validateAvailableStock(item.productId(), warehouseId, item.quantity());
        }
    }
}