package com.empresa.serpent.inventory.domain.entity;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A product's reorder configuration at one warehouse: EXCEPTIONS to the product's own
 * figures, never a replacement for them.
 *
 * <p>THE TABLE NAME IS NARROWER THAN THE CONTENT. It was born holding only
 * {@code minimum_stock} and now holds the whole triplet — minimum, reorder point and
 * reorder quantity. The physical name stays {@code product_warehouse_minimum_stock} so no
 * index, constraint or foreign key has to be rewritten for a cosmetic gain; read it as
 * "per-warehouse reorder overrides".
 *
 * <p>Each of the three resolves in cascade INDEPENDENTLY: for a given (product, warehouse)
 * the value is this row's if it is not null, and the product's otherwise. That
 * independence is deliberate — a branch that sells three times as much needs to order
 * earlier, which is a different statement from needing a higher floor, and it must be able
 * to make either one without the other.
 *
 * <p>Two figures play different roles and are not interchangeable: the MINIMUM is the floor
 * you do not want to break through, the REORDER POINT is when you order so the goods arrive
 * before you touch it. Hence the invariant, checked on the RESOLVED pair rather than on the
 * raw columns: the reorder point that applies at a warehouse may not sit below the minimum
 * that applies there, whichever level each of them came from.
 *
 * <p>A product with no minimum at either level can never be low and stays out of the report
 * entirely: there are goods nobody wants to track. Same for the reorder point and
 * replenishment.
 *
 * <p>Lives in {@code inventory} because that is where warehouses and stock live;
 * {@code catalog} deliberately depends on nothing from here.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "product_warehouse_minimum_stock",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_pwms_product_warehouse",
                        columnNames = {"product_id", "warehouse_id"}
                )
        }
)
public class ProductWarehouseMinimumStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_warehouse_minimum_stock_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    /**
     * This warehouse's own floor, or null to inherit the product's.
     *
     * <p>Nullable so a branch can override the trigger without restating the floor. Copying
     * the product's minimum in just to fill the column would create a duplicate that goes
     * stale the day the product's minimum changes.
     */
    @Column(name = "minimum_stock", precision = 12, scale = 3)
    private BigDecimal minimumStock;

    /**
     * When this warehouse reorders, or null to inherit the product's reorder point.
     *
     * <p>A branch that sells three times as much has to order earlier, not just hold a
     * higher floor — that is the whole reason this is separate from {@link #minimumStock}.
     */
    @Column(name = "reorder_point", precision = 12, scale = 3)
    private BigDecimal reorderPoint;

    /** How much this warehouse orders, or null to inherit the product's quantity. */
    @Column(name = "reorder_quantity", precision = 12, scale = 3)
    private BigDecimal reorderQuantity;
}
