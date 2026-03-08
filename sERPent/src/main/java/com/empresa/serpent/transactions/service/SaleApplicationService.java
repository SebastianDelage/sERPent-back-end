package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.inventory.service.StockValidationService;
import com.empresa.serpent.inventory.web.dto.request.StockCheckItemRequest;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleItemRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateSaleResponse;
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
public class SaleApplicationService {

    private final TransactionRepository transactionRepository;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockValidationService stockValidationService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateSaleResponse createSale(CreateSaleRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        PaymentMethodEntity paymentMethod = null;
        if (request.paymentMethodId() != null) {
            paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                    .orElseThrow(() ->
                            new NotFoundException("Payment method not found: " + request.paymentMethodId()));
        }

        WarehouseEntity warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() ->
                        new NotFoundException("Warehouse not found: " + request.warehouseId()));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new IllegalArgumentException("Warehouse is inactive: " + request.warehouseId());
        }

        if (request.invoiceNumber() != null
                && !request.invoiceNumber().isBlank()
                && saleRepository.existsByInvoiceNumber(request.invoiceNumber())) {
            throw new IllegalArgumentException("Invoice number already exists: " + request.invoiceNumber());
        }

        stockValidationService.validateSaleItemsStock(
                request.items().stream()
                        .map(item -> new StockCheckItemRequest(item.productId(), item.quantity()))
                        .toList(),
                warehouse.getId()
        );

        /*
         Batch load products to avoid N+1 queries.
         */
        List<Long> productIds = request.items().stream()
                .map(CreateSaleItemRequest::productId)
                .toList();

        List<ProductEntity> products = productRepository.findByIdIn(productIds);

        Map<Long, ProductEntity> productMap = products.stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED)
                .description(request.description())
                .paymentMethod(paymentMethod)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CreateSaleItemRequest item : request.items()) {

            ProductEntity product = productMap.get(item.productId());
            if (product == null) {
                throw new NotFoundException("Product not found: " + item.productId());
            }

            if (item.unitPrice() == null) {
                throw new IllegalArgumentException("Item unitPrice cannot be null");
            }

            if (item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Item unitPrice cannot be negative");
            }

            BigDecimal subtotal = item.unitPrice().multiply(item.quantity());

            TransactionDetailEntity detail = TransactionDetailEntity.builder()
                    .transaction(transaction)
                    .product(product)
                    .description(
                            item.description() != null && !item.description().isBlank()
                                    ? item.description()
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

        SaleEntity sale = SaleEntity.builder()
                .transaction(savedTransaction)
                .customerId(request.customerId())
                .customerName(request.customerName())
                .customerDocument(request.customerDocument())
                .invoiceNumber(request.invoiceNumber())
                .taxTotal(BigDecimal.ZERO)
                .build();

        SaleEntity savedSale = saleRepository.save(sale);

        inventoryMovementService.registerSaleMovements(savedTransaction, warehouse);

        /*
         TODO FUTURE:
         - calculate taxes
         - integrate with AFIP
         - generate CAE
         */
        return new CreateSaleResponse(
                savedTransaction.getId(),
                savedSale.getId(),
                savedTransaction.getStatus().name(),
                "Sale created successfully"
        );
    }
}