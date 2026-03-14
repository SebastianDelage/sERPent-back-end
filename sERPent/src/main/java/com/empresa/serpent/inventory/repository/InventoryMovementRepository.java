package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryMovementRepository extends
        JpaRepository<InventoryMovementEntity, Long>,
        JpaSpecificationExecutor<InventoryMovementEntity> {

    List<InventoryMovementEntity> findByProductId(Long productId);

    List<InventoryMovementEntity> findByWarehouseId(Long warehouseId);

    List<InventoryMovementEntity> findByTransactionId(Long transactionId);

    List<InventoryMovementEntity> findByMovementType(MovementType movementType);

    List<InventoryMovementEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<InventoryMovementEntity> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Query("""
       SELECT
           m.movementType AS movementType,
           COUNT(m.id) AS movements,
           COALESCE(SUM(m.quantity), 0) AS totalQuantity
       FROM InventoryMovementEntity m
       GROUP BY m.movementType
       ORDER BY m.movementType
       """)
    List<com.empresa.serpent.reports.repository.projection.InventoryMovementsByTypeProjection> getInventoryMovementsByTypeReportRaw();
}