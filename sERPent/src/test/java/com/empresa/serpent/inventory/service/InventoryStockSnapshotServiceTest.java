package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    void applyMovement_shouldCreateSnapshotWhenItDoesNotExist_andAddStockForInMovement() {
        InventoryMovementEntity movement = buildMovement(
                100L,
                MovementType.IN,
                "10.000",
                product,
                warehouseOne,
                LocalDateTime.of(2026, 3, 13, 10, 0)
        );

        when(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 10L))
                .thenReturn(Optional.empty());
        when(inventoryStockSnapshotRepository.save(any(InventoryStockSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryStockSnapshotService.applyMovement(movement);

        ArgumentCaptor<InventoryStockSnapshotEntity> captor =
                ArgumentCaptor.forClass(InventoryStockSnapshotEntity.class);

        verify(inventoryStockSnapshotRepository).save(captor.capture());

        InventoryStockSnapshotEntity saved = captor.getValue();

        assertEquals(product.getId(), saved.getProduct().getId());
        assertEquals(warehouseOne.getId(), saved.getWarehouse().getId());
        assertEquals(new BigDecimal("10.000"), saved.getCurrentStock());
        assertEquals(100L, saved.getLastMovementId());
    }

    @Test
    void applyMovement_shouldSubtractStockForOutMovement() {
        InventoryStockSnapshotEntity existingSnapshot = InventoryStockSnapshotEntity.builder()
                .id(1L)
                .product(product)
                .warehouse(warehouseOne)
                .currentStock(new BigDecimal("10.000"))
                .lastMovementId(90L)
                .build();

        InventoryMovementEntity movement = buildMovement(
                101L,
                MovementType.OUT,
                "3.000",
                product,
                warehouseOne,
                LocalDateTime.of(2026, 3, 13, 11, 0)
        );

        when(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 10L))
                .thenReturn(Optional.of(existingSnapshot));
        when(inventoryStockSnapshotRepository.save(any(InventoryStockSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryStockSnapshotService.applyMovement(movement);

        ArgumentCaptor<InventoryStockSnapshotEntity> captor =
                ArgumentCaptor.forClass(InventoryStockSnapshotEntity.class);

        verify(inventoryStockSnapshotRepository).save(captor.capture());

        InventoryStockSnapshotEntity saved = captor.getValue();

        assertEquals(new BigDecimal("7.000"), saved.getCurrentStock());
        assertEquals(101L, saved.getLastMovementId());
    }

    @Test
    void applyMovement_shouldHandleTransferOutAndTransferInCorrectly() {
        InventoryStockSnapshotEntity sourceSnapshot = InventoryStockSnapshotEntity.builder()
                .id(1L)
                .product(product)
                .warehouse(warehouseOne)
                .currentStock(new BigDecimal("8.000"))
                .lastMovementId(80L)
                .build();

        InventoryMovementEntity transferOut = buildMovement(
                102L,
                MovementType.TRANSFER_OUT,
                "2.000",
                product,
                warehouseOne,
                LocalDateTime.of(2026, 3, 13, 12, 0)
        );

        when(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 10L))
                .thenReturn(Optional.of(sourceSnapshot));
        when(inventoryStockSnapshotRepository.save(any(InventoryStockSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryStockSnapshotService.applyMovement(transferOut);

        assertEquals(new BigDecimal("6.000"), sourceSnapshot.getCurrentStock());
        assertEquals(102L, sourceSnapshot.getLastMovementId());

        InventoryMovementEntity transferIn = buildMovement(
                103L,
                MovementType.TRANSFER_IN,
                "2.000",
                product,
                warehouseTwo,
                LocalDateTime.of(2026, 3, 13, 12, 1)
        );

        when(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 20L))
                .thenReturn(Optional.empty());

        inventoryStockSnapshotService.applyMovement(transferIn);

        ArgumentCaptor<InventoryStockSnapshotEntity> captor =
                ArgumentCaptor.forClass(InventoryStockSnapshotEntity.class);

        verify(inventoryStockSnapshotRepository, times(2)).save(captor.capture());

        List<InventoryStockSnapshotEntity> savedSnapshots = captor.getAllValues();
        InventoryStockSnapshotEntity targetSaved = savedSnapshots.get(1);

        assertEquals(new BigDecimal("2.000"), targetSaved.getCurrentStock());
        assertEquals(103L, targetSaved.getLastMovementId());
        assertEquals(warehouseTwo.getId(), targetSaved.getWarehouse().getId());
    }

    @Test
    void applyMovement_shouldAddStockForReturnIn() {
        InventoryStockSnapshotEntity snapshot = InventoryStockSnapshotEntity.builder()
                .id(1L)
                .product(product)
                .warehouse(warehouseOne)
                .currentStock(new BigDecimal("5.000"))
                .lastMovementId(70L)
                .build();

        InventoryMovementEntity movement = buildMovement(
                104L,
                MovementType.RETURN_IN,
                "1.000",
                product,
                warehouseOne,
                LocalDateTime.of(2026, 3, 13, 13, 0)
        );

        when(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 10L))
                .thenReturn(Optional.of(snapshot));
        when(inventoryStockSnapshotRepository.save(any(InventoryStockSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryStockSnapshotService.applyMovement(movement);

        assertEquals(new BigDecimal("6.000"), snapshot.getCurrentStock());
        assertEquals(104L, snapshot.getLastMovementId());
    }

    @Test
    void applyMovement_shouldThrowWhenMovementIdIsNull() {
        InventoryMovementEntity movement = buildMovement(
                null,
                MovementType.IN,
                "5.000",
                product,
                warehouseOne,
                LocalDateTime.of(2026, 3, 13, 14, 0)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryStockSnapshotService.applyMovement(movement)
        );

        assertEquals("Inventory movement id cannot be null", exception.getMessage());
        verify(inventoryStockSnapshotRepository, never()).save(any());
    }

    @Test
    void rebuildSnapshots_shouldRebuildFromAllMovementsOrderedByCreatedAtAndId() {
        InventoryMovementEntity movement1 = buildMovement(
                201L,
                MovementType.IN,
                "10.000",
                product,
                warehouseOne,
                LocalDateTime.of(2026, 3, 13, 9, 0)
        );

        InventoryMovementEntity movement2 = buildMovement(
                202L,
                MovementType.OUT,
                "4.000",
                product,
                warehouseOne,
                LocalDateTime.of(2026, 3, 13, 10, 0)
        );

        InventoryMovementEntity movement3 = buildMovement(
                203L,
                MovementType.TRANSFER_IN,
                "2.000",
                product,
                warehouseTwo,
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