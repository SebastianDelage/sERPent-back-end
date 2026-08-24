package com.empresa.serpent.reports.service;

import com.empresa.serpent.catalog.domain.entity.ProductSupplierEntity;
import com.empresa.serpent.catalog.repository.ProductSupplierRepository;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.reports.repository.projection.InventoryReplenishmentProjection;
import com.empresa.serpent.reports.repository.projection.LastPurchasePriceProjection;
import com.empresa.serpent.reports.web.dto.response.*;
import com.empresa.serpent.transactions.repository.TransactionDetailRepository;
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
    private final ProductSupplierRepository productSupplierRepository;
    private final TransactionDetailRepository transactionDetailRepository;

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

    /**
     * What to reorder, per branch, with who to buy it from and what it cost last time.
     *
     * <p>Scoped like every other read: an ADMIN with no filter sees every branch, an employee
     * sees their own, and naming somebody else's is refused before any figure is computed.
     *
     * <p>The suppliers and the last prices are fetched in two batch queries over the products
     * that actually came back, not per row: the report is read on a screen, and a query per
     * line would make it quadratic in the number of shortages.
     */
    public List<InventoryReplenishmentResponse> getReplenishmentReport(Long warehouseId) {
        WarehouseScope scope = warehouseScopeService.resolve(warehouseId);

        if (scope.seesNothing()) {
            return List.of();
        }

        List<InventoryReplenishmentProjection> rows = snapshotRepository.getReplenishmentReportRaw(
                scope.unrestricted(), scope.warehouseIds(), warehouseId);

        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = rows.stream()
                .map(InventoryReplenishmentProjection::getProductId)
                .distinct()
                .toList();

        Map<Long, ProductSupplierEntity> preferredByProduct =
                productSupplierRepository.findPreferredForProducts(productIds).stream()
                        .collect(Collectors.toMap(ps -> ps.getProduct().getId(), ps -> ps));

        Map<Long, LastPurchasePriceProjection> lastPriceByProduct =
                transactionDetailRepository.findLastPurchasePrices(productIds).stream()
                        .collect(Collectors.toMap(LastPurchasePriceProjection::getProductId, p -> p));

        return rows.stream()
                .map(row -> toReplenishmentResponse(
                        row,
                        preferredByProduct.get(row.getProductId()),
                        lastPriceByProduct.get(row.getProductId())))
                .toList();
    }

    private InventoryReplenishmentResponse toReplenishmentResponse(
            InventoryReplenishmentProjection row,
            ProductSupplierEntity preferred,
            LastPurchasePriceProjection lastPurchase) {

        return new InventoryReplenishmentResponse(
                row.getProductId(),
                row.getProductName(),
                row.getProductSku(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getCurrentStock(),
                row.getMinimumStock(),
                row.getReorderPoint(),
                row.getReorderQuantity(),
                suggestedOrderQuantity(row),
                preferred == null ? null : preferred.getSupplierEntity().getId(),
                preferred == null ? null : preferred.getSupplierEntity().getName(),
                preferred == null ? null : preferred.getSupplierProductCode(),
                preferred == null ? null : preferred.getLeadTimeDays(),
                lastPurchase == null ? null : lastPurchase.getUnitPrice(),
                lastPurchase == null ? null : lastPurchase.getPurchaseDate(),
                lastPurchase == null ? null : lastPurchase.getSupplierName()
        );
    }

    /**
     * How much to order: enough to bring the stock up to the reorder quantity.
     *
     * <p>Unchanged arithmetic from before this report grew a cascade — including the fallback
     * to a full batch when the target already sits below the current stock, which happens
     * when the reorder point is set higher than the reorder quantity. The reorder point fired,
     * so something has to be ordered, and a full batch is the sensible default.
     *
     * <p>What DID change: a missing reorder quantity now yields null instead of zero. The line
     * still belongs in the report — the product IS short — but nobody has said how much to
     * buy, and "0" read as "order nothing" for something that needs ordering.
     */
    private BigDecimal suggestedOrderQuantity(InventoryReplenishmentProjection row) {
        BigDecimal target = row.getReorderQuantity();

        if (target == null) {
            return null;
        }

        BigDecimal suggested = target.subtract(row.getCurrentStock());
        return suggested.compareTo(BigDecimal.ZERO) < 0 ? target : suggested;
    }

    private record WarehouseKey(
            Long warehouseId,
            String warehouseName
    ) {
    }
}