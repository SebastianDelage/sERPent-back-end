package com.empresa.serpent.catalog.domain;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name="product_suppliers")
public class productSuppliers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productSuppliersId;
    @NotNull
    private Double CostPrice;
    @NotEmpty
    private String preferred; // no me acuerdo que es este campo
    @NotEmpty
    private Boolean active;

    public Long getProductSuppliersId() {
        return productSuppliersId;
    }

    public void setProductSuppliersId(Long productSuppliersId) {
        this.productSuppliersId = productSuppliersId;
    }

    public Double getCostPrice() {
        return CostPrice;
    }

    public void setCostPrice(Double costPrice) {
        CostPrice = costPrice;
    }

    public String getPreferred() {
        return preferred;
    }

    public void setPreferred(String preferred) {
        this.preferred = preferred;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
