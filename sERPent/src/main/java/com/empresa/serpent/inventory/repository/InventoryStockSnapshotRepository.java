package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.reports.repository.projection.InventoryReplenishmentProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InventoryStockSnapshotRepository extends JpaRepository<InventoryStockSnapshotEntity, Long> {

    Optional<InventoryStockSnapshotEntity> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<InventoryStockSnapshotEntity> findByProductId(Long productId);

    List<InventoryStockSnapshotEntity> findByWarehouseId(Long warehouseId);

    @Query("""
       SELECT
           p.id AS productId,
           p.name AS productName,
           w.id AS warehouseId,
           w.name AS warehouseName,
           s.currentStock AS currentStock,
           p.reorderPoint AS reorderPoint,
           p.reorderQuantity AS reorderQuantity
       FROM InventoryStockSnapshotEntity s
       JOIN s.product p
       JOIN s.warehouse w
       WHERE p.reorderPoint IS NOT NULL
         AND s.currentStock <= p.reorderPoint
       ORDER BY p.name
       """)
    List<InventoryReplenishmentProjection> getReplenishmentReportRaw();
}