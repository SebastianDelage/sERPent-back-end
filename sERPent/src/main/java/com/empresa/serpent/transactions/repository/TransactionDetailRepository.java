package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.reports.repository.projection.LastPurchasePriceProjection;
import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TransactionDetailRepository extends JpaRepository<TransactionDetailEntity, Long> {

    List<TransactionDetailEntity> findByTransactionId(Long transactionId);

    List<TransactionDetailEntity> findByProductId(Long productId);

    List<TransactionDetailEntity> findByTransactionIdAndProductId(Long transactionId, Long productId);

    /**
     * The most recent purchase price for each of the given products, with its date and the
     * supplier it came from.
     *
     * <p>Feeds the replenishment report, which needs to say what a product last cost without
     * keeping a second copy of that figure anywhere. Reads the purchase lines directly, so
     * the number is the purchase.
     *
     * <p>"Most recent" is settled by date and then by line id, so two purchases loaded in the
     * same instant still have one definite last — otherwise the report would flicker between
     * them from one refresh to the next.
     *
     * <p>Only CONFIRMED purchases count: a cancelled one is not something we paid.
     */
    @Query("""
           SELECT d.product.id AS productId,
                  d.unitPrice AS unitPrice,
                  t.date AS purchaseDate,
                  pu.supplier.name AS supplierName
           FROM TransactionDetailEntity d
           JOIN d.transaction t
           JOIN PurchaseEntity pu ON pu.transaction = t
           WHERE t.type = com.empresa.serpent.transactions.domain.enums.TransactionType.PURCHASE
             AND t.status = com.empresa.serpent.transactions.domain.enums.TransactionStatus.CONFIRMED
             AND d.product.id IN :productIds
             AND NOT EXISTS (
                 SELECT 1 FROM TransactionDetailEntity later
                  JOIN later.transaction lt
                  WHERE later.product = d.product
                    AND lt.type = com.empresa.serpent.transactions.domain.enums.TransactionType.PURCHASE
                    AND lt.status = com.empresa.serpent.transactions.domain.enums.TransactionStatus.CONFIRMED
                    AND (lt.date > t.date OR (lt.date = t.date AND later.id > d.id))
             )
           """)
    List<LastPurchasePriceProjection> findLastPurchasePrices(@Param("productIds") List<Long> productIds);
}
