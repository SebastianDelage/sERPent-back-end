package com.empresa.serpent.inventory.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;

import java.util.Date;
@Entity
@Table(name = "warehouse")
public class warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long warehouserId;
    @NotEmpty
    private String name;
    private Boolean active;
    @NotEmpty
    private Date cratedAt;

    public warehouse() {
    }

    public Long getWarehouserId() {
        return warehouserId;
    }

    public void setWarehouserId(Long warehouserId) {
        this.warehouserId = warehouserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Date getCratedAt() {
        return cratedAt;
    }

    public void setCratedAt(Date cratedAt) {
        this.cratedAt = cratedAt;
    }
}
