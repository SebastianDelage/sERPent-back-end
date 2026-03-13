package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryStockSnapshotRepository extends JpaRepository<InventoryStockSnapshotEntity, Long> {

    Optional<InventoryStockSnapshotEntity> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<InventoryStockSnapshotEntity> findByProductId(Long productId);

    List<InventoryStockSnapshotEntity> findByWarehouseId(Long warehouseId);
}