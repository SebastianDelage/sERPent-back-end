package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ExpenseRepository extends
        JpaRepository<ExpenseEntity, Long>,
        JpaSpecificationExecutor<ExpenseEntity> {

    Optional<ExpenseEntity> findByTransactionId(Long transactionId);

    Optional<ExpenseEntity> findByReceiptNumberIgnoreCase(String receiptNumber);
}