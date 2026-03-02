package com.empresa.serpent.transactions.domain.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="expense_categories")
public class ExpenseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_category_id",nullable = false,updatable = false)
    private Long id;
    @NotEmpty
    @Column(name = "name",length = 100,unique = true)
    private String name;
    @Column(name = "description")
    private String description;
    @Column(name = "active", nullable = false)
    @NotEmpty
    private Boolean active;
}
