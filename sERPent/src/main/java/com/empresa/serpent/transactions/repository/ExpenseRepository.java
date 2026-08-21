package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import com.empresa.serpent.transactions.repository.projection.TransactionWarehouseProjection;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends
        JpaRepository<ExpenseEntity, Long>,
        JpaSpecificationExecutor<ExpenseEntity> {

    Optional<ExpenseEntity> findByTransactionId(Long transactionId);

    Optional<ExpenseEntity> findByReceiptNumberIgnoreCase(String receiptNumber);

    /**
     * The branch of each of these expenses, in one query, skipping the general ones.
     *
     * <p>Expenses move money and not stock, so they leave no inventory movement and the
     * history's branch column has to reach for them separately.
     */
    @Query("""
           SELECT e.transaction.id AS transactionId, w.name AS warehouseName
           FROM ExpenseEntity e
           JOIN e.warehouse w
           WHERE e.transaction.id IN :transactionIds
           """)
    List<TransactionWarehouseProjection> findWarehouseNamesByTransactionIds(
            @Param("transactionIds") List<Long> transactionIds);
}