package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.reports.repository.projection.InventoryMovementsByProductProjection;
import com.empresa.serpent.reports.repository.projection.InventoryMovementsByTypeProjection;
import com.empresa.serpent.reports.repository.projection.InventoryMovementsByWarehouseProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
       WHERE (:warehouseId IS NULL OR m.warehouse.id = :warehouseId)
       GROUP BY m.movementType
       ORDER BY m.movementType
       """)
    List<InventoryMovementsByTypeProjection> getInventoryMovementsByTypeReportRaw(
            @Param("warehouseId") Long warehouseId
    );

    @Query("""
       SELECT
           m.warehouse.id AS warehouseId,
           m.warehouse.name AS warehouseName,
           COUNT(m.id) AS movements,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalIn,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_OUT
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalOut,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE -m.quantity
               END
           ), 0) AS netQuantity
       FROM InventoryMovementEntity m
       GROUP BY m.warehouse.id, m.warehouse.name
       ORDER BY m.warehouse.name
       """)
    List<InventoryMovementsByWarehouseProjection> getInventoryMovementsByWarehouseReportRaw();

    @Query("""
       SELECT
           m.product.id AS productId,
           m.product.name AS productName,
           COUNT(m.id) AS movements,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalIn,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_OUT,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_OUT
                   )
                   THEN m.quantity
                   ELSE 0
               END
           ), 0) AS totalOut,
           COALESCE(SUM(
               CASE
                   WHEN m.movementType IN (
                       com.empresa.serpent.inventory.domain.enums.MovementType.IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.ADJUSTMENT_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.TRANSFER_IN,
                       com.empresa.serpent.inventory.domain.enums.MovementType.RETURN_IN
                   )
                   THEN m.quantity
                   ELSE -m.quantity
               END
           ), 0) AS netQuantity
       FROM InventoryMovementEntity m
       WHERE (:warehouseId IS NULL OR m.warehouse.id = :warehouseId)
       GROUP BY m.product.id, m.product.name
       ORDER BY m.product.name
       """)
    List<InventoryMovementsByProductProjection> getInventoryMovementsByProductReportRaw(
            @Param("warehouseId") Long warehouseId
    );
}