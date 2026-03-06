package com.empresa.serpent.inventory.domain.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovementEntity, Long> {

    List<InventoryMovementEntity> findByProductId(Long productId);

    List<InventoryMovementEntity> findByWarehouseEntityId(Long warehouseId);

    List<InventoryMovementEntity> findByProductIdAndWarehouseEntityId(Long productId, Long warehouseId);

    List<InventoryMovementEntity> findByTransactionId(Long transactionId);

    List<InventoryMovementEntity> findByTransactionDetailId(Long transactionDetailId);

    List<InventoryMovementEntity> findByMovementType(MovementType movementType);

}