package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.PurchaseEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.PurchaseRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.web.dto.request.CreatePurchaseItemRequest;
import com.empresa.serpent.transactions.web.dto.request.CreatePurchaseRequest;
import com.empresa.serpent.transactions.web.dto.response.CreatePurchaseResponse;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseApplicationService {

    private final TransactionRepository transactionRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreatePurchaseResponse createPurchase(CreatePurchaseRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        PaymentMethodEntity paymentMethod = null;
        if (request.paymentMethodId() != null) {
            paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                    .orElseThrow(() ->
                            new NotFoundException("Payment method not found: " + request.paymentMethodId()));
        }

        SupplierEntity supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() ->
                            new NotFoundException("Supplier not found: " + request.supplierId()));

            if (!Boolean.TRUE.equals(supplier.getActive())) {
                throw new IllegalArgumentException("Supplier is inactive: " + request.supplierId());
            }
        }

        WarehouseEntity warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() ->
                        new NotFoundException("Warehouse not found: " + request.warehouseId()));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new IllegalArgumentException("Warehouse is inactive: " + request.warehouseId());
        }

        validateReceiptNumber(request.receiptNumber());

        List<Long> productIds = request.items().stream()
                .map(CreatePurchaseItemRequest::productId)
                .toList();

        List<ProductEntity> products = productRepository.findByIdIn(productIds);

        Map<Long, ProductEntity> productMap = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.PURCHASE)
                .status(TransactionStatus.CONFIRMED)
                .description(normalizeOptional(request.description()))
                .paymentMethod(paymentMethod)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CreatePurchaseItemRequest item : request.items()) {

            ProductEntity product = productMap.get(item.productId());
            if (product == null) {
                throw new NotFoundException("Product not found: " + item.productId());
            }

            validateItem(item);

            BigDecimal subtotal = item.unitPrice().multiply(item.quantity());

            TransactionDetailEntity detail = TransactionDetailEntity.builder()
                    .transaction(transaction)
                    .product(product)
                    .description(
                            item.description() != null && !item.description().isBlank()
                                    ? item.description().trim()
                                    : product.getName()
                    )
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .subtotal(subtotal)
                    .build();

            transaction.getDetails().add(detail);
            total = total.add(subtotal);
        }

        transaction.setTotal(total);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        PurchaseEntity purchase = PurchaseEntity.builder()
                .transaction(savedTransaction)
                .supplier(supplier)
                .warehouse(warehouse)
                .receiptNumber(normalizeOptional(request.receiptNumber()))
                .notes(normalizeOptional(request.notes()))
                .build();

        PurchaseEntity savedPurchase = purchaseRepository.save(purchase);

        inventoryMovementService.registerPurchaseMovements(savedTransaction, warehouse);

        return new CreatePurchaseResponse(
                savedTransaction.getId(),
                savedPurchase.getId(),
                savedTransaction.getStatus().name(),
                "Purchase created successfully"
        );
    }

    private void validateItem(CreatePurchaseItemRequest item) {
        if (item.quantity() == null) {
            throw new IllegalArgumentException("Item quantity cannot be null");
        }

        if (item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Item quantity must be greater than zero");
        }

        if (item.unitPrice() == null) {
            throw new IllegalArgumentException("Item unitPrice cannot be null");
        }

        if (item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Item unitPrice cannot be negative");
        }
    }

    private void validateReceiptNumber(String receiptNumber) {
        String normalized = normalizeOptional(receiptNumber);

        if (normalized == null) {
            return;
        }

        if (purchaseRepository.existsByReceiptNumberIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Receipt number already exists: " + normalized);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}