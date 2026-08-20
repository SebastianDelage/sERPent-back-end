    package com.empresa.serpent.transactions.domain.entity;

    import jakarta.persistence.*;
    import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
    import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
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

        /**
         * The branch this expense belongs to, when it belongs to one.
         *
         * <p>NULL means GENERAL — a company-wide expense such as the accountant or the
         * insurance — and is a deliberate answer, not a missing one. Rent and electricity
         * belong to a location; the accountant does not, and making someone pick a branch
         * for it would invent a fact.
         *
         * <p>Consequence worth knowing: filtering expenses by branch leaves the general
         * ones out, so the branches never add up to the total. The listing reports the
         * excluded general expenses alongside so that gap is stated rather than discovered.
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "warehouse_id")
        private WarehouseEntity warehouse;
    }
