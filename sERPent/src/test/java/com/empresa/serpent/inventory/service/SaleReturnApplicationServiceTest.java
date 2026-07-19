package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateSaleReturnRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateSaleReturnResponse;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.SaleReturnRepository;
import com.empresa.serpent.transactions.repository.TransactionDetailRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private UserRepository userRepository;
    @Mock private InventoryMovementService inventoryMovementService;

    private SaleReturnApplicationService service;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long WAREHOUSE_ID = 3L;
    private static final Long SALE_ID = 5L;
    private static final Long SALE_TX_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new SaleReturnApplicationService(
                transactionRepository,
                transactionDetailRepository,
                saleRepository,
                saleReturnRepository,
                productRepository,
                warehouseRepository,
                userRepository,
                inventoryMovementService
        );
    }

    private CreateSaleReturnRequest request(BigDecimal quantity) {
        return new CreateSaleReturnRequest(SALE_ID, PRODUCT_ID, WAREHOUSE_ID, quantity, "Fallado", USER_ID);
    }

    private UserEntity user() {
        return UserEntity.builder().id(USER_ID).username("admin").build();
    }

    private ProductEntity product() {
        return ProductEntity.builder().id(PRODUCT_ID).name("Pollo entero").active(true).build();
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

    private TransactionDetailEntity soldLine(BigDecimal qty) {
        return TransactionDetailEntity.builder().product(product()).quantity(qty).build();
    }

    /** Wires the happy-path lookups shared by most tests. */
    private void stubCommonLookups(boolean warehouseActive) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse(warehouseActive)));
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
                any(String.class)
        );
    }

    // --- lookups / not found ---

    @Test
    @DisplayName("Should throw when the user is not found")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.createReturn(request(BigDecimal.ONE)));
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
}