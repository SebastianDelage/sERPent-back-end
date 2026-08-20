package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.transactions.domain.enums.AdjustmentType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "sales")
public class SaleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id", nullable = false, updatable = false)
    private Long id;

    /**
     * The named customer, when there is one. Optional: most sales are counter sales, which
     * carry only the free-text fields below.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_document")
    private String customerDocument;

    /**
     * The sale was taken on credit: nothing was collected, and the total was added to the
     * customer's balance instead.
     *
     * <p>A flag of its own rather than a payment method called "Fiado", which would put
     * money that never arrived into the sales-by-payment-method report. A sale on credit
     * carries no payment method at all, and {@code customer} is then mandatory — free text
     * cannot owe anything.
     */
    @Builder.Default
    @Column(name = "on_credit", nullable = false)
    private Boolean onCredit = false;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "tax_total", precision = 19, scale = 4)
    private BigDecimal taxTotal;

    /*
     Manual adjustment on the sale total (a discount or a surcharge). It lives on the
     sale header, not as a transaction line: it is money, not a stock movement.
     Direction is carried by the sign, so there is no separate discount/surcharge flag.
     */

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    @Builder.Default
    private AdjustmentType adjustmentType = AdjustmentType.NONE;

    /** The cashier's raw input: -10 for a 10% discount, 500 for a $500 surcharge. */
    @Column(name = "adjustment_value", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal adjustmentValue = BigDecimal.ZERO;

    /**
     * The resolved adjustment, frozen at creation time and never recomputed:
     * negative discounts, positive surcharges. {@code total = sum(subtotals) + adjustmentAmount}.
     */
    @Column(name = "adjustment_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;

    @Column(name = "cae")
    private String cae;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private WarehouseEntity warehouse;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;
}
