package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import com.empresa.serpent.inventory.domain.enums.StockStatusFilter;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.filter.StockPageFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotSpecifications;
import com.empresa.serpent.inventory.repository.ProductWarehouseMinimumStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private final InventoryStockSnapshotRepository inventoryStockSnapshotRepository;
    private final ProductRepository productRepository;
    private final ProductWarehouseMinimumStockRepository productWarehouseMinimumStockRepository;

    public List<StockResponse> getStock(StockFilter filter) {
        List<InventoryStockSnapshotEntity> snapshots = loadSnapshots(filter);

        return snapshots.stream()
                .map(StockQueryService::toStockResponse)
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

    private static StockResponse toStockResponse(InventoryStockSnapshotEntity snapshot) {
        return new StockResponse(
                snapshot.getProduct().getId(),
                snapshot.getProduct().getName(),
                snapshot.getWarehouse().getId(),
                snapshot.getWarehouse().getName(),
                snapshot.getCurrentStock(),
                snapshot.getWarehouse().getActive()
        );
    }

    /**
     * The per-warehouse view, paginated and filtered in the query.
     *
     * <p>Kept apart from {@link #getStock(StockFilter)}, which stays unpaginated: the
     * sale, adjustment, transformation and transfer forms all need a warehouse's whole
     * stock list to work, and handing them a page would break them for no gain.
     */
    public Page<StockResponse> searchStock(StockPageFilter filter, Pageable pageable) {
        return inventoryStockSnapshotRepository
                .findAll(InventoryStockSnapshotSpecifications.fromFilter(filter), withStableSort(pageable))
                .map(StockQueryService::toStockResponse);
    }

    /**
     * Paging without a deterministic order is not stable: rows can repeat on one page and
     * vanish from another as the database is free to return them in any order. Falls back
     * to product then warehouse, matching the unpaginated view's ordering.
     */
    private Pageable withStableSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("product.name", "warehouse.name"));
    }

    /** The per-product view, paginated: stock summed across warehouses, one row per product. */
    public Page<ProductStockResponse> searchStockGroupedByProduct(
            StockPageFilter filter, Pageable pageable) {

        StockStatusFilter status = filter.statusOrAll();

        return inventoryStockSnapshotRepository.searchGroupedByProduct(
                        blankToNull(filter.search()),
                        filter.warehouseId(),
                        status == StockStatusFilter.OUT_OF_STOCK,
                        status == StockStatusFilter.IN_STOCK,
                        status == StockStatusFilter.BELOW_MINIMUM,
                        pageable)
                .map(row -> new ProductStockResponse(
                        row.getProductId(),
                        row.getProductName(),
                        row.getTotalStock()));
    }

    /**
     * How many (product, warehouse) pairs are below their minimum within the filtered set.
     *
     * <p>Counted over the whole filtered set, never over the current page — a page's worth
     * of alerts is not the number of situations to attend to. The status filter is
     * deliberately ignored: honouring it would make this echo the paginator whenever the
     * user filters by "below minimum".
     */
    public long countLowStockAlerts(StockPageFilter filter) {
        return inventoryStockSnapshotRepository.count(
                InventoryStockSnapshotSpecifications.fromFilterIgnoringStatus(filter));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        return getLowStock(null);
    }

    /**
     * Low stock, decided PER WAREHOUSE. Restricted to one warehouse when
     * {@code warehouseId} is given.
     *
     * <p>The threshold resolves in cascade: the per-warehouse override for that
     * (product, warehouse) if one exists, otherwise the product's own minimum. A product
     * with no minimum at either level is never low and is left out — there are goods
     * nobody wants to track.
     *
     * <p>Comparing per warehouse instead of against the summed total is the whole point:
     * a product at zero in one branch and overstocked in another has to surface, and
     * summing first would hide exactly that case.
     */
    public List<LowStockResponse> getLowStock(Long warehouseId) {
        List<StockResponse> stockRows = getStock(new StockFilter(null, warehouseId, null));

        Map<Long, ProductEntity> productMap = productRepository.findAll().stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        // (productId, warehouseId) -> override, loaded once to keep the loop free of N+1.
        Map<String, BigDecimal> overrides = productWarehouseMinimumStockRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        row -> overrideKey(row.getProduct().getId(), row.getWarehouse().getId()),
                        ProductWarehouseMinimumStockEntity::getMinimumStock));

        return stockRows.stream()
                .map(row -> toLowStockRow(row, productMap, overrides))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(LowStockResponse::productName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(LowStockResponse::warehouseName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The row if this (product, warehouse) is at or below its minimum, null otherwise. */
    private LowStockResponse toLowStockRow(StockResponse row,
                                           Map<Long, ProductEntity> productMap,
                                           Map<String, BigDecimal> overrides) {
        ProductEntity product = productMap.get(row.productId());
        if (product == null) {
            return null;
        }

        BigDecimal override = overrides.get(overrideKey(row.productId(), row.warehouseId()));
        BigDecimal minimum = override != null ? override : product.getMinimumStock();

        // No minimum at either level: nothing to be below.
        if (minimum == null) {
            return null;
        }

        // "At or below" counts as low, matching the pre-existing criterion.
        if (row.stock().compareTo(minimum) > 0) {
            return null;
        }

        BigDecimal missing = minimum.subtract(row.stock()).max(BigDecimal.ZERO);

        return new LowStockResponse(
                row.productId(),
                row.productName(),
                row.warehouseId(),
                row.warehouseName(),
                row.stock(),
                minimum,
                override != null,
                missing
        );
    }

    private String overrideKey(Long productId, Long warehouseId) {
        return productId + "-" + warehouseId;
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