package com.empresa.serpent.transactions.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sale_returns")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaleReturnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_return_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "reason")
    private String reason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private TransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_sale_id", nullable = false)
    private SaleEntity originalSale;
}