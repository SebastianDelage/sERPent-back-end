package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.reports.repository.projection.SalesDailyProjection;
import com.empresa.serpent.reports.repository.projection.SalesSummaryProjection;
import com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse;
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

    @Query(value = """
           SELECT
               CAST(t.date AS DATE) AS date,
               COUNT(DISTINCT t.transaction_id) AS transactions,
               SUM(t.total) AS totalRevenue
           FROM transactions t
           WHERE t.type = 'SALE'
           GROUP BY CAST(t.date AS DATE)
           ORDER BY CAST(t.date AS DATE) DESC
           """, nativeQuery = true)
    List<SalesDailyProjection> getSalesDailyReportRaw();

    @Query("""
           SELECT new com.empresa.serpent.reports.web.dto.response.SalesByPaymentMethodResponse(
               pm.id,
               pm.name,
               COUNT(t.id),
               SUM(t.total)
           )
           FROM TransactionEntity t
           JOIN t.paymentMethod pm
           WHERE t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
           GROUP BY pm.id, pm.name
           ORDER BY SUM(t.total) DESC
           """)
    List<SalesByPaymentMethodResponse> getSalesByPaymentMethodReport();

    @Query(value = """
       SELECT
           COUNT(t.transaction_id) AS transactions,
           COALESCE(SUM(t.total), 0) AS totalRevenue,
           COALESCE(AVG(t.total), 0) AS averageTicket
       FROM transactions t
       WHERE t.type = 'SALE'
       """, nativeQuery = true)
    SalesSummaryProjection getSalesSummaryReportRaw();
}