package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.web.dto.response.InventoryReconciliationResponse;
import com.empresa.serpent.shared.exception.InsufficientStockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final InventoryStockSnapshotSeedService inventoryStockSnapshotSeedService;

    /**
     * Applies a single movement to the derived stock snapshot with an atomic, DB-level conditional
     * UPDATE. This UPDATE — not the earlier fail-fast check in
     * {@code StockValidationService.validateAvailableStock}/{@code validateSaleItemsStock} — is the
     * real guard against oversell. Those pre-checks stay for a clear per-item message, but two
     * concurrent operations can both pass an in-memory check and still race; only the conditional
     * {@code current_stock >= quantity} in {@code decreaseStockWithFloor} prevents the lost update.
     */
    @Transactional
    public void applyMovement(InventoryMovementEntity movement) {
        validateMovement(movement);

        Long productId = movement.getProduct().getId();
        Long warehouseId = movement.getWarehouse().getId();
        BigDecimal quantity = movement.getQuantity();
        Long movementId = movement.getId();
        MovementType movementType = movement.getMovementType();

        int affectedRows = applyDelta(movementType, productId, warehouseId, quantity, movementId);
        if (affectedRows > 0) {
            return;
        }

        if (isVendibleOut(movementType)) {
            // The conditional UPDATE matched no row: the balance is below the requested quantity,
            // or no snapshot exists yet. For a vendible output both mean "not enough available".
            throw new InsufficientStockException(insufficientStockMessage(movement));
        }

        // Increase or ADJUSTMENT_OUT and the snapshot row does not exist yet (first movement for
        // this product+warehouse). Seed a zero-balance row, then re-apply the delta exactly once.
        // Seeding runs in a separate bean with REQUIRES_NEW, so a concurrent-insert collision is
        // resolved at that boundary and never poisons this transaction.
        seedZeroSnapshot(productId, warehouseId);

        int affectedRowsAfterSeed = applyDelta(movementType, productId, warehouseId, quantity, movementId);
        if (affectedRowsAfterSeed == 0) {
            // Do not retry again: one seed + one UPDATE is the hard limit. Fail loudly instead of
            // looping.
            throw new IllegalStateException(
                    "No se pudo actualizar el stock luego de inicializar el saldo del producto.");
        }
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

        // Rebuild is a single-threaded, full reconstruction of the ledger after deleting every
        // snapshot, so it does not use the concurrency-safe conditional UPDATE nor its floor: it
        // must faithfully reproduce the ledger sum for each product+warehouse (including any
        // historical negative balance), not enforce the live oversell guard.
        for (InventoryMovementEntity movement : movements) {
            reapplyMovementForRebuild(movement);
        }
    }

    private void reapplyMovementForRebuild(InventoryMovementEntity movement) {
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

    private int applyDelta(
            MovementType movementType,
            Long productId,
            Long warehouseId,
            BigDecimal quantity,
            Long movementId
    ) {
        return switch (movementType) {
            case IN, ADJUSTMENT_IN, TRANSFER_IN, RETURN_IN ->
                    inventoryStockSnapshotRepository.increaseStock(productId, warehouseId, quantity, movementId);
            case OUT, TRANSFER_OUT ->
                    inventoryStockSnapshotRepository.decreaseStockWithFloor(productId, warehouseId, quantity, movementId);
            case ADJUSTMENT_OUT ->
                    inventoryStockSnapshotRepository.decreaseStockWithoutFloor(productId, warehouseId, quantity, movementId);
        };
    }

    private boolean isVendibleOut(MovementType movementType) {
        return movementType == MovementType.OUT || movementType == MovementType.TRANSFER_OUT;
    }

    private void seedZeroSnapshot(Long productId, Long warehouseId) {
        try {
            inventoryStockSnapshotSeedService.seedZeroSnapshot(productId, warehouseId);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent movement seeded the same product+warehouse first. The row now exists and
            // the follow-up UPDATE will apply the delta. The collision stayed inside the
            // REQUIRES_NEW transaction and did not touch this one.
        }
    }

    private String insufficientStockMessage(InventoryMovementEntity movement) {
        String productName = movement.getProduct().getName();
        return "No hay stock suficiente de \"" + productName
                + "\" en el depósito seleccionado para completar la operación.";
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