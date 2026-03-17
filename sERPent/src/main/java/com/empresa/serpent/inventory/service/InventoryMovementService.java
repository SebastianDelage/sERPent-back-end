package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.transactions.domain.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryStockSnapshotService inventoryStockSnapshotService;

    @Transactional
    public void registerSaleMovements(TransactionEntity transaction, WarehouseEntity warehouse) {
        validateTransactionAndWarehouse(transaction, warehouse);

        List<InventoryMovementEntity> movements = transaction.getDetails()
                .stream()
                .map(detail -> toSaleMovement(detail, transaction, warehouse))
                .toList();

        List<InventoryMovementEntity> savedMovements = inventoryMovementRepository.saveAll(movements);
        inventoryStockSnapshotService.applyMovements(savedMovements);
    }

    @Transactional
    public void registerPurchaseMovements(TransactionEntity transaction, WarehouseEntity warehouse) {
        validateTransactionAndWarehouse(transaction, warehouse);

        List<InventoryMovementEntity> movements = transaction.getDetails()
                .stream()
                .map(detail -> toPurchaseMovement(detail, transaction, warehouse))
                .toList();

        List<InventoryMovementEntity> savedMovements = inventoryMovementRepository.saveAll(movements);
        inventoryStockSnapshotService.applyMovements(savedMovements);
    }

    @Transactional
    public void registerAdjustmentMovement(
            TransactionEntity transaction,
            WarehouseEntity warehouse,
            ProductEntity product,
            MovementType movementType,
            BigDecimal quantity,
            String note
    ) {
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

        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }

        if (product.getId() == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if (movementType == null) {
            throw new IllegalArgumentException("Movement type cannot be null");
        }

        if (movementType != MovementType.ADJUSTMENT_IN
                && movementType != MovementType.ADJUSTMENT_OUT
                && movementType != MovementType.RETURN_IN) {
            throw new IllegalArgumentException(
                    "Movement type must be ADJUSTMENT_IN, ADJUSTMENT_OUT or RETURN_IN"
            );
        }

        if (quantity == null) {
            throw new IllegalArgumentException("Quantity cannot be null");
        }

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        InventoryMovementEntity movement = InventoryMovementEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .transaction(transaction)
                .movementType(movementType)
                .quantity(quantity)
                .unitCost(null)
                .note(note)
                .build();

        InventoryMovementEntity savedMovement = inventoryMovementRepository.save(movement);
        inventoryStockSnapshotService.applyMovement(savedMovement);
    }

    @Transactional
    public void registerTransferMovements(
            TransactionEntity transaction,
            WarehouseEntity sourceWarehouse,
            WarehouseEntity targetWarehouse
    ) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        if (transaction.getId() == null) {
            throw new IllegalArgumentException("Transaction id cannot be null");
        }

        if (sourceWarehouse == null) {
            throw new IllegalArgumentException("Source warehouse cannot be null");
        }

        if (sourceWarehouse.getId() == null) {
            throw new IllegalArgumentException("Source warehouse id cannot be null");
        }

        if (targetWarehouse == null) {
            throw new IllegalArgumentException("Target warehouse cannot be null");
        }

        if (targetWarehouse.getId() == null) {
            throw new IllegalArgumentException("Target warehouse id cannot be null");
        }

        if (sourceWarehouse.getId().equals(targetWarehouse.getId())) {
            throw new IllegalArgumentException("Source and target warehouse cannot be the same");
        }

        if (transaction.getDetails() == null || transaction.getDetails().isEmpty()) {
            throw new IllegalArgumentException("Transaction must contain at least one detail");
        }

        List<InventoryMovementEntity> movements = transaction.getDetails()
                .stream()
                .flatMap(detail -> toTransferMovements(detail, transaction, sourceWarehouse, targetWarehouse))
                .toList();

        List<InventoryMovementEntity> savedMovements = inventoryMovementRepository.saveAll(movements);
        inventoryStockSnapshotService.applyMovements(savedMovements);
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

    private Stream<InventoryMovementEntity> toTransferMovements(
            TransactionDetailEntity detail,
            TransactionEntity transaction,
            WarehouseEntity sourceWarehouse,
            WarehouseEntity targetWarehouse
    ) {
        validateDetail(detail);

        InventoryMovementEntity transferOut = InventoryMovementEntity.builder()
                .product(detail.getProduct())
                .warehouse(sourceWarehouse)
                .transaction(transaction)
                .movementType(MovementType.TRANSFER_OUT)
                .quantity(detail.getQuantity())
                .unitCost(null)
                .note("Transfer #" + transaction.getId() + " to warehouse " + targetWarehouse.getId())
                .build();

        InventoryMovementEntity transferIn = InventoryMovementEntity.builder()
                .product(detail.getProduct())
                .warehouse(targetWarehouse)
                .transaction(transaction)
                .movementType(MovementType.TRANSFER_IN)
                .quantity(detail.getQuantity())
                .unitCost(null)
                .note("Transfer #" + transaction.getId() + " from warehouse " + sourceWarehouse.getId())
                .build();

        return Stream.of(transferOut, transferIn);
    }

    private void validateTransformation(ProductTransformationEntity transformation) {
        if (transformation == null) {
            throw new IllegalArgumentException("Transformation cannot be null");
        }

        if (transformation.getId() == null) {
            throw new IllegalArgumentException("Transformation id cannot be null");
        }

        if (transformation.getTransaction() == null) {
            throw new IllegalArgumentException("Transformation transaction cannot be null");
        }

        if (transformation.getTransaction().getId() == null) {
            throw new IllegalArgumentException("Transformation transaction id cannot be null");
        }

        if (transformation.getWarehouse() == null) {
            throw new IllegalArgumentException("Transformation warehouse cannot be null");
        }

        if (transformation.getWarehouse().getId() == null) {
            throw new IllegalArgumentException("Transformation warehouse id cannot be null");
        }

        if (transformation.getInputs() == null || transformation.getInputs().isEmpty()) {
            throw new IllegalArgumentException("Transformation must contain at least one input");
        }

        if (transformation.getOutputs() == null || transformation.getOutputs().isEmpty()) {
            throw new IllegalArgumentException("Transformation must contain at least one output");
        }

        for (ProductTransformationInputEntity input : transformation.getInputs()) {
            validateTransformationInput(input);
        }

        for (ProductTransformationOutputEntity output : transformation.getOutputs()) {
            validateTransformationOutput(output);
        }
    }

    private void validateTransformationInput(ProductTransformationInputEntity input) {
        if (input == null) {
            throw new IllegalArgumentException("Transformation input cannot be null");
        }

        if (input.getProduct() == null || input.getProduct().getId() == null) {
            throw new IllegalArgumentException("Transformation input product cannot be null");
        }

        if (input.getQuantity() == null) {
            throw new IllegalArgumentException("Transformation input quantity cannot be null");
        }

        if (input.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transformation input quantity must be greater than zero");
        }
    }

    private void validateTransformationOutput(ProductTransformationOutputEntity output) {
        if (output == null) {
            throw new IllegalArgumentException("Transformation output cannot be null");
        }

        if (output.getProduct() == null || output.getProduct().getId() == null) {
            throw new IllegalArgumentException("Transformation output product cannot be null");
        }

        if (output.getQuantity() == null) {
            throw new IllegalArgumentException("Transformation output quantity cannot be null");
        }

        if (output.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transformation output quantity must be greater than zero");
        }
    }

    private InventoryMovementEntity toTransformationInputMovement(
            ProductTransformationInputEntity input,
            ProductTransformationEntity transformation
    ) {
        return InventoryMovementEntity.builder()
                .product(input.getProduct())
                .warehouse(transformation.getWarehouse())
                .transaction(transformation.getTransaction())
                .movementType(MovementType.OUT)
                .quantity(input.getQuantity())
                .unitCost(null)
                .note("Transformation #" + transformation.getId() + " input")
                .build();
    }

    private InventoryMovementEntity toTransformationOutputMovement(
            ProductTransformationOutputEntity output,
            ProductTransformationEntity transformation
    ) {
        return InventoryMovementEntity.builder()
                .product(output.getProduct())
                .warehouse(transformation.getWarehouse())
                .transaction(transformation.getTransaction())
                .movementType(MovementType.IN)
                .quantity(output.getQuantity())
                .unitCost(null)
                .note("Transformation #" + transformation.getId() + " output")
                .build();
    }

    @Transactional
    public void registerTransformationMovements(ProductTransformationEntity transformation) {
        validateTransformation(transformation);

        List<InventoryMovementEntity> inputMovements = transformation.getInputs()
                .stream()
                .map(input -> toTransformationInputMovement(input, transformation))
                .toList();

        List<InventoryMovementEntity> outputMovements = transformation.getOutputs()
                .stream()
                .map(output -> toTransformationOutputMovement(output, transformation))
                .toList();

        List<InventoryMovementEntity> allMovements = Stream.concat(
                inputMovements.stream(),
                outputMovements.stream()
        ).toList();

        List<InventoryMovementEntity> savedMovements = inventoryMovementRepository.saveAll(allMovements);
        inventoryStockSnapshotService.applyMovements(savedMovements);
    }
}