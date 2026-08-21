package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.web.dto.request.CreateSaleReturnRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateSaleReturnResponse;
import com.empresa.serpent.inventory.web.dto.response.SaleReturnResponse;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.SaleReturnRepository;
import com.empresa.serpent.transactions.repository.TransactionDetailRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.users.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SaleReturnApplicationService {

    /** Matches the NUMERIC(19,4) money columns. */
    private static final int AMOUNT_SCALE = 4;

    private final TransactionRepository transactionRepository;
    private final TransactionDetailRepository transactionDetailRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final ProductRepository productRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final WarehouseAccessService warehouseAccessService;
    private final InventoryMovementService inventoryMovementService;

    @Transactional
    public CreateSaleReturnResponse createReturn(CreateSaleReturnRequest request) {

        UserEntity createdBy = authenticatedUserService.requireCurrentUser();
        authenticatedUserService.requireMatchingCreatedByUserId(request.createdByUserId(), createdBy);

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + request.productId()));

        // Resolves the warehouse (from the terminal when one is named), and checks that it
        // exists, is active, and is assigned to the acting user.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ValidationException("El depósito seleccionado está inactivo.");
        }

        SaleEntity originalSale = saleRepository.findById(request.saleId())
                .orElseThrow(() -> new NotFoundException("Sale not found: " + request.saleId()));

        TransactionEntity originalTransaction = originalSale.getTransaction();

        if (originalTransaction == null) {
            throw new ValidationException("La venta original no tiene una transacción asociada.");
        }

        if (originalTransaction.getType() != TransactionType.SALE) {
            throw new ValidationException("La transacción original no es una venta.");
        }

        BigDecimal soldQuantity = getSoldQuantity(originalTransaction.getId(), request.productId());

        if (soldQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "El producto \"" + product.getName() + "\" no forma parte de esta venta."
            );
        }

        BigDecimal alreadyReturnedQuantity = getAlreadyReturnedQuantity(request.saleId(), request.productId());

        BigDecimal remainingReturnableQuantity = soldQuantity.subtract(alreadyReturnedQuantity);

        if (remainingReturnableQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Ya se devolvió toda la cantidad vendida de \"" + product.getName() + "\"."
            );
        }

        if (request.quantity().compareTo(remainingReturnableQuantity) > 0) {
            throw new ValidationException(
                    "No podés devolver esa cantidad de \"" + product.getName() + "\". "
                            + "Se vendieron " + soldQuantity
                            + " y ya se devolvieron " + alreadyReturnedQuantity + "."
            );
        }

        PaymentMethodEntity refundPaymentMethod = resolveRefundPaymentMethod(request, originalSale);

        String description = request.reason() != null && !request.reason().isBlank()
                ? request.reason().trim()
                : "Devolución de la venta #" + request.saleId() + " — " + product.getName();

        // Refunded at what the customer actually paid: the original line price, prorated
        // by any manual adjustment on the sale (a 10% discount refunds 90% of the line).
        BigDecimal originalUnitPrice = getOriginalUnitPrice(originalTransaction.getId(), request.productId());
        BigDecimal adjustedUnitPrice = applySaleAdjustment(originalUnitPrice, originalSale, originalTransaction);

        // Returns are stored negative: money going out, so it subtracts in any aggregation.
        // The detail's subtotal is derived from unitPrice * quantity by the entity itself,
        // so carrying the sign on unitPrice keeps total == sum(subtotals).
        BigDecimal returnUnitPrice = adjustedUnitPrice.negate();
        BigDecimal returnTotal = returnUnitPrice.multiply(request.quantity());

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.RETURN)
                .status(TransactionStatus.CONFIRMED)
                .description(description)
                // How the money went back out. Null only for a credit-sale return, where
                // none did — see resolveRefundPaymentMethod.
                .paymentMethod(refundPaymentMethod)
                .createdByUserEntity(createdBy)
                .total(returnTotal)
                .details(new ArrayList<>())
                .build();

        TransactionDetailEntity detail = TransactionDetailEntity.builder()
                .transaction(transaction)
                .product(product)
                .description("Devolución")
                .quantity(request.quantity())
                .unitPrice(returnUnitPrice)
                .subtotal(returnTotal)
                .build();

        transaction.getDetails().add(detail);

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        SaleReturnEntity saleReturn = SaleReturnEntity.builder()
                .transaction(savedTransaction)
                .originalSale(originalSale)
                .reason(request.reason())
                .build();

        saleReturnRepository.save(saleReturn);

        inventoryMovementService.registerAdjustmentMovement(
                savedTransaction,
                warehouse,
                product,
                MovementType.RETURN_IN,
                request.quantity(),
                "Devolución de la venta #" + request.saleId()
        );

        /*
         Returning goods from a sale that was taken on account does not send cash out the
         door: it lowers what the customer owes. No extra bookkeeping is needed for that —
         the return's total is already stored negative, and the customer's statement adds
         returns against their credit sales alongside the sales themselves, so the balance
         drops by exactly this amount. What DOES need saying is that no money changes hands,
         because the cashier is standing in front of the customer deciding whether to open
         the till.
         */
        boolean lowersCustomerBalance = Boolean.TRUE.equals(originalSale.getOnCredit());

        return new CreateSaleReturnResponse(
                savedTransaction.getId(),
                originalSale.getId(),
                product.getId(),
                product.getName(),
                warehouse.getId(),
                warehouse.getName(),
                request.quantity(),
                lowersCustomerBalance,
                lowersCustomerBalance
                        ? "Devolución registrada. Como la venta fue a cuenta corriente, "
                          + "el importe se descuenta del saldo del cliente y no se devuelve dinero."
                        : "Devolución registrada correctamente."
        );
    }

    /** Returns every return registered against a sale, one row per returned product. */
    @Transactional(readOnly = true)
    public List<SaleReturnResponse> findBySaleId(Long saleId) {
        return saleReturnRepository.findByOriginalSaleId(saleId).stream()
                .flatMap(saleReturn -> saleReturn.getTransaction().getDetails().stream()
                        .map(detail -> new SaleReturnResponse(
                                saleReturn.getId(),
                                saleReturn.getTransaction().getId(),
                                saleReturn.getTransaction().getDate(),
                                saleId,
                                detail.getProduct().getId(),
                                detail.getProduct().getName(),
                                detail.getQuantity(),
                                saleReturn.getReason()
                        )))
                .toList();
    }

    /**
     * The price the customer actually paid for this product, taken from the original
     * sale. When the sale carries the product across several lines at different prices,
     * the weighted average is used so partial returns refund proportionally.
     */
    private BigDecimal getOriginalUnitPrice(Long saleTransactionId, Long productId) {
        List<TransactionDetailEntity> soldLines =
                transactionDetailRepository.findByTransactionIdAndProductId(saleTransactionId, productId);

        BigDecimal totalQuantity = soldLines.stream()
                .map(TransactionDetailEntity::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalAmount = soldLines.stream()
                .map(line -> {
                    BigDecimal unitPrice = line.getUnitPrice();
                    BigDecimal quantity = line.getQuantity();
                    if (unitPrice == null || quantity == null) {
                        return BigDecimal.ZERO;
                    }
                    return unitPrice.multiply(quantity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalAmount.divide(totalQuantity, AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Scales a line's unit price by the sale's manual adjustment, so a return refunds
     * what the customer actually paid: {@code unitPrice * saleTotal / saleSubtotal}.
     *
     * <p>The subtotal is derived from the frozen adjustment rather than re-summing the
     * lines, and the division is done in one step at money scale. Rounding an
     * intermediate factor first would leak error proportional to the sale size — on a
     * three-million-peso sale that reached a full peso.
     */
    private BigDecimal applySaleAdjustment(BigDecimal unitPrice,
                                           SaleEntity originalSale,
                                           TransactionEntity originalTransaction) {

        BigDecimal adjustmentAmount = originalSale.getAdjustmentAmount();
        if (adjustmentAmount == null || adjustmentAmount.compareTo(BigDecimal.ZERO) == 0) {
            return unitPrice;
        }

        BigDecimal saleTotal = originalTransaction.getTotal();
        BigDecimal saleSubtotal = saleTotal.subtract(adjustmentAmount);

        if (saleSubtotal.compareTo(BigDecimal.ZERO) == 0) {
            return unitPrice;
        }

        return unitPrice.multiply(saleTotal).divide(saleSubtotal, AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * How the refund left the till, when it left at all.
     *
     * <p>Asked for rather than copied from the original sale. Refunding cash for a card sale
     * is an ordinary thing to do — the customer is standing there and the card refund takes
     * days — so reading the sale's method would record something nobody said. Wrong here is
     * worse than missing: the shift count would come up short in one bucket and long in
     * another, and no one would know why.
     *
     * <p>A return against a credit sale is the one case with no method: nothing was ever
     * collected, so nothing goes back out. It lowers the customer's balance instead, and
     * naming a method would put a payout in the count that never happened.
     */
    private PaymentMethodEntity resolveRefundPaymentMethod(CreateSaleReturnRequest request,
                                                           SaleEntity originalSale) {
        boolean onCredit = Boolean.TRUE.equals(originalSale.getOnCredit());

        if (onCredit) {
            if (request.refundPaymentMethodId() != null) {
                throw new ValidationException(
                        "Esta venta fue a cuenta corriente, así que la devolución no paga plata: "
                                + "baja el saldo del cliente. No indiques un método de pago.");
            }
            return null;
        }

        if (request.refundPaymentMethodId() == null) {
            throw new ValidationException(
                    "Indicá por qué método devolvés la plata. Sin eso, el arqueo de caja no puede "
                            + "saber de dónde salió.");
        }

        return paymentMethodRepository.findById(request.refundPaymentMethodId())
                .orElseThrow(() -> new NotFoundException(
                        "Payment method not found: " + request.refundPaymentMethodId()));
    }

    private BigDecimal getSoldQuantity(Long saleTransactionId, Long productId) {
        return transactionDetailRepository.findByTransactionIdAndProductId(saleTransactionId, productId)
                .stream()
                .map(TransactionDetailEntity::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getAlreadyReturnedQuantity(Long saleId, Long productId) {
        List<SaleReturnEntity> saleReturns = saleReturnRepository.findByOriginalSaleId(saleId);

        if (saleReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return saleReturns.stream()
                .map(SaleReturnEntity::getTransaction)
                .filter(transaction -> transaction != null && transaction.getId() != null)
                .map(TransactionEntity::getId)
                .map(returnTransactionId ->
                        transactionDetailRepository.findByTransactionIdAndProductId(returnTransactionId, productId)
                )
                .flatMap(List::stream)
                .map(TransactionDetailEntity::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}