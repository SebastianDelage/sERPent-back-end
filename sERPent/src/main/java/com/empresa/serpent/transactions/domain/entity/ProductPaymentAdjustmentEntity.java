package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A product's price adjustment for one payment method: the classic case is tobacco
 * carrying a surcharge when paid by card.
 *
 * <p>Lives in {@code transactions} rather than {@code catalog} because it needs both
 * {@link ProductEntity} and {@link PaymentMethodEntity}; {@code catalog} deliberately
 * depends on nothing from {@code transactions}, and putting the rule there would
 * invert that.
 *
 * <p>Percentage only, and the direction is carried by the sign — negative discounts,
 * positive surcharges — the same single-source-of-truth convention as the sale-level
 * manual adjustment. One rule per (product, payment method); {@code active} lets a
 * rule be switched off without losing it, since surcharges change over time.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "product_payment_adjustments")
public class ProductPaymentAdjustmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_payment_adjustment_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethodEntity paymentMethod;

    /**
     * Signed: -5 takes 5% off the line, +10 adds 10%. Never below -100, which would
     * drive the line's unit price negative.
     */
    @Column(name = "adjustment_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal adjustmentPercentage;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
