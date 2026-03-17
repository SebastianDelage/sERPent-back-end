package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateInventoryAdjustmentRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateInventoryAdjustmentResponse;
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
public class InventoryAdjustmentApplicationService {

    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final StockQueryService stockQueryService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateInventoryAdjustmentResponse createAdjustment(CreateInventoryAdjustmentRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() ->
                        new NotFoundException("Product not found: " + request.productId()));

        WarehouseEntity warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() ->
                        new NotFoundException("Warehouse not found: " + request.warehouseId()));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new IllegalArgumentException("Warehouse is inactive: " + request.warehouseId());
        }

        BigDecimal previousStock = stockQueryService.getStockByProductAndWarehouse(
                request.productId(),
                request.warehouseId()
        );

        if (request.countedQuantity().compareTo(previousStock) == 0) {
            throw new IllegalArgumentException("Counted quantity matches current stock. No adjustment is required");
        }

        MovementType movementType = request.countedQuantity().compareTo(previousStock) > 0
                ? MovementType.ADJUSTMENT_IN
                : MovementType.ADJUSTMENT_OUT;

        BigDecimal adjustmentQuantity = request.countedQuantity()
                .subtract(previousStock)
                .abs();

        String transactionDescription = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : "Inventory adjustment for product " + product.getId() + " in warehouse " + warehouse.getId();

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.ADJUSTMENT)
                .status(TransactionStatus.CONFIRMED)
                .description(transactionDescription)
                .paymentMethod(null)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        TransactionDetailEntity detail = TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description("Stock adjustment")
                .quantity(adjustmentQuantity)
                .unitPrice(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .build();

        transaction.getDetails().add(detail);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        String movementNote = "Stock adjustment: counted "
                + request.countedQuantity()
                + ", previous "
                + previousStock;

        inventoryMovementService.registerAdjustmentMovement(
                savedTransaction,
                warehouse,
                product,
                movementType,
                adjustmentQuantity,
                movementNote
        );

        return new CreateInventoryAdjustmentResponse(
                savedTransaction.getId(),
                product.getId(),
                product.getName(),
                warehouse.getId(),
                warehouse.getName(),
                movementType.name(),
                previousStock,
                request.countedQuantity(),
                adjustmentQuantity,
                "Inventory adjustment created successfully"
        );
    }
}