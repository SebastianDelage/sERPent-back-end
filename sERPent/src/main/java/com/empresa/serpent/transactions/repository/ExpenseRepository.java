package com.empresa.serpent.transactions.repository;


import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    Optional<ExpenseEntity> findByTransactionId(Long transactionId);

    Optional<ExpenseEntity> findByReceiptNumberIgnoreCase(String receiptNumber);

    List<ExpenseEntity> findBySupplierId(Long supplierId);

    List<ExpenseEntity> findByExpenseCategoryId(Long categoryId);

    List<ExpenseEntity> findByReimbursableTrue();
}