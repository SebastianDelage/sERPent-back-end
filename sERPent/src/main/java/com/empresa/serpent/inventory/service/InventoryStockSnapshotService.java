package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.web.dto.response.InventoryReconciliationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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

        snapshot.setCurrentStock(snapshot.getCurrentStock().add(toSignedQuantity(movement)));
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
                .sorted(
                        Comparator.comparing(InventoryMovementEntity::getCreatedAt)
                                .thenComparing(InventoryMovementEntity::getId)
                )
                .toList();

        applyMovements(movements);
    }

    @Transactional(readOnly = true)
    public List<InventoryReconciliationResponse> reconcileSnapshots() {
        Map<ReconciliationKey, BigDecimal> ledgerMap = inventoryMovementRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(
                        movement -> new ReconciliationKey(
                                movement.getProduct().getId(),
                                movement.getProduct().getName(),
                                movement.getWarehouse().getId(),
                                movement.getWarehouse().getName()
                        ),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                this::toSignedQuantity,
                                BigDecimal::add
                        )
                ));

        Map<ReconciliationKey, BigDecimal> snapshotMap = inventoryStockSnapshotRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        snapshot -> new ReconciliationKey(
                                snapshot.getProduct().getId(),
                                snapshot.getProduct().getName(),
                                snapshot.getWarehouse().getId(),
                                snapshot.getWarehouse().getName()
                        ),
                        InventoryStockSnapshotEntity::getCurrentStock
                ));

        Set<ReconciliationKey> allKeys = new HashSet<>();
        allKeys.addAll(ledgerMap.keySet());
        allKeys.addAll(snapshotMap.keySet());

        return allKeys.stream()
                .map(key -> {
                    BigDecimal ledgerStock = ledgerMap.getOrDefault(key, BigDecimal.ZERO);
                    BigDecimal snapshotStock = snapshotMap.getOrDefault(key, BigDecimal.ZERO);
                    BigDecimal difference = ledgerStock.subtract(snapshotStock);

                    return new InventoryReconciliationResponse(
                            key.productId(),
                            key.productName(),
                            key.warehouseId(),
                            key.warehouseName(),
                            ledgerStock,
                            snapshotStock,
                            difference,
                            difference.compareTo(BigDecimal.ZERO) == 0
                    );
                })
                .sorted(Comparator
                        .comparing(InventoryReconciliationResponse::productName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(InventoryReconciliationResponse::warehouseName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryReconciliationResponse> findSnapshotInconsistencies() {
        return reconcileSnapshots().stream()
                .filter(response -> !response.consistent())
                .toList();
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

    private BigDecimal toSignedQuantity(InventoryMovementEntity movement) {
        return switch (movement.getMovementType()) {
            case IN, ADJUSTMENT_IN, TRANSFER_IN, RETURN_IN -> movement.getQuantity();
            case OUT, ADJUSTMENT_OUT, TRANSFER_OUT -> movement.getQuantity().negate();
        };
    }

    private record ReconciliationKey(
            Long productId,
            String productName,
            Long warehouseId,
            String warehouseName
    ) {
    }
}