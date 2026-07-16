package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
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

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_document")
    private String customerDocument;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "tax_total", precision = 19, scale = 4)
    private BigDecimal taxTotal;

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
