package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.web.dto.filter.ExpenseFilter;
import org.springframework.data.jpa.domain.Specification;

public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<ExpenseEntity> hasSupplierId(Long supplierId) {
        return (root, query, cb) ->
                supplierId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("supplier").get("id"), supplierId);
    }

    public static Specification<ExpenseEntity> hasExpenseCategoryId(Long expenseCategoryId) {
        return (root, query, cb) ->
                expenseCategoryId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("expenseCategory").get("id"), expenseCategoryId);
    }

    public static Specification<ExpenseEntity> hasReimbursable(Boolean reimbursable) {
        return (root, query, cb) ->
                reimbursable == null
                        ? cb.conjunction()
                        : cb.equal(root.get("reimbursable"), reimbursable);
    }

    public static Specification<ExpenseEntity> hasTransactionId(Long transactionId) {
        return (root, query, cb) ->
                transactionId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("transaction").get("id"), transactionId);
    }

    public static Specification<ExpenseEntity> hasReceiptNumber(String receiptNumber) {
        return (root, query, cb) -> {
            if (receiptNumber == null || receiptNumber.isBlank()) {
                return cb.conjunction();
            }

            return cb.equal(
                    cb.lower(root.get("receiptNumber")),
                    receiptNumber.trim().toLowerCase()
            );
        };
    }

    public static Specification<ExpenseEntity> fromFilter(ExpenseFilter filter) {
        if (filter == null) {
            return Specification.where(null);
        }

        return Specification
                .where(hasSupplierId(filter.supplierId()))
                .and(hasExpenseCategoryId(filter.expenseCategoryId()))
                .and(hasReimbursable(filter.reimbursable()))
                .and(hasTransactionId(filter.transactionId()))
                .and(hasReceiptNumber(filter.receiptNumber()));
    }
}