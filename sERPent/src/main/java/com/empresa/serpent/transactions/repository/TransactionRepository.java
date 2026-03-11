package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.reports.web.dto.response.SalesByProductResponse;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionStatus;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends
        JpaRepository<TransactionEntity, Long>,
        JpaSpecificationExecutor<TransactionEntity> {

    List<TransactionEntity> findByType(TransactionType type);

    List<TransactionEntity> findByStatus(TransactionStatus status);

    List<TransactionEntity> findByDateBetween(LocalDateTime start, LocalDateTime end);

    List<TransactionEntity> findByCreatedByUserEntityId(Long userId);

    @Query("""
           SELECT new com.empresa.serpent.reports.web.dto.response.SalesByProductResponse(
               d.product.id,
               d.product.name,
               SUM(d.quantity),
               SUM(d.subtotal)
           )
           FROM TransactionEntity t
           JOIN t.details d
           WHERE t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
             AND d.product IS NOT NULL
           GROUP BY d.product.id, d.product.name
           ORDER BY SUM(d.quantity) DESC
           """)
    List<SalesByProductResponse> getSalesByProductReport();
}