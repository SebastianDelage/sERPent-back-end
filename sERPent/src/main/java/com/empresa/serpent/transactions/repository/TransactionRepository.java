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
import org.springframework.data.repository.query.Param;

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
               COALESCE(SUM(CASE WHEN t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
                                 THEN d.quantity ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.RETURN
                                 THEN d.quantity ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
                                 THEN d.subtotal ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.RETURN
                                 THEN d.subtotal ELSE 0 END), 0),
               COALESCE(SUM(d.subtotal), 0),
               COALESCE(SUM(d.subtotal), 0)
           )
           FROM TransactionEntity t
           JOIN t.details d
           WHERE t.type IN (
                     com.empresa.serpent.transactions.domain.enums.TransactionType.SALE,
                     com.empresa.serpent.transactions.domain.enums.TransactionType.RETURN
                 )
             AND d.product IS NOT NULL
             AND (:dateFrom IS NULL OR t.date >= :dateFrom)
             AND (:dateTo IS NULL OR t.date <= :dateTo)
           GROUP BY d.product.id, d.product.name
           ORDER BY COALESCE(SUM(CASE WHEN t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
                                      THEN d.quantity ELSE 0 END), 0) DESC
           """)
    List<SalesByProductResponse> getSalesByProductReport(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    /** A return lands on the day it was registered, not the day of the original sale. */
    @Query(value = """
           SELECT
               CAST(t.date AS DATE) AS date,
               COUNT(DISTINCT CASE WHEN t.type = 'SALE' THEN t.transaction_id END) AS transactions,
               COALESCE(SUM(CASE WHEN t.type = 'SALE' THEN t.total END), 0) AS grossSales,
               COALESCE(SUM(CASE WHEN t.type = 'RETURN' THEN t.total END), 0) AS returnsTotal,
               COALESCE(SUM(t.total), 0) AS netSales
           FROM transactions t
           WHERE t.type IN ('SALE', 'RETURN')
             AND (:dateFrom IS NULL OR t.date >= :dateFrom)
             AND (:dateTo IS NULL OR t.date <= :dateTo)
           GROUP BY CAST(t.date AS DATE)
           ORDER BY CAST(t.date AS DATE) DESC
           """, nativeQuery = true)
    List<SalesDailyProjection> getSalesDailyReportRaw(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    /**
     * Gross sales per payment method: returns are deliberately excluded. A return is
     * recorded without a payment method (refunding cash for a card sale is a real
     * case), so attributing it to the original sale's method would assert something
     * the system does not know.
     */
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
             AND (:dateFrom IS NULL OR t.date >= :dateFrom)
             AND (:dateTo IS NULL OR t.date <= :dateTo)
           GROUP BY pm.id, pm.name
           ORDER BY SUM(t.total) DESC
           """)
    List<SalesByPaymentMethodResponse> getSalesByPaymentMethodReport(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    @Query(value = """
       SELECT
           COUNT(CASE WHEN t.type = 'SALE' THEN 1 END) AS transactions,
           COALESCE(SUM(CASE WHEN t.type = 'SALE' THEN t.total END), 0) AS grossSales,
           COALESCE(SUM(CASE WHEN t.type = 'RETURN' THEN t.total END), 0) AS returnsTotal,
           COALESCE(SUM(t.total), 0) AS netSales,
           COALESCE(AVG(CASE WHEN t.type = 'SALE' THEN t.total END), 0) AS averageTicket
       FROM transactions t
       WHERE t.type IN ('SALE', 'RETURN')
         AND (:dateFrom IS NULL OR t.date >= :dateFrom)
         AND (:dateTo IS NULL OR t.date <= :dateTo)
       """, nativeQuery = true)
    SalesSummaryProjection getSalesSummaryReportRaw(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );
}