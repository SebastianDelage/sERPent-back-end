package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import com.empresa.serpent.inventory.web.dto.response.ProductStockResponse;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockQueryServiceTest {

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private StockQueryService stockQueryService;

    @Test
    @DisplayName("Should calculate stock grouped by product and warehouse using movement signs")
    void shouldCalculateStockGroupedByProductAndWarehouseUsingMovementSigns() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryMovementEntity> movements = List.of(
                movement(pollo, central, MovementType.IN, "20.000"),
                movement(pollo, central, MovementType.OUT, "1.000"),
                movement(pollo, norte, MovementType.IN, "8.000")
        );

        given(inventoryMovementRepository.findAll(any(Specification.class))).willReturn(movements);

        List<StockResponse> result = stockQueryService.getStock(new StockFilter(null, null, null));

        assertThat(result).hasSize(2);

        assertThat(result.get(0).productId()).isEqualTo(1L);
        assertThat(result.get(0).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(0).warehouseId()).isEqualTo(1L);
        assertThat(result.get(0).warehouseName()).isEqualTo("Depósito Central");
        assertThat(result.get(0).stock()).isEqualByComparingTo("19.000");

        assertThat(result.get(1).productId()).isEqualTo(1L);
        assertThat(result.get(1).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(1).warehouseId()).isEqualTo(2L);
        assertThat(result.get(1).warehouseName()).isEqualTo("Sucursal Norte");
        assertThat(result.get(1).stock()).isEqualByComparingTo("8.000");

        verify(inventoryMovementRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("Should return only positive stock rows when onlyPositive is true")
    void shouldReturnOnlyPositiveStockRowsWhenOnlyPositiveIsTrue() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryMovementEntity> movements = List.of(
                movement(pollo, central, MovementType.IN, "5.000"),
                movement(pollo, central, MovementType.OUT, "5.000"),
                movement(pollo, norte, MovementType.IN, "3.000")
        );

        given(inventoryMovementRepository.findAll(any(Specification.class))).willReturn(movements);

        List<StockResponse> result = stockQueryService.getStock(new StockFilter(null, null, true));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).warehouseId()).isEqualTo(2L);
        assertThat(result.get(0).stock()).isEqualByComparingTo("3.000");

        verify(inventoryMovementRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("Should return total stock by product")
    void shouldReturnTotalStockByProduct() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryMovementEntity> movements = List.of(
                movement(pollo, central, MovementType.IN, "20.000"),
                movement(pollo, central, MovementType.OUT, "1.000"),
                movement(pollo, norte, MovementType.IN, "8.000")
        );

        given(inventoryMovementRepository.findAll(any(Specification.class))).willReturn(movements);

        BigDecimal result = stockQueryService.getTotalStockByProduct(1L);

        assertThat(result).isEqualByComparingTo("27.000");

        verify(inventoryMovementRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("Should return stock by product and warehouse")
    void shouldReturnStockByProductAndWarehouse() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");

        List<InventoryMovementEntity> movements = List.of(
                movement(pollo, central, MovementType.IN, "20.000"),
                movement(pollo, central, MovementType.OUT, "1.000")
        );

        given(inventoryMovementRepository.findAll(any(Specification.class))).willReturn(movements);

        BigDecimal result = stockQueryService.getStockByProductAndWarehouse(1L, 1L);

        assertThat(result).isEqualByComparingTo("19.000");

        verify(inventoryMovementRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("Should return total stock grouped by product")
    void shouldReturnTotalStockGroupedByProduct() {

        ProductEntity milanesa = product(3L, "Milanesa de pollo", null);
        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));

        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryMovementEntity> movements = List.of(
                movement(milanesa, central, MovementType.IN, "15.000"),
                movement(milanesa, norte, MovementType.IN, "5.000"),
                movement(pollo, central, MovementType.IN, "20.000"),
                movement(pollo, central, MovementType.OUT, "1.000"),
                movement(pollo, norte, MovementType.IN, "8.000")
        );

        given(inventoryMovementRepository.findAll(any(Specification.class))).willReturn(movements);

        List<ProductStockResponse> result = stockQueryService.getTotalStockGroupedByProduct(false);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).productId()).isEqualTo(3L);
        assertThat(result.get(0).productName()).isEqualTo("Milanesa de pollo");
        assertThat(result.get(0).totalStock()).isEqualByComparingTo("20.000");

        assertThat(result.get(1).productId()).isEqualTo(1L);
        assertThat(result.get(1).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(1).totalStock()).isEqualByComparingTo("27.000");

        verify(inventoryMovementRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("Should return low stock products with configured minimum stock")
    void shouldReturnLowStockProductsWithConfiguredMinimumStock() {

        ProductEntity pataMuslo = product(2L, "Pata muslo", new BigDecimal("20.000"));
        ProductEntity milanesa = product(3L, "Milanesa de pollo", null);

        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryMovementEntity> movements = List.of(
                movement(pataMuslo, central, MovementType.IN, "20.000"),
                movement(pataMuslo, central, MovementType.OUT, "1.000"),
                movement(milanesa, central, MovementType.IN, "15.000"),
                movement(milanesa, norte, MovementType.IN, "5.000")
        );

        given(inventoryMovementRepository.findAll(any(Specification.class))).willReturn(movements);
        given(productRepository.findAll()).willReturn(List.of(pataMuslo, milanesa));

        List<LowStockResponse> result = stockQueryService.getLowStock();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(2L);
        assertThat(result.get(0).productName()).isEqualTo("Pata muslo");
        assertThat(result.get(0).currentStock()).isEqualByComparingTo("19.000");
        assertThat(result.get(0).minimumStock()).isEqualByComparingTo("20.000");

        verify(inventoryMovementRepository).findAll(any(Specification.class));
        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("Should ignore products without minimum stock in low stock detection")
    void shouldIgnoreProductsWithoutMinimumStockInLowStockDetection() {

        ProductEntity milanesa = product(3L, "Milanesa de pollo", null);
        WarehouseEntity central = warehouse(1L, "Depósito Central");

        List<InventoryMovementEntity> movements = List.of(
                movement(milanesa, central, MovementType.IN, "5.000")
        );

        given(inventoryMovementRepository.findAll(any(Specification.class))).willReturn(movements);
        given(productRepository.findAll()).willReturn(List.of(milanesa));

        List<LowStockResponse> result = stockQueryService.getLowStock();

        assertThat(result).isEmpty();

        verify(inventoryMovementRepository).findAll(any(Specification.class));
        verify(productRepository).findAll();
    }

    private ProductEntity product(Long id, String name, BigDecimal minimumStock) {
        return ProductEntity.builder()
                .id(id)
                .name(name)
                .minimumStock(minimumStock)
                .build();
    }

    private WarehouseEntity warehouse(Long id, String name) {
        return WarehouseEntity.builder()
                .id(id)
                .name(name)
                .build();
    }

    private InventoryMovementEntity movement(ProductEntity product,
                                             WarehouseEntity warehouse,
                                             MovementType movementType,
                                             String quantity) {
        return InventoryMovementEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .movementType(movementType)
                .quantity(new BigDecimal(quantity))
                .build();
    }
}