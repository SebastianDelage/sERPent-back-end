package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import com.empresa.serpent.transactions.domain.entity.ExpenseEntity;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.web.dto.filter.TransactionFilter;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<TransactionEntity> hasType(TransactionType type) {
        return (root, query, cb) ->
                type == null ? cb.conjunction() : cb.equal(root.get("type"), type);
    }

    public static Specification<TransactionEntity> hasStatus(TransactionStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<TransactionEntity> dateFrom(LocalDateTime from) {
        return (root, query, cb) ->
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<TransactionEntity> dateTo(LocalDateTime to) {
        return (root, query, cb) ->
                to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("date"), to);
    }

    public static Specification<TransactionEntity> createdByUserId(Long userId) {
        return (root, query, cb) ->
                userId == null ? cb.conjunction()
                        : cb.equal(root.get("createdByUserEntity").get("id"), userId);
    }

    public static Specification<TransactionEntity> paymentMethodId(Long paymentMethodId) {
        return (root, query, cb) ->
                paymentMethodId == null ? cb.conjunction()
                        : cb.equal(root.get("paymentMethod").get("id"), paymentMethodId);
    }

    public static Specification<TransactionEntity> descriptionContains(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + text.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("description")), like);
        };
    }

    /**
     * Restricts the history to the branches the caller may see.
     *
     * <p>A transaction has no warehouse column of its own, so its branch is derived from
     * the inventory movements it produced. That choice is what makes this uniform: every
     * stock-moving type writes movements, and a TRANSFER writes two — one at each end — so
     * it stays visible from either side without a special case.
     *
     * <p>Expenses are the exception, because they move money and not stock and therefore
     * have no movements. They carry their own branch instead, and a GENERAL expense (no
     * branch at all) is visible to everyone: it belongs to the company, so it is not
     * somebody else's branch data.
     */
    public static Specification<TransactionEntity> withinScope(WarehouseScope scope) {
        return (root, query, cb) -> {
            if (scope.unrestricted()) {
                return cb.conjunction();
            }

            Subquery<Long> movements = query.subquery(Long.class);
            Root<InventoryMovementEntity> movement = movements.from(InventoryMovementEntity.class);
            movements.select(cb.literal(1L)).where(
                    cb.equal(movement.get("transaction"), root),
                    movement.get("warehouse").get("id").in(scope.warehouseIds()));

            Subquery<Long> expenses = query.subquery(Long.class);
            Root<ExpenseEntity> expense = expenses.from(ExpenseEntity.class);
            expenses.select(cb.literal(1L)).where(
                    cb.equal(expense.get("transaction"), root),
                    cb.or(
                            cb.isNull(expense.get("warehouse")),
                            expense.get("warehouse").get("id").in(scope.warehouseIds())));

            return cb.or(cb.exists(movements), cb.exists(expenses));
        };
    }

    public static Specification<TransactionEntity> fromFilter(TransactionFilter f) {
        if (f == null) {
            return Specification.where(null);
        }

        return Specification
                .where(hasType(f.type()))
                .and(hasStatus(f.status()))
                .and(dateFrom(f.dateFrom()))
                .and(dateTo(f.dateTo()))
                .and(createdByUserId(f.createdByUserId()))
                .and(paymentMethodId(f.paymentMethodId()))
                .and(descriptionContains(f.text()));
    }
}