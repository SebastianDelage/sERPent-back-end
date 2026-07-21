package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.response.InventoryReconciliationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the zero-seed path of {@link InventoryStockSnapshotService#applyMovement}.
 *
 * <p>Verifies that when the very first movement for a brand-new product+warehouse seeds a zero-row
 * inside a REQUIRES_NEW transaction and the outer transaction (TX-A) then rolls back, the ledger
 * movement disappears, the seeded snapshot survives at zero, and reconcile reports no drift.
 *
 * <p>Runs on an in-memory H2 (forced with {@code @AutoConfigureTestDatabase}) so it never touches a
 * real datasource.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class InventoryStockSnapshotSeedRollbackTest {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private InventoryStockSnapshotService snapshotService;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private InventoryStockSnapshotRepository snapshotRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;

    private Long productId;
    private Long warehouseId;

    @BeforeEach
    void setUp() {
        cleanUp();
        ProductEntity product = productRepository.save(ProductEntity.builder()
                .name("Seed IT product").price(new BigDecimal("1000")).sku("SEED_IT_PRODUCT").active(true).build());
        WarehouseEntity warehouse = warehouseRepository.save(WarehouseEntity.builder()
                .name("Seed IT warehouse").active(true).build());
        productId = product.getId();
        warehouseId = warehouse.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        movementRepository.deleteAll();
        snapshotRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();
    }

    @Test
    @DisplayName("(e) Zero-seed survives a real TX-A rollback with no ledger↔snapshot drift")
    void zeroSeedSurvivesRollbackWithoutDrift() {
        // The product+warehouse is genuinely new: there is no snapshot row yet.
        assertThat(snapshotRepository.findByProductIdAndWarehouseId(productId, warehouseId)).isEmpty();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            ProductEntity product = productRepository.findById(productId).orElseThrow();
            WarehouseEntity warehouse = warehouseRepository.findById(warehouseId).orElseThrow();

            InventoryMovementEntity movement = movementRepository.save(InventoryMovementEntity.builder()
                    .product(product)
                    .warehouse(warehouse)
                    .movementType(MovementType.IN)
                    .quantity(new BigDecimal("10.000"))
                    .note("seed-it")
                    .build());

            // First UPDATE finds no row → seeds a zero row (REQUIRES_NEW, commits) → second UPDATE
            // raises the balance to 10 inside TX-A.
            snapshotService.applyMovement(movement);

            // Force a genuine rollback of TX-A (not a commit).
            status.setRollbackOnly();
        });

        // The ledger movement was rolled back with TX-A: it does not exist.
        assertThat(movementRepository.findByProductIdAndWarehouseId(productId, warehouseId)).isEmpty();

        // The snapshot row exists at zero: it can only exist because the REQUIRES_NEW seed committed
        // (the +10 update was rolled back). This rules out a false green where seeding never ran.
        InventoryStockSnapshotEntity snapshot = snapshotRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow();
        assertThat(snapshot.getCurrentStock()).isEqualByComparingTo("0");

        // Reconcile confirms no drift for this product+warehouse: ledger 0, snapshot 0, consistent.
        InventoryReconciliationResponse reconciliation = snapshotService.reconcileSnapshots().stream()
                .filter(r -> r.productId().equals(productId) && r.warehouseId().equals(warehouseId))
                .findFirst()
                .orElseThrow();
        assertThat(reconciliation.ledgerStock()).isEqualByComparingTo("0");
        assertThat(reconciliation.snapshotStock()).isEqualByComparingTo("0");
        assertThat(reconciliation.consistent()).isTrue();
    }
}
