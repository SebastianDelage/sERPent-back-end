package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.web.dto.request.ProductCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.ProductUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.ProductResponse;
import com.empresa.serpent.catalog.web.mapper.ProductMapper;
import com.empresa.serpent.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductMapper productMapper;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productMapper = Mappers.getMapper(ProductMapper.class);
        productService = new ProductService(productRepository, productMapper);
    }

    @Test
    @DisplayName("Should create product successfully")
    void shouldCreateProductSuccessfully() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Pollo entero",
                "Whole chicken",
                new BigDecimal("2500.0000"),
                "POLLO001",
                true,
                new BigDecimal("20.000"),
                new BigDecimal("25.000"),
                new BigDecimal("50.000")
        );

        when(productRepository.findBySku("POLLO001")).thenReturn(Optional.empty());

        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            entity.setCreatedAt(LocalDateTime.of(2026, 3, 16, 10, 0));
            return entity;
        });

        ProductResponse response = productService.create(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Pollo entero", response.name());
        assertEquals("Whole chicken", response.description());
        assertEquals(0, response.price().compareTo(new BigDecimal("2500.0000")));
        assertEquals("POLLO001", response.sku());
        assertTrue(response.active());
        assertEquals(0, response.minimumStock().compareTo(new BigDecimal("20.000")));
        assertEquals(0, response.reorderPoint().compareTo(new BigDecimal("25.000")));
        assertEquals(0, response.reorderQuantity().compareTo(new BigDecimal("50.000")));
    }

    @Test
    @DisplayName("Should default active to true when null on create")
    void shouldDefaultActiveToTrueWhenNullOnCreate() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Pata muslo",
                "Chicken leg quarter",
                new BigDecimal("1800.0000"),
                "POLLO002",
                null,
                null,
                null,
                null
        );

        when(productRepository.findBySku("POLLO002")).thenReturn(Optional.empty());

        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            return entity;
        });

        ProductResponse response = productService.create(request);

        assertEquals(2L, response.id());
        assertTrue(response.active());
    }

    @Test
    @DisplayName("Should normalize blank SKU to null on create")
    void shouldNormalizeBlankSkuToNullOnCreate() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Milanesa de pollo",
                "Chicken milanese",
                new BigDecimal("3000.0000"),
                "   ",
                true,
                null,
                null,
                null
        );

        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return entity;
        });

        ProductResponse response = productService.create(request);

        ArgumentCaptor<ProductEntity> captor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(captor.capture());

        assertNull(captor.getValue().getSku());
        assertNull(response.sku());
        verify(productRepository, never()).findBySku(any());
    }

    @Test
    @DisplayName("Should throw when SKU already exists on create")
    void shouldThrowWhenSkuAlreadyExistsOnCreate() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Pollo entero",
                "Whole chicken",
                new BigDecimal("2500.0000"),
                "POLLO001",
                true,
                null,
                null,
                null
        );

        ProductEntity existing = ProductEntity.builder().id(99L).sku("POLLO001").build();

        when(productRepository.findBySku("POLLO001")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(request)
        );

        assertEquals("SKU already exists: POLLO001", ex.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when price is null on create")
    void shouldThrowWhenPriceIsNullOnCreate() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Pollo entero",
                "Whole chicken",
                null,
                "POLLO001",
                true,
                null,
                null,
                null
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(request)
        );

        assertEquals("Price cannot be null", ex.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when price is negative on create")
    void shouldThrowWhenPriceIsNegativeOnCreate() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Pollo entero",
                "Whole chicken",
                new BigDecimal("-1.0000"),
                "POLLO001",
                true,
                null,
                null,
                null
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(request)
        );

        assertEquals("Price cannot be negative", ex.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when minimum stock is negative")
    void shouldThrowWhenMinimumStockIsNegative() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Pollo entero",
                "Whole chicken",
                new BigDecimal("2500.0000"),
                "POLLO001",
                true,
                new BigDecimal("-1.000"),
                null,
                null
        );

        when(productRepository.findBySku("POLLO001")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(request)
        );

        assertEquals("Minimum stock cannot be negative", ex.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when reorder point is less than minimum stock")
    void shouldThrowWhenReorderPointIsLessThanMinimumStock() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Pollo entero",
                "Whole chicken",
                new BigDecimal("2500.0000"),
                "POLLO001",
                true,
                new BigDecimal("20.000"),
                new BigDecimal("10.000"),
                new BigDecimal("50.000")
        );

        when(productRepository.findBySku("POLLO001")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(request)
        );

        assertEquals("Reorder point cannot be less than minimum stock", ex.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update product successfully")
    void shouldUpdateProductSuccessfully() {
        ProductEntity existing = ProductEntity.builder()
                .id(1L)
                .name("Pollo entero")
                .description("Whole chicken")
                .price(new BigDecimal("2500.0000"))
                .sku("POLLO001")
                .active(true)
                .minimumStock(new BigDecimal("20.000"))
                .reorderPoint(new BigDecimal("25.000"))
                .reorderQuantity(new BigDecimal("50.000"))
                .build();

        ProductUpdateRequest request = new ProductUpdateRequest(
                "Pollo premium",
                "Updated description",
                new BigDecimal("3000.0000"),
                "POLLO001",
                false,
                new BigDecimal("15.000"),
                new BigDecimal("20.000"),
                new BigDecimal("40.000")
        );

        when(productRepository.findBySku("POLLO001")).thenReturn(Optional.of(existing));
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.update(1L, request);

        assertEquals("Pollo premium", response.name());
        assertEquals("Updated description", response.description());
        assertEquals(0, response.price().compareTo(new BigDecimal("3000.0000")));
        assertEquals("POLLO001", response.sku());
        assertFalse(response.active());
        assertEquals(0, response.minimumStock().compareTo(new BigDecimal("15.000")));
        assertEquals(0, response.reorderPoint().compareTo(new BigDecimal("20.000")));
        assertEquals(0, response.reorderQuantity().compareTo(new BigDecimal("40.000")));
    }

    @Test
    @DisplayName("Should throw when SKU already exists on update for another product")
    void shouldThrowWhenSkuAlreadyExistsOnUpdateForAnotherProduct() {
        ProductEntity other = ProductEntity.builder().id(2L).sku("POLLO002").build();

        ProductUpdateRequest request = new ProductUpdateRequest(
                "Pollo entero",
                "Whole chicken",
                new BigDecimal("2500.0000"),
                "POLLO002",
                true,
                null,
                null,
                null
        );

        when(productRepository.findBySku("POLLO002")).thenReturn(Optional.of(other));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> productService.update(1L, request)
        );

        assertEquals("SKU already exists: POLLO002", ex.getMessage());
        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when product not found on update")
    void shouldThrowWhenProductNotFoundOnUpdate() {
        ProductUpdateRequest request = new ProductUpdateRequest(
                "Pollo entero",
                "Whole chicken",
                new BigDecimal("2500.0000"),
                "POLLO001",
                true,
                null,
                null,
                null
        );

        when(productRepository.findBySku("POLLO001")).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> productService.update(1L, request)
        );

        assertEquals("Product not found: 1", ex.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should find product by id")
    void shouldFindProductById() {
        ProductEntity product = ProductEntity.builder()
                .id(1L)
                .name("Pollo entero")
                .description("Whole chicken")
                .price(new BigDecimal("2500.0000"))
                .sku("POLLO001")
                .active(true)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.findById(1L);

        assertEquals(1L, response.id());
        assertEquals("Pollo entero", response.name());
        assertEquals("POLLO001", response.sku());
    }

    @Test
    @DisplayName("Should throw when product not found by id")
    void shouldThrowWhenProductNotFoundById() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> productService.findById(1L)
        );

        assertEquals("Product not found: 1", ex.getMessage());
    }

    @Test
    @DisplayName("Should return all active products")
    void shouldReturnAllActiveProducts() {
        ProductEntity p1 = ProductEntity.builder().id(1L).name("Pollo entero").active(true).build();
        ProductEntity p2 = ProductEntity.builder().id(2L).name("Pata muslo").active(true).build();

        when(productRepository.findByActiveTrue()).thenReturn(List.of(p1, p2));

        List<ProductResponse> result = productService.findAllActive();

        assertEquals(2, result.size());
        assertEquals("Pollo entero", result.get(0).name());
        assertEquals("Pata muslo", result.get(1).name());
    }

    @Test
    @DisplayName("Should search active products by name")
    void shouldSearchActiveProductsByName() {
        ProductEntity p1 = ProductEntity.builder().id(1L).name("Pollo entero").active(true).build();

        when(productRepository.findByActiveTrueAndNameContainingIgnoreCase("pollo"))
                .thenReturn(List.of(p1));

        List<ProductResponse> result = productService.searchActiveByName("pollo");

        assertEquals(1, result.size());
        assertEquals("Pollo entero", result.get(0).name());
    }

    @Test
    @DisplayName("Should return all active products when search name is blank")
    void shouldReturnAllActiveProductsWhenSearchNameIsBlank() {
        ProductEntity p1 = ProductEntity.builder().id(1L).name("Pollo entero").active(true).build();
        ProductEntity p2 = ProductEntity.builder().id(2L).name("Pata muslo").active(true).build();

        when(productRepository.findByActiveTrue()).thenReturn(List.of(p1, p2));

        List<ProductResponse> result = productService.searchActiveByName("   ");

        assertEquals(2, result.size());
        verify(productRepository).findByActiveTrue();
        verify(productRepository, never()).findByActiveTrueAndNameContainingIgnoreCase(any());
    }
}