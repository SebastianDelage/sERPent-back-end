package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import com.empresa.serpent.transactions.web.dto.filter.TransactionFilter;
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
