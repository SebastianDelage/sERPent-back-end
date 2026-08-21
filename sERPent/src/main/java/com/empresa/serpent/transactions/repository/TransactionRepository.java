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

/**
 * <h2>How sales reports attribute a row to a warehouse</h2>
 *
 * A SALE carries its warehouse directly ({@code sales.warehouse_id}). A RETURN does not:
 * {@code sale_returns} only points at the original sale, and the return is processed
 * against whatever warehouse the operator's session/terminal resolves to — which is not
 * necessarily where the sale happened.
 *
 * <p>Every warehouse-filtered query here attributes a return to the warehouse of its
 * ORIGINAL SALE, not to where it was processed. A return is a reversal of revenue and
 * belongs where the revenue was booked, so a branch's net figure stays internally
 * consistent: the sale and the money coming back sit on the same side.
 *
 * <p><b>Consequence, deliberately accepted:</b> a return processed at branch B against a
 * sale made at branch A does not appear in B's net at all — it lands in A's. The stock
 * movement is unaffected and still belongs to B, where the goods physically came back in
 * (see {@code SaleReturnApplicationService}), so inventory reports and sales reports will
 * legitimately disagree about that return's location. They are answering different
 * questions.
 */
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
           LEFT JOIN t.sale s
           LEFT JOIN SaleReturnEntity sr ON sr.transaction = t
           LEFT JOIN sr.originalSale os
           WHERE t.type IN (
                     com.empresa.serpent.transactions.domain.enums.TransactionType.SALE,
                     com.empresa.serpent.transactions.domain.enums.TransactionType.RETURN
                 )
             AND d.product IS NOT NULL
             AND (:dateFrom IS NULL OR t.date >= :dateFrom)
             AND (:dateTo IS NULL OR t.date <= :dateTo)
             AND (:unrestricted = TRUE
                  OR COALESCE(s.warehouse.id, os.warehouse.id) IN :warehouseIds)
           GROUP BY d.product.id, d.product.name
           ORDER BY COALESCE(SUM(CASE WHEN t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
                                      THEN d.quantity ELSE 0 END), 0) DESC
           """)
    List<SalesByProductResponse> getSalesByProductReport(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
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
           LEFT JOIN sales s ON s.transaction_id = t.transaction_id
           LEFT JOIN sale_returns sr ON sr.transaction_id = t.transaction_id
           LEFT JOIN sales os ON os.sale_id = sr.original_sale_id
           WHERE t.type IN ('SALE', 'RETURN')
             AND (:dateFrom IS NULL OR t.date >= :dateFrom)
             AND (:dateTo IS NULL OR t.date <= :dateTo)
             AND (:unrestricted = TRUE
                  OR COALESCE(s.warehouse_id, os.warehouse_id) IN :warehouseIds)
           GROUP BY CAST(t.date AS DATE)
           ORDER BY CAST(t.date AS DATE) DESC
           """, nativeQuery = true)
    List<SalesDailyProjection> getSalesDailyReportRaw(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
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
           LEFT JOIN t.sale s
           WHERE t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
             AND (:dateFrom IS NULL OR t.date >= :dateFrom)
             AND (:dateTo IS NULL OR t.date <= :dateTo)
             AND (:unrestricted = TRUE OR s.warehouse.id IN :warehouseIds)
           GROUP BY pm.id, pm.name
           ORDER BY SUM(t.total) DESC
           """)
    List<SalesByPaymentMethodResponse> getSalesByPaymentMethodReport(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
    );

    /**
     * The summary, split into the parts that add up to it:
     * {@code listPriceSales + paymentMethodSurcharges + manualAdjustments + returnsTotal
     * == netSales}.
     *
     * <p>One query on purpose. Splitting it in three would let a sale committed between
     * them land in some parts and not others, so the identity would fail intermittently
     * under concurrency — a single statement sees a single snapshot.
     *
     * <p>The line-level figures come from scalar subqueries rather than a join: joining
     * transactions to its details is 1:N and would fan out SUM(t.total). They are anchored
     * on the stored {@code d.subtotal}, so a line's list-price part and its surcharge part
     * sum back to it exactly, by algebra rather than by rounding. They are also restricted
     * to SALE lines: a return's lines carry a negative subtotal and no base price, and
     * would otherwise be counted a second time on top of returnsTotal.
     *
     * <p>The join to {@code sales} is safe — one row per transaction, enforced by
     * ux_sales_transaction — and returns have no sales row, so SUM skips their NULL. The
     * two extra joins added for the warehouse filter ({@code sale_returns} and the
     * original sale behind it) are safe for the same reason: ux_sale_returns_transaction
     * makes them at most one row, and a transaction is either a sale or a return, never
     * both.
     *
     * <p>The warehouse filter is repeated in the subqueries rather than hoisted, because
     * they are independent statements: leaving them unfiltered would sum every branch's
     * line figures against one branch's totals and break the identity outright.
     */
    @Query(value = """
       SELECT
           COUNT(CASE WHEN t.type = 'SALE' THEN 1 END) AS transactions,
           COALESCE(SUM(CASE WHEN t.type = 'RETURN' THEN t.total END), 0) AS returnsTotal,
           COALESCE(SUM(t.total), 0) AS netSales,
           COALESCE(AVG(CASE WHEN t.type = 'SALE' THEN t.total END), 0) AS averageTicket,
           COALESCE(SUM(s.adjustment_amount), 0) AS manualAdjustments,
           COALESCE((
               SELECT SUM(CASE WHEN d.base_unit_price IS NOT NULL
                               THEN d.base_unit_price * d.quantity
                               ELSE d.subtotal END)
               FROM transaction_details d
               JOIN transactions td ON td.transaction_id = d.transaction_id
               LEFT JOIN sales sd ON sd.transaction_id = td.transaction_id
               WHERE d.transaction_type = 'SALE'
                 AND (:dateFrom IS NULL OR td.date >= :dateFrom)
                 AND (:dateTo IS NULL OR td.date <= :dateTo)
                 AND (:unrestricted = TRUE OR sd.warehouse_id IN :warehouseIds)
           ), 0) AS listPriceSales,
           COALESCE((
               SELECT SUM(CASE WHEN d.base_unit_price IS NOT NULL
                               THEN d.subtotal - (d.base_unit_price * d.quantity)
                               ELSE 0 END)
               FROM transaction_details d
               JOIN transactions td ON td.transaction_id = d.transaction_id
               LEFT JOIN sales sd ON sd.transaction_id = td.transaction_id
               WHERE d.transaction_type = 'SALE'
                 AND (:dateFrom IS NULL OR td.date >= :dateFrom)
                 AND (:dateTo IS NULL OR td.date <= :dateTo)
                 AND (:unrestricted = TRUE OR sd.warehouse_id IN :warehouseIds)
           ), 0) AS paymentMethodSurcharges
       FROM transactions t
       LEFT JOIN sales s ON s.transaction_id = t.transaction_id
       LEFT JOIN sale_returns sr ON sr.transaction_id = t.transaction_id
       LEFT JOIN sales os ON os.sale_id = sr.original_sale_id
       WHERE t.type IN ('SALE', 'RETURN')
         AND (:dateFrom IS NULL OR t.date >= :dateFrom)
         AND (:dateTo IS NULL OR t.date <= :dateTo)
         AND (:unrestricted = TRUE
              OR COALESCE(s.warehouse_id, os.warehouse_id) IN :warehouseIds)
       """, nativeQuery = true)
    SalesSummaryProjection getSalesSummaryReportRaw(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
    );
}