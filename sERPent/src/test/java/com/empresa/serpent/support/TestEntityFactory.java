package com.empresa.serpent.support;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.users.domain.entity.UserEntity;

import java.math.BigDecimal;
import java.util.List;

public final class TestEntityFactory {

    private TestEntityFactory() {
    }

    public static ProductEntity product(Long id, String name) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal("1000.0000"));
        product.setActive(true);
        return product;
    }

    public static WarehouseEntity warehouse(Long id, String name, boolean active) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setName(name);
        warehouse.setActive(active);
        return warehouse;
    }

    public static UserEntity user(Long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setName("Admin");
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }

    public static PaymentMethodEntity paymentMethod(Long id, String name) {
        PaymentMethodEntity pm = new PaymentMethodEntity();
        pm.setId(id);
        pm.setName(name);
        pm.setActive(true);
        return pm;
    }

    public static TransactionDetailEntity detail(ProductEntity product, String quantity, String unitPrice) {
        TransactionDetailEntity detail = new TransactionDetailEntity();
        detail.setProduct(product);
        detail.setQuantity(new BigDecimal(quantity));
        detail.setUnitPrice(new BigDecimal(unitPrice));
        return detail;
    }

    public static TransactionEntity transaction(Long id, TransactionDetailEntity... details) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setDetails(List.of(details));
        return transaction;
    }

    public static TransactionEntity transactionWithoutDetails(Long id) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setDetails(List.of());
        return transaction;
    }
}