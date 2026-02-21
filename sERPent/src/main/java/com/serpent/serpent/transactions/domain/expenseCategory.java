package com.serpent.serpent.transactions.domain;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;

@Entity
@Table(name="expense_category")
public class expenseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long expenseCategoryId;
    @NotEmpty
    private String name;
    private String description;
    @NotEmpty
    private Boolean active;
}
