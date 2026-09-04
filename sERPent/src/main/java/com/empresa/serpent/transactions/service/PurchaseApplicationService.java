package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.inventory.service.WarehouseAccessService;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
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
    private final PaymentMethodRepository paymentMethodRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryMovementService inventoryMovementService;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;

    @Transactional
    public CreatePurchaseResponse createPurchase(CreatePurchaseRequest request) {

        UserEntity createdBy = authenticatedUserService.requireCurrentUser();
        authenticatedUserService.requireMatchingCreatedByUserId(request.createdByUserId(), createdBy);

        /*
         A purchase on credit is owed to a particular supplier and pays nothing now, so it
         carries no payment method. Rejecting the method outright rather than dropping it
         keeps a caller from believing it paid something it did not.

         Note this only constrains purchases that ASK for credit. A purchase with no
         payment method and no credit flag keeps meaning whatever it meant before: nothing
         in particular, which is how it has behaved since purchases were introduced.
         */
        if (request.isOnCredit()) {
            if (request.paymentMethodId() != null) {
                throw new ValidationException(
                        "Una compra a plazo no lleva método de pago, porque no se paga en el momento.");
            }
            if (request.supplierId() == null) {
                throw new ValidationException(
                        "Una compra a plazo tiene que indicar el proveedor al que se le debe.");
            }
        }

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

        // Resolves the warehouse (from the terminal when one is named), and checks that it
        // exists, is active, and is assigned to the acting user.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

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
                .onCredit(request.isOnCredit())
                .warehouse(warehouse)
                .receiptNumber(normalizeOptional(request.receiptNumber()))
                // purchases.notes ya no se escribe: el texto del operador vive en
                // transactions.description, que es donde toda la app lo busca. La columna
                // queda como archivo de lo ya cargado, sin escritor, igual que
                // inventory_movements.note.
                .notes(null)
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