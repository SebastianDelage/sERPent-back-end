package com.empresa.serpent.catalog.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Which suppliers a product can be bought from, and under what code.
 *
 * <p>Deliberately does NOT hold a price. What we last paid is already recorded, exactly and
 * with its date, on the purchase lines; a second copy here would be a number that answers
 * the same question and drifts the first time someone loads a purchase without coming back
 * to update the catalog. The replenishment report derives the last price from the purchases
 * themselves.
 *
 * <p>At most ONE active supplier per product may be {@code preferred} — that is the one the
 * replenishment report proposes. Enforced in {@code ProductSupplierService}: PostgreSQL also
 * carries a partial unique index for it, but H2 has no partial indexes, so the service is
 * where the guarantee actually lives and the index is a second line of defence.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "product_suppliers")
public class ProductSupplierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_supplier_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplierEntity;

    /**
     * The supplier's own code for this product: what appears on their price list and their
     * invoice, and what you quote back at them when ordering.
     *
     * <p>NOT our SKU, and deliberately not unique across suppliers — two suppliers can
     * perfectly well use the same code for different things. Optional, because plenty of
     * small suppliers do not use one at all.
     */
    @Column(name = "supplier_product_code", length = 80)
    private String supplierProductCode;

    /** The supplier the replenishment report proposes. At most one active one per product. */
    @Builder.Default
    @Column(name = "preferred", nullable = false)
    private Boolean preferred = false;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;
}
