package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryMovementSpecifications;
import com.empresa.serpent.inventory.web.dto.filter.InventoryMovementFilter;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final ProductRepository productRepository;

    public List<StockResponse> getStock(StockFilter filter) {

        InventoryMovementFilter movementFilter = new InventoryMovementFilter(
                filter.productId(),
                filter.warehouseId(),
                null,
                null,
                null,
                null
        );

        List<InventoryMovementEntity> movements = inventoryMovementRepository.findAll(
                InventoryMovementSpecifications.fromFilter(movementFilter)
        );

        Map<StockKey, BigDecimal> grouped = movements.stream()
                .collect(Collectors.groupingBy(
                        movement -> new StockKey(
                                movement.getProduct().getId(),
                                movement.getProduct().getName(),
                                movement.getWarehouse().getId(),
                                movement.getWarehouse().getName()
                        ),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                this::signedQuantity,
                                BigDecimal::add
                        )
                ));

        return grouped.entrySet()
                .stream()
                .map(entry -> new StockResponse(
                        entry.getKey().productId(),
                        entry.getKey().productName(),
                        entry.getKey().warehouseId(),
                        entry.getKey().warehouseName(),
                        entry.getValue()
                ))
                .filter(response ->
                        filter.onlyPositive() == null
                                || !filter.onlyPositive()
                                || response.stock().compareTo(BigDecimal.ZERO) > 0
                )
                .sorted((a, b) -> {
                    int productCompare = a.productName().compareToIgnoreCase(b.productName());
                    if (productCompare != 0) {
                        return productCompare;
                    }
                    return a.warehouseName().compareToIgnoreCase(b.warehouseName());
                })
                .toList();
    }

    public BigDecimal getStockByProductAndWarehouse(Long productId, Long warehouseId) {
        return getStock(new StockFilter(productId, warehouseId, null))
                .stream()
                .map(StockResponse::stock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalStockByProduct(Long productId) {
        return getStock(new StockFilter(productId, null, null))
                .stream()
                .map(StockResponse::stock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ProductStockResponse> getTotalStockGroupedByProduct(Boolean onlyPositive) {

        List<StockResponse> stockRows = getStock(new StockFilter(null, null, null));

        Map<ProductKey, BigDecimal> grouped = stockRows.stream()
                .collect(Collectors.groupingBy(
                        row -> new ProductKey(
                                row.productId(),
                                row.productName()
                        ),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                StockResponse::stock,
                                BigDecimal::add
                        )
                ));

        return grouped.entrySet()
                .stream()
                .map(entry -> new ProductStockResponse(
                        entry.getKey().productId(),
                        entry.getKey().productName(),
                        entry.getValue()
                ))
                .filter(response ->
                        onlyPositive == null
                                || !onlyPositive
                                || response.totalStock().compareTo(BigDecimal.ZERO) > 0
                )
                .sorted((a, b) -> a.productName().compareToIgnoreCase(b.productName()))
                .toList();
    }

    /*
     LOW STOCK DETECTION

     This method detects products whose stock is at or below the
     configured minimum stock level.

     IMPORTANT BUSINESS RULE

     A product will ONLY be considered for low-stock detection if it
     has a minimumStock configured.

     If minimumStock is NULL, the system assumes that this product does
     not require strict stock monitoring and it will be ignored by this
     check.

     This allows sERPent to support businesses where some items are
     produced or handled dynamically (for example fresh food,
     handmade products or items produced on demand).

     In those cases, the system will not recommend increasing stock
     unless a minimumStock level is explicitly defined.
     */
    public List<LowStockResponse> getLowStock() {

        List<ProductStockResponse> stockRows = getTotalStockGroupedByProduct(false);

        Map<Long, ProductEntity> productMap = productRepository.findAll().stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        return stockRows.stream()
                .filter(stockRow -> {
                    ProductEntity product = productMap.get(stockRow.productId());

                    // Ignore products without inventory configuration
                    if (product == null || product.getMinimumStock() == null) {
                        return false;
                    }

                    return stockRow.totalStock()
                            .compareTo(product.getMinimumStock()) <= 0;
                })
                .map(stockRow -> {
                    ProductEntity product = productMap.get(stockRow.productId());

                    return new LowStockResponse(
                            stockRow.productId(),
                            stockRow.productName(),
                            stockRow.totalStock(),
                            product.getMinimumStock()
                    );
                })
                .sorted((a, b) -> a.productName().compareToIgnoreCase(b.productName()))
                .toList();
    }

    private BigDecimal signedQuantity(InventoryMovementEntity movement) {
        return switch (movement.getMovementType()) {
            case IN, ADJUSTMENT_IN, TRANSFER_IN, RETURN_IN -> movement.getQuantity();
            case OUT, ADJUSTMENT_OUT, TRANSFER_OUT -> movement.getQuantity().negate();
        };
    }

    private record StockKey(
            Long productId,
            String productName,
            Long warehouseId,
            String warehouseName
    ) {
    }

    private record ProductKey(
            Long productId,
            String productName
    ) {
    }
}