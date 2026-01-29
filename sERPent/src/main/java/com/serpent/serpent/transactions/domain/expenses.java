package com.serpent.serpent.transactions.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;

@Entity
@Table(name = "expenses")
public class expenses {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long expenses_id;
    @NotEmpty
    private Double recepit_numbrer; //No recuerdo que es este dato
    @NotEmpty
    private Boolean reimubursable;
    @ManyToOne
    @JoinColumn(name ="transaction_id",nullable = false)
    private  Transcation transcation;
    @ManyToOne
    @JoinColumn(name = "supplier_id",nullable = false)
    private Supplier supplier;
    @ManyToOne
    @JoinColumn(name = "expense_category_id",nullable = false)
    private Expense_category expenseCategory;
}
