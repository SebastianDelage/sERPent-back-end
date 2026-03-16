package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.SupplierEntity;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.ExpenseCategoryEntity;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.repository.ExpenseCategoryRepository;
import com.empresa.serpent.transactions.repository.ExpenseRepository;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateExpenseRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateExpenseResponse;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ExpenseApplicationService {

    private final TransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final SupplierRepository supplierRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;

    @Transactional
    public CreateExpenseResponse createExpense(CreateExpenseRequest request) {

        UserEntity createdBy = userRepository.findById(request.createdByUserId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.createdByUserId()));

        PaymentMethodEntity paymentMethod = null;
        if (request.paymentMethodId() != null) {
            paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                    .orElseThrow(() ->
                            new NotFoundException("Payment method not found: " + request.paymentMethodId()));
        }

        SupplierEntity supplier = null;
        if (request.supplierId() != null) {
            supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() ->
                            new NotFoundException("Supplier not found: " + request.supplierId()));

            if (!Boolean.TRUE.equals(supplier.getActive())) {
                throw new IllegalArgumentException("Supplier is inactive: " + request.supplierId());
            }
        }

        ExpenseCategoryEntity expenseCategory = expenseCategoryRepository.findById(request.expenseCategoryId())
                .orElseThrow(() ->
                        new NotFoundException("Expense category not found: " + request.expenseCategoryId()));

        if (!Boolean.TRUE.equals(expenseCategory.getActive())) {
            throw new IllegalArgumentException("Expense category is inactive: " + request.expenseCategoryId());
        }

        validateTotal(request.total());
        validateReceiptNumber(request.receiptNumber());

        TransactionEntity transaction = TransactionEntity.builder()
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.CONFIRMED)
                .description(normalizeOptional(request.description()))
                .paymentMethod(paymentMethod)
                .createdByUserEntity(createdBy)
                .total(request.total())
                .build();

        TransactionEntity savedTransaction = transactionRepository.save(transaction);

        ExpenseEntity expense = ExpenseEntity.builder()
                .transaction(savedTransaction)
                .supplier(supplier)
                .expenseCategory(expenseCategory)
                .receiptNumber(normalizeOptional(request.receiptNumber()))
                .reimbursable(request.reimbursable() != null ? request.reimbursable() : false)
                .notes(normalizeOptional(request.notes()))
                .build();

        ExpenseEntity savedExpense = expenseRepository.save(expense);

        return new CreateExpenseResponse(
                savedTransaction.getId(),
                savedExpense.getId(),
                savedTransaction.getStatus().name(),
                "Expense created successfully"
        );
    }

    private void validateTotal(BigDecimal total) {
        if (total == null) {
            throw new IllegalArgumentException("Total cannot be null");
        }

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total cannot be negative");
        }
    }

    private void validateReceiptNumber(String receiptNumber) {
        String normalized = normalizeOptional(receiptNumber);

        if (normalized == null) {
            return;
        }

        expenseRepository.findByReceiptNumberIgnoreCase(normalized)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Receipt number already exists: " + normalized);
                });
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}