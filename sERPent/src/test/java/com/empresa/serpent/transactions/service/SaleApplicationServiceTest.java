package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
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
import com.empresa.serpent.inventory.repository.WarehouseRepository;
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

import static com.empresa.serpent.support.TestEntityFactory.paymentMethod;
import static com.empresa.serpent.support.TestEntityFactory.product;
import static com.empresa.serpent.support.TestEntityFactory.user;
import static com.empresa.serpent.support.TestEntityFactory.warehouse;
import static com.empresa.serpent.support.TestRequestFactory.createSaleRequestOneItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    /**
     * Returns an empty list by default, so a sale whose products carry no
     * payment-method rule needs no stubbing here.
     */
    @Mock
    private ProductPaymentAdjustmentRepository productPaymentAdjustmentRepository;

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
        UserEntity createdBy = user(1L);
        PaymentMethodEntity paymentMethod = paymentMethod(1L, "Cash");
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = createSaleRequestOneItem();

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(false);
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(100L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(200L);
            return sale;
        });

        CreateSaleResponse response = saleApplicationService.createSale(request);

        assertThat(response.transactionId()).isEqualTo(100L);
        assertThat(response.saleId()).isEqualTo(200L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.message()).isEqualTo("Sale created successfully");

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getType()).isEqualTo(TransactionType.SALE);
        assertThat(savedTransaction.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(savedTransaction.getPaymentMethod()).isEqualTo(paymentMethod);
        assertThat(savedTransaction.getCreatedByUserEntity()).isEqualTo(createdBy);
        assertThat(savedTransaction.getDescription()).isEqualTo("Venta mostrador");
        assertThat(savedTransaction.getTotal()).isEqualByComparingTo("4500.0000");
        assertThat(savedTransaction.getDetails()).hasSize(1);

        TransactionDetailEntity detail = savedTransaction.getDetails().get(0);
        assertThat(detail.getProduct()).isEqualTo(product);
        assertThat(detail.getDescription()).isEqualTo("Pollo entero");
        assertThat(detail.getQuantity()).isEqualByComparingTo("1.000");
        assertThat(detail.getUnitPrice()).isEqualByComparingTo("4500.0000");
        assertThat(detail.getSubtotal()).isEqualByComparingTo("4500.0000");

        verify(stockValidationService).validateSaleItemsStock(anyList(), eq(1L));
        verify(inventoryMovementService).registerSaleMovements(any(TransactionEntity.class), eq(warehouse));
    }

    /**
     * Inverted deliberately: a sale without a payment method used to be a supported
     * case, but a payment method is now required — the per-product surcharge rules key
     * off it, so a sale with no method cannot be priced. Checked inside the service
     * rather than only via {@code @NotNull}, because the offline sync path bypasses
     * Bean Validation.
     */
    @Test
    @DisplayName("Should reject a sale with no payment method")
    void shouldRejectSaleWithoutPaymentMethod() {
        CreateSaleRequest request = new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                "A-0001-00000001",
                null,
                1L,
                1L,
                "Venta sin payment method",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                null,
                                new BigDecimal("1.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> saleApplicationService.createSale(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tenés que indicar el método de pago de la venta.");

        // Rejected before the lookup, so nothing is read or written.
        verify(paymentMethodRepository, never()).findById(any());
        verify(transactionRepository, never()).save(any());
        verify(saleRepository, never()).save(any());
        verify(inventoryMovementService, never()).registerSaleMovements(any(), any());
    }

    @Test
    @DisplayName("Should create sale successfully with multiple items")
    void shouldCreateSaleSuccessfullyWithMultipleItems() {
        UserEntity createdBy = user(1L);
        PaymentMethodEntity paymentMethod = paymentMethod(1L, "Cash");
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product1 = product(10L, "Pollo entero");
        ProductEntity product2 = product(20L, "Pata muslo");

        CreateSaleRequest request = new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                "A-0001-00000002",
                1L,
                1L,
                1L,
                "Venta múltiple",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                null,
                                new BigDecimal("2.000"),
                                new BigDecimal("4500.0000")
                        ),
                        new CreateSaleItemRequest(
                                20L,
                                null,
                                new BigDecimal("1.000"),
                                new BigDecimal("3200.0000")
                        )
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(saleRepository.existsByInvoiceNumber("A-0001-00000002")).willReturn(false);
        given(productRepository.findByIdIn(List.of(10L, 20L))).willReturn(List.of(product1, product2));

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(102L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(202L);
            return sale;
        });

        CreateSaleResponse response = saleApplicationService.createSale(request);

        assertThat(response.transactionId()).isEqualTo(102L);
        assertThat(response.saleId()).isEqualTo(202L);

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getDetails()).hasSize(2);
        assertThat(savedTransaction.getTotal()).isEqualByComparingTo("12200.0000");

        assertThat(savedTransaction.getDetails().get(0).getSubtotal()).isEqualByComparingTo("9000.0000");
        assertThat(savedTransaction.getDetails().get(1).getSubtotal()).isEqualByComparingTo("3200.0000");
    }

    @Test
    @DisplayName("Should use product name when item description is blank")
    void shouldUseProductNameWhenItemDescriptionIsBlank() {
        UserEntity createdBy = user(1L);
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                null,
                1L,
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

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(103L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(203L);
            return sale;
        });

        saleApplicationService.createSale(request);

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getDetails()).hasSize(1);
        assertThat(savedTransaction.getDetails().get(0).getDescription()).isEqualTo("Pollo entero");
    }

    @Test
    @DisplayName("Should use item description when provided")
    void shouldUseItemDescriptionWhenProvided() {
        UserEntity createdBy = user(1L);
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                null,
                1L,
                1L,
                1L,
                "Venta con descripción custom",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                "Pollo entero premium",
                                new BigDecimal("1.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(104L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(204L);
            return sale;
        });

        saleApplicationService.createSale(request);

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getDetails()).hasSize(1);
        assertThat(savedTransaction.getDetails().get(0).getDescription()).isEqualTo("Pollo entero premium");
    }

    @Test
    @DisplayName("Should create sale without invoice number and skip duplicate check")
    void shouldCreateSaleWithoutInvoiceNumberAndSkipDuplicateCheck() {
        UserEntity createdBy = user(1L);
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                null,
                1L,
                1L,
                1L,
                "Venta sin factura",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                null,
                                new BigDecimal("1.000"),
                                new BigDecimal("4500.0000")
                        )
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(105L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(205L);
            return sale;
        });

        saleApplicationService.createSale(request);

        verify(saleRepository, never()).existsByInvoiceNumber(any());
    }

    @Test
    @DisplayName("Should calculate total correctly with decimal quantities")
    void shouldCalculateTotalCorrectlyWithDecimalQuantities() {
        UserEntity createdBy = user(1L);
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product1 = product(10L, "Pollo entero");
        ProductEntity product2 = product(20L, "Pata muslo");

        CreateSaleRequest request = new CreateSaleRequest(
                100L,
                "Consumidor Final",
                "12345678",
                null,
                1L,
                1L,
                1L,
                "Venta decimal",
                List.of(
                        new CreateSaleItemRequest(
                                10L,
                                null,
                                new BigDecimal("2.500"),
                                new BigDecimal("1000.0000")
                        ),
                        new CreateSaleItemRequest(
                                20L,
                                null,
                                new BigDecimal("1.250"),
                                new BigDecimal("500.0000")
                        )
                )
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(productRepository.findByIdIn(List.of(10L, 20L))).willReturn(List.of(product1, product2));

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(106L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(206L);
            return sale;
        });

        saleApplicationService.createSale(request);

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getDetails()).hasSize(2);
        assertThat(savedTransaction.getDetails().get(0).getSubtotal()).isEqualByComparingTo("2500.0000");
        assertThat(savedTransaction.getDetails().get(1).getSubtotal()).isEqualByComparingTo("625.0000");
        assertThat(savedTransaction.getTotal()).isEqualByComparingTo("3125.0000");
    }

    @Test
    @DisplayName("Should validate stock with expected items and warehouse")
    void shouldValidateStockWithExpectedItemsAndWarehouse() {

        UserEntity createdBy = user(1L);
        PaymentMethodEntity paymentMethod = paymentMethod(1L, "Cash");
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = createSaleRequestOneItem();

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));
        given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(false);

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(107L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(207L);
            return sale;
        });

        saleApplicationService.createSale(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockCheckItemRequest>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(stockValidationService).validateSaleItemsStock(itemsCaptor.capture(), eq(1L));

        List<StockCheckItemRequest> capturedItems = itemsCaptor.getValue();

        assertThat(capturedItems).hasSize(1);
        assertThat(capturedItems.get(0).productId()).isEqualTo(10L);
        assertThat(capturedItems.get(0).quantity()).isEqualByComparingTo("1.000");
    }

    @Test
    @DisplayName("Should register inventory movements with saved transaction and warehouse")
    void shouldRegisterInventoryMovementsWithSavedTransactionAndWarehouse() {

        UserEntity createdBy = user(1L);
        PaymentMethodEntity paymentMethod = paymentMethod(1L, "Cash");
        WarehouseEntity warehouse = warehouse(1L, "Main Warehouse", true);
        ProductEntity product = product(10L, "Pollo entero");

        CreateSaleRequest request = createSaleRequestOneItem();

        given(userRepository.findById(1L)).willReturn(Optional.of(createdBy));
        given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod));
        given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse));
        given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product));
        given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(false);

        given(transactionRepository.save(any(TransactionEntity.class))).willAnswer(invocation -> {
            TransactionEntity tx = invocation.getArgument(0);
            tx.setId(108L);
            return tx;
        });

        given(saleRepository.save(any(SaleEntity.class))).willAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            sale.setId(208L);
            return sale;
        });

        saleApplicationService.createSale(request);

        ArgumentCaptor<TransactionEntity> transactionCaptor =
                ArgumentCaptor.forClass(TransactionEntity.class);

        verify(inventoryMovementService)
                .registerSaleMovements(transactionCaptor.capture(), eq(warehouse));

        TransactionEntity capturedTransaction = transactionCaptor.getValue();

        assertThat(capturedTransaction.getId()).isEqualTo(108L);
        assertThat(capturedTransaction.getDetails()).hasSize(1);
        assertThat(capturedTransaction.getDetails().get(0).getProduct().getId()).isEqualTo(10L);
    }

    @Nested
    class Adjustments {

        /** One line of 2 x 5000 = 10000 subtotal, so the adjustment maths are easy to read. */
        private CreateSaleRequest requestWith(AdjustmentType type, BigDecimal value) {
            return new CreateSaleRequest(
                    100L, "Consumidor Final", "12345678", null, 1L, 1L, 1L, "Venta con ajuste",
                    List.of(new CreateSaleItemRequest(
                            10L, null, new BigDecimal("2.000"), new BigDecimal("5000.0000"))),
                    type, value
            );
        }

        private void stubHappyPath() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L))
                    .willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product(10L, "Pollo entero")));
            given(transactionRepository.save(any(TransactionEntity.class)))
                    .willAnswer(inv -> inv.getArgument(0));
            given(saleRepository.save(any(SaleEntity.class))).willAnswer(inv -> inv.getArgument(0));
        }

        private TransactionEntity savedTransaction() {
            ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
            verify(transactionRepository).save(captor.capture());
            return captor.getValue();
        }

        private SaleEntity savedSale() {
            ArgumentCaptor<SaleEntity> captor = ArgumentCaptor.forClass(SaleEntity.class);
            verify(saleRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("PERCENTAGE discount resolves to a negative amount and lowers the total")
        void percentageDiscount() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(AdjustmentType.PERCENTAGE, new BigDecimal("-10")));

            // 10000 - 10% = 9000
            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("9000.0000");

            SaleEntity sale = savedSale();
            assertThat(sale.getAdjustmentType()).isEqualTo(AdjustmentType.PERCENTAGE);
            assertThat(sale.getAdjustmentValue()).isEqualByComparingTo("-10");
            assertThat(sale.getAdjustmentAmount()).isEqualByComparingTo("-1000.0000");
        }

        @Test
        @DisplayName("PERCENTAGE surcharge resolves to a positive amount and raises the total")
        void percentageSurcharge() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(AdjustmentType.PERCENTAGE, new BigDecimal("10")));

            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("11000.0000");
            assertThat(savedSale().getAdjustmentAmount()).isEqualByComparingTo("1000.0000");
        }

        @Test
        @DisplayName("FIXED discount is taken as-is and lowers the total")
        void fixedDiscount() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(AdjustmentType.FIXED, new BigDecimal("-500.0000")));

            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("9500.0000");

            SaleEntity sale = savedSale();
            assertThat(sale.getAdjustmentType()).isEqualTo(AdjustmentType.FIXED);
            assertThat(sale.getAdjustmentAmount()).isEqualByComparingTo("-500.0000");
        }

        @Test
        @DisplayName("FIXED surcharge is taken as-is and raises the total")
        void fixedSurcharge() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(AdjustmentType.FIXED, new BigDecimal("500.0000")));

            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("10500.0000");
            assertThat(savedSale().getAdjustmentAmount()).isEqualByComparingTo("500.0000");
        }

        @Test
        @DisplayName("A sale with no adjustment freezes NONE and a zero amount")
        void noAdjustment() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(AdjustmentType.NONE, null));

            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("10000.0000");

            SaleEntity sale = savedSale();
            assertThat(sale.getAdjustmentType()).isEqualTo(AdjustmentType.NONE);
            assertThat(sale.getAdjustmentValue()).isEqualByComparingTo("0");
            assertThat(sale.getAdjustmentAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Omitting the adjustment entirely behaves like NONE")
        void omittedAdjustmentBehavesAsNone() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(null, null));

            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("10000.0000");
            assertThat(savedSale().getAdjustmentType()).isEqualTo(AdjustmentType.NONE);
        }

        @Test
        @DisplayName("A FIXED discount larger than the subtotal is rejected")
        void fixedDiscountBeyondSubtotalIsRejected() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L))
                    .willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product(10L, "Pollo entero")));

            // Discounting 15000 off a 10000 sale.
            assertThatThrownBy(() -> saleApplicationService.createSale(
                    requestWith(AdjustmentType.FIXED, new BigDecimal("-15000.0000"))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("El descuento aplicado no puede dejar el total de la venta en negativo.");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("An absurd percentage discount (over 100%) is caught by the same guard")
        void percentageDiscountOverOneHundredIsRejected() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L))
                    .willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product(10L, "Pollo entero")));

            // -150% of 10000 is -15000, so the total would land at -5000.
            assertThatThrownBy(() -> saleApplicationService.createSale(
                    requestWith(AdjustmentType.PERCENTAGE, new BigDecimal("-150"))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("El descuento aplicado no puede dejar el total de la venta en negativo.");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("A discount that lands the total exactly at zero is allowed")
        void discountToExactlyZeroIsAllowed() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(AdjustmentType.FIXED, new BigDecimal("-10000.0000")));

            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("A surcharge has no upper bound")
        void surchargeHasNoCap() {
            stubHappyPath();

            saleApplicationService.createSale(requestWith(AdjustmentType.PERCENTAGE, new BigDecimal("500")));

            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("60000.0000");
        }

        @Test
        @DisplayName("Choosing an adjustment type without a value is rejected")
        void typeWithoutValueIsRejected() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L))
                    .willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(productRepository.findByIdIn(List.of(10L))).willReturn(List.of(product(10L, "Pollo entero")));

            assertThatThrownBy(() -> saleApplicationService.createSale(
                    requestWith(AdjustmentType.PERCENTAGE, null)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Elegiste un tipo de ajuste pero no indicaste el valor.");

            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    class ProductPaymentRules {

        private static final Long CARD_ID = 1L;

        /** A cart of one product, so the per-line maths stay readable. */
        private CreateSaleRequest requestFor(Long productId, String quantity, String unitPrice) {
            return new CreateSaleRequest(
                    100L, "Consumidor Final", "12345678", null, CARD_ID, 1L, 1L, "Venta con tarjeta",
                    List.of(new CreateSaleItemRequest(
                            productId, null, new BigDecimal(quantity), new BigDecimal(unitPrice)))
            );
        }

        private ProductPaymentAdjustmentEntity rule(ProductEntity product, String percentage) {
            return ProductPaymentAdjustmentEntity.builder()
                    .product(product)
                    .paymentMethod(paymentMethod(CARD_ID, "Tarjeta"))
                    .adjustmentPercentage(new BigDecimal(percentage))
                    .active(true)
                    .build();
        }

        private void stubLookups(List<ProductEntity> products) {
            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(CARD_ID))
                    .willReturn(Optional.of(paymentMethod(CARD_ID, "Tarjeta")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(productRepository.findByIdIn(anyList())).willReturn(products);
            given(transactionRepository.save(any(TransactionEntity.class)))
                    .willAnswer(inv -> inv.getArgument(0));
            given(saleRepository.save(any(SaleEntity.class))).willAnswer(inv -> inv.getArgument(0));
        }

        private TransactionEntity savedTransaction() {
            ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
            verify(transactionRepository).save(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("A product surcharged for this payment method has its line marked up")
        void surchargeIsAppliedToTheLine() {
            ProductEntity cigarettes = product(10L, "Cigarrillos");
            stubLookups(List.of(cigarettes));
            given(productPaymentAdjustmentRepository
                    .findByPaymentMethodIdAndProductIdInAndActiveTrue(CARD_ID, List.of(10L)))
                    .willReturn(List.of(rule(cigarettes, "10")));

            saleApplicationService.createSale(requestFor(10L, "2.000", "1000.0000"));

            TransactionDetailEntity detail = savedTransaction().getDetails().getFirst();
            // The surcharge rides on the unit price, so the derived subtotal carries it too.
            assertThat(detail.getUnitPrice()).isEqualByComparingTo("1100.0000");
            assertThat(detail.getSubtotal()).isEqualByComparingTo("2200.0000");
            assertThat(savedTransaction().getTotal()).isEqualByComparingTo("2200.0000");
        }

        @Test
        @DisplayName("The same product is untouched when paid with a method it has no rule for")
        void noRuleForThisMethodLeavesThePriceAlone() {
            ProductEntity cigarettes = product(10L, "Cigarrillos");
            stubLookups(List.of(cigarettes));
            // Paid in cash: the card rule is simply not returned for this method.
            given(productPaymentAdjustmentRepository
                    .findByPaymentMethodIdAndProductIdInAndActiveTrue(CARD_ID, List.of(10L)))
                    .willReturn(List.of());

            saleApplicationService.createSale(requestFor(10L, "2.000", "1000.0000"));

            TransactionDetailEntity detail = savedTransaction().getDetails().getFirst();
            assertThat(detail.getUnitPrice()).isEqualByComparingTo("1000.0000");
            assertThat(detail.getSubtotal()).isEqualByComparingTo("2000.0000");
        }

        @Test
        @DisplayName("A negative percentage discounts the line instead of surcharging it")
        void negativePercentageDiscountsTheLine() {
            ProductEntity milk = product(10L, "Leche");
            stubLookups(List.of(milk));
            given(productPaymentAdjustmentRepository
                    .findByPaymentMethodIdAndProductIdInAndActiveTrue(CARD_ID, List.of(10L)))
                    .willReturn(List.of(rule(milk, "-5")));

            saleApplicationService.createSale(requestFor(10L, "2.000", "1000.0000"));

            TransactionDetailEntity detail = savedTransaction().getDetails().getFirst();
            assertThat(detail.getUnitPrice()).isEqualByComparingTo("950.0000");
            assertThat(detail.getSubtotal()).isEqualByComparingTo("1900.0000");
        }

        @Test
        @DisplayName("A rule touches only its own product, not the line next to it")
        void ruleDoesNotLeakToOtherLines() {
            ProductEntity cigarettes = product(10L, "Cigarrillos");
            ProductEntity bread = product(20L, "Pan");
            stubLookups(List.of(cigarettes, bread));
            given(productPaymentAdjustmentRepository
                    .findByPaymentMethodIdAndProductIdInAndActiveTrue(CARD_ID, List.of(10L, 20L)))
                    .willReturn(List.of(rule(cigarettes, "10")));

            CreateSaleRequest request = new CreateSaleRequest(
                    100L, "Consumidor Final", "12345678", null, CARD_ID, 1L, 1L, "Venta mixta",
                    List.of(
                            new CreateSaleItemRequest(10L, null, new BigDecimal("1.000"), new BigDecimal("1000.0000")),
                            new CreateSaleItemRequest(20L, null, new BigDecimal("2.000"), new BigDecimal("500.0000"))
                    )
            );

            saleApplicationService.createSale(request);

            TransactionEntity saved = savedTransaction();
            // Cigarettes surcharged to 1100; bread stays at 2 x 500.
            assertThat(saved.getDetails().get(0).getSubtotal()).isEqualByComparingTo("1100.0000");
            assertThat(saved.getDetails().get(1).getSubtotal()).isEqualByComparingTo("1000.0000");
            assertThat(saved.getTotal()).isEqualByComparingTo("2100.0000");
        }

        /**
         * The two adjustment layers must compose in order: the product rule marks the
         * line up first, then the sale-wide manual adjustment acts on that already
         * marked-up sum. A fixed global discount makes the order observable — inverting
         * it would give 1650 instead of 1700.
         */
        @Test
        @DisplayName("The product rule applies before the sale-wide manual adjustment")
        void productRuleAppliesBeforeTheSaleWideAdjustment() {
            ProductEntity cigarettes = product(10L, "Cigarrillos");
            stubLookups(List.of(cigarettes));
            given(productPaymentAdjustmentRepository
                    .findByPaymentMethodIdAndProductIdInAndActiveTrue(CARD_ID, List.of(10L)))
                    .willReturn(List.of(rule(cigarettes, "10")));

            CreateSaleRequest request = new CreateSaleRequest(
                    100L, "Consumidor Final", "12345678", null, CARD_ID, 1L, 1L, "Venta con ambas capas",
                    List.of(new CreateSaleItemRequest(
                            10L, null, new BigDecimal("2.000"), new BigDecimal("1000.0000"))),
                    AdjustmentType.FIXED, new BigDecimal("-500.0000")
            );

            saleApplicationService.createSale(request);

            TransactionEntity saved = savedTransaction();
            // Layer 1: 2 x 1000 +10% = 2200 stored on the line.
            assertThat(saved.getDetails().getFirst().getSubtotal()).isEqualByComparingTo("2200.0000");
            // Layer 2: -500 off the marked-up 2200 = 1700 (not 1650, which is the inverted order).
            assertThat(saved.getTotal()).isEqualByComparingTo("1700.0000");

            ArgumentCaptor<SaleEntity> saleCaptor = ArgumentCaptor.forClass(SaleEntity.class);
            verify(saleRepository).save(saleCaptor.capture());
            assertThat(saleCaptor.getValue().getAdjustmentAmount()).isEqualByComparingTo("-500.0000");
        }
    }

    @Nested
    class ErrorCases {

        @Test
        @DisplayName("Should throw when user is not found")
        void shouldThrowWhenUserIsNotFound() {
            CreateSaleRequest request = createSaleRequestOneItem();

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
            CreateSaleRequest request = createSaleRequestOneItem();

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
            CreateSaleRequest request = createSaleRequestOneItem();

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
            CreateSaleRequest request = createSaleRequestOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", false)));

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("El depósito seleccionado está inactivo.");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when invoice number already exists")
        void shouldThrowWhenInvoiceNumberAlreadyExists() {
            CreateSaleRequest request = createSaleRequestOneItem();

            given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
            given(paymentMethodRepository.findById(1L)).willReturn(Optional.of(paymentMethod(1L, "Cash")));
            given(warehouseRepository.findById(1L)).willReturn(Optional.of(warehouse(1L, "Central", true)));
            given(saleRepository.existsByInvoiceNumber("A-0001-00000001")).willReturn(true);

            assertThatThrownBy(() -> saleApplicationService.createSale(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Ya existe una venta con el comprobante \"A-0001-00000001\".");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when product is not found")
        void shouldThrowWhenProductIsNotFound() {
            CreateSaleRequest request = createSaleRequestOneItem();

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
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("El precio de un ítem es obligatorio.");

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
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("El precio de un ítem no puede ser negativo.");

            verify(transactionRepository, never()).save(any());
            verify(saleRepository, never()).save(any());
        }
    }
}