package com.empresa.serpent.transactions.web.dto.response;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;

import java.math.BigDecimal;

/**
 * One line of a transaction.
 *
 * <p>The three {@code base/applied} fields are the frozen breakdown of this line's
 * payment-method surcharge and are null together when no rule applied. They are
 * per line, and distinct from the sale-wide manual adjustment
 * ({@code adjustmentType}/{@code adjustmentValue}/{@code adjustmentAmount}) that
 * {@link TransactionDetailResponse} carries for the whole sale.
 */
public record TransactionItemResponse(
        Long id,
        Long productId,
        String productName,
        /** Read-only, from the product. Lets the UI pick a sensible quantity step. */
        UnitOfMeasure unitOfMeasure,
        String description,
        BigDecimal quantity,
        /** The effective price charged, surcharge included. */
        BigDecimal unitPrice,
        BigDecimal subtotal,
        /** Price before this line's payment-method rule. Null when none applied. */
        BigDecimal baseUnitPrice,
        /** Signed rule percentage: positive surcharges, negative discounts. */
        BigDecimal appliedPercentage,
        /** The payment method's name as it read at the time of the sale. */
        String appliedMethodName
) {
}