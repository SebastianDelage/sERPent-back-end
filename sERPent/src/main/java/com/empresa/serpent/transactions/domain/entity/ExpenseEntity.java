    package com.empresa.serpent.transactions.domain.entity;

    import jakarta.persistence.*;
    import com.empresa.serpent.catalog.domain.SupplierEntity;
    import lombok.*;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Entity
    @Table(name = "expenses")
    public class ExpenseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "expense_id", nullable = false, updatable = false)
        private Long id;

        @Column(name = "receipt_number", length = 80)
        private String receiptNumber;

        @Builder.Default
        @Column(name = "reimbursable", nullable = false)
        private Boolean reimbursable = false;

        @Column(name = "notes")
        private String notes;

        @OneToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "transaction_id", nullable = false, unique = true)
        private TransactionEntity transaction;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "supplier_id")
        private SupplierEntity supplier;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "expense_category_id", nullable = false)
        private ExpenseCategoryEntity expenseCategory;
    }
