package com.serpent.serpent.catalog.domain;


import jakarta.persistence.*;
import jdk.jfr.Name;

@Entity
@Table(name="product_suppliers")
public class productSuppliers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productSuppliersId;

    private Double CostPrice;
    private String preferred; // no me acuerdo que es este campo
    private Boolean active;
    
}
