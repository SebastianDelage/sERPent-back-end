package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.shared.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryStockSnapshotServiceTest {

    @Mock
    private InventoryStockSnapshotRepository inventoryStockSnapshotRepository;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private InventoryStockSnapshotSeedService inventoryStockSnapshotSeedService;

    @InjectMocks
    private InventoryStockSnapshotService inventoryStockSnapshotService;

    private ProductEntity product;
    private WarehouseEntity warehouseOne;
    private WarehouseEntity warehouseTwo;

    @BeforeEach
    void setUp() {
        product = ProductEntity.builder()
                .id(1L)
                .name("Pollo entero")
                .price(BigDecimal.valueOf(100))
                .active(true)
                .build();

        warehouseOne = WarehouseEntity.builder()
                .id(10L)
                .name("Depósito Central")
                .active(true)
                .build();

        warehouseTwo = WarehouseEntity.builder()
                .id(20L)
                .name("Sucursal")
                .active(true)
                .build();
    }

    @Test
    void applyMovement_shouldIncreaseStockForInMovementWhenRowExists() {
        InventoryMovementEntity movement = buildMovement(
                100L, MovementType.IN, "10.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 10, 0)
        );

        when(inventoryStockSnapshotRepository.increaseStock(1L, 10L, new BigDecimal("10.000"), 100L))
                .thenReturn(1);

        inventoryStockSnapshotService.applyMovement(movement);

        verify(inventoryStockSnapshotRepository).increaseStock(1L, 10L, new BigDecimal("10.000"), 100L);
        verify(inventoryStockSnapshotSeedService, never()).seedZeroSnapshot(any(), any());
    }

    @Test
    void applyMovement_shouldSeedZeroRowAndReapplyWhenRowDoesNotExistForIncrease() {
        InventoryMovementEntity movement = buildMovement(
                100L, MovementType.IN, "10.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 10, 0)
        );

        // First UPDATE matches no row (snapshot missing), then after seeding it succeeds.
        when(inventoryStockSnapshotRepository.increaseStock(1L, 10L, new BigDecimal("10.000"), 100L))
                .thenReturn(0, 1);

        inventoryStockSnapshotService.applyMovement(movement);

        verify(inventoryStockSnapshotSeedService).seedZeroSnapshot(1L, 10L);
        verify(inventoryStockSnapshotRepository, times(2))
                .increaseStock(1L, 10L, new BigDecimal("10.000"), 100L);
    }

    @Test
    void applyMovement_shouldDecreaseWithFloorForOutMovementWhenStockIsSufficient() {
        InventoryStockSnapshotEntity existingSnapshot = InventoryStockSnapshotEntity.builder()
                .id(1L).product(product).warehouse(warehouseOne)
                .currentStock(new BigDecimal("10.000")).lastMovementId(90L).build();

        InventoryMovementEntity movement = buildMovement(
                101L, MovementType.OUT, "3.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 11, 0)
        );

        when(inventoryStockSnapshotRepository.decreaseStockWithFloor(1L, 10L, new BigDecimal("3.000"), 101L))
                .thenReturn(1);

        inventoryStockSnapshotService.applyMovement(movement);

        verify(inventoryStockSnapshotRepository).decreaseStockWithFloor(1L, 10L, new BigDecimal("3.000"), 101L);
        verify(inventoryStockSnapshotSeedService, never()).seedZeroSnapshot(any(), any());
        // The row is never seeded nor read for a vendible OUT: the conditional UPDATE is the guard.
        verifyNoMoreInteractions(inventoryStockSnapshotSeedService);
        assertThat(existingSnapshot.getCurrentStock()).isEqualByComparingTo("10.000");
    }

    @Test
    void applyMovement_shouldThrowInsufficientStockForOutMovementWhenFloorRejects() {
        InventoryMovementEntity movement = buildMovement(
                101L, MovementType.OUT, "999.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 11, 0)
        );

        // Conditional UPDATE matched no row: not enough stock (or no row). Real oversell guard.
        when(inventoryStockSnapshotRepository.decreaseStockWithFloor(1L, 10L, new BigDecimal("999.000"), 101L))
                .thenReturn(0);

        assertThatThrownBy(() -> inventoryStockSnapshotService.applyMovement(movement))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("No hay stock suficiente")
                .hasMessageContaining("Pollo entero");

        verify(inventoryStockSnapshotSeedService, never()).seedZeroSnapshot(any(), any());
    }

    @Test
    void applyMovement_shouldDecreaseWithoutFloorForAdjustmentOut() {
        InventoryMovementEntity movement = buildMovement(
                105L, MovementType.ADJUSTMENT_OUT, "4.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 13, 30)
        );

        when(inventoryStockSnapshotRepository.decreaseStockWithoutFloor(1L, 10L, new BigDecimal("4.000"), 105L))
                .thenReturn(1);

        inventoryStockSnapshotService.applyMovement(movement);

        verify(inventoryStockSnapshotRepository).decreaseStockWithoutFloor(1L, 10L, new BigDecimal("4.000"), 105L);
        verify(inventoryStockSnapshotRepository, never()).decreaseStockWithFloor(any(), any(), any(), any());
        verify(inventoryStockSnapshotSeedService, never()).seedZeroSnapshot(any(), any());
    }

    @Test
    void applyMovement_shouldHandleTransferOutWithFloorAndTransferInAsIncrease() {
        InventoryMovementEntity transferOut = buildMovement(
                102L, MovementType.TRANSFER_OUT, "2.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 12, 0)
        );
        InventoryMovementEntity transferIn = buildMovement(
                103L, MovementType.TRANSFER_IN, "2.000", product, warehouseTwo,
                LocalDateTime.of(2026, 3, 13, 12, 1)
        );

        when(inventoryStockSnapshotRepository.decreaseStockWithFloor(1L, 10L, new BigDecimal("2.000"), 102L))
                .thenReturn(1);
        when(inventoryStockSnapshotRepository.increaseStock(1L, 20L, new BigDecimal("2.000"), 103L))
                .thenReturn(1);

        inventoryStockSnapshotService.applyMovement(transferOut);
        inventoryStockSnapshotService.applyMovement(transferIn);

        verify(inventoryStockSnapshotRepository).decreaseStockWithFloor(1L, 10L, new BigDecimal("2.000"), 102L);
        verify(inventoryStockSnapshotRepository).increaseStock(1L, 20L, new BigDecimal("2.000"), 103L);
        verify(inventoryStockSnapshotSeedService, never()).seedZeroSnapshot(any(), any());
    }

    @Test
    void applyMovement_shouldIncreaseStockForReturnIn() {
        InventoryMovementEntity movement = buildMovement(
                104L, MovementType.RETURN_IN, "1.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 13, 0)
        );

        when(inventoryStockSnapshotRepository.increaseStock(1L, 10L, new BigDecimal("1.000"), 104L))
                .thenReturn(1);

        inventoryStockSnapshotService.applyMovement(movement);

        verify(inventoryStockSnapshotRepository).increaseStock(1L, 10L, new BigDecimal("1.000"), 104L);
        verify(inventoryStockSnapshotSeedService, never()).seedZeroSnapshot(any(), any());
    }

    @Test
    void applyMovement_shouldThrowWhenMovementIdIsNull() {
        InventoryMovementEntity movement = buildMovement(
                null, MovementType.IN, "5.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 14, 0)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryStockSnapshotService.applyMovement(movement)
        );

        assertEquals("Inventory movement id cannot be null", exception.getMessage());
        verify(inventoryStockSnapshotRepository, never()).increaseStock(any(), any(), any(), any());
        verify(inventoryStockSnapshotRepository, never()).decreaseStockWithFloor(any(), any(), any(), any());
        verify(inventoryStockSnapshotRepository, never()).decreaseStockWithoutFloor(any(), any(), any(), any());
    }

    @Test
    void rebuildSnapshots_shouldRebuildFromAllMovementsOrderedByCreatedAtAndId() {
        InventoryMovementEntity movement1 = buildMovement(
                201L, MovementType.IN, "10.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 9, 0)
        );
        InventoryMovementEntity movement2 = buildMovement(
                202L, MovementType.OUT, "4.000", product, warehouseOne,
                LocalDateTime.of(2026, 3, 13, 10, 0)
        );
        InventoryMovementEntity movement3 = buildMovement(
                203L, MovementType.TRANSFER_IN, "2.000", product, warehouseTwo,
                LocalDateTime.of(2026, 3, 13, 11, 0)
        );

        when(inventoryMovementRepository.findAll())
                .thenReturn(List.of(movement3, movement2, movement1));

        when(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 10L))
                .thenReturn(Optional.empty(), Optional.of(
                        InventoryStockSnapshotEntity.builder()
                                .product(product)
                                .warehouse(warehouseOne)
                                .currentStock(new BigDecimal("10.000"))
                                .lastMovementId(201L)
                                .build()
                ));

        when(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 20L))
                .thenReturn(Optional.empty());

        when(inventoryStockSnapshotRepository.save(any(InventoryStockSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryStockSnapshotService.rebuildSnapshots();

        verify(inventoryStockSnapshotRepository).deleteAllInBatch();
        verify(inventoryMovementRepository).findAll();
        verify(inventoryStockSnapshotRepository, times(3)).save(any(InventoryStockSnapshotEntity.class));
        // Rebuild is a single-threaded reconstruction: it uses read-modify-write, not the atomic
        // conditional UPDATE, and therefore never seeds via the REQUIRES_NEW collaborator.
        verify(inventoryStockSnapshotSeedService, never()).seedZeroSnapshot(any(), any());
        verify(inventoryStockSnapshotRepository, never()).increaseStock(any(), any(), any(), any());
    }

    private InventoryMovementEntity buildMovement(
            Long id,
            MovementType movementType,
            String quantity,
            ProductEntity product,
            WarehouseEntity warehouse,
            LocalDateTime createdAt
    ) {
        InventoryMovementEntity movement = InventoryMovementEntity.builder()
                .id(id)
                .movementType(movementType)
                .quantity(new BigDecimal(quantity))
                .product(product)
                .warehouse(warehouse)
                .note("test")
                .build();

        movement.setCreatedAt(createdAt);
        return movement;
    }
}
