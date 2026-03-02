package com.empresa.serpent.catalog.domain;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="product_suppliers")
public class ProductSuppliers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prodcut_suppliers_id",nullable = false,updatable = false)
    private Long productSuppliersId;
    @NotNull
    @Column(name = "cost_price",nullable = false)
    private Double CostPrice;
    @NotEmpty
    @Column(name = "preferred",nullable = false)
    private String preferred; // no me acuerdo que es este campo
    @NotEmpty
    @Column(name = "active")
    private Boolean active;
}
