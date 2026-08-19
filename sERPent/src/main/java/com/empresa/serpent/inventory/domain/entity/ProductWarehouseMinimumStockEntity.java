package com.empresa.serpent.inventory.domain.entity;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A product's minimum stock at one warehouse: an EXCEPTION to the product's own
 * {@code minimumStock}, not a replacement for it.
 *
 * <p>Low stock resolves in cascade. For a given (product, warehouse) the threshold is
 * the row here if one exists, and the product's {@code minimumStock} otherwise. Branches
 * sell at different volumes, so the central warehouse may warrant a floor of 50 where a
 * small branch is fine at 5 — but most products need no such distinction, and forcing a
 * row per warehouse for all of them would be noise. Defining one is opt-in; deleting it
 * falls straight back to the product-level minimum.
 *
 * <p>A product with no minimum at either level can never be low and stays out of the
 * report entirely: there are goods nobody wants to track.
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

    @Column(name = "minimum_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal minimumStock;
}
