package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.CustomerRepository;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.inventory.service.StockValidationService;
import com.empresa.serpent.inventory.service.WarehouseAccessService;
import com.empresa.serpent.inventory.web.dto.request.StockCheckItemRequest;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
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
    private final CustomerRepository customerRepository;
    private final ProductPaymentAdjustmentRepository productPaymentAdjustmentRepository;
    private final StockValidationService stockValidationService;
    private final InventoryMovementService inventoryMovementService;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;

    /** Online path: the acting user is whoever is holding the session. */
    @Transactional
    public CreateSaleResponse createSale(CreateSaleRequest request) {
        UserEntity createdBy = authenticatedUserService.requireCurrentUser();
        authenticatedUserService.requireMatchingCreatedByUserId(request.createdByUserId(), createdBy);

        return createSale(request, createdBy);
    }

    /**
     * Offline sync path: the acting user is the one named in the queued payload, not whoever
     * happens to be uploading it. A sale made by cashier A and synced by cashier B must stay
     * attributed to A — attribution belongs to whoever made the sale.
     *
     * <p>This is knowingly the weaker of the two paths: the payload is client-supplied and
     * therefore forgeable, so a caller could name another user here. It is accepted because
     * the alternative — attributing the sale to whoever syncs — corrupts the data itself,
     * which is worse than the residual risk. The warehouse assignment is still enforced,
     * against the user named in the payload.
     */
    @Transactional
    public CreateSaleResponse createSaleFromSync(CreateSaleRequest request) {
        if (request.createdByUserId() == null) {
            throw new ValidationException("La operación no indica el usuario que la registró.");
        }

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        return createSale(request, createdBy);
    }

    private CreateSaleResponse createSale(CreateSaleRequest request, UserEntity createdBy) {

        /*
         Checked here and not only through Bean Validation: the offline sync path
         (SyncCommandResultService.processCreateSale) deserializes the payload with
         Jackson and calls this method directly, bypassing Bean Validation entirely.

         A sale is either collected now — and then it names how — or taken on the
         customer's account, and then it names whom. Never both, never neither.
         */
        CustomerEntity customer = resolveCustomer(request);
        PaymentMethodEntity paymentMethod = resolvePaymentMethod(request);

        // Resolves the warehouse (from the terminal when one is named), and checks that it
        // exists, is active, and is assigned to the acting user.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

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

         A credit sale has no payment method, so there is no rule to look up and the map
         stays empty: it sells at list price, and the three frozen breakdown fields on each
         line stay null, which is exactly what they mean. Charging extra for buying on
         account would be a new pricing rule nobody asked for; the per-sale manual
         adjustment is already there for that.
         */
        Map<Long, BigDecimal> percentageByProduct = paymentMethod == null
                ? Map.of()
                : productPaymentAdjustmentRepository
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

            /*
             REDONDEADO POR RENGLÓN, y esto CONTRADICE A PROPÓSITO un commit anterior.

             Hace unas tandas se sacó el redondeo por línea del frontend justamente para
             alinearlo con este multiply, que no redondeaba. Aquello buscaba que el total
             previsualizado coincidiera con el guardado, y para eso los dos lados tenían
             que multiplicar igual.

             La venta por peso agregó un invariante que antes no se podía violar: que la
             suma de los renglones dé el total. Con cantidades enteras era imposible
             romperlo —un precio de 4 decimales por un entero sigue teniendo 4—, pero con
             cantidades de 3 decimales el producto llega a 7, la columna guarda 4, y el
             total se sumaba sin redondear mientras cada renglón se recortaba solo.

             Se redondea con la MISMA regla que TransactionDetailEntity.calculateSubtotal(),
             que es quien fija el valor que termina en la base: si las dos se separan, el
             total deja de ser la suma de lo guardado. El frontend replica esta línea.
            */
            BigDecimal lineSubtotal = effectiveUnitPrice.multiply(item.quantity())
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

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
                .customer(customer)
                .onCredit(request.isOnCredit())
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

    /**
     * The named customer, mandatory when the sale goes on account.
     *
     * <p>A balance has to belong to someone the system can find again: the free-text
     * customer name on the sale is enough to print on a ticket, but nothing can be
     * collected from it later.
     */
    private CustomerEntity resolveCustomer(CreateSaleRequest request) {
        if (request.customerId() == null) {
            if (request.isOnCredit()) {
                throw new ValidationException(
                        "Una venta a cuenta corriente tiene que indicar el cliente que se lleva la deuda.");
            }
            return null;
        }

        CustomerEntity customer = customerRepository.findById(request.customerId())
                .orElseThrow(() ->
                        new NotFoundException("Customer not found: " + request.customerId()));

        if (!Boolean.TRUE.equals(customer.getActive())) {
            throw new ValidationException(
                    "El cliente \"" + customer.getName() + "\" está inactivo.");
        }

        return customer;
    }

    /**
     * The payment method, mandatory for a collected sale and forbidden for a credit one.
     *
     * <p>Rejecting the method outright on a credit sale rather than ignoring it keeps the
     * sales-by-payment-method report honest: money that never arrived must not be
     * attributed to a method, and silently dropping the field would hide a caller that
     * believes it collected the sale.
     */
    private PaymentMethodEntity resolvePaymentMethod(CreateSaleRequest request) {
        if (request.isOnCredit()) {
            if (request.paymentMethodId() != null) {
                throw new ValidationException(
                        "Una venta a cuenta corriente no lleva método de pago, porque no se cobra en el momento.");
            }
            return null;
        }

        if (request.paymentMethodId() == null) {
            throw new ValidationException("Tenés que indicar el método de pago de la venta.");
        }

        return paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() ->
                        new NotFoundException("Payment method not found: " + request.paymentMethodId()));
    }
}