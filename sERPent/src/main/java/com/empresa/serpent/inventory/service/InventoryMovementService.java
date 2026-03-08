package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;

    @Transactional
    public void registerSaleMovements(TransactionEntity transaction, WarehouseEntity warehouse) {
        validateTransactionAndWarehouse(transaction, warehouse);

        List<InventoryMovementEntity> movements = transaction.getDetails()
                .stream()
                .map(detail -> toSaleMovement(detail, transaction, warehouse))
                .toList();

        inventoryMovementRepository.saveAll(movements);
    }

    @Transactional
    public void registerPurchaseMovements(TransactionEntity transaction, WarehouseEntity warehouse) {
        validateTransactionAndWarehouse(transaction, warehouse);

        List<InventoryMovementEntity> movements = transaction.getDetails()
                .stream()
                .map(detail -> toPurchaseMovement(detail, transaction, warehouse))
                .toList();

        inventoryMovementRepository.saveAll(movements);
    }

    private void validateTransactionAndWarehouse(TransactionEntity transaction, WarehouseEntity warehouse) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        if (transaction.getId() == null) {
            throw new IllegalArgumentException("Transaction id cannot be null");
        }

        if (warehouse == null) {
            throw new IllegalArgumentException("Warehouse cannot be null");
        }

        if (warehouse.getId() == null) {
            throw new IllegalArgumentException("Warehouse id cannot be null");
        }

        if (transaction.getDetails() == null || transaction.getDetails().isEmpty()) {
            throw new IllegalArgumentException("Transaction must contain at least one detail");
        }

        for (TransactionDetailEntity detail : transaction.getDetails()) {
            validateDetail(detail);
        }
    }

    private void validateDetail(TransactionDetailEntity detail) {
        if (detail == null) {
            throw new IllegalArgumentException("Transaction detail cannot be null");
        }

        if (detail.getProduct() == null) {
            throw new IllegalArgumentException("Transaction detail product cannot be null");
        }

        if (detail.getQuantity() == null) {
            throw new IllegalArgumentException("Transaction detail quantity cannot be null");
        }

        if (detail.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction detail quantity must be greater than zero");
        }
    }

    private InventoryMovementEntity toSaleMovement(
            TransactionDetailEntity detail,
            TransactionEntity transaction,
            WarehouseEntity warehouse
    ) {
        return InventoryMovementEntity.builder()
                .product(detail.getProduct())
                .warehouse(warehouse)
                .transaction(transaction)
                .movementType(MovementType.OUT)
                .quantity(detail.getQuantity())
                .unitCost(null)
                .note("Sale #" + transaction.getId())
                .build();
    }

    private InventoryMovementEntity toPurchaseMovement(
            TransactionDetailEntity detail,
            TransactionEntity transaction,
            WarehouseEntity warehouse
    ) {
        return InventoryMovementEntity.builder()
                .product(detail.getProduct())
                .warehouse(warehouse)
                .transaction(transaction)
                .movementType(MovementType.IN)
                .quantity(detail.getQuantity())
                .unitCost(detail.getUnitPrice())
                .note("Purchase #" + transaction.getId())
                .build();
    }
    /*
 FUTURE MOVEMENT TYPES

 This service will eventually support additional inventory operations
 beyond sales and purchases.

 1. INVENTORY ADJUSTMENTS

 Used when the physical stock does not match the system stock.
 This may happen due to:

     - damaged products
     - expired items
     - counting corrections during stock audits
     - shrinkage or losses

 Example:
     System stock = 20
     Physical stock = 18

     Adjustment movement:
     movementType = OUT
     quantity = 2
     note = "Inventory adjustment"

 This operation should be triggered by an InventoryAdjustmentService
 or a stock counting process.

 ------------------------------------------------------------

 2. WAREHOUSE TRANSFERS

 Used when products are moved from one warehouse to another.

 Example:
     Transfer 10 units from Warehouse A to Warehouse B

     Movement 1:
         warehouse = A
         movementType = OUT
         quantity = 10
         note = "Transfer to warehouse B"

     Movement 2:
         warehouse = B
         movementType = IN
         quantity = 10
         note = "Transfer from warehouse A"

 Transfers should always generate TWO movements to keep the
 inventory ledger balanced.

 ------------------------------------------------------------

 3. RETURNS

 Used when products return to inventory after a sale.

 Example:
     Customer returns 1 unit from a previous sale.

     Movement:
         movementType = IN
         quantity = 1
         note = "Return from sale #123"

 Returns may optionally reference the original transaction.

 ------------------------------------------------------------

 These operations will likely be implemented through dedicated
 application services such as:

     - InventoryAdjustmentService
     - WarehouseTransferService
     - ReturnService

 InventoryMovementService will remain responsible only for
 registering the resulting inventory movements.
    */

}