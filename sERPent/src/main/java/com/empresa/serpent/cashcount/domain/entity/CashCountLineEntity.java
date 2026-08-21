package com.empresa.serpent.cashcount.domain.entity;

import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One payment method's share of a till count: expected, counted, and the gap between them.
 *
 * <p>A child table rather than columns on the count, because payment methods are catalog
 * data: they get added and retired, so they cannot be a fixed set of columns.
 *
 * <p>{@link #paymentMethodName} and {@link #isCash} are FROZEN COPIES, not conveniences.
 * Renaming "Cash" to "Efectivo", or moving the cash flag to another method, must not change
 * what a count from last March says it counted — a historical record that shifts meaning
 * because someone edited the catalog is not a record.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "cash_count_lines")
public class CashCountLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_count_line_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_count_id", nullable = false)
    private CashCountEntity cashCount;

    /** Kept for traceability, but deliberately NOT what the count reads to display itself. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethodEntity paymentMethod;

    /** Frozen at close time. See the class javadoc. */
    @Column(name = "payment_method_name", nullable = false, length = 80)
    private String paymentMethodName;

    /** Frozen at close time: whether this method WAS the drawer when the count was taken. */
    @Column(name = "is_cash", nullable = false)
    private Boolean isCash;

    /** What the system believed should be there. Never recomputed after the close. */
    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    /** What the person actually counted. */
    @Column(name = "counted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal countedAmount;

    /**
     * {@code counted - expected}. Negative means the till is short.
     *
     * <p>Stored rather than derived on read: it is the number the owner acted on, and it
     * belongs to the photo like everything else here.
     */
    @Column(name = "difference_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal differenceAmount;
}
