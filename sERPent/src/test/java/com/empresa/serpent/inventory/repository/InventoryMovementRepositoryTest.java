package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.catalog.domain.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class InventoryMovementRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryMovementRepository repository;

    @Test
    @DisplayName("Should find movements by product id")
    void shouldFindByProductId() {

        ProductEntity product = persistProduct("Pollo entero");
        WarehouseEntity warehouse = persistWarehouse("Central");

        persistMovement(product, warehouse, MovementType.IN, "10.000");

        entityManager.flush();
        entityManager.clear();

        List<InventoryMovementEntity> result = repository.findByProductId(product.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("Should find movements by warehouse id")
    void shouldFindByWarehouseId() {

        ProductEntity product = persistProduct("Pollo entero");
        WarehouseEntity warehouse = persistWarehouse("Central");

        persistMovement(product, warehouse, MovementType.IN, "5.000");

        entityManager.flush();
        entityManager.clear();

        List<InventoryMovementEntity> result = repository.findByWarehouseId(warehouse.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMovementType()).isEqualTo(MovementType.IN);
    }

    @Test
    @DisplayName("Should find movements by movement type")
    void shouldFindByMovementType() {

        ProductEntity product = persistProduct("Pollo entero");
        WarehouseEntity warehouse = persistWarehouse("Central");

        persistMovement(product, warehouse, MovementType.OUT, "2.000");

        entityManager.flush();
        entityManager.clear();

        List<InventoryMovementEntity> result = repository.findByMovementType(MovementType.OUT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("2.000");
    }

    @Test
    @DisplayName("Should find movements between dates")
    void shouldFindByCreatedAtBetween() {

        ProductEntity product = persistProduct("Pollo entero");
        WarehouseEntity warehouse = persistWarehouse("Central");

        persistMovement(product, warehouse, MovementType.IN, "8.000");

        entityManager.flush();
        entityManager.clear();

        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now().plusDays(1);

        List<InventoryMovementEntity> result = repository.findByCreatedAtBetween(from, to);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should find movements by product and warehouse")
    void shouldFindByProductAndWarehouse() {

        ProductEntity product = persistProduct("Pollo entero");
        WarehouseEntity warehouse = persistWarehouse("Central");

        persistMovement(product, warehouse, MovementType.IN, "12.000");

        entityManager.flush();
        entityManager.clear();

        List<InventoryMovementEntity> result =
                repository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId());

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Should find movements by transaction id")
    void shouldFindByTransactionId() {

        UserEntity user = persistUser();
        PaymentMethodEntity paymentMethod = persistPaymentMethod();

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.SALE)
                .status(TransactionStatus.CONFIRMED)
                .total(new BigDecimal("1000.0000"))
                .createdByUserEntity(user)
                .paymentMethod(paymentMethod)
                .build();

        entityManager.persist(transaction);

        ProductEntity product = persistProduct("Pollo entero");
        WarehouseEntity warehouse = persistWarehouse("Central");

        InventoryMovementEntity movement = InventoryMovementEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .transaction(transaction)
                .movementType(MovementType.OUT)
                .quantity(new BigDecimal("1.000"))
                .build();

        entityManager.persist(movement);

        entityManager.flush();
        entityManager.clear();

        List<InventoryMovementEntity> result =
                repository.findByTransactionId(transaction.getId());

        assertThat(result).hasSize(1);
    }

    // -------------------------
    // helpers
    // -------------------------

    private ProductEntity persistProduct(String name) {
        ProductEntity product = ProductEntity.builder()
                .name(name)
                .price(new BigDecimal("1000"))
                .sku(name.replace(" ", "_"))
                .active(true)
                .build();

        return entityManager.persist(product);
    }

    private WarehouseEntity persistWarehouse(String name) {
        WarehouseEntity warehouse = WarehouseEntity.builder()
                .name(name)
                .active(true)
                .build();

        return entityManager.persist(warehouse);
    }

    private InventoryMovementEntity persistMovement(ProductEntity product,
                                                    WarehouseEntity warehouse,
                                                    MovementType type,
                                                    String quantity) {

        InventoryMovementEntity movement = InventoryMovementEntity.builder()
                .product(product)
                .warehouse(warehouse)
                .movementType(type)
                .quantity(new BigDecimal(quantity))
                .build();

        return entityManager.persist(movement);
    }

    private UserEntity persistUser() {
        UserEntity user = UserEntity.builder()
                .name("Admin")
                .username("admin_test")
                .passwordHash("test")
                .active(true)
                .build();

        return entityManager.persist(user);
    }

    private PaymentMethodEntity persistPaymentMethod() {
        PaymentMethodEntity pm = PaymentMethodEntity.builder()
                .name("Cash")
                .active(true)
                .build();

        return entityManager.persist(pm);
    }
}