package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.PurchaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<PurchaseEntity, Long> {

    Optional<PurchaseEntity> findByTransactionId(Long transactionId);

    Optional<PurchaseEntity> findByReceiptNumberIgnoreCase(String receiptNumber);

    boolean existsByReceiptNumberIgnoreCase(String receiptNumber);

    /** The credit purchases that make up a supplier's balance, oldest first. */
    @Query("""
           SELECT p FROM PurchaseEntity p
           JOIN FETCH p.transaction t
           WHERE p.supplier.id = :supplierId
             AND p.onCredit = TRUE
           ORDER BY t.date, p.id
           """)
    List<PurchaseEntity> findCreditPurchasesBySupplierId(@Param("supplierId") Long supplierId);

    @Query("""
           SELECT COALESCE(SUM(p.transaction.total), 0)
           FROM PurchaseEntity p
           WHERE p.supplier.id = :supplierId
             AND p.onCredit = TRUE
           """)
    BigDecimal sumCreditPurchasesBySupplierId(@Param("supplierId") Long supplierId);
}