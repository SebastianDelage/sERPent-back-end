package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryMovementSpecifications;
import com.empresa.serpent.inventory.web.dto.filter.InventoryMovementFilter;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private final InventoryMovementRepository inventoryMovementRepository;

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
                .toList();
    }

    private BigDecimal signedQuantity(InventoryMovementEntity movement) {
        return switch (movement.getMovementType()) {
            case IN, ADJUSTMENT_IN, TRANSFER_IN -> movement.getQuantity();
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
