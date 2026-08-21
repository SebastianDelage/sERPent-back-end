package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.web.dto.filter.InventoryMovementFilter;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class InventoryMovementSpecifications {

    private InventoryMovementSpecifications() {
    }

    public static Specification<InventoryMovementEntity> hasProductId(Long productId) {
        return (root, query, cb) ->
                productId == null ? cb.conjunction()
                        : cb.equal(root.get("product").get("id"), productId);
    }

    public static Specification<InventoryMovementEntity> hasWarehouseId(Long warehouseId) {
        return (root, query, cb) ->
                warehouseId == null ? cb.conjunction()
                        : cb.equal(root.get("warehouse").get("id"), warehouseId);
    }

    public static Specification<InventoryMovementEntity> hasTransactionId(Long transactionId) {
        return (root, query, cb) ->
                transactionId == null ? cb.conjunction()
                        : cb.equal(root.get("transaction").get("id"), transactionId);
    }

    public static Specification<InventoryMovementEntity> hasMovementType(MovementType movementType) {
        return (root, query, cb) ->
                movementType == null ? cb.conjunction()
                        : cb.equal(root.get("movementType"), movementType);
    }

    public static Specification<InventoryMovementEntity> dateFrom(LocalDateTime from) {
        return (root, query, cb) ->
                from == null ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<InventoryMovementEntity> dateTo(LocalDateTime to) {
        return (root, query, cb) ->
                to == null ? cb.conjunction()
                        : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<InventoryMovementEntity> fromFilter(InventoryMovementFilter filter) {
        if (filter == null) {
            return Specification.where(null);
        }

        return Specification
                .where(hasProductId(filter.productId()))
                .and(hasWarehouseId(filter.warehouseId()))
                .and(hasTransactionId(filter.transactionId()))
                .and(hasMovementType(filter.movementType()))
                .and(dateFrom(filter.dateFrom()))
                .and(dateTo(filter.dateTo()));
    }

    /**
     * Restricts to the branches the caller may see. A no-op for an unrestricted caller.
     *
     * <p>Callers must short-circuit an empty scope before reaching this: an empty IN list is
     * a portability trap, and "sees nothing" is answerable without a query.
     */
    public static Specification<InventoryMovementEntity> withinScope(WarehouseScope scope) {
        return (root, query, cb) -> scope.unrestricted()
                ? cb.conjunction()
                : root.get("warehouse").get("id").in(scope.warehouseIds());
    }
}
