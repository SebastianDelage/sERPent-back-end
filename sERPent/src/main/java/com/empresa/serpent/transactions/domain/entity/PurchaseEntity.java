package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "purchases")
public class PurchaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "receipt_number", length = 80)
    private String receiptNumber;

    @Column(name = "notes")
    private String notes;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private TransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private SupplierEntity supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    /**
     * The purchase was taken on credit: nothing was paid, and the total was added to the
     * supplier's balance instead.
     *
     * <p>An explicit flag, NOT inferred from a missing payment method. Purchases have
     * accepted a null payment method since they were introduced, with no defined meaning,
     * so reading that null as "on credit" would silently reinterpret rows that already
     * exist. {@code supplier} is mandatory when this is set — a balance needs someone to
     * belong to.
     */
    @Builder.Default
    @Column(name = "on_credit", nullable = false)
    private Boolean onCredit = false;
}