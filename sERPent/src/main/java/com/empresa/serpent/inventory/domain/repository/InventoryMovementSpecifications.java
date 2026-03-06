package com.empresa.serpent.inventory.domain.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.web.dto.filter.InventoryMovementFilter;
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
                        : cb.equal(root.get("warehouseEntity").get("id"), warehouseId);
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
}
