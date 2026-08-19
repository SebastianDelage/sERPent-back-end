package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.ProductWarehouseMinimumStockRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.UpsertProductWarehouseMinimumStockRequest;
import com.empresa.serpent.inventory.web.dto.response.ProductWarehouseMinimumStockResponse;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-warehouse minimum stock overrides for a product.
 *
 * <p>Reads resolve the cascade for every active warehouse, so a caller never has to know
 * the fallback rule. Writes only ever touch the override rows: the product's own
 * {@code minimumStock} is edited through the product form and is not this service's
 * business.
 */
@Service
@RequiredArgsConstructor
public class ProductWarehouseMinimumStockService {

    private final ProductWarehouseMinimumStockRepository repository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * Every ACTIVE warehouse with the minimum that applies to it, override or inherited.
     * Inactive warehouses are left out: nothing can be stocked there, so a threshold for
     * them would be dead configuration.
     */
    @Transactional(readOnly = true)
    public List<ProductWarehouseMinimumStockResponse> findByProduct(Long productId) {
        ProductEntity product = requireProduct(productId);

        Map<Long, BigDecimal> overrides = repository.findByProductId(productId).stream()
                .collect(Collectors.toMap(
                        row -> row.getWarehouse().getId(),
                        ProductWarehouseMinimumStockEntity::getMinimumStock));

        return warehouseRepository.search(false).stream()
                .map(warehouse -> {
                    BigDecimal own = overrides.get(warehouse.getId());

                    return new ProductWarehouseMinimumStockResponse(
                            warehouse.getId(),
                            warehouse.getName(),
                            own,
                            own != null ? own : product.getMinimumStock(),
                            own == null
                    );
                })
                .toList();
    }

    /** Defines or replaces the override for one warehouse. */
    @Transactional
    public ProductWarehouseMinimumStockResponse upsert(
            Long productId, UpsertProductWarehouseMinimumStockRequest request) {

        ProductEntity product = requireProduct(productId);
        WarehouseEntity warehouse = requireActiveWarehouse(request.warehouseId());

        ProductWarehouseMinimumStockEntity entity = repository
                .findByProductIdAndWarehouseId(productId, request.warehouseId())
                .orElseGet(() -> ProductWarehouseMinimumStockEntity.builder()
                        .product(product)
                        .warehouse(warehouse)
                        .build());

        entity.setMinimumStock(request.minimumStock());
        ProductWarehouseMinimumStockEntity saved = repository.save(entity);

        return new ProductWarehouseMinimumStockResponse(
                warehouse.getId(),
                warehouse.getName(),
                saved.getMinimumStock(),
                saved.getMinimumStock(),
                false
        );
    }

    /**
     * Drops the override, so the warehouse goes back to inheriting the product's own
     * minimum. Deleting something that was never defined is a no-op, not an error: the
     * end state the caller asked for is already the case.
     */
    @Transactional
    public void delete(Long productId, Long warehouseId) {
        requireProduct(productId);
        repository.deleteByProductIdAndWarehouseId(productId, warehouseId);
    }

    private ProductEntity requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    }

    private WarehouseEntity requireActiveWarehouse(Long warehouseId) {
        WarehouseEntity warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + warehouseId));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ValidationException(
                    "No podés definir un mínimo para el depósito \"" + warehouse.getName()
                            + "\" porque está inactivo.");
        }

        return warehouse;
    }
}
