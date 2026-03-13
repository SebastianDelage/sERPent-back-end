package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryStockSnapshotService {

    private final InventoryStockSnapshotRepository inventoryStockSnapshotRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    @Transactional
    public void applyMovement(InventoryMovementEntity movement) {
        validateMovement(movement);

        InventoryStockSnapshotEntity snapshot = inventoryStockSnapshotRepository
                .findByProductIdAndWarehouseId(
                        movement.getProduct().getId(),
                        movement.getWarehouse().getId()
                )
                .orElseGet(() -> InventoryStockSnapshotEntity.builder()
                        .product(movement.getProduct())
                        .warehouse(movement.getWarehouse())
                        .currentStock(BigDecimal.ZERO)
                        .lastMovementId(null)
                        .build());

        BigDecimal signedQuantity = signedQuantity(movement);

        snapshot.setCurrentStock(snapshot.getCurrentStock().add(signedQuantity));
        snapshot.setLastMovementId(movement.getId());

        inventoryStockSnapshotRepository.save(snapshot);
    }

    @Transactional
    public void applyMovements(List<InventoryMovementEntity> movements) {
        if (movements == null || movements.isEmpty()) {
            return;
        }

        for (InventoryMovementEntity movement : movements) {
            applyMovement(movement);
        }
    }

    @Transactional
    public void rebuildSnapshots() {
        inventoryStockSnapshotRepository.deleteAllInBatch();

        List<InventoryMovementEntity> movements = inventoryMovementRepository.findAll()
                .stream()
                .sorted((a, b) -> {
                    int createdAtCompare = a.getCreatedAt().compareTo(b.getCreatedAt());
                    if (createdAtCompare != 0) {
                        return createdAtCompare;
                    }
                    return a.getId().compareTo(b.getId());
                })
                .toList();

        applyMovements(movements);
    }

    private void validateMovement(InventoryMovementEntity movement) {
        if (movement == null) {
            throw new IllegalArgumentException("Inventory movement cannot be null");
        }

        if (movement.getId() == null) {
            throw new IllegalArgumentException("Inventory movement id cannot be null");
        }

        if (movement.getProduct() == null || movement.getProduct().getId() == null) {
            throw new IllegalArgumentException("Inventory movement product cannot be null");
        }

        if (movement.getWarehouse() == null || movement.getWarehouse().getId() == null) {
            throw new IllegalArgumentException("Inventory movement warehouse cannot be null");
        }

        if (movement.getMovementType() == null) {
            throw new IllegalArgumentException("Inventory movement type cannot be null");
        }

        if (movement.getQuantity() == null) {
            throw new IllegalArgumentException("Inventory movement quantity cannot be null");
        }

        if (movement.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Inventory movement quantity must be greater than zero");
        }
    }

    private BigDecimal signedQuantity(InventoryMovementEntity movement) {
        return switch (movement.getMovementType()) {
            case IN, ADJUSTMENT_IN, TRANSFER_IN, RETURN_IN -> movement.getQuantity();
            case OUT, ADJUSTMENT_OUT, TRANSFER_OUT -> movement.getQuantity().negate();
        };
    }
}