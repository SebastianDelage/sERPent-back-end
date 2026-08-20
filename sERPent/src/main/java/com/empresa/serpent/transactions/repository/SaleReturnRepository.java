package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SaleReturnRepository extends JpaRepository<SaleReturnEntity, Long> {

    Optional<SaleReturnEntity> findByTransactionId(Long transactionId);

    List<SaleReturnEntity> findByOriginalSaleId(Long originalSaleId);

    /**
     * Returns taken against a customer's credit sales, oldest first.
     *
     * <p>Goods coming back from a sale that was never paid do not send cash out the door:
     * they reduce what the customer owes. The return's total is already stored negative,
     * so it lowers the balance by being added like any other movement.
     */
    @Query("""
           SELECT sr FROM SaleReturnEntity sr
           JOIN FETCH sr.transaction t
           JOIN sr.originalSale os
           WHERE os.customer.id = :customerId
             AND os.onCredit = TRUE
           ORDER BY t.date, sr.id
           """)
    List<SaleReturnEntity> findAgainstCreditSalesByCustomerId(@Param("customerId") Long customerId);

    /** Already negative, so it subtracts when added to the balance. */
    @Query("""
           SELECT COALESCE(SUM(sr.transaction.total), 0)
           FROM SaleReturnEntity sr
           WHERE sr.originalSale.customer.id = :customerId
             AND sr.originalSale.onCredit = TRUE
           """)
    BigDecimal sumAgainstCreditSalesByCustomerId(@Param("customerId") Long customerId);
}