package com.empresa.serpent.transactions.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "payment_methods")
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 80, unique = true)
    private String name;

    /**
     * This is the money in the drawer.
     *
     * <p>A flag rather than a name check: payment methods are catalog data the owner edits,
     * so "Efectivo" may be renamed, translated, or spelled some other way, and matching on
     * the name would break silently the day it changes. Only the flagged method is affected
     * by an expense or a supplier payment taking money out of the till.
     *
     * <p>At most one method may carry it, enforced by {@code PaymentMethodService}. The
     * database does not express it: a partial unique index would do it in PostgreSQL, but
     * H2 has none and both migration sets have to stay identical in effect.
     */
    @Builder.Default
    @Column(name = "is_cash", nullable = false)
    private Boolean isCash = false;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
