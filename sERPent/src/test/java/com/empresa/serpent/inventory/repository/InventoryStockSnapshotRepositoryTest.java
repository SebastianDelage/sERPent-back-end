package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class InventoryStockSnapshotRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryStockSnapshotRepository repository;

    @Test
    @DisplayName("(c) decreaseStockWithFloor subtracts when stock is enough and rejects (0 rows) when it is not")
    void decreaseStockWithFloor_enforcesFloor() {
        ProductEntity product = persistProduct("Pollo entero");
        WarehouseEntity warehouse = persistWarehouse("Central");
        persistSnapshot(product, warehouse, "5.000");

        int applied = repository.decreaseStockWithFloor(
                product.getId(), warehouse.getId(), new BigDecimal("3.000"), 999L);
        assertThat(applied).isEqualTo(1);
        assertThat(currentStock(product, warehouse)).isEqualByComparingTo("2.000");

        int rejected = repository.decreaseStockWithFloor(
                product.getId(), warehouse.getId(), new BigDecimal("10.000"), 1000L);
        assertThat(rejected).isEqualTo(0);
        assertThat(currentStock(product, warehouse)).isEqualByComparingTo("2.000");
    }

    @Test
    @DisplayName("(d) decreaseStockWithoutFloor lowers ADJUSTMENT_OUT stock with no floor, even below zero")
    void decreaseStockWithoutFloor_allowsGoingBelowZero() {
        ProductEntity product = persistProduct("Merluza");
        WarehouseEntity warehouse = persistWarehouse("Sucursal");
        persistSnapshot(product, warehouse, "2.000");

        int applied = repository.decreaseStockWithoutFloor(
                product.getId(), warehouse.getId(), new BigDecimal("5.000"), 999L);

        assertThat(applied).isEqualTo(1);
        assertThat(currentStock(product, warehouse)).isEqualByComparingTo("-3.000");
    }

    @Test
    @DisplayName("increaseStock and the decrement queries affect 0 rows when the snapshot does not exist yet")
    void updates_affectNoRowsWhenSnapshotMissing() {
        ProductEntity product = persistProduct("Vacío");
        WarehouseEntity warehouse = persistWarehouse("Depósito vacío");

        assertThat(repository.increaseStock(
                product.getId(), warehouse.getId(), new BigDecimal("5.000"), 999L)).isEqualTo(0);
        assertThat(repository.decreaseStockWithoutFloor(
                product.getId(), warehouse.getId(), new BigDecimal("5.000"), 999L)).isEqualTo(0);
        assertThat(repository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())).isEmpty();
    }

    @Test
    @DisplayName("insertZeroSnapshot seeds a zero-balance row")
    void insertZeroSnapshot_createsZeroRow() {
        ProductEntity product = persistProduct("Nuevo");
        WarehouseEntity warehouse = persistWarehouse("Nuevo depósito");

        repository.insertZeroSnapshot(product.getId(), warehouse.getId());
        entityManager.clear();

        InventoryStockSnapshotEntity seeded = repository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseThrow();
        assertThat(seeded.getCurrentStock()).isEqualByComparingTo("0");
        assertThat(seeded.getLastMovementId()).isNull();
    }

    @Test
    @DisplayName("A second insertZeroSnapshot for the same product+warehouse collides against the unique constraint")
    void insertZeroSnapshot_collisionRaisesDataIntegrityViolation() {
        ProductEntity product = persistProduct("Duplicado");
        WarehouseEntity warehouse = persistWarehouse("Depósito dup");

        repository.insertZeroSnapshot(product.getId(), warehouse.getId());

        assertThatThrownBy(() ->
                repository.insertZeroSnapshot(product.getId(), warehouse.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------
    // helpers
    // -------------------------

    private BigDecimal currentStock(ProductEntity product, WarehouseEntity warehouse) {
        entityManager.clear();
        return repository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseThrow()
                .getCurrentStock();
    }

    private ProductEntity persistProduct(String name) {
        ProductEntity product = ProductEntity.builder()
                .name(name)
                .price(new BigDecimal("1000"))
                .sku(name.replace(" ", "_"))
                .active(true)
                .build();
        return entityManager.persistAndFlush(product);
    }

    private WarehouseEntity persistWarehouse(String name) {
        WarehouseEntity warehouse = WarehouseEntity.builder()
                .name(name)
                .active(true)
                .build();
        return entityManager.persistAndFlush(warehouse);
    }

    private void persistSnapshot(ProductEntity product, WarehouseEntity warehouse, String stock) {
        InventoryStockSnapshotEntity snapshot = InventoryStockSnapshotEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .currentStock(new BigDecimal(stock))
                .lastMovementId(null)
                .build();
        entityManager.persistAndFlush(snapshot);
    }
}
