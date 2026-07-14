package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.web.dto.request.StockCheckItemRequest;
import com.empresa.serpent.shared.exception.InsufficientStockException;
import com.empresa.serpent.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockValidationServiceTest {

    @Mock
    private StockQueryService stockQueryService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private StockValidationService stockValidationService;

    @Nested
    class ValidatePositiveQuantityTests {

        @Test
        @DisplayName("Should allow positive quantity")
        void shouldAllowPositiveQuantity() {
            assertThatCode(() -> stockValidationService.validatePositiveQuantity(new BigDecimal("1.000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw when quantity is null")
        void shouldThrowWhenQuantityIsNull() {
            assertThatThrownBy(() -> stockValidationService.validatePositiveQuantity(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La cantidad debe ser mayor a cero.");
        }

        @Test
        @DisplayName("Should throw when quantity is zero")
        void shouldThrowWhenQuantityIsZero() {
            assertThatThrownBy(() -> stockValidationService.validatePositiveQuantity(BigDecimal.ZERO))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La cantidad debe ser mayor a cero.");
        }

        @Test
        @DisplayName("Should throw when quantity is negative")
        void shouldThrowWhenQuantityIsNegative() {
            assertThatThrownBy(() -> stockValidationService.validatePositiveQuantity(new BigDecimal("-1.000")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La cantidad debe ser mayor a cero.");
        }
    }

    @Nested
    class ValidateAvailableStockTests {

        @Test
        @DisplayName("Should allow when stock is greater than requested quantity")
        void shouldAllowWhenStockIsGreaterThanRequestedQuantity() {
            given(stockQueryService.getStockByProductAndWarehouse(10L, 1L))
                    .willReturn(new BigDecimal("10.000"));

            assertThatCode(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, new BigDecimal("3.000"))
            ).doesNotThrowAnyException();

            verify(stockQueryService).getStockByProductAndWarehouse(10L, 1L);
        }

        @Test
        @DisplayName("Should allow when stock is equal to requested quantity")
        void shouldAllowWhenStockIsEqualToRequestedQuantity() {
            given(stockQueryService.getStockByProductAndWarehouse(10L, 1L))
                    .willReturn(new BigDecimal("5.000"));

            assertThatCode(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, new BigDecimal("5.000"))
            ).doesNotThrowAnyException();

            verify(stockQueryService).getStockByProductAndWarehouse(10L, 1L);
        }

        @Test
        @DisplayName("Should throw when stock is insufficient")
        void shouldThrowWhenStockIsInsufficient() {
            given(stockQueryService.getStockByProductAndWarehouse(10L, 1L))
                    .willReturn(new BigDecimal("2.000"));
            given(productRepository.findById(10L))
                    .willReturn(Optional.of(ProductEntity.builder().id(10L).name("Pollo entero").build()));

            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, new BigDecimal("3.000"))
            )
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("No hay stock suficiente de \"Pollo entero\". Disponible: 2.000, solicitado: 3.000.");

            verify(stockQueryService).getStockByProductAndWarehouse(10L, 1L);
        }

        @Test
        @DisplayName("Should throw when requested quantity is null")
        void shouldThrowWhenRequestedQuantityIsNull() {
            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, null)
            )
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La cantidad debe ser mayor a cero.");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when requested quantity is zero")
        void shouldThrowWhenRequestedQuantityIsZero() {
            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, BigDecimal.ZERO)
            )
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La cantidad debe ser mayor a cero.");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when requested quantity is negative")
        void shouldThrowWhenRequestedQuantityIsNegative() {
            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, new BigDecimal("-1.000"))
            )
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La cantidad debe ser mayor a cero.");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }
    }

    @Nested
    class ValidateSaleItemsStockTests {

        @Test
        @DisplayName("Should validate all sale items successfully")
        void shouldValidateAllSaleItemsSuccessfully() {
            List<StockCheckItemRequest> items = List.of(
                    new StockCheckItemRequest(10L, new BigDecimal("2.000")),
                    new StockCheckItemRequest(20L, new BigDecimal("1.000"))
            );

            given(stockQueryService.getStockByProductAndWarehouse(10L, 1L))
                    .willReturn(new BigDecimal("5.000"));
            given(stockQueryService.getStockByProductAndWarehouse(20L, 1L))
                    .willReturn(new BigDecimal("3.000"));

            assertThatCode(() ->
                    stockValidationService.validateSaleItemsStock(items, 1L)
            ).doesNotThrowAnyException();

            verify(stockQueryService).getStockByProductAndWarehouse(10L, 1L);
            verify(stockQueryService).getStockByProductAndWarehouse(20L, 1L);
        }

        @Test
        @DisplayName("Should throw when items list is null")
        void shouldThrowWhenItemsListIsNull() {
            assertThatThrownBy(() ->
                    stockValidationService.validateSaleItemsStock(null, 1L)
            )
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La venta debe tener al menos un ítem.");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when items list is empty")
        void shouldThrowWhenItemsListIsEmpty() {
            assertThatThrownBy(() ->
                    stockValidationService.validateSaleItemsStock(List.of(), 1L)
            )
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La venta debe tener al menos un ítem.");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when item productId is null")
        void shouldThrowWhenItemProductIdIsNull() {
            List<StockCheckItemRequest> items = List.of(
                    new StockCheckItemRequest(null, new BigDecimal("1.000"))
            );

            assertThatThrownBy(() ->
                    stockValidationService.validateSaleItemsStock(items, 1L)
            )
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Falta indicar el producto de un ítem.");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when one item has insufficient stock")
        void shouldThrowWhenOneItemHasInsufficientStock() {
            List<StockCheckItemRequest> items = List.of(
                    new StockCheckItemRequest(10L, new BigDecimal("2.000")),
                    new StockCheckItemRequest(20L, new BigDecimal("10.000"))
            );

            given(stockQueryService.getStockByProductAndWarehouse(10L, 1L))
                    .willReturn(new BigDecimal("5.000"));
            given(stockQueryService.getStockByProductAndWarehouse(20L, 1L))
                    .willReturn(new BigDecimal("3.000"));
            given(productRepository.findById(20L))
                    .willReturn(Optional.of(ProductEntity.builder().id(20L).name("Pata muslo").build()));

            assertThatThrownBy(() ->
                    stockValidationService.validateSaleItemsStock(items, 1L)
            )
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("No hay stock suficiente de \"Pata muslo\". Disponible: 3.000, solicitado: 10.000.");

            verify(stockQueryService).getStockByProductAndWarehouse(10L, 1L);
            verify(stockQueryService).getStockByProductAndWarehouse(20L, 1L);
        }
    }
}