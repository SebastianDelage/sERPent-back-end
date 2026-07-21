package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a zero-balance snapshot row in its OWN transaction.
 *
 * <p>The seeding lives in a separate bean, annotated {@link Propagation#REQUIRES_NEW}, so that a
 * unique-constraint collision (two concurrent first movements for the same product+warehouse) is
 * fully contained in this transaction and can never poison the caller's transaction. The caller
 * catches the resulting {@code DataIntegrityViolationException} at this bean boundary. Because the
 * seeded balance is zero, the snapshot stays consistent with the append-only ledger even if the
 * caller's transaction later rolls back (the ledger movement is rolled back too, and reconcile
 * sees ledger = snapshot = 0).
 */
@Service
@RequiredArgsConstructor
public class InventoryStockSnapshotSeedService {

    private final InventoryStockSnapshotRepository inventoryStockSnapshotRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedZeroSnapshot(Long productId, Long warehouseId) {
        inventoryStockSnapshotRepository.insertZeroSnapshot(productId, warehouseId);
    }
}
