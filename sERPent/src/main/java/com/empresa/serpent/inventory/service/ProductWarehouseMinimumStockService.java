package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.service.ProductReorderOverrideGuard;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-warehouse reorder overrides for a product: minimum, reorder point and quantity.
 *
 * <p>Reads resolve the cascade for every active warehouse, so a caller never has to know the
 * fallback rule. Writes only ever touch the override rows: the product's own figures are
 * edited through the product form and are not this service's business.
 *
 * <p>THE INVARIANT THIS SERVICE PROTECTS: at any warehouse, the reorder point that applies
 * may not sit below the minimum that applies. It is checked on the RESOLVED pair, which is
 * the only way to catch the mixed cases — a warehouse with its own minimum but an inherited
 * reorder point, or the reverse. Comparing the row's raw columns would miss both, because in
 * each of them one side of the comparison is not in the row at all.
 */
@Service
@RequiredArgsConstructor
public class ProductWarehouseMinimumStockService implements ProductReorderOverrideGuard {

    private final ProductWarehouseMinimumStockRepository repository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * Every ACTIVE warehouse with the configuration that applies to it, override or
     * inherited. Inactive warehouses are left out: nothing can be stocked there, so
     * thresholds for them would be dead configuration.
     */
    @Transactional(readOnly = true)
    public List<ProductWarehouseMinimumStockResponse> findByProduct(Long productId) {
        ProductEntity product = requireProduct(productId);

        Map<Long, ProductWarehouseMinimumStockEntity> overrides = repository.findByProductId(productId).stream()
                .collect(Collectors.toMap(row -> row.getWarehouse().getId(), Function.identity()));

        return warehouseRepository.search(null, false).stream()
                .map(warehouse -> toResponse(product, warehouse, overrides.get(warehouse.getId())))
                .toList();
    }

    /**
     * Defines or replaces the override for one warehouse.
     *
     * <p>An upsert that clears all three figures deletes the row instead of storing one that
     * overrides nothing — "inherit everything" and "no override" are the same statement, and
     * only one of them should be representable.
     */
    @Transactional
    public ProductWarehouseMinimumStockResponse upsert(
            Long productId, UpsertProductWarehouseMinimumStockRequest request) {

        ProductEntity product = requireProduct(productId);
        WarehouseEntity warehouse = requireActiveWarehouse(request.warehouseId());

        boolean overridesNothing = request.minimumStock() == null
                && request.reorderPoint() == null
                && request.reorderQuantity() == null;

        if (overridesNothing) {
            repository.deleteByProductIdAndWarehouseId(productId, request.warehouseId());
            return toResponse(product, warehouse, null);
        }

        // Validated against what will APPLY once saved, not against what was sent: the
        // figures the request leaves out still take part, they just come from the product.
        validateResolvedPair(
                warehouse.getName(),
                resolve(request.minimumStock(), product.getMinimumStock()),
                resolve(request.reorderPoint(), product.getReorderPoint()));

        ProductWarehouseMinimumStockEntity entity = repository
                .findByProductIdAndWarehouseId(productId, request.warehouseId())
                .orElseGet(() -> ProductWarehouseMinimumStockEntity.builder()
                        .product(product)
                        .warehouse(warehouse)
                        .build());

        entity.setMinimumStock(request.minimumStock());
        entity.setReorderPoint(request.reorderPoint());
        entity.setReorderQuantity(request.reorderQuantity());

        return toResponse(product, warehouse, repository.save(entity));
    }

    /**
     * Drops the override, so the warehouse goes back to inheriting every figure from the
     * product. Deleting something that was never defined is a no-op, not an error: the end
     * state the caller asked for is already the case.
     *
     * <p>Needs no invariant check. Afterwards both figures come from the product, and the
     * product's own pair is already validated by {@code ProductService} — so a full delete
     * can never be what breaks the rule.
     */
    @Transactional
    public void delete(Long productId, Long warehouseId) {
        requireProduct(productId);
        repository.deleteByProductIdAndWarehouseId(productId, warehouseId);
    }

    /**
     * Re-checks every existing override against a product's proposed figures.
     *
     * <p>Called by {@code ProductService} before it lets a product's own minimum or reorder
     * point move: those two are the fallback for every warehouse that does not override
     * them, so raising the minimum — or lowering the reorder point — can break a warehouse
     * that was perfectly consistent a moment earlier and is not itself being edited.
     *
     * <p>Refused rather than silently allowed. An inconsistent pair does not fail loudly at
     * the warehouse: it just makes the replenishment report fire after the floor has already
     * been breached, which is exactly the kind of wrong that nobody notices. The message
     * names the warehouses so the fix is obvious.
     */
    @Transactional(readOnly = true)
    @Override
    public void validateOverridesAgainst(Long productId,
                                         BigDecimal productMinimumStock,
                                         BigDecimal productReorderPoint) {
        List<String> offending = repository.findByProductId(productId).stream()
                .filter(row -> !isConsistent(
                        resolve(row.getMinimumStock(), productMinimumStock),
                        resolve(row.getReorderPoint(), productReorderPoint)))
                .map(row -> row.getWarehouse().getName())
                .toList();

        if (!offending.isEmpty()) {
            throw new ValidationException(
                    "No se puede guardar: con estos valores, el punto de reposición quedaría por "
                            + "debajo del stock mínimo en " + String.join(", ", offending)
                            + ". Ajustá primero la configuración de "
                            + (offending.size() == 1 ? "ese depósito" : "esos depósitos") + ".");
        }
    }

    /** The cascade, in one place: the override when it has one, the product's otherwise. */
    private BigDecimal resolve(BigDecimal own, BigDecimal fromProduct) {
        return own != null ? own : fromProduct;
    }

    private boolean isConsistent(BigDecimal effectiveMinimum, BigDecimal effectiveReorderPoint) {
        // Either side missing means there is nothing to compare: a product with no minimum
        // is never low, and one with no reorder point never reaches the report.
        if (effectiveMinimum == null || effectiveReorderPoint == null) {
            return true;
        }
        return effectiveReorderPoint.compareTo(effectiveMinimum) >= 0;
    }

    private void validateResolvedPair(String warehouseName,
                                      BigDecimal effectiveMinimum,
                                      BigDecimal effectiveReorderPoint) {
        if (!isConsistent(effectiveMinimum, effectiveReorderPoint)) {
            throw new ValidationException(
                    "En " + warehouseName + " el punto de reposición (" + effectiveReorderPoint
                            + ") no puede ser menor al stock mínimo que aplica ahí ("
                            + effectiveMinimum + "). Recordá que los valores que no definís "
                            + "para el depósito se heredan del producto.");
        }
    }

    private ProductWarehouseMinimumStockResponse toResponse(ProductEntity product,
                                                            WarehouseEntity warehouse,
                                                            ProductWarehouseMinimumStockEntity override) {
        BigDecimal ownMinimum = override == null ? null : override.getMinimumStock();
        BigDecimal ownReorderPoint = override == null ? null : override.getReorderPoint();
        BigDecimal ownReorderQuantity = override == null ? null : override.getReorderQuantity();

        return new ProductWarehouseMinimumStockResponse(
                warehouse.getId(),
                warehouse.getName(),
                ownMinimum,
                resolve(ownMinimum, product.getMinimumStock()),
                ownReorderPoint,
                resolve(ownReorderPoint, product.getReorderPoint()),
                ownReorderQuantity,
                resolve(ownReorderQuantity, product.getReorderQuantity()),
                override == null
        );
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
                    "No podés definir valores para el depósito \"" + warehouse.getName()
                            + "\" porque está inactivo.");
        }

        return warehouse;
    }
}
