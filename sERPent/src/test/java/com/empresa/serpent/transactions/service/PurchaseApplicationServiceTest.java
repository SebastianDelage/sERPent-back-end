package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.catalog.domain.SupplierEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.PurchaseEntity;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseApplicationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private InventoryMovementService inventoryMovementService;

    @InjectMocks
    private PurchaseApplicationService purchaseApplicationService;

    @Test
    void createPurchase_shouldCreatePurchaseSuccessfully_andRegisterInventoryMovements() {
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                1L,
                1L,
                1L,
                1L,
                "PUR-002",
                "Compra de reposición",
                "Ingreso de mercadería para stock",
                List.of(
                        new CreatePurchaseItemRequest(1L, "Pollo entero", new BigDecimal("3.000"), new BigDecimal("3000.0000")),
                        new CreatePurchaseItemRequest(2L, "Pata muslo", new BigDecimal("2.000"), new BigDecimal("3200.0000"))
                )
        );

        UserEntity user = UserEntity.builder().id(1L).name("Admin").build();
        PaymentMethodEntity paymentMethod = PaymentMethodEntity.builder().id(1L).name("Cash").active(true).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).name("Proveedor Central").active(true).build();
        WarehouseEntity warehouse = WarehouseEntity.builder().id(1L).name("Depósito Central").active(true).build();

        ProductEntity product1 = ProductEntity.builder().id(1L).name("Pollo entero").price(new BigDecimal("2500")).active(true).build();
        ProductEntity product2 = ProductEntity.builder().id(2L).name("Pata muslo").price(new BigDecimal("1800")).active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(paymentMethod));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(purchaseRepository.existsByReceiptNumberIgnoreCase("PUR-002")).thenReturn(false);
        when(productRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(product1, product2));

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(4L);
            return tx;
        });

        when(purchaseRepository.save(any(PurchaseEntity.class))).thenAnswer(invocation -> {
            PurchaseEntity purchase = invocation.getArgument(0);
            purchase.setId(2L);
            return purchase;
        });

        CreatePurchaseResponse response = purchaseApplicationService.createPurchase(request);

        assertNotNull(response);
        assertEquals(4L, response.transactionId());
        assertEquals(2L, response.purchaseId());
        assertEquals("CONFIRMED", response.status());
        assertEquals("Purchase created successfully", response.message());

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertEquals(TransactionType.PURCHASE, savedTransaction.getType());
        assertEquals(TransactionStatus.CONFIRMED, savedTransaction.getStatus());
        assertEquals(0, savedTransaction.getTotal().compareTo(new BigDecimal("15400.0000")));
        assertEquals(2, savedTransaction.getDetails().size());

        assertEquals(0, savedTransaction.getDetails().get(0).getSubtotal().compareTo(new BigDecimal("9000.0000")));
        assertEquals(0, savedTransaction.getDetails().get(1).getSubtotal().compareTo(new BigDecimal("6400.0000")));

        verify(inventoryMovementService).registerPurchaseMovements(any(TransactionEntity.class), eq(warehouse));
    }

    @Test
    void createPurchase_shouldThrowWhenUserDoesNotExist() {
        CreatePurchaseRequest request = baseRequest();

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> purchaseApplicationService.createPurchase(request)
        );

        assertEquals("User not found: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(purchaseRepository, never()).save(any());
        verify(inventoryMovementService, never()).registerPurchaseMovements(any(), any());
    }

    @Test
    void createPurchase_shouldThrowWhenSupplierIsInactive() {
        CreatePurchaseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(false).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> purchaseApplicationService.createPurchase(request)
        );

        assertEquals("Supplier is inactive: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void createPurchase_shouldThrowWhenWarehouseIsInactive() {
        CreatePurchaseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        WarehouseEntity warehouse = WarehouseEntity.builder().id(1L).active(false).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> purchaseApplicationService.createPurchase(request)
        );

        assertEquals("Warehouse is inactive: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void createPurchase_shouldThrowWhenReceiptNumberAlreadyExists() {
        CreatePurchaseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        WarehouseEntity warehouse = WarehouseEntity.builder().id(1L).active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(purchaseRepository.existsByReceiptNumberIgnoreCase("PUR-002")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> purchaseApplicationService.createPurchase(request)
        );

        assertEquals("Receipt number already exists: PUR-002", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void createPurchase_shouldThrowWhenProductDoesNotExist() {
        CreatePurchaseRequest request = baseRequest();

        UserEntity user = UserEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        WarehouseEntity warehouse = WarehouseEntity.builder().id(1L).active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(purchaseRepository.existsByReceiptNumberIgnoreCase("PUR-002")).thenReturn(false);
        when(productRepository.findByIdIn(List.of(1L))).thenReturn(List.of());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> purchaseApplicationService.createPurchase(request)
        );

        assertEquals("Product not found: 1", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void createPurchase_shouldThrowWhenItemUnitPriceIsNegative() {
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                1L,
                null,
                1L,
                1L,
                "PUR-005",
                "Compra inválida",
                null,
                List.of(
                        new CreatePurchaseItemRequest(1L, "Pollo entero", new BigDecimal("2.000"), new BigDecimal("-100.0000"))
                )
        );

        UserEntity user = UserEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        WarehouseEntity warehouse = WarehouseEntity.builder().id(1L).active(true).build();
        ProductEntity product = ProductEntity.builder().id(1L).name("Pollo entero").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(purchaseRepository.existsByReceiptNumberIgnoreCase("PUR-005")).thenReturn(false);
        when(productRepository.findByIdIn(List.of(1L))).thenReturn(List.of(product));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> purchaseApplicationService.createPurchase(request)
        );

        assertEquals("Item unitPrice cannot be negative", ex.getMessage());
        verify(transactionRepository, never()).save(any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void createPurchase_shouldAllowNullReceiptNumber() {
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                1L,
                null,
                1L,
                1L,
                null,
                "Compra sin comprobante",
                null,
                List.of(
                        new CreatePurchaseItemRequest(1L, "Pollo entero", new BigDecimal("1.000"), new BigDecimal("3000.0000"))
                )
        );

        UserEntity user = UserEntity.builder().id(1L).build();
        SupplierEntity supplier = SupplierEntity.builder().id(1L).active(true).build();
        WarehouseEntity warehouse = WarehouseEntity.builder().id(1L).active(true).build();
        ProductEntity product = ProductEntity.builder().id(1L).name("Pollo entero").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findByIdIn(List.of(1L))).thenReturn(List.of(product));

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(5L);
            return tx;
        });

        when(purchaseRepository.save(any(PurchaseEntity.class))).thenAnswer(invocation -> {
            PurchaseEntity purchase = invocation.getArgument(0);
            purchase.setId(3L);
            return purchase;
        });

        CreatePurchaseResponse response = purchaseApplicationService.createPurchase(request);

        assertEquals(5L, response.transactionId());
        assertEquals(3L, response.purchaseId());
        verify(inventoryMovementService).registerPurchaseMovements(any(TransactionEntity.class), eq(warehouse));
        verify(purchaseRepository, never()).existsByReceiptNumberIgnoreCase(any());
    }

    private CreatePurchaseRequest baseRequest() {
        return new CreatePurchaseRequest(
                1L,
                null,
                1L,
                1L,
                "PUR-002",
                "Compra de reposición",
                "Notas",
                List.of(
                        new CreatePurchaseItemRequest(1L, "Pollo entero", new BigDecimal("2.000"), new BigDecimal("3000.0000"))
                )
        );
    }
}