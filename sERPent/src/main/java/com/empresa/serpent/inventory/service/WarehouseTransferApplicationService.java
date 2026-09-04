package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateWarehouseTransferRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateWarehouseTransferResponse;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.users.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class WarehouseTransferApplicationService {

    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockValidationService stockValidationService;
    private final InventoryMovementService inventoryMovementService;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;

    @Transactional
    public CreateWarehouseTransferResponse createTransfer(CreateWarehouseTransferRequest request) {

        UserEntity createdBy = authenticatedUserService.requireCurrentUser();
        authenticatedUserService.requireMatchingCreatedByUserId(request.createdByUserId(), createdBy);

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + request.productId()));

        WarehouseEntity sourceWarehouse = warehouseRepository.findById(request.sourceWarehouseId())
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + request.sourceWarehouseId()));

        WarehouseEntity targetWarehouse = warehouseRepository.findById(request.targetWarehouseId())
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + request.targetWarehouseId()));

        if (!Boolean.TRUE.equals(sourceWarehouse.getActive())) {
            throw new IllegalArgumentException("Source warehouse is inactive: " + request.sourceWarehouseId());
        }

        if (!Boolean.TRUE.equals(targetWarehouse.getActive())) {
            throw new IllegalArgumentException("Target warehouse is inactive: " + request.targetWarehouseId());
        }

        if (sourceWarehouse.getId().equals(targetWarehouse.getId())) {
            throw new IllegalArgumentException("Source and target warehouse cannot be the same");
        }

        /*
         Only the source is checked against the user's assignment. A transfer is
         cross-warehouse by nature, and the source is where the stock actually leaves —
         the side with the consequence. Requiring the target too would force assigning the
         central warehouse to every branch operator, which would hollow out the control
         it is meant to be.
         */
        warehouseAccessService.requireAssigned(sourceWarehouse, createdBy);

        stockValidationService.validateAvailableStock(
                request.productId(),
                request.sourceWarehouseId(),
                request.quantity()
        );

        /*
         * ONLY WHAT THE PERSON WROTE. See InventoryAdjustmentApplicationService for the whole
         * reasoning; the fallback here was "Transfer of product 5 from warehouse 1 to warehouse
         * 3", which is the same defect with two ids instead of one.
         *
         * <p>The two branches were the one thing in that sentence not already on the detail
         * screen. They are not lost and they need no new column: both ends of a transfer leave
         * an inventory movement, and TransactionQueryService already reads branch names from
         * those movements for the transaction list. The detail response now carries the same
         * list, and the screen composes "Depósitos: Central, Sucursal Norte" from it.
         */
        String description = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : null;

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.CONFIRMED)
                .description(description)
                .paymentMethod(null)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        TransactionDetailEntity detail = TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                // Same as the adjustment's: dead text behind productName, frozen in English.
                .description(null)
                .quantity(request.quantity())
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .build();

        transaction.getDetails().add(detail);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        inventoryMovementService.registerTransferMovements(
                savedTransaction,
                sourceWarehouse,
                targetWarehouse
        );

        return new CreateWarehouseTransferResponse(
                savedTransaction.getId(),
                product.getId(),
                product.getName(),
                sourceWarehouse.getId(),
                sourceWarehouse.getName(),
                targetWarehouse.getId(),
                targetWarehouse.getName(),
                request.quantity(),
                "Warehouse transfer created successfully"
        );
    }
}