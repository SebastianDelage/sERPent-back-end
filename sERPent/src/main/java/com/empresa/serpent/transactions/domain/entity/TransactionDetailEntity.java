package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.transactions.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "transaction_details")
public class TransactionDetailEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_detail_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "description")
    private String description;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    /*
     Frozen breakdown of this line's payment-method surcharge (mechanism 2), so the
     sale detail can explain where unitPrice came from long after the catalog price
     or the rule changed. All three are null together when no rule applied — the
     ordinary case — and unitPrice alone tells the whole story.

     Not to be confused with the sale-wide manual adjustment (adjustmentType /
     adjustmentValue / adjustmentAmount), which lives on SaleEntity: that one is a
     single figure for the whole sale, this one is per line.
     */

    /** What the line was priced at before the rule. Null when no rule applied. */
    @Column(name = "base_unit_price", precision = 19, scale = 4)
    private BigDecimal baseUnitPrice;

    /** Signed rule percentage: positive surcharges, negative discounts. */
    @Column(name = "applied_percentage", precision = 9, scale = 4)
    private BigDecimal appliedPercentage;

    /**
     * The payment method's name as it read when the sale was made. Frozen rather than
     * read from the transaction's method, which would show today's name on an old sale
     * if the method was later renamed.
     */
    @Column(name = "applied_method_name", length = 80)
    private String appliedMethodName;

    /**
     * Mirror of the parent transaction's type. Denormalized so the amount-sign CHECK
     * constraints can be scoped per type (a CHECK cannot reach into another table).
     * Never set by hand: it is copied from the parent on persist, and the parent's
     * type is immutable, so the copy cannot drift.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30, updatable = false)
    private TransactionType transactionType;

    @PrePersist
    @PreUpdate
    private void calculateSubtotal() {
        if (quantity != null && unitPrice != null) {
            this.subtotal = unitPrice.multiply(quantity);
        }
        if (transaction != null) {
            this.transactionType = transaction.getType();
        }
    }
}
