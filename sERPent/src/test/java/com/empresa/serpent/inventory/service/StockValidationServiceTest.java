package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.web.dto.request.StockCheckItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockValidationServiceTest {

    @Mock
    private StockQueryService stockQueryService;

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
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than zero");
        }

        @Test
        @DisplayName("Should throw when quantity is zero")
        void shouldThrowWhenQuantityIsZero() {
            assertThatThrownBy(() -> stockValidationService.validatePositiveQuantity(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than zero");
        }

        @Test
        @DisplayName("Should throw when quantity is negative")
        void shouldThrowWhenQuantityIsNegative() {
            assertThatThrownBy(() -> stockValidationService.validatePositiveQuantity(new BigDecimal("-1.000")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than zero");
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

            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, new BigDecimal("3.000"))
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Insufficient stock for product 10 in warehouse 1. Current stock: 2.000, requested: 3.000");

            verify(stockQueryService).getStockByProductAndWarehouse(10L, 1L);
        }

        @Test
        @DisplayName("Should throw when requested quantity is null")
        void shouldThrowWhenRequestedQuantityIsNull() {
            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, null)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than zero");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when requested quantity is zero")
        void shouldThrowWhenRequestedQuantityIsZero() {
            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, BigDecimal.ZERO)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than zero");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when requested quantity is negative")
        void shouldThrowWhenRequestedQuantityIsNegative() {
            assertThatThrownBy(() ->
                    stockValidationService.validateAvailableStock(10L, 1L, new BigDecimal("-1.000"))
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be greater than zero");

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
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Sale must contain at least one item");

            verify(stockQueryService, never()).getStockByProductAndWarehouse(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw when items list is empty")
        void shouldThrowWhenItemsListIsEmpty() {
            assertThatThrownBy(() ->
                    stockValidationService.validateSaleItemsStock(List.of(), 1L)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Sale must contain at least one item");

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
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Item productId cannot be null");

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

            assertThatThrownBy(() ->
                    stockValidationService.validateSaleItemsStock(items, 1L)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Insufficient stock for product 20 in warehouse 1. Current stock: 3.000, requested: 10.000");

            verify(stockQueryService).getStockByProductAndWarehouse(10L, 1L);
            verify(stockQueryService).getStockByProductAndWarehouse(20L, 1L);
        }
    }
}