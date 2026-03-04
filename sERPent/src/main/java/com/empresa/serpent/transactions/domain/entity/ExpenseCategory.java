package com.empresa.serpent.transactions.domain.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "expense_categories")
public class ExpenseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_category_id", nullable = false, updatable = false)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 120, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
