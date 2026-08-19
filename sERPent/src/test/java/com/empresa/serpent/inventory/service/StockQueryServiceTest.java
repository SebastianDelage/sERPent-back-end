package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.ProductWarehouseMinimumStockRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockQueryServiceTest {

    @Mock
    private InventoryStockSnapshotRepository inventoryStockSnapshotRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductWarehouseMinimumStockRepository productWarehouseMinimumStockRepository;

    @InjectMocks
    private StockQueryService stockQueryService;

    @Test
    @DisplayName("Should return stock grouped by product and warehouse from snapshot")
    void shouldReturnStockGroupedByProductAndWarehouseFromSnapshot() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryStockSnapshotEntity> snapshots = List.of(
                snapshot(pollo, central, "19.000"),
                snapshot(pollo, norte, "8.000")
        );

        given(inventoryStockSnapshotRepository.findAll()).willReturn(snapshots);

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

        verify(inventoryStockSnapshotRepository).findAll();
    }

    @Test
    @DisplayName("Should return only positive stock rows when onlyPositive is true")
    void shouldReturnOnlyPositiveStockRowsWhenOnlyPositiveIsTrue() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryStockSnapshotEntity> snapshots = List.of(
                snapshot(pollo, central, "0.000"),
                snapshot(pollo, norte, "3.000")
        );

        given(inventoryStockSnapshotRepository.findAll()).willReturn(snapshots);

        List<StockResponse> result = stockQueryService.getStock(new StockFilter(null, null, true));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).warehouseId()).isEqualTo(2L);
        assertThat(result.get(0).stock()).isEqualByComparingTo("3.000");

        verify(inventoryStockSnapshotRepository).findAll();
    }

    @Test
    @DisplayName("Should return total stock by product")
    void shouldReturnTotalStockByProduct() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        given(inventoryStockSnapshotRepository.findByProductId(1L))
                .willReturn(List.of(
                        snapshot(pollo, central, "19.000"),
                        snapshot(pollo, norte, "8.000")
                ));

        BigDecimal result = stockQueryService.getTotalStockByProduct(1L);

        assertThat(result).isEqualByComparingTo("27.000");

        verify(inventoryStockSnapshotRepository).findByProductId(1L);
    }

    @Test
    @DisplayName("Should return stock by product and warehouse")
    void shouldReturnStockByProductAndWarehouse() {

        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));
        WarehouseEntity central = warehouse(1L, "Depósito Central");

        given(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 1L))
                .willReturn(Optional.of(snapshot(pollo, central, "19.000")));

        BigDecimal result = stockQueryService.getStockByProductAndWarehouse(1L, 1L);

        assertThat(result).isEqualByComparingTo("19.000");

        verify(inventoryStockSnapshotRepository).findByProductIdAndWarehouseId(1L, 1L);
    }

    @Test
    @DisplayName("Should return zero when stock by product and warehouse does not exist")
    void shouldReturnZeroWhenStockByProductAndWarehouseDoesNotExist() {

        given(inventoryStockSnapshotRepository.findByProductIdAndWarehouseId(1L, 1L))
                .willReturn(Optional.empty());

        BigDecimal result = stockQueryService.getStockByProductAndWarehouse(1L, 1L);

        assertThat(result).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("Should return total stock grouped by product")
    void shouldReturnTotalStockGroupedByProduct() {

        ProductEntity milanesa = product(3L, "Milanesa de pollo", null);
        ProductEntity pollo = product(1L, "Pollo entero", new BigDecimal("20.000"));

        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryStockSnapshotEntity> snapshots = List.of(
                snapshot(milanesa, central, "15.000"),
                snapshot(milanesa, norte, "5.000"),
                snapshot(pollo, central, "19.000"),
                snapshot(pollo, norte, "8.000")
        );

        given(inventoryStockSnapshotRepository.findAll()).willReturn(snapshots);

        List<ProductStockResponse> result = stockQueryService.getTotalStockGroupedByProduct(false);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).productId()).isEqualTo(3L);
        assertThat(result.get(0).productName()).isEqualTo("Milanesa de pollo");
        assertThat(result.get(0).totalStock()).isEqualByComparingTo("20.000");

        assertThat(result.get(1).productId()).isEqualTo(1L);
        assertThat(result.get(1).productName()).isEqualTo("Pollo entero");
        assertThat(result.get(1).totalStock()).isEqualByComparingTo("27.000");

        verify(inventoryStockSnapshotRepository).findAll();
    }

    @Test
    @DisplayName("Should return low stock rows per warehouse, using the product minimum")
    void shouldReturnLowStockProductsWithConfiguredMinimumStock() {

        ProductEntity pataMuslo = product(2L, "Pata muslo", new BigDecimal("20.000"));
        ProductEntity milanesa = product(3L, "Milanesa de pollo", null);

        WarehouseEntity central = warehouse(1L, "Depósito Central");
        WarehouseEntity norte = warehouse(2L, "Sucursal Norte");

        List<InventoryStockSnapshotEntity> snapshots = List.of(
                snapshot(pataMuslo, central, "19.000"),
                snapshot(milanesa, central, "15.000"),
                snapshot(milanesa, norte, "5.000")
        );

        given(inventoryStockSnapshotRepository.findAll()).willReturn(snapshots);
        given(productRepository.findAll()).willReturn(List.of(pataMuslo, milanesa));
        given(productWarehouseMinimumStockRepository.findAll()).willReturn(List.of());

        List<LowStockResponse> result = stockQueryService.getLowStock();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(2L);
        assertThat(result.get(0).productName()).isEqualTo("Pata muslo");
        assertThat(result.get(0).warehouseId()).isEqualTo(1L);
        assertThat(result.get(0).currentStock()).isEqualByComparingTo("19.000");
        assertThat(result.get(0).minimumStock()).isEqualByComparingTo("20.000");
        assertThat(result.get(0).minimumFromWarehouse()).isFalse();
        assertThat(result.get(0).missingQuantity()).isEqualByComparingTo("1.000");

        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("Should ignore products without minimum stock in low stock detection")
    void shouldIgnoreProductsWithoutMinimumStockInLowStockDetection() {

        ProductEntity milanesa = product(3L, "Milanesa de pollo", null);
        WarehouseEntity central = warehouse(1L, "Depósito Central");

        List<InventoryStockSnapshotEntity> snapshots = List.of(
                snapshot(milanesa, central, "5.000")
        );

        given(inventoryStockSnapshotRepository.findAll()).willReturn(snapshots);
        given(productRepository.findAll()).willReturn(List.of(milanesa));
        given(productWarehouseMinimumStockRepository.findAll()).willReturn(List.of());

        List<LowStockResponse> result = stockQueryService.getLowStock();

        assertThat(result).isEmpty();

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

    private InventoryStockSnapshotEntity snapshot(ProductEntity product,
                                                  WarehouseEntity warehouse,
                                                  String currentStock) {
        return InventoryStockSnapshotEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .currentStock(new BigDecimal(currentStock))
                .build();
    }
}