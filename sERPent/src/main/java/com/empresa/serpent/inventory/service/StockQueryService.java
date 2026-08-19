package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
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

    private final InventoryStockSnapshotRepository inventoryStockSnapshotRepository;
    private final ProductRepository productRepository;

    public List<StockResponse> getStock(StockFilter filter) {
        List<InventoryStockSnapshotEntity> snapshots = loadSnapshots(filter);

        return snapshots.stream()
                .map(snapshot -> new StockResponse(
                        snapshot.getProduct().getId(),
                        snapshot.getProduct().getName(),
                        snapshot.getWarehouse().getId(),
                        snapshot.getWarehouse().getName(),
                        snapshot.getCurrentStock()
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
        return inventoryStockSnapshotRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .map(InventoryStockSnapshotEntity::getCurrentStock)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getTotalStockByProduct(Long productId) {
        return inventoryStockSnapshotRepository.findByProductId(productId)
                .stream()
                .map(InventoryStockSnapshotEntity::getCurrentStock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ProductStockResponse> getTotalStockGroupedByProduct(Boolean onlyPositive) {
        return getTotalStockGroupedByProduct(onlyPositive, null);
    }

    /** Same, restricted to one warehouse when {@code warehouseId} is given. */
    public List<ProductStockResponse> getTotalStockGroupedByProduct(Boolean onlyPositive, Long warehouseId) {
        List<InventoryStockSnapshotEntity> snapshots = warehouseId == null
                ? inventoryStockSnapshotRepository.findAll()
                : inventoryStockSnapshotRepository.findByWarehouseId(warehouseId);

        Map<ProductKey, BigDecimal> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(
                        snapshot -> new ProductKey(
                                snapshot.getProduct().getId(),
                                snapshot.getProduct().getName()
                        ),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                InventoryStockSnapshotEntity::getCurrentStock,
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

    public List<LowStockResponse> getLowStock() {
        List<ProductStockResponse> stockRows = getTotalStockGroupedByProduct(false);

        Map<Long, ProductEntity> productMap = productRepository.findAll().stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        return stockRows.stream()
                .filter(stockRow -> {
                    ProductEntity product = productMap.get(stockRow.productId());

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

    private List<InventoryStockSnapshotEntity> loadSnapshots(StockFilter filter) {
        if (filter.productId() != null && filter.warehouseId() != null) {
            return inventoryStockSnapshotRepository
                    .findByProductIdAndWarehouseId(filter.productId(), filter.warehouseId())
                    .stream()
                    .toList();
        }

        if (filter.productId() != null) {
            return inventoryStockSnapshotRepository.findByProductId(filter.productId());
        }

        if (filter.warehouseId() != null) {
            return inventoryStockSnapshotRepository.findByWarehouseId(filter.warehouseId());
        }

        return inventoryStockSnapshotRepository.findAll();
    }

    private record ProductKey(
            Long productId,
            String productName
    ) {
    }
}