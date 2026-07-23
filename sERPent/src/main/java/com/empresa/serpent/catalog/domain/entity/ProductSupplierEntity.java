package com.empresa.serpent.catalog.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "product_suppliers")
public class ProductSupplierEntity {

    /**
     * Product ↔ supplier relation: which suppliers a product can be bought from,
     * under which code and at what price.
     *
     * MODELLED AHEAD OF USE — there is deliberately no service or controller on top
     * of this yet. The table and repository exist because product↔supplier is a
     * first-class entity in every reference ERP (Odoo's product.supplierinfo, SAP
     * Business One's preferred vendor and vendor catalog numbers, Dynamics BC's Item
     * Vendor Catalog), so it belongs in the model of a general retail ERP even before
     * the business layer lands.
     *
     * Implement it together with the replenishment report: on its own the report can
     * only say a product is running low, whereas with this relation it can say who to
     * buy it from and at what price — and the purchase form can autocomplete a
     * supplier's catalog instead of having the operator type every line. A minimum
     * viable version needs only the supplier link, the supplier's own product code
     * (distinct from our SKU, since it's what appears on their invoice) and the last
     * purchase price; lead time, minimum order quantity and price validity are
     * supermarket-scale concerns that can wait.
     */

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

    @NotNull
    @Column(name = "cost_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal costPrice;

    @Builder.Default
    @Column(name = "preferred", nullable = false)
    private Boolean preferred = false;  // preferred = true marca el proveedor preferido para ese producto:
                                                        // -cuando cargás una compra, el sistema sugiere ese proveedor
                                                        // -para costos, tiempos de entrega, cotizaciones

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;
}
