package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<SaleEntity, Long> {

    Optional<SaleEntity> findByTransactionId(Long transactionId);

    Optional<SaleEntity> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<SaleEntity> findByCustomerNameContainingIgnoreCase(String customerName);

    List<SaleEntity> findByCustomerDocument(String customerDocument);

    List<SaleEntity> findByDueDate(LocalDate dueDate);

    /** The credit sales that make up a customer's balance, oldest first. */
    @Query("""
           SELECT s FROM SaleEntity s
           JOIN FETCH s.transaction t
           WHERE s.customer.id = :customerId
             AND s.onCredit = TRUE
           ORDER BY t.date, s.id
           """)
    List<SaleEntity> findCreditSalesByCustomerId(@Param("customerId") Long customerId);

    @Query("""
           SELECT COALESCE(SUM(s.transaction.total), 0)
           FROM SaleEntity s
           WHERE s.customer.id = :customerId
             AND s.onCredit = TRUE
           """)
    BigDecimal sumCreditSalesByCustomerId(@Param("customerId") Long customerId);

    /**
     * What was sold on credit in a period, as one figure.
     *
     * <p>Reported alongside the sales-by-payment-method breakdown. A credit sale has no
     * payment method, so it is absent from that breakdown by construction, and without
     * this number the report would silently stop adding up to total sales.
     */
    @Query("""
           SELECT COALESCE(SUM(s.transaction.total), 0)
           FROM SaleEntity s
           WHERE s.onCredit = TRUE
             AND (:dateFrom IS NULL OR s.transaction.date >= :dateFrom)
             AND (:dateTo IS NULL OR s.transaction.date <= :dateTo)
             AND (:unrestricted = TRUE OR s.warehouse.id IN :warehouseIds)
           """)
    BigDecimal sumCreditSales(
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo") java.time.LocalDateTime dateTo,
            @Param("unrestricted") boolean unrestricted,
            @Param("warehouseIds") List<Long> warehouseIds
    );
}
