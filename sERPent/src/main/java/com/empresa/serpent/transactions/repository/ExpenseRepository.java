package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {

    Optional<ExpenseEntity> findByTransactionId(Long transactionId);

    List<ExpenseEntity> findBySupplierEntityId(Long supplierId);

    List<ExpenseEntity> findByExpenseCategoryId(Long categoryId);

    List<ExpenseEntity> findByReimbursableTrue();

    List<ExpenseEntity> findByExpenseCategoryIdOrderByIdDesc(Long categoryId);
}