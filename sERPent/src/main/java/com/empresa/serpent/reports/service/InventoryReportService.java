package com.empresa.serpent.reports.service;

import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.reports.web.dto.response.*;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
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
public class InventoryReportService {

    private final StockQueryService stockQueryService;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final WarehouseScopeService warehouseScopeService;
    private final InventoryStockSnapshotRepository snapshotRepository;

    public List<InventorySummaryResponse> getInventorySummary(Long warehouseId) {
        return stockQueryService.getTotalStockGroupedByProduct(false, warehouseId)
                .stream()
                .map(this::toInventorySummaryResponse)
                .toList();
    }

    public List<InventoryByWarehouseResponse> getInventoryByWarehouse() {
        return stockQueryService.getStock(new StockFilter(null, null, null))
                .stream()
                .map(this::toInventoryByWarehouseResponse)
                .toList();
    }

    public List<LowStockResponse> getLowStockReport(Long warehouseId) {
        return stockQueryService.getLowStock(warehouseId);
    }

    public List<WarehouseSummaryResponse> getWarehouseSummary() {

        List<StockResponse> stockRows = stockQueryService.getStock(new StockFilter(null, null, null));

        Map<WarehouseKey, List<StockResponse>> grouped = stockRows.stream()
                .collect(Collectors.groupingBy(row -> new WarehouseKey(
                        row.warehouseId(),
                        row.warehouseName()
                )));

        return grouped.entrySet()
                .stream()
                .map(entry -> {
                    WarehouseKey key = entry.getKey();
                    List<StockResponse> rows = entry.getValue();

                    long distinctProducts = rows.stream()
                            .map(StockResponse::productId)
                            .distinct()
                            .count();

                    BigDecimal totalUnits = rows.stream()
                            .map(StockResponse::stock)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new WarehouseSummaryResponse(
                            key.warehouseId(),
                            key.warehouseName(),
                            distinctProducts,
                            totalUnits
                    );
                })
                .sorted((a, b) -> a.warehouseName().compareToIgnoreCase(b.warehouseName()))
                .toList();
    }

    public List<InventoryMovementsByTypeResponse> getInventoryMovementsByType(Long warehouseId) {
        WarehouseScope scope = warehouseScopeService.resolve(warehouseId);
        if (scope.seesNothing()) {
            return List.of();
        }

        return inventoryMovementRepository
                .getInventoryMovementsByTypeReportRaw(scope.unrestricted(), scope.warehouseIds())
                .stream()
                .map(row -> new InventoryMovementsByTypeResponse(
                        row.getMovementType(),
                        row.getMovements(),
                        row.getTotalQuantity()
                ))
                .toList();
    }

    public List<InventoryMovementsByWarehouseResponse> getInventoryMovementsByWarehouse() {
        return inventoryMovementRepository.getInventoryMovementsByWarehouseReportRaw()
                .stream()
                .map(row -> new InventoryMovementsByWarehouseResponse(
                        row.getWarehouseId(),
                        row.getWarehouseName(),
                        row.getMovements(),
                        row.getTotalIn(),
                        row.getTotalOut(),
                        row.getNetQuantity()
                ))
                .toList();
    }

    public List<InventoryMovementsByProductResponse> getInventoryMovementsByProduct(Long warehouseId) {
        WarehouseScope scope = warehouseScopeService.resolve(warehouseId);
        if (scope.seesNothing()) {
            return List.of();
        }

        return inventoryMovementRepository
                .getInventoryMovementsByProductReportRaw(scope.unrestricted(), scope.warehouseIds())
                .stream()
                .map(row -> new InventoryMovementsByProductResponse(
                        row.getProductId(),
                        row.getProductName(),
                        row.getMovements(),
                        row.getTotalIn(),
                        row.getTotalOut(),
                        row.getNetQuantity()
                ))
                .toList();
    }

    private InventorySummaryResponse toInventorySummaryResponse(ProductStockResponse row) {
        return new InventorySummaryResponse(
                row.productId(),
                row.productName(),
                row.totalStock()
        );
    }

    private InventoryByWarehouseResponse toInventoryByWarehouseResponse(StockResponse row) {
        return new InventoryByWarehouseResponse(
                row.productId(),
                row.productName(),
                row.warehouseId(),
                row.warehouseName(),
                row.stock()
        );
    }

    public List<InventoryReplenishmentResponse> getReplenishmentReport() {

        return snapshotRepository.getReplenishmentReportRaw()
                .stream()
                .map(row -> {

                    BigDecimal reorderQty =
                            row.getReorderQuantity() == null
                                    ? BigDecimal.ZERO
                                    : row.getReorderQuantity();

                    BigDecimal suggested =
                            reorderQty.subtract(row.getCurrentStock());

                    if (suggested.compareTo(BigDecimal.ZERO) < 0) {
                        suggested = reorderQty;
                    }

                    return new InventoryReplenishmentResponse(
                            row.getProductId(),
                            row.getProductName(),
                            row.getWarehouseId(),
                            row.getWarehouseName(),
                            row.getCurrentStock(),
                            row.getReorderPoint(),
                            reorderQty,
                            suggested
                    );
                })
                .toList();
    }

    private record WarehouseKey(
            Long warehouseId,
            String warehouseName
    ) {
    }
}