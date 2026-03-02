package com.empresa.serpent.transactions.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import com.empresa.serpent.catalog.domain.Suppliers;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "expenses")
public class Expenses {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exppenses_id",nullable = false,updatable = false)
    private Long id;
    @NotNull
    @Column(name = "recepit_number")
    private Double recepitNumber; //No recuerdo que es este dato
    @NotNull
    @Column(name = "reimubursable")
    private Boolean reimubursable;
    @ManyToOne
    @JoinColumn(name ="transaction_id",nullable = false)
    private Transaction transaction;
    @ManyToOne
    @JoinColumn(name = "supplier_id",nullable = false)
    private Suppliers suppliers;
    @ManyToOne
    @JoinColumn(name = "expense_category_id",nullable = false)
    private ExpenseCategory expenseCategory;

}
