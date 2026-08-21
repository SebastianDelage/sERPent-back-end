package com.empresa.serpent.cashcount.repository;

import com.empresa.serpent.cashcount.repository.projection.MethodAmountProjection;
import com.empresa.serpent.transactions.domain.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The money that moved through one branch's till in one shift, grouped by payment method.
 *
 * <p>Six queries rather than one union, because the six sources live in different tables
 * with different rules about what counts. Each one carries the reason it is shaped the way
 * it is; the service adds them up.
 *
 * <p>Every query takes {@code periodFrom} as nullable, meaning "since the first record
 * there is" — which is what a branch's very first count covers, before there is a previous
 * close to anchor to.
 *
 * <p>KNOWN LIMITATION, not fixed here: a sale made offline gets its {@code date} from the
 * moment it was synced, not the moment it was made, because {@code TransactionEntity.date}
 * is a {@code @CreationTimestamp} and the sync module does not carry the original time. A
 * sale made before a close but synced after it therefore lands in the following shift. That
 * belongs to the sync module, not to the count.
 *
 * <p>Extends {@code JpaRepository<TransactionEntity, Long>} only to have a managed home for
 * these queries; it is not the transaction repository and nothing should treat it as one.
 */
public interface CashCountSourceRepository extends JpaRepository<TransactionEntity, Long> {

    /**
     * Sales collected in the period, by the method they were collected with.
     *
     * <p>Credit sales are excluded by construction rather than by a filter: they carry no
     * payment method, so the join drops them. That is the correct answer — nothing was
     * collected, so nothing entered the till. They raise the customer's balance and show up
     * later, as a collection, if and when the customer pays.
     *
     * <p>The total already includes the manual adjustment, which is how rounding at the
     * counter is modelled (selling 1366.50 and charging 1400). So this is the money that
     * actually came in, not the list price.
     */
    @Query("""
           SELECT pm.id AS paymentMethodId, COALESCE(SUM(t.total), 0) AS amount
           FROM TransactionEntity t
           JOIN t.paymentMethod pm
           JOIN t.sale s
           WHERE t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.SALE
             AND s.warehouse.id = :warehouseId
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           GROUP BY pm.id
           """)
    List<MethodAmountProjection> sumSalesByMethod(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    /**
     * Money handed back for returns, by the method it went out through.
     *
     * <p>Returns against CREDIT sales are excluded: no money was ever collected for them, so
     * none goes back out — the customer's balance drops instead. Counting them would report
     * a payout that never happened.
     *
     * <p>Totals are already stored negative, so this sums to a negative number and adds
     * rather than subtracts. Rows recorded before the refund method was asked for have no
     * method and are dropped by the join; they are reported separately as unattributable, so
     * the gap is stated instead of silently absorbed.
     */
    @Query("""
           SELECT pm.id AS paymentMethodId, COALESCE(SUM(t.total), 0) AS amount
           FROM SaleReturnEntity sr
           JOIN sr.transaction t
           JOIN t.paymentMethod pm
           JOIN sr.originalSale os
           WHERE os.warehouse.id = :warehouseId
             AND os.onCredit = FALSE
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           GROUP BY pm.id
           """)
    List<MethodAmountProjection> sumReturnsByMethod(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    /**
     * Collections against customer balances, by method.
     *
     * <p>Not revenue — the sale they settle already counted when it happened — but the money
     * is physically in the drawer, so the count has to see it.
     *
     * <p>Anchored on {@code createdAt}, not {@code paymentDate}: the latter is a date with no
     * time, so it cannot place a collection inside a shift.
     */
    @Query("""
           SELECT pm.id AS paymentMethodId, COALESCE(SUM(p.amount), 0) AS amount
           FROM CustomerPaymentEntity p
           JOIN p.paymentMethod pm
           WHERE p.warehouse.id = :warehouseId
             AND (:periodFrom IS NULL OR p.createdAt > :periodFrom)
             AND p.createdAt <= :periodTo
           GROUP BY pm.id
           """)
    List<MethodAmountProjection> sumCustomerPaymentsByMethod(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    /**
     * Payments made against supplier balances, by method. Money leaving, so the service
     * subtracts it.
     *
     * <p>Not expenses — the purchase already hit the result when the goods arrived — but the
     * cash physically left, so without this the count comes up short with no explanation.
     */
    @Query("""
           SELECT pm.id AS paymentMethodId, COALESCE(SUM(p.amount), 0) AS amount
           FROM SupplierPaymentEntity p
           JOIN p.paymentMethod pm
           WHERE p.warehouse.id = :warehouseId
             AND (:periodFrom IS NULL OR p.createdAt > :periodFrom)
             AND p.createdAt <= :periodTo
           GROUP BY pm.id
           """)
    List<MethodAmountProjection> sumSupplierPaymentsByMethod(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    /**
     * Expenses paid out of this branch, by method. Money leaving, so the service subtracts it.
     *
     * <p>Only expenses that NAME this branch count. A general expense — the accountant, the
     * insurance — has no branch on purpose, and it is not paid out of any one shop's drawer,
     * so charging it to whoever happens to be closing would invent a fact. Same rule the
     * expense listing already applies.
     */
    @Query("""
           SELECT pm.id AS paymentMethodId, COALESCE(SUM(t.total), 0) AS amount
           FROM ExpenseEntity e
           JOIN e.transaction t
           JOIN t.paymentMethod pm
           WHERE e.warehouse.id = :warehouseId
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           GROUP BY pm.id
           """)
    List<MethodAmountProjection> sumExpensesByMethod(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    /**
     * Purchases paid on the spot, by method. Money leaving, so the service subtracts it.
     *
     * <p>Buying stock and paying for it at the counter empties the drawer exactly like an
     * expense does. Purchases taken on credit are excluded: nothing was paid, the supplier's
     * balance went up instead, and it leaves the till later as a supplier payment.
     */
    @Query("""
           SELECT pm.id AS paymentMethodId, COALESCE(SUM(t.total), 0) AS amount
           FROM PurchaseEntity p
           JOIN p.transaction t
           JOIN t.paymentMethod pm
           WHERE p.warehouse.id = :warehouseId
             AND p.onCredit = FALSE
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           GROUP BY pm.id
           """)
    List<MethodAmountProjection> sumPurchasesByMethod(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    // ---------------------------------------------------------------------------------
    // What the queries above had to leave out.
    // ---------------------------------------------------------------------------------

    /**
     * Returns in the period that carry no refund method, as an absolute amount.
     *
     * <p>Recorded before the method was asked for, so nobody knows which bucket they left
     * from. Surfaced instead of guessed: an unexplained gap the cashier can see beats a
     * number that looks right and is not.
     */
    @Query("""
           SELECT COALESCE(SUM(ABS(t.total)), 0)
           FROM SaleReturnEntity sr
           JOIN sr.transaction t
           JOIN sr.originalSale os
           WHERE os.warehouse.id = :warehouseId
             AND os.onCredit = FALSE
             AND t.paymentMethod IS NULL
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           """)
    BigDecimal sumUnattributedReturns(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    @Query("""
           SELECT COUNT(sr)
           FROM SaleReturnEntity sr
           JOIN sr.transaction t
           JOIN sr.originalSale os
           WHERE os.warehouse.id = :warehouseId
             AND os.onCredit = FALSE
             AND t.paymentMethod IS NULL
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           """)
    long countUnattributedReturns(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    /** Expenses in the period with no payment method, for the same reason as the returns above. */
    @Query("""
           SELECT COALESCE(SUM(ABS(t.total)), 0)
           FROM ExpenseEntity e
           JOIN e.transaction t
           WHERE e.warehouse.id = :warehouseId
             AND t.paymentMethod IS NULL
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           """)
    BigDecimal sumUnattributedExpenses(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );

    @Query("""
           SELECT COUNT(e)
           FROM ExpenseEntity e
           JOIN e.transaction t
           WHERE e.warehouse.id = :warehouseId
             AND t.paymentMethod IS NULL
             AND (:periodFrom IS NULL OR t.date > :periodFrom)
             AND t.date <= :periodTo
           """)
    long countUnattributedExpenses(
            @Param("warehouseId") Long warehouseId,
            @Param("periodFrom") LocalDateTime periodFrom,
            @Param("periodTo") LocalDateTime periodTo
    );
}
