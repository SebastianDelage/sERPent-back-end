package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.web.dto.request.CreateInventoryAdjustmentRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateInventoryAdjustmentResponse;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
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
public class InventoryAdjustmentApplicationService {

    private final TransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;
    private final StockQueryService stockQueryService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateInventoryAdjustmentResponse createAdjustment(CreateInventoryAdjustmentRequest request) {

        UserEntity createdBy = authenticatedUserService.requireCurrentUser();
        authenticatedUserService.requireMatchingCreatedByUserId(request.createdByUserId(), createdBy);

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() ->
                        new NotFoundException("Product not found: " + request.productId()));

        // Resolves the warehouse (from the terminal when one is named), and checks that it
        // exists, is active, and is assigned to the acting user.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

        BigDecimal previousStock = stockQueryService.getStockByProductAndWarehouse(
                request.productId(),
                warehouse.getId()
        );

        if (request.countedQuantity().compareTo(previousStock) == 0) {
            throw new ValidationException("La cantidad contada coincide con el stock actual. No hace falta registrar un ajuste.");
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

        String countDetail = "Conteo: " + request.countedQuantity()
                + ", anterior: " + previousStock;

        String movementNote = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim() + " — " + countDetail
                : countDetail;

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