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
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final StockValidationService stockValidationService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateWarehouseTransferResponse createTransfer(CreateWarehouseTransferRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.createdByUserId()));

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

        stockValidationService.validateAvailableStock(
                request.productId(),
                request.sourceWarehouseId(),
                request.quantity()
        );

        String description = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : "Transfer of product " + product.getId()
                + " from warehouse " + sourceWarehouse.getId()
                + " to warehouse " + targetWarehouse.getId();

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
                .description("Warehouse transfer")
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