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

    /** Restricts to one branch. General expenses have no warehouse and never match. */
    public static Specification<ExpenseEntity> hasWarehouseId(Long warehouseId) {
        return (root, query, cb) ->
                warehouseId == null
                        ? cb.conjunction()
                        : cb.equal(root.get("warehouse").get("id"), warehouseId);
    }

    /** The general expenses: the ones that belong to the company and not to any branch. */
    public static Specification<ExpenseEntity> isGeneral() {
        return (root, query, cb) -> cb.isNull(root.get("warehouse"));
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

        return everythingButWarehouse(filter)
                .and(hasWarehouseId(filter.warehouseId()));
    }

    /**
     * The same filter with the branch restriction swapped for "has no branch at all".
     *
     * <p>This is what the excluded-general-expenses figure is counted over: the user's other
     * filters still apply, but the warehouse one is replaced rather than dropped. Answers
     * "what did filtering by branch leave out", which is a different question from the one
     * {@link #fromFilter} answers.
     */
    public static Specification<ExpenseEntity> generalFromFilter(ExpenseFilter filter) {
        if (filter == null) {
            return isGeneral();
        }

        return everythingButWarehouse(filter).and(isGeneral());
    }

    private static Specification<ExpenseEntity> everythingButWarehouse(ExpenseFilter filter) {
        return Specification
                .where(hasSupplierId(filter.supplierId()))
                .and(hasExpenseCategoryId(filter.expenseCategoryId()))
                .and(hasReimbursable(filter.reimbursable()))
                .and(hasTransactionId(filter.transactionId()))
                .and(hasReceiptNumber(filter.receiptNumber()));
    }
}