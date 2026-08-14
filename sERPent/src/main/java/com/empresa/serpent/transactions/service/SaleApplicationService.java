package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.inventory.service.StockValidationService;
import com.empresa.serpent.inventory.web.dto.request.StockCheckItemRequest;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.ProductPaymentAdjustmentEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.ProductPaymentAdjustmentRepository;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleApplicationService {

    /** Matches the NUMERIC(19,4) money columns. */
    private static final int AMOUNT_SCALE = 4;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ProductPaymentAdjustmentRepository productPaymentAdjustmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockValidationService stockValidationService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateSaleResponse createSale(CreateSaleRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        /*
         Checked here and not only via @NotNull on the request: the offline sync path
         (SyncCommandResultService.processCreateSale) deserializes the payload with
         Jackson and calls this method directly, bypassing Bean Validation entirely.
         */
        if (request.paymentMethodId() == null) {
            throw new ValidationException("Tenés que indicar el método de pago de la venta.");
        }

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() ->
                        new NotFoundException("Payment method not found: " + request.paymentMethodId()));

        WarehouseEntity warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() ->
                        new NotFoundException("Warehouse not found: " + request.warehouseId()));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ValidationException("El depósito seleccionado está inactivo.");
        }

        if (request.invoiceNumber() != null
                && !request.invoiceNumber().isBlank()
                && saleRepository.existsByInvoiceNumber(request.invoiceNumber())) {
            throw new ConflictException("Ya existe una venta con el comprobante \"" + request.invoiceNumber() + "\".");
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

        /*
         Per-product surcharge/discount for this sale's payment method, in one query
         for the whole cart. Products without a rule are simply absent from the map.
         */
        Map<Long, BigDecimal> percentageByProduct = productPaymentAdjustmentRepository
                .findByPaymentMethodIdAndProductIdInAndActiveTrue(paymentMethod.getId(), productIds)
                .stream()
                .collect(Collectors.toMap(
                        rule -> rule.getProduct().getId(),
                        ProductPaymentAdjustmentEntity::getAdjustmentPercentage));

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED)
                .description(request.description())
                .paymentMethod(paymentMethod)
                .createdByUserEntity(createdBy)
                .total(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateSaleItemRequest item : request.items()) {

            ProductEntity product = productMap.get(item.productId());
            if (product == null) {
                throw new NotFoundException("Product not found: " + item.productId());
            }

            if (item.unitPrice() == null) {
                throw new ValidationException("El precio de un ítem es obligatorio.");
            }

            if (item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("El precio de un ítem no puede ser negativo.");
            }

            /*
             Layer 1 of 2: the product's own rule for this payment method rides on the
             unit price, because TransactionDetailEntity derives subtotal from
             unitPrice * quantity on persist — a surcharged subtotal set by hand would
             be overwritten. One multiply and one divide, so no intermediate rounding
             leaks error the way a pre-rounded factor would.
             Layer 2 (the sale-wide manual adjustment) then acts on the sum of these
             already-adjusted lines, further down.
             */
            BigDecimal effectiveUnitPrice = item.unitPrice();
            BigDecimal productPercentage = percentageByProduct.get(item.productId());
            boolean ruleApplied = productPercentage != null && productPercentage.signum() != 0;

            if (ruleApplied) {
                effectiveUnitPrice = item.unitPrice()
                        .multiply(ONE_HUNDRED.add(productPercentage))
                        .divide(ONE_HUNDRED, AMOUNT_SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal lineSubtotal = effectiveUnitPrice.multiply(item.quantity());

            TransactionDetailEntity detail = TransactionDetailEntity.builder()
                    .transaction(transaction)
                    .product(product)
                    .description(
                            item.description() != null && !item.description().isBlank()
                                    ? item.description()
                                    : product.getName()
                    )
                    .quantity(item.quantity())
                    .unitPrice(effectiveUnitPrice)
                    .subtotal(lineSubtotal)
                    // Frozen only when a rule actually moved the price: all three together,
                    // so the detail screen can later explain this line's unit price.
                    .baseUnitPrice(ruleApplied ? item.unitPrice() : null)
                    .appliedPercentage(ruleApplied ? productPercentage : null)
                    .appliedMethodName(ruleApplied ? paymentMethod.getName() : null)
                    .build();

            transaction.getDetails().add(detail);
            subtotal = subtotal.add(lineSubtotal);
        }

        AdjustmentType adjustmentType = request.adjustmentType() != null
                ? request.adjustmentType()
                : AdjustmentType.NONE;

        if (adjustmentType != AdjustmentType.NONE && request.adjustmentValue() == null) {
            throw new ValidationException("Elegiste un tipo de ajuste pero no indicaste el valor.");
        }

        BigDecimal adjustmentValue = adjustmentType == AdjustmentType.NONE
                ? BigDecimal.ZERO
                : request.adjustmentValue();

        BigDecimal adjustmentAmount = switch (adjustmentType) {
            case PERCENTAGE -> subtotal.multiply(adjustmentValue)
                    .divide(ONE_HUNDRED, AMOUNT_SCALE, RoundingMode.HALF_UP);
            case FIXED -> adjustmentValue;
            case NONE -> BigDecimal.ZERO;
        };

        BigDecimal total = subtotal.add(adjustmentAmount);

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("El descuento aplicado no puede dejar el total de la venta en negativo.");
        }

        transaction.setTotal(total);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        SaleEntity sale = SaleEntity.builder()
                .transaction(savedTransaction)
                .warehouse(warehouse)
                .customerId(request.customerId())
                .customerName(request.customerName())
                .customerDocument(request.customerDocument())
                .invoiceNumber(request.invoiceNumber())
                .taxTotal(BigDecimal.ZERO)
                .adjustmentType(adjustmentType)
                .adjustmentValue(adjustmentValue)
                .adjustmentAmount(adjustmentAmount)
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