package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.service.InventoryMovementService;
import com.empresa.serpent.inventory.service.StockValidationService;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.ProductTransformationEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.ProductTransformationRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateProductTransformationInputRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateProductTransformationOutputRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateProductTransformationRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateProductTransformationResponse;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductTransformationApplicationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ProductTransformationRepository productTransformationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockValidationService stockValidationService;

    @Mock
    private InventoryMovementService inventoryMovementService;

    @InjectMocks
    private ProductTransformationApplicationService productTransformationApplicationService;

    private UserEntity user;
    private WarehouseEntity warehouse;
    private ProductEntity inputProduct;
    private ProductEntity outputProduct1;
    private ProductEntity outputProduct2;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .id(1L)
                .name("Admin")
                .username("admin")
                .build();

        warehouse = WarehouseEntity.builder()
                .id(1L)
                .name("Depósito Central")
                .active(true)
                .build();

        inputProduct = ProductEntity.builder()
                .id(1L)
                .name("Pollo entero")
                .price(BigDecimal.valueOf(2500))
                .active(true)
                .unitOfMeasure(UnitOfMeasure.UNIT)
                .build();

        outputProduct1 = ProductEntity.builder()
                .id(2L)
                .name("Pata muslo")
                .price(BigDecimal.valueOf(1800))
                .active(true)
                .unitOfMeasure(UnitOfMeasure.KG)
                .build();

        outputProduct2 = ProductEntity.builder()
                .id(3L)
                .name("Milanesa de pollo")
                .price(BigDecimal.valueOf(3000))
                .active(true)
                .unitOfMeasure(UnitOfMeasure.KG)
                .build();
    }

    @Test
    void createTransformation_shouldCreateTransformationSuccessfully() {
        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                1L,
                1L,
                "Despiece de pollo entero",
                "Prueba inicial de transformation",
                List.of(
                        new CreateProductTransformationInputRequest(
                                1L,
                                "Pollo entero a transformar",
                                BigDecimal.valueOf(2)
                        )
                ),
                List.of(
                        new CreateProductTransformationOutputRequest(
                                2L,
                                "Pata muslo resultante",
                                BigDecimal.valueOf(2)
                        ),
                        new CreateProductTransformationOutputRequest(
                                3L,
                                "Milanesa resultante",
                                BigDecimal.valueOf(1)
                        )
                )
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findByIdIn(any())).thenReturn(List.of(inputProduct, outputProduct1, outputProduct2));

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity transaction = invocation.getArgument(0);
            transaction.setId(4L);
            return transaction;
        });

        when(productTransformationRepository.save(any(ProductTransformationEntity.class))).thenAnswer(invocation -> {
            ProductTransformationEntity transformation = invocation.getArgument(0);
            transformation.setId(1L);
            return transformation;
        });

        CreateProductTransformationResponse response =
                productTransformationApplicationService.createTransformation(request);

        assertThat(response).isNotNull();
        assertThat(response.transactionId()).isEqualTo(4L);
        assertThat(response.transformationId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.message()).isEqualTo("Transformation created successfully");

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());

        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getType()).isEqualTo(TransactionType.TRANSFORMATION);
        assertThat(savedTransaction.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(savedTransaction.getDescription()).isEqualTo("Despiece de pollo entero");
        assertThat(savedTransaction.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(savedTransaction.getCreatedByUserEntity()).isEqualTo(user);

        ArgumentCaptor<ProductTransformationEntity> transformationCaptor =
                ArgumentCaptor.forClass(ProductTransformationEntity.class);
        verify(productTransformationRepository).save(transformationCaptor.capture());

        ProductTransformationEntity savedTransformation = transformationCaptor.getValue();
        assertThat(savedTransformation.getWarehouse()).isEqualTo(warehouse);
        assertThat(savedTransformation.getNotes()).isEqualTo("Prueba inicial de transformation");
        assertThat(savedTransformation.getInputs()).hasSize(1);
        assertThat(savedTransformation.getOutputs()).hasSize(2);

        assertThat(savedTransformation.getInputs().get(0).getProduct()).isEqualTo(inputProduct);
        assertThat(savedTransformation.getInputs().get(0).getDescription()).isEqualTo("Pollo entero a transformar");
        assertThat(savedTransformation.getInputs().get(0).getQuantity()).isEqualByComparingTo("2");

        assertThat(savedTransformation.getOutputs().get(0).getTransformation()).isEqualTo(savedTransformation);
        assertThat(savedTransformation.getOutputs().get(1).getTransformation()).isEqualTo(savedTransformation);

        verify(stockValidationService).validateAvailableStock(1L, 1L, BigDecimal.valueOf(2));
        verify(inventoryMovementService).registerTransformationMovements(any(ProductTransformationEntity.class));
    }

    @Test
    void createTransformation_shouldThrowWhenUserNotFound() {
        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                99L,
                1L,
                "Despiece",
                null,
                List.of(new CreateProductTransformationInputRequest(1L, null, BigDecimal.ONE)),
                List.of(new CreateProductTransformationOutputRequest(2L, null, BigDecimal.ONE))
        );

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productTransformationApplicationService.createTransformation(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found: 99");

        verify(transactionRepository, never()).save(any());
        verify(productTransformationRepository, never()).save(any());
        verify(inventoryMovementService, never()).registerTransformationMovements(any());
    }

    @Test
    void createTransformation_shouldThrowWhenWarehouseNotFound() {
        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                1L,
                99L,
                "Despiece",
                null,
                List.of(new CreateProductTransformationInputRequest(1L, null, BigDecimal.ONE)),
                List.of(new CreateProductTransformationOutputRequest(2L, null, BigDecimal.ONE))
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(warehouseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productTransformationApplicationService.createTransformation(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Warehouse not found: 99");

        verify(transactionRepository, never()).save(any());
        verify(productTransformationRepository, never()).save(any());
        verify(inventoryMovementService, never()).registerTransformationMovements(any());
    }

    @Test
    void createTransformation_shouldThrowWhenWarehouseIsInactive() {
        WarehouseEntity inactiveWarehouse = WarehouseEntity.builder()
                .id(3L)
                .name("Depósito Inactivo")
                .active(false)
                .build();

        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                1L,
                3L,
                "Despiece",
                null,
                List.of(new CreateProductTransformationInputRequest(1L, null, BigDecimal.ONE)),
                List.of(new CreateProductTransformationOutputRequest(2L, null, BigDecimal.ONE))
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(warehouseRepository.findById(3L)).thenReturn(Optional.of(inactiveWarehouse));

        assertThatThrownBy(() -> productTransformationApplicationService.createTransformation(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Warehouse is inactive: 3");

        verify(transactionRepository, never()).save(any());
        verify(productTransformationRepository, never()).save(any());
        verify(inventoryMovementService, never()).registerTransformationMovements(any());
    }

    @Test
    void createTransformation_shouldThrowWhenInputProductNotFound() {
        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                1L,
                1L,
                "Despiece",
                null,
                List.of(new CreateProductTransformationInputRequest(999L, null, BigDecimal.ONE)),
                List.of(new CreateProductTransformationOutputRequest(2L, null, BigDecimal.ONE))
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findByIdIn(any())).thenReturn(List.of(outputProduct1));

        assertThatThrownBy(() -> productTransformationApplicationService.createTransformation(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product not found: 999");

        verify(transactionRepository, never()).save(any());
        verify(productTransformationRepository, never()).save(any());
        verify(inventoryMovementService, never()).registerTransformationMovements(any());
    }

    @Test
    void createTransformation_shouldThrowWhenOutputProductNotFound() {
        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                1L,
                1L,
                "Despiece",
                null,
                List.of(new CreateProductTransformationInputRequest(1L, null, BigDecimal.ONE)),
                List.of(new CreateProductTransformationOutputRequest(999L, null, BigDecimal.ONE))
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findByIdIn(any())).thenReturn(List.of(inputProduct));

        assertThatThrownBy(() -> productTransformationApplicationService.createTransformation(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product not found: 999");

        verify(transactionRepository, never()).save(any());
        verify(productTransformationRepository, never()).save(any());
        verify(inventoryMovementService, never()).registerTransformationMovements(any());
    }

    @Test
    void createTransformation_shouldValidateStockForEveryInput() {
        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                1L,
                1L,
                "Despiece múltiple",
                null,
                List.of(
                        new CreateProductTransformationInputRequest(1L, null, BigDecimal.valueOf(2)),
                        new CreateProductTransformationInputRequest(2L, null, BigDecimal.valueOf(1.5))
                ),
                List.of(
                        new CreateProductTransformationOutputRequest(3L, null, BigDecimal.valueOf(2.5))
                )
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findByIdIn(any())).thenReturn(List.of(inputProduct, outputProduct1, outputProduct2));

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity transaction = invocation.getArgument(0);
            transaction.setId(4L);
            return transaction;
        });

        when(productTransformationRepository.save(any(ProductTransformationEntity.class))).thenAnswer(invocation -> {
            ProductTransformationEntity transformation = invocation.getArgument(0);
            transformation.setId(1L);
            return transformation;
        });

        productTransformationApplicationService.createTransformation(request);

        verify(stockValidationService).validateAvailableStock(1L, 1L, BigDecimal.valueOf(2));
        verify(stockValidationService).validateAvailableStock(2L, 1L, BigDecimal.valueOf(1.5));
        verify(inventoryMovementService).registerTransformationMovements(any(ProductTransformationEntity.class));
    }

    @Test
    void createTransformation_shouldUseProductNameWhenLineDescriptionIsBlank() {
        CreateProductTransformationRequest request = new CreateProductTransformationRequest(
                1L,
                1L,
                "Despiece",
                "Notas",
                List.of(
                        new CreateProductTransformationInputRequest(1L, "   ", BigDecimal.ONE)
                ),
                List.of(
                        new CreateProductTransformationOutputRequest(2L, null, BigDecimal.ONE)
                )
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findByIdIn(any())).thenReturn(List.of(inputProduct, outputProduct1));

        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(invocation -> {
            TransactionEntity transaction = invocation.getArgument(0);
            transaction.setId(4L);
            return transaction;
        });

        when(productTransformationRepository.save(any(ProductTransformationEntity.class))).thenAnswer(invocation -> {
            ProductTransformationEntity transformation = invocation.getArgument(0);
            transformation.setId(1L);
            return transformation;
        });

        productTransformationApplicationService.createTransformation(request);

        ArgumentCaptor<ProductTransformationEntity> transformationCaptor =
                ArgumentCaptor.forClass(ProductTransformationEntity.class);
        verify(productTransformationRepository).save(transformationCaptor.capture());

        ProductTransformationEntity transformation = transformationCaptor.getValue();
        assertThat(transformation.getInputs().get(0).getDescription()).isEqualTo("Pollo entero");
        assertThat(transformation.getOutputs().get(0).getDescription()).isEqualTo("Pata muslo");
    }
}