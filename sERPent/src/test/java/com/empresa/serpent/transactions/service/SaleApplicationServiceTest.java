package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.inventory.service.StockValidationService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleApplicationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockValidationService stockValidationService;

    @Mock
    private InventoryMovementService inventoryMovementService;

    @InjectMocks
    private SaleApplicationService saleApplicationService;

    @Test
    @DisplayName("Should create sale successfully")
    void shouldCreateSaleSuccessfully() {

        UserEntity user = user(1L);
        PaymentMethodEntity paymentMethod = paymentMethod(1L, "Cash");
        WarehouseEntity warehouse = warehouse(1L, "Central", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                "A-0001-00000001",
                1L,
                1L,
                1L,
                "Venta mostrador",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                null,
                                new BigDecimal("2.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(false);
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

        given(transactionRepository.save(any(TransactionEntity.class)))
                .willAnswer(invocation -> {
                    TransactionEntity tx = invocation.getArgument(0);
                    tx.setId(200L);
                    return tx;
                });

        given(saleRepository.save(any(SaleEntity.class)))
                .willAnswer(invocation -> {
                    SaleEntity sale = invocation.getArgument(0);
                    sale.setId(300L);
                    return sale;
                });

        CreateSaleResponse result = saleApplicationService.createSale(request);

        assertThat(result.transactionId()).isEqualTo(200L);
        assertThat(result.saleId()).isEqualTo(300L);
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.message()).isEqualTo("Sale created successfully");

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getType()).isEqualTo(TransactionType.SALE);
        assertThat(savedTransaction.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(savedTransaction.getPaymentMethod()).isEqualTo(paymentMethod);
        assertThat(savedTransaction.getCreatedByUserEntity()).isEqualTo(user);
        assertThat(savedTransaction.getDescription()).isEqualTo("Venta mostrador");
        assertThat(savedTransaction.getTotal()).isEqualByComparingTo("9000.0000");
        assertThat(savedTransaction.getDetails()).hasSize(1);

        assertThat(savedTransaction.getDetails().get(0).getProduct()).isEqualTo(product);
        assertThat(savedTransaction.getDetails().get(0).getDescription()).isEqualTo("Pollo entero");
        assertThat(savedTransaction.getDetails().get(0).getQuantity()).isEqualByComparingTo("2.000");
        assertThat(savedTransaction.getDetails().get(0).getUnitPrice()).isEqualByComparingTo("4500.0000");
        assertThat(savedTransaction.getDetails().get(0).getSubtotal()).isEqualByComparingTo("9000.0000");

        ArgumentCaptor<SaleEntity> saleCaptor = ArgumentCaptor.forClass(SaleEntity.class);
        verify(saleRepository).save(saleCaptor.capture());

        SaleEntity savedSale = saleCaptor.getValue();
        assertThat(savedSale.getCustomerId()).isEqualTo(100L);
        assertThat(savedSale.getCustomerName()).isEqualTo("Consumidor Final");
        assertThat(savedSale.getCustomerDocument()).isEqualTo("12345678");
        assertThat(savedSale.getInvoiceNumber()).isEqualTo("A-0001-00000001");
        assertThat(savedSale.getTaxTotal()).isEqualByComparingTo("0");

        verify(stockValidationService).validateSaleItemsStock(anyList(), eq(1L));
        verify(inventoryMovementService).registerSaleMovements(any(TransactionEntity.class), eq(warehouse));
    }

    @Test
    @DisplayName("Should use product name when item description is blank")
    void shouldUseProductNameWhenItemDescriptionIsBlank() {

        UserEntity user = user(1L);
        WarehouseEntity warehouse = warehouse(1L, "Central", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = new CreateSaleRequest(
                null,
                "Consumidor Final",
                null,
                null,
                null,
                1L,
                1L,
                "Venta simple",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                "   ",
                                new BigDecimal("1.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

        given(transactionRepository.save(any(TransactionEntity.class)))
                .willAnswer(invocation -> {
                    TransactionEntity tx = invocation.getArgument(0);
                    tx.setId(200L);
                    return tx;
                });

        given(saleRepository.save(any(SaleEntity.class)))
                .willAnswer(invocation -> {
                    SaleEntity sale = invocation.getArgument(0);
                    sale.setId(300L);
                    return sale;
                });

        saleApplicationService.createSale(request);

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getDetails()).hasSize(1);
        assertThat(savedTransaction.getDetails().get(0).getDescription()).isEqualTo("Pollo entero");
    }

    @Nested
    class ErrorCases {

        @Test
        @DisplayName("Should throw when user is not found")
        void shouldThrowWhenUserIsNotFound() {

            CreateSaleRequest request = requestWithOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: 1");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
            verify(inventoryMovementService, never()).registerSaleMovements(any(), any());
        }

        @Test
        @DisplayName("Should throw when payment method is not found")
        void shouldThrowWhenPaymentMethodIsNotFound() {

            CreateSaleRequest request = requestWithOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Payment method not found: 1");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when warehouse is not found")
        void shouldThrowWhenWarehouseIsNotFound() {

            CreateSaleRequest request = requestWithOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Warehouse not found: 1");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when warehouse is inactive")
        void shouldThrowWhenWarehouseIsInactive() {

            CreateSaleRequest request = requestWithOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", false)));

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Warehouse is inactive: 1");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when invoice number already exists")
        void shouldThrowWhenInvoiceNumberAlreadyExists() {

            CreateSaleRequest request = requestWithOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(true);

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invoice number already exists: A-0001-00000001");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when product is not found")
        void shouldThrowWhenProductIsNotFound() {

            CreateSaleRequest request = requestWithOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(false);
            given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of());

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Product not found: 10");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when item unit price is null")
        void shouldThrowWhenItemUnitPriceIsNull() {

            CreateSaleRequest request = new CreateSaleRequest(
                    100L,
                    "Consumidor Final",
                    "12345678",
                    "A-0001-00000001",
                    1L,
                    1L,
                    1L,
                    "Venta mostrador",
                    List.of(
                            new CreateSaleItemRequest(
                                    10L,
                                    null,
                                    new BigDecimal("1.000"),
                                    null
                            )
                    )
            );

            ProductEntity product = product(10L, "Pollo entero");

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(false);
            given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Item unitPrice cannot be null");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when item unit price is negative")
        void shouldThrowWhenItemUnitPriceIsNegative() {

            CreateSaleRequest request = new CreateSaleRequest(
                    100L,
                    "Consumidor Final",
                    "12345678",
                    "A-0001-00000001",
                    1L,
                    1L,
                    1L,
                    "Venta mostrador",
                    List.of(
                            new CreateSaleItemRequest(
                                    10L,
                                    null,
                                    new BigDecimal("1.000"),
                                    new BigDecimal("-1.0000")
                            )
                    )
            );

            ProductEntity product = product(10L, "Pollo entero");

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(false);
            given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Item unitPrice cannot be negative");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }
    }

    private CreateSaleRequest requestWithOneItem() {
        return new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                "A-0001-00000001",
                1L,
                1L,
                1L,
                "Venta mostrador",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                null,
                                new BigDecimal("1.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );
    }

    private UserEntity user(Long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setName("Admin");
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }

    private PaymentMethodEntity paymentMethod(Long id, String name) {
        PaymentMethodEntity pm = new PaymentMethodEntity();
        pm.setId(id);
        pm.setName(name);
        pm.setActive(true);
        return pm;
    }

    private WarehouseEntity warehouse(Long id, String name, boolean active) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setName(name);
        warehouse.setActive(active);
        return warehouse;
    }

    private ProductEntity product(Long id, String name) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal("1000.0000"));
        product.setActive(true);
        return product;
    }
}