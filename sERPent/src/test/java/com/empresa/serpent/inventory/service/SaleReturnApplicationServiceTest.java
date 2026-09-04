package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateSaleReturnRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateSaleReturnResponse;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.SaleReturnRepository;
import com.empresa.serpent.transactions.repository.TransactionDetailRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SaleReturnApplicationService")
class SaleReturnApplicationServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionDetailRepository transactionDetailRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private SaleReturnRepository saleReturnRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private WarehouseAccessService warehouseAccessService;
    @Mock private AuthenticatedUserService authenticatedUserService;
    @Mock private InventoryMovementService inventoryMovementService;

    private SaleReturnApplicationService service;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long WAREHOUSE_ID = 3L;
    private static final Long SALE_ID = 5L;
    private static final Long SALE_TX_ID = 100L;

    /** The method the refund goes back out through, now that it has to be stated. */
    private static final Long REFUND_METHOD_ID = 7L;

    /** What the customer paid at the time of the sale. */
    private static final BigDecimal SALE_UNIT_PRICE = new BigDecimal("4500.0000");

    /** Today's list price, deliberately different: refunds must not use it. */
    private static final BigDecimal CURRENT_PRODUCT_PRICE = new BigDecimal("7000.0000");

    @BeforeEach
    void setUp() {
        service = new SaleReturnApplicationService(
                transactionRepository,
                transactionDetailRepository,
                saleRepository,
                saleReturnRepository,
                productRepository,
                paymentMethodRepository,
                authenticatedUserService,
                warehouseAccessService,
                inventoryMovementService
        );

        // The refund method is now asked for on every return of a paid sale. Lenient: the
        // tests that fail before reaching it never resolve it.
        lenient().when(paymentMethodRepository.findById(REFUND_METHOD_ID))
                .thenReturn(Optional.of(PaymentMethodEntity.builder()
                        .id(REFUND_METHOD_ID).name("Efectivo").isCash(true).active(true).build()));
    }

    private CreateSaleReturnRequest request(BigDecimal quantity) {
        return new CreateSaleReturnRequest(SALE_ID, PRODUCT_ID, WAREHOUSE_ID, quantity, REFUND_METHOD_ID, "Fallado", USER_ID);
    }

    private UserEntity user() {
        return UserEntity.builder().id(USER_ID).username("admin").build();
    }

    private ProductEntity product() {
        return ProductEntity.builder()
                .id(PRODUCT_ID)
                .name("Pollo entero")
                .price(CURRENT_PRODUCT_PRICE)
                .active(true)
                .build();
    }

    private WarehouseEntity warehouse(boolean active) {
        return WarehouseEntity.builder().id(WAREHOUSE_ID).name("Central").active(active).build();
    }

    /** A confirmed SALE transaction with its inverse SaleEntity wired both ways. */
    private SaleEntity confirmedSale() {
        TransactionEntity tx = TransactionEntity.builder()
                .id(SALE_TX_ID)
                .type(TransactionType.SALE)
                .build();
        SaleEntity sale = SaleEntity.builder().id(SALE_ID).transaction(tx).build();
        tx.setSale(sale);
        return sale;
    }

    /**
     * A sale carrying a manual adjustment. The total is the post-adjustment figure, so
     * {@code subtotal = total - adjustmentAmount} is what the proration divides by.
     */
    private SaleEntity adjustedSale(String total, String adjustmentAmount) {
        TransactionEntity tx = TransactionEntity.builder()
                .id(SALE_TX_ID)
                .type(TransactionType.SALE)
                .total(new BigDecimal(total))
                .build();
        SaleEntity sale = SaleEntity.builder()
                .id(SALE_ID)
                .transaction(tx)
                .adjustmentType(AdjustmentType.FIXED)
                .adjustmentAmount(new BigDecimal(adjustmentAmount))
                .build();
        tx.setSale(sale);
        return sale;
    }

    /** A line of the original sale. Carries a unit price: refunds are priced off it. */
    private TransactionDetailEntity soldLine(BigDecimal qty) {
        return soldLine(qty, SALE_UNIT_PRICE);
    }

    private TransactionDetailEntity soldLine(BigDecimal qty, BigDecimal unitPrice) {
        return TransactionDetailEntity.builder()
                .product(product())
                .quantity(qty)
                .unitPrice(unitPrice)
                .build();
    }

    /** The RETURN transaction handed to the repository. */
    private TransactionEntity savedReturn() {
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    /** Wires the happy-path lookups shared by most tests. */
    private void stubCommonLookups(boolean warehouseActive) {
        when(authenticatedUserService.requireCurrentUser()).thenReturn(user());
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(warehouseAccessService.resolveForOperation(any(), any(), any())).thenReturn(warehouse(warehouseActive));
    }

    // --- happy path ---

    @Test
    @DisplayName("Should register a return and move stock back in")
    void shouldRegisterReturnAndMoveStockIn() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // Sold 5, nothing returned yet.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSaleReturnResponse response = service.createReturn(request(new BigDecimal("2")));

        assertEquals("Pollo entero", response.productName());
        assertEquals(new BigDecimal("2"), response.quantity());
        assertEquals("Devolución registrada correctamente.", response.message());

        // A return transaction and the sale-return link are persisted.
        verify(transactionRepository).save(any(TransactionEntity.class));
        verify(saleReturnRepository).save(any(SaleReturnEntity.class));
        // Stock returns to the warehouse as RETURN_IN for the exact quantity.
        verify(inventoryMovementService).registerAdjustmentMovement(
                any(TransactionEntity.class),
                any(WarehouseEntity.class),
                any(ProductEntity.class),
                eq(MovementType.RETURN_IN),
                eq(new BigDecimal("2")),
                // isNull y no any(String.class): el movimiento ya no lleva nota. La frase
                // "Devolución de la venta #12" que llevaba antes era texto compuesto acá y
                // congelado en la columna, y encima le ganaba al motivo que escribió la persona.
                isNull()
        );
    }

    // --- refunded amount ---

    @Test
    @DisplayName("Should price the refund off the original sale, not the current product price")
    void shouldRefundAtOriginalSalePrice() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // Sold 5 at 4500; the product now lists at 7000.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("2")));

        TransactionDetailEntity detail = savedReturn().getDetails().getFirst();
        // 2 x 4500 = 9000, refunded — not 2 x 7000.
        assertThat(detail.getUnitPrice()).isEqualByComparingTo("-4500.0000");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("-9000.0000");
    }

    @Test
    @DisplayName("Should store the return total as a negative amount")
    void shouldStoreReturnTotalAsNegative() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("2")));

        TransactionEntity saved = savedReturn();
        assertThat(saved.getType()).isEqualTo(TransactionType.RETURN);
        assertThat(saved.getTotal()).isNegative();
        assertThat(saved.getTotal()).isEqualByComparingTo("-9000.0000");
        // The quantity stays a positive magnitude; only the money carries the sign.
        assertThat(saved.getDetails().getFirst().getQuantity()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("Should refund proportionally on a partial return")
    void shouldRefundProportionallyOnPartialReturn() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // Sold 5 at 4500 = 22500 total.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("2")));

        // Returning 2 of 5 refunds 9000, not the whole 22500.
        assertThat(savedReturn().getTotal()).isEqualByComparingTo("-9000.0000");
    }

    @Test
    @DisplayName("Should use the weighted average when the sale priced the product on several lines")
    void shouldUseWeightedAverageAcrossSaleLines() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // 2 at 4000 and 3 at 5000 -> 23000 over 5 units = 4600 each.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(
                        soldLine(new BigDecimal("2"), new BigDecimal("4000.0000")),
                        soldLine(new BigDecimal("3"), new BigDecimal("5000.0000"))
                ));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("2")));

        assertThat(savedReturn().getTotal()).isEqualByComparingTo("-9200.0000");
    }

    // --- refunds on a sale carrying an adjustment ---

    @Test
    @DisplayName("Should refund the discounted price, not the list price, on a discounted sale")
    void shouldRefundProratedByDiscount() {
        stubCommonLookups(true);
        // Subtotal 3000, 10% discount frozen as -300, so the customer paid 2700.
        when(saleRepository.findById(SALE_ID))
                .thenReturn(Optional.of(adjustedSale("2700.0000", "-300.0000")));
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("1"), new BigDecimal("3000.0000"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("1")));

        // The 3000 line refunds 2700: what was actually paid for it.
        assertThat(savedReturn().getTotal()).isEqualByComparingTo("-2700.0000");
    }

    @Test
    @DisplayName("Should refund above list price on a surcharged sale")
    void shouldRefundProratedBySurcharge() {
        stubCommonLookups(true);
        // Subtotal 3000, 10% surcharge frozen as +300, so the customer paid 3300.
        when(saleRepository.findById(SALE_ID))
                .thenReturn(Optional.of(adjustedSale("3300.0000", "300.0000")));
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("1"), new BigDecimal("3000.0000"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("1")));

        assertThat(savedReturn().getTotal()).isEqualByComparingTo("-3300.0000");
    }

    @Test
    @DisplayName("Should refund the plain line price when the sale carries no adjustment")
    void shouldRefundUnproratedWhenSaleHasNoAdjustment() {
        stubCommonLookups(true);
        // Adjustment amount zero: the proration is a no-op, factor 1.0.
        when(saleRepository.findById(SALE_ID))
                .thenReturn(Optional.of(adjustedSale("3000.0000", "0.0000")));
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("1"), new BigDecimal("3000.0000"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("1")));

        assertThat(savedReturn().getTotal()).isEqualByComparingTo("-3000.0000");
    }

    @Test
    @DisplayName("Returning a whole discounted sale refunds what the customer paid, within a cent")
    void shouldRefundWholeDiscountedSaleWithoutStrayCents() {
        // Three products, 1 unit at 1000 each: subtotal 3000, fixed discount -1000, paid 2000.
        // The factor (2/3) does not terminate, so this is the worst case for rounding.
        BigDecimal saleTotal = new BigDecimal("2000.0000");
        SaleEntity sale = adjustedSale("2000.0000", "-1000.0000");

        List<Long> productIds = List.of(10L, 20L, 30L);

        when(authenticatedUserService.requireCurrentUser()).thenReturn(user());
        when(warehouseAccessService.resolveForOperation(any(), any(), any())).thenReturn(warehouse(true));
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(sale));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        for (Long productId : productIds) {
            ProductEntity p = ProductEntity.builder()
                    .id(productId).name("Producto " + productId).price(CURRENT_PRODUCT_PRICE).active(true).build();
            when(productRepository.findById(productId)).thenReturn(Optional.of(p));
            when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, productId))
                    .thenReturn(List.of(TransactionDetailEntity.builder()
                            .product(p)
                            .quantity(new BigDecimal("1"))
                            .unitPrice(new BigDecimal("1000.0000"))
                            .build()));
        }

        // Return every product in full, one call per product: that is the whole sale back.
        for (Long productId : productIds) {
            service.createReturn(new CreateSaleReturnRequest(
                    SALE_ID, productId, WAREHOUSE_ID, new BigDecimal("1"), REFUND_METHOD_ID, "Fallado", USER_ID));
        }

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, times(productIds.size())).save(captor.capture());

        BigDecimal totalRefunded = captor.getAllValues().stream()
                .map(TransactionEntity::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();

        // Prorating a non-terminating factor across lines cannot land on the exact total:
        // each line is rounded to the money scale independently. What matters is that the
        // residue stays far below one cent, so no real money is created or lost.
        BigDecimal residue = totalRefunded.subtract(saleTotal).abs();
        assertThat(residue).isLessThan(new BigDecimal("0.01"));
        assertThat(totalRefunded).isEqualByComparingTo("2000.0001");
    }

    @Test
    @DisplayName("Should refund the surcharged price on a sale whose product carried a payment-method rule")
    void shouldRefundSurchargedPriceFromProductRule() {
        stubCommonLookups(true);
        // Cigarettes at 1000 with a +10% card rule were stored at 1100 a unit: the
        // surcharge rides on the line's unit price, so nothing here needs to know
        // about the rule — the existing formula reads what was actually charged.
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("2"), new BigDecimal("1100.0000"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("1")));

        // One unit back refunds 1100, the surcharged price — not the 1000 list price.
        assertThat(savedReturn().getTotal()).isEqualByComparingTo("-1100.0000");
    }

    // --- lookups / not found ---

    @Test
    @DisplayName("Should throw when there is no authenticated user")
    void shouldThrowWhenUserNotFound() {
        when(authenticatedUserService.requireCurrentUser())
                .thenThrow(new ForbiddenException("Tenés que iniciar sesión para realizar esta acción."));

        assertThrows(ForbiddenException.class, () -> service.createReturn(request(BigDecimal.ONE)));
        verify(transactionRepository, never()).save(any());
    }

    // --- validations ---

    @Test
    @DisplayName("Should reject a return into an inactive warehouse")
    void shouldRejectInactiveWarehouse() {
        stubCommonLookups(false);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.createReturn(request(BigDecimal.ONE))
        );

        assertEquals("El depósito seleccionado está inactivo.", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(inventoryMovementService, never())
                .registerAdjustmentMovement(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject when the original transaction is not a sale")
    void shouldRejectWhenOriginalIsNotASale() {
        stubCommonLookups(true);

        TransactionEntity tx = TransactionEntity.builder()
                .id(SALE_TX_ID)
                .type(TransactionType.PURCHASE)
                .build();
        SaleEntity sale = SaleEntity.builder().id(SALE_ID).transaction(tx).build();
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(sale));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.createReturn(request(BigDecimal.ONE))
        );

        assertEquals("La transacción original no es una venta.", ex.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a product that was not part of the sale")
    void shouldRejectProductNotInSale() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // No lines for this product in the sale.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of());

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.createReturn(request(BigDecimal.ONE))
        );

        assertEquals("El producto \"Pollo entero\" no forma parte de esta venta.", ex.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject when everything sold was already returned")
    void shouldRejectWhenFullyReturned() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // Sold 5.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));

        // A prior return transaction that already gave back all 5.
        TransactionEntity returnTx = TransactionEntity.builder().id(200L).build();
        SaleReturnEntity prior = SaleReturnEntity.builder().id(1L).transaction(returnTx).build();
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of(prior));
        when(transactionDetailRepository.findByTransactionIdAndProductId(200L, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.createReturn(request(BigDecimal.ONE))
        );

        assertEquals("Ya se devolvió toda la cantidad vendida de \"Pollo entero\".", ex.getMessage());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a quantity beyond what remains returnable")
    void shouldRejectQuantityBeyondRemaining() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // Sold 5.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));

        // Already returned 3 in a prior return, so only 2 remain.
        TransactionEntity returnTx = TransactionEntity.builder().id(200L).build();
        SaleReturnEntity prior = SaleReturnEntity.builder().id(1L).transaction(returnTx).build();
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of(prior));
        when(transactionDetailRepository.findByTransactionIdAndProductId(200L, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("3"))));

        // Asking for 3 when only 2 remain.
        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> service.createReturn(request(new BigDecimal("3")))
        );

        assertEquals(
                "No podés devolver esa cantidad de \"Pollo entero\". Se vendieron 5 y ya se devolvieron 3.",
                ex.getMessage()
        );
        verify(transactionRepository, never()).save(any());
        verify(inventoryMovementService, never())
                .registerAdjustmentMovement(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should allow returning exactly what remains after a partial return")
    void shouldAllowReturningExactlyWhatRemains() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        // Sold 5.
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));

        // Already returned 3, so 2 remain.
        TransactionEntity returnTx = TransactionEntity.builder().id(200L).build();
        SaleReturnEntity prior = SaleReturnEntity.builder().id(1L).transaction(returnTx).build();
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of(prior));
        when(transactionDetailRepository.findByTransactionIdAndProductId(200L, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("3"))));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Returning exactly the remaining 2 is allowed.
        CreateSaleReturnResponse response = service.createReturn(request(new BigDecimal("2")));

        assertEquals(new BigDecimal("2"), response.quantity());
        verify(saleReturnRepository).save(any(SaleReturnEntity.class));
        verify(inventoryMovementService).registerAdjustmentMovement(
                any(), any(), any(), eq(MovementType.RETURN_IN), eq(new BigDecimal("2")), any());
    }

    // --- operator-facing text is composed on screen, never stored ---

    @Test
    @DisplayName("Should not freeze any operator-facing text on the return line")
    void shouldNotStoreALineDescription() {
        stubCommonLookups(true);
        when(saleRepository.findById(SALE_ID)).thenReturn(Optional.of(confirmedSale()));
        when(transactionDetailRepository.findByTransactionIdAndProductId(SALE_TX_ID, PRODUCT_ID))
                .thenReturn(List.of(soldLine(new BigDecimal("5"))));
        when(saleReturnRepository.findByOriginalSaleId(SALE_ID)).thenReturn(List.of());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReturn(request(new BigDecimal("2")));

        /*
         * The line used to carry the literal "Devolución". It is a type label, and the type is
         * already on the row, so the screen composes it with TRANSACTION_TYPE_LABELS. A stored
         * label is a label frozen in whatever language and wording it had the day it was
         * written — which is exactly how the movement origins ended up in English.
         */
        assertThat(savedReturn().getDetails().getFirst().getDescription()).isNull();
    }
}