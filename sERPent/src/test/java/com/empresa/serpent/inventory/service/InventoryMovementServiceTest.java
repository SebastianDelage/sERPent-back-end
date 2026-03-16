package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.empresa.serpent.support.TestEntityFactory.detail;
import static com.empresa.serpent.support.TestEntityFactory.product;
import static com.empresa.serpent.support.TestEntityFactory.transaction;
import static com.empresa.serpent.support.TestEntityFactory.transactionWithoutDetails;
import static com.empresa.serpent.support.TestEntityFactory.warehouse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryMovementServiceTest {

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private InventoryStockSnapshotService inventoryStockSnapshotService;

    @InjectMocks
    private InventoryMovementService inventoryMovementService;

    @Test
    @DisplayName("Should register sale movements as OUT")
    void shouldRegisterSaleMovementsAsOut() {

        ProductEntity product = product(10L, "Pollo entero");
        WarehouseEntity warehouse = warehouse(1L, "Central", true);
        TransactionEntity transaction = transaction(100L, detail(product, "2.000", "4500.0000"));

        inventoryMovementService.registerSaleMovements(transaction, warehouse);

        ArgumentCaptor<List<InventoryMovementEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryMovementRepository).saveAll(captor.capture());
        verify(inventoryStockSnapshotService).applyMovements(anyList());

        List<InventoryMovementEntity> movements = captor.getValue();
        assertThat(movements).hasSize(1);

        InventoryMovementEntity movement = movements.get(0);
        assertThat(movement.getProduct()).isEqualTo(product);
        assertThat(movement.getWarehouse()).isEqualTo(warehouse);
        assertThat(movement.getTransaction()).isEqualTo(transaction);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.OUT);
        assertThat(movement.getQuantity()).isEqualByComparingTo("2.000");
        assertThat(movement.getUnitCost()).isNull();
        assertThat(movement.getNote()).isEqualTo("Sale #100");
    }

    @Test
    @DisplayName("Should register purchase movements as IN with unit cost")
    void shouldRegisterPurchaseMovementsAsInWithUnitCost() {

        ProductEntity product = product(10L, "Pollo entero");
        WarehouseEntity warehouse = warehouse(1L, "Central", true);
        TransactionEntity transaction = transaction(200L, detail(product, "3.000", "3200.0000"));

        inventoryMovementService.registerPurchaseMovements(transaction, warehouse);

        ArgumentCaptor<List<InventoryMovementEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryMovementRepository).saveAll(captor.capture());
        verify(inventoryStockSnapshotService).applyMovements(anyList());

        List<InventoryMovementEntity> movements = captor.getValue();
        assertThat(movements).hasSize(1);

        InventoryMovementEntity movement = movements.get(0);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.IN);
        assertThat(movement.getQuantity()).isEqualByComparingTo("3.000");
        assertThat(movement.getUnitCost()).isEqualByComparingTo("3200.0000");
        assertThat(movement.getNote()).isEqualTo("Purchase #200");
    }

    @Test
    @DisplayName("Should register adjustment movement successfully")
    void shouldRegisterAdjustmentMovementSuccessfully() {

        TransactionEntity transaction = transactionWithoutDetails(300L);
        WarehouseEntity warehouse = warehouse(1L, "Central", true);
        ProductEntity product = product(10L, "Pollo entero");

        when(inventoryMovementRepository.save(any(InventoryMovementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryMovementService.registerAdjustmentMovement(
                transaction,
                warehouse,
                product,
                MovementType.ADJUSTMENT_IN,
                new BigDecimal("4.000"),
                "Manual adjustment"
        );

        ArgumentCaptor<InventoryMovementEntity> captor = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(inventoryMovementRepository).save(captor.capture());
        verify(inventoryStockSnapshotService).applyMovement(any(InventoryMovementEntity.class));

        InventoryMovementEntity movement = captor.getValue();
        assertThat(movement.getTransaction()).isEqualTo(transaction);
        assertThat(movement.getWarehouse()).isEqualTo(warehouse);
        assertThat(movement.getProduct()).isEqualTo(product);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.ADJUSTMENT_IN);
        assertThat(movement.getQuantity()).isEqualByComparingTo("4.000");
        assertThat(movement.getUnitCost()).isNull();
        assertThat(movement.getNote()).isEqualTo("Manual adjustment");
    }

    @Test
    @DisplayName("Should register transfer movements with TRANSFER_OUT and TRANSFER_IN")
    void shouldRegisterTransferMovementsWithTransferOutAndTransferIn() {

        ProductEntity product = product(10L, "Pollo entero");
        WarehouseEntity sourceWarehouse = warehouse(1L, "Central", true);
        WarehouseEntity targetWarehouse = warehouse(2L, "North", true);
        TransactionEntity transaction = transaction(400L, detail(product, "5.000", "4500.0000"));

        inventoryMovementService.registerTransferMovements(transaction, sourceWarehouse, targetWarehouse);

        ArgumentCaptor<List<InventoryMovementEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryMovementRepository).saveAll(captor.capture());
        verify(inventoryStockSnapshotService).applyMovements(anyList());

        List<InventoryMovementEntity> movements = captor.getValue();
        assertThat(movements).hasSize(2);

        InventoryMovementEntity transferOut = movements.get(0);
        InventoryMovementEntity transferIn = movements.get(1);

        assertThat(transferOut.getMovementType()).isEqualTo(MovementType.TRANSFER_OUT);
        assertThat(transferOut.getWarehouse()).isEqualTo(sourceWarehouse);
        assertThat(transferOut.getQuantity()).isEqualByComparingTo("5.000");
        assertThat(transferOut.getNote()).isEqualTo("Transfer #400 to warehouse 2");

        assertThat(transferIn.getMovementType()).isEqualTo(MovementType.TRANSFER_IN);
        assertThat(transferIn.getWarehouse()).isEqualTo(targetWarehouse);
        assertThat(transferIn.getQuantity()).isEqualByComparingTo("5.000");
        assertThat(transferIn.getNote()).isEqualTo("Transfer #400 from warehouse 1");
    }

    @Nested
    class ValidationCases {

        @Test
        @DisplayName("Should throw when transaction is null in sale movements")
        void shouldThrowWhenTransactionIsNullInSaleMovements() {

            WarehouseEntity warehouse = warehouse(1L, "Central", true);

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(null, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction cannot be null");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when transaction id is null in sale movements")
        void shouldThrowWhenTransactionIdIsNullInSaleMovements() {

            ProductEntity product = product(10L, "Pollo entero");
            WarehouseEntity warehouse = warehouse(1L, "Central", true);
            TransactionEntity transaction = transaction(null, detail(product, "1.000", "4500.0000"));

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(transaction, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction id cannot be null");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when warehouse is null in sale movements")
        void shouldThrowWhenWarehouseIsNullInSaleMovements() {

            ProductEntity product = product(10L, "Pollo entero");
            TransactionEntity transaction = transaction(100L, detail(product, "1.000", "4500.0000"));

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(transaction, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Warehouse cannot be null");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when warehouse id is null in sale movements")
        void shouldThrowWhenWarehouseIdIsNullInSaleMovements() {

            ProductEntity product = product(10L, "Pollo entero");
            TransactionEntity transaction = transaction(100L, detail(product, "1.000", "4500.0000"));
            WarehouseEntity warehouse = warehouse(null, "Central", true);

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(transaction, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Warehouse id cannot be null");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when transaction details are empty in sale movements")
        void shouldThrowWhenTransactionDetailsAreEmptyInSaleMovements() {

            TransactionEntity transaction = transactionWithoutDetails(100L);
            WarehouseEntity warehouse = warehouse(1L, "Central", true);

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(transaction, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction must contain at least one detail");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when detail product is null")
        void shouldThrowWhenDetailProductIsNull() {

            TransactionDetailEntity detail = new TransactionDetailEntity();
            detail.setProduct(null);
            detail.setQuantity(new BigDecimal("1.000"));
            detail.setUnitPrice(new BigDecimal("4500.0000"));

            TransactionEntity transaction = transaction(100L, detail);
            WarehouseEntity warehouse = warehouse(1L, "Central", true);

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(transaction, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction detail product cannot be null");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when detail quantity is null")
        void shouldThrowWhenDetailQuantityIsNull() {

            ProductEntity product = product(10L, "Pollo entero");

            TransactionDetailEntity detail = new TransactionDetailEntity();
            detail.setProduct(product);
            detail.setQuantity(null);
            detail.setUnitPrice(new BigDecimal("4500.0000"));

            TransactionEntity transaction = transaction(100L, detail);
            WarehouseEntity warehouse = warehouse(1L, "Central", true);

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(transaction, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction detail quantity cannot be null");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when detail quantity is not positive")
        void shouldThrowWhenDetailQuantityIsNotPositive() {

            ProductEntity product = product(10L, "Pollo entero");
            TransactionEntity transaction = transaction(100L, detail(product, "0.000", "4500.0000"));
            WarehouseEntity warehouse = warehouse(1L, "Central", true);

            assertThatThrownBy(() -> inventoryMovementService.registerSaleMovements(transaction, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction detail quantity must be greater than zero");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when adjustment movement type is invalid")
        void shouldThrowWhenAdjustmentMovementTypeIsInvalid() {

            TransactionEntity transaction = transactionWithoutDetails(300L);
            WarehouseEntity warehouse = warehouse(1L, "Central", true);
            ProductEntity product = product(10L, "Pollo entero");

            assertThatThrownBy(() -> inventoryMovementService.registerAdjustmentMovement(
                    transaction,
                    warehouse,
                    product,
                    MovementType.OUT,
                    new BigDecimal("1.000"),
                    "Invalid adjustment"
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Movement type must be ADJUSTMENT_IN, ADJUSTMENT_OUT or RETURN_IN");

            verify(inventoryMovementRepository, never()).save(any());
            verify(inventoryStockSnapshotService, never()).applyMovement(any());
        }

        @Test
        @DisplayName("Should throw when adjustment quantity is not positive")
        void shouldThrowWhenAdjustmentQuantityIsNotPositive() {

            TransactionEntity transaction = transactionWithoutDetails(300L);
            WarehouseEntity warehouse = warehouse(1L, "Central", true);
            ProductEntity product = product(10L, "Pollo entero");

            assertThatThrownBy(() -> inventoryMovementService.registerAdjustmentMovement(
                    transaction,
                    warehouse,
                    product,
                    MovementType.ADJUSTMENT_OUT,
                    BigDecimal.ZERO,
                    "Zero adjustment"
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than zero");

            verify(inventoryMovementRepository, never()).save(any());
            verify(inventoryStockSnapshotService, never()).applyMovement(any());
        }

        @Test
        @DisplayName("Should throw when transfer warehouses are the same")
        void shouldThrowWhenTransferWarehousesAreTheSame() {

            ProductEntity product = product(10L, "Pollo entero");
            WarehouseEntity warehouse = warehouse(1L, "Central", true);
            TransactionEntity transaction = transaction(400L, detail(product, "5.000", "4500.0000"));

            assertThatThrownBy(() -> inventoryMovementService.registerTransferMovements(transaction, warehouse, warehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Source and target warehouse cannot be the same");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }

        @Test
        @DisplayName("Should throw when transfer transaction has no details")
        void shouldThrowWhenTransferTransactionHasNoDetails() {

            WarehouseEntity sourceWarehouse = warehouse(1L, "Central", true);
            WarehouseEntity targetWarehouse = warehouse(2L, "North", true);
            TransactionEntity transaction = transactionWithoutDetails(400L);

            assertThatThrownBy(() -> inventoryMovementService.registerTransferMovements(transaction, sourceWarehouse, targetWarehouse))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction must contain at least one detail");

            verify(inventoryMovementRepository, never()).saveAll(anyList());
            verify(inventoryStockSnapshotService, never()).applyMovements(anyList());
        }
    }
}