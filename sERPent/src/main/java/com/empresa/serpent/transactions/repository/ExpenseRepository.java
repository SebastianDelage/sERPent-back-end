package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByTransactionId(Long transactionId);

    List<Expense> findBySupplierEntityId(Long supplierId);

    List<Expense> findByExpenseCategoryId(Long categoryId);

    List<Expense> findByReimbursableTrue();

    List<Expense> findByExpenseCategoryIdOrderByIdDesc(Long categoryId);

}