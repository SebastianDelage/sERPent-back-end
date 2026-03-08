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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;

    public void registerSaleMovements(TransactionEntity transaction, WarehouseEntity warehouse) {
        List<InventoryMovementEntity> movements = transaction.getDetails()
                .stream()
                .map(detail -> toSaleMovement(detail, transaction, warehouse))
                .toList();

        inventoryMovementRepository.saveAll(movements);
    }

    public void registerPurchaseMovements(TransactionEntity transaction, WarehouseEntity warehouse) {
        List<InventoryMovementEntity> movements = transaction.getDetails()
                .stream()
                .map(detail -> toPurchaseMovement(detail, transaction, warehouse))
                .toList();

        inventoryMovementRepository.saveAll(movements);
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
}