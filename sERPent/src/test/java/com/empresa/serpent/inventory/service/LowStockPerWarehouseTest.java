package com.empresa.serpent.inventory.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.ProductWarehouseMinimumStockEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.ProductWarehouseMinimumStockRepository;
import com.empresa.serpent.inventory.web.dto.response.LowStockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.BDDMockito.given;

/**
 * Low stock decided per warehouse, with the minimum resolved in cascade.
 *
 * <p>The scenario is the one the old aggregation got wrong: "Pollo entero" is empty at
 * the branch and overstocked at the central warehouse. Summing first hid it; comparing
 * per warehouse surfaces it.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Low stock per warehouse")
class LowStockPerWarehouseTest {

    @Mock
    private InventoryStockSnapshotRepository snapshotRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductWarehouseMinimumStockRepository minimumStockRepository;

    @Mock
    private WarehouseScopeService warehouseScopeService;

    /**
     * Mirrors the real contract instead of always answering "unrestricted": no branch asked
     * for means everything, a branch asked for means that branch. Stubbing it flat would
     * quietly drop the warehouse filter these tests are here to check.
     */
    @BeforeEach
    void scopeMirrorsTheRequest() {
        lenient().when(warehouseScopeService.resolve(any()))
                .thenAnswer(invocation -> {
                    Long requested = invocation.getArgument(0);
                    return requested == null
                            ? new WarehouseScope(true, List.of())
                            : new WarehouseScope(false, List.of(requested));
                });
    }

    @InjectMocks
    private StockQueryService stockQueryService;

    private static final Long CENTRAL_ID = 1L;
    private static final Long BRANCH_ID = 2L;

    private WarehouseEntity central() {
        return WarehouseEntity.builder().id(CENTRAL_ID).name("Depósito Central").active(true).build();
    }

    private WarehouseEntity branch() {
        return WarehouseEntity.builder().id(BRANCH_ID).name("Sucursal Norte").active(true).build();
    }

    private ProductEntity product(Long id, String name, String minimum) {
        return ProductEntity.builder()
                .id(id).name(name).sku("SKU" + id).active(true)
                .price(new BigDecimal("1000.0000"))
                .minimumStock(minimum == null ? null : new BigDecimal(minimum))
                .build();
    }

    private InventoryStockSnapshotEntity snapshot(ProductEntity p, WarehouseEntity w, String stock) {
        return InventoryStockSnapshotEntity.builder()
                .product(p).warehouse(w).currentStock(new BigDecimal(stock)).build();
    }

    private ProductWarehouseMinimumStockEntity override(ProductEntity p, WarehouseEntity w, String minimum) {
        return ProductWarehouseMinimumStockEntity.builder()
                .product(p).warehouse(w).minimumStock(new BigDecimal(minimum)).build();
    }

    @Test
    @DisplayName("Surfaces a product empty at one branch and overstocked at another")
    void surfacesShortageHiddenByTheOldTotal() {
        ProductEntity pollo = product(1L, "Pollo entero", "20.000");
        WarehouseEntity central = central();
        WarehouseEntity branch = branch();

        // 100 + 2 = 102 total, comfortably over a minimum of 20: the old aggregation
        // reported nothing at all. Per warehouse, the branch is clearly short.
        given(snapshotRepository.findAll()).willReturn(List.of(
                snapshot(pollo, central, "100.000"),
                snapshot(pollo, branch, "2.000")
        ));
        given(productRepository.findAll()).willReturn(List.of(pollo));
        given(minimumStockRepository.findAll()).willReturn(List.of());

        List<LowStockResponse> result = stockQueryService.getLowStock();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).warehouseId()).isEqualTo(BRANCH_ID);
        assertThat(result.get(0).currentStock()).isEqualByComparingTo("2.000");
        assertThat(result.get(0).minimumStock()).isEqualByComparingTo("20.000");
        assertThat(result.get(0).missingQuantity()).isEqualByComparingTo("18.000");
    }

    @Test
    @DisplayName("Uses the warehouse override where there is one, the product minimum elsewhere")
    void cascadeResolvesPerWarehouse() {
        ProductEntity pollo = product(1L, "Pollo entero", "20.000");
        WarehouseEntity central = central();
        WarehouseEntity branch = branch();

        // The central warehouse moves more volume, so it carries a floor of 50.
        given(snapshotRepository.findAll()).willReturn(List.of(
                snapshot(pollo, central, "40.000"),
                snapshot(pollo, branch, "25.000")
        ));
        given(productRepository.findAll()).willReturn(List.of(pollo));
        given(minimumStockRepository.findAll()).willReturn(List.of(
                override(pollo, central, "50.000")
        ));

        List<LowStockResponse> result = stockQueryService.getLowStock();

        // Central: 40 <= 50 (override) -> low. Branch: 25 > 20 (product) -> fine.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).warehouseId()).isEqualTo(CENTRAL_ID);
        assertThat(result.get(0).minimumStock()).isEqualByComparingTo("50.000");
        assertThat(result.get(0).minimumFromWarehouse()).isTrue();
        assertThat(result.get(0).missingQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("An override can also keep a warehouse out that the product minimum would flag")
    void overrideCanLowerTheThreshold() {
        ProductEntity pollo = product(1L, "Pollo entero", "20.000");
        WarehouseEntity branch = branch();

        // A small branch is fine holding 5, even though the product-wide floor is 20.
        given(snapshotRepository.findAll()).willReturn(List.of(
                snapshot(pollo, branch, "8.000")
        ));
        given(productRepository.findAll()).willReturn(List.of(pollo));
        given(minimumStockRepository.findAll()).willReturn(List.of(
                override(pollo, branch, "5.000")
        ));

        assertThat(stockQueryService.getLowStock()).isEmpty();
    }

    @Test
    @DisplayName("A product with no minimum at either level never appears")
    void productWithoutAnyMinimumIsNeverLow() {
        ProductEntity untracked = product(9L, "Bolsas", null);
        WarehouseEntity branch = branch();

        given(snapshotRepository.findAll()).willReturn(List.of(
                snapshot(untracked, branch, "0.000")
        ));
        given(productRepository.findAll()).willReturn(List.of(untracked));
        given(minimumStockRepository.findAll()).willReturn(List.of());

        assertThat(stockQueryService.getLowStock()).isEmpty();
    }

    @Test
    @DisplayName("An override gives a minimum to a product that has none of its own")
    void overrideAppliesEvenWithoutProductMinimum() {
        ProductEntity untracked = product(9L, "Bolsas", null);
        WarehouseEntity branch = branch();

        given(snapshotRepository.findAll()).willReturn(List.of(
                snapshot(untracked, branch, "3.000")
        ));
        given(productRepository.findAll()).willReturn(List.of(untracked));
        given(minimumStockRepository.findAll()).willReturn(List.of(
                override(untracked, branch, "10.000")
        ));

        List<LowStockResponse> result = stockQueryService.getLowStock();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).minimumFromWarehouse()).isTrue();
        assertThat(result.get(0).missingQuantity()).isEqualByComparingTo("7.000");
    }

    @Test
    @DisplayName("Filtering by warehouse returns only that warehouse's shortages")
    void filtersByWarehouse() {
        ProductEntity pollo = product(1L, "Pollo entero", "20.000");
        WarehouseEntity central = central();
        WarehouseEntity branch = branch();

        // Short at both, so the filter is what makes the difference, not the data.
        given(snapshotRepository.findByWarehouseIdIn(List.of(BRANCH_ID))).willReturn(List.of(
                snapshot(pollo, branch, "2.000")
        ));
        given(productRepository.findAll()).willReturn(List.of(pollo));
        given(minimumStockRepository.findAll()).willReturn(List.of());

        List<LowStockResponse> result = stockQueryService.getLowStock(BRANCH_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).warehouseId()).isEqualTo(BRANCH_ID);
    }

    @Test
    @DisplayName("Stock exactly at the minimum still counts as low")
    void atTheMinimumCountsAsLow() {
        ProductEntity pollo = product(1L, "Pollo entero", "20.000");
        WarehouseEntity branch = branch();

        given(snapshotRepository.findAll()).willReturn(List.of(
                snapshot(pollo, branch, "20.000")
        ));
        given(productRepository.findAll()).willReturn(List.of(pollo));
        given(minimumStockRepository.findAll()).willReturn(List.of());

        List<LowStockResponse> result = stockQueryService.getLowStock();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).missingQuantity()).isEqualByComparingTo("0.000");
    }
}
