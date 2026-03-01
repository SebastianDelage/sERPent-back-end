package com.empresa.serpent.catalog.domain;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Entity
@Table(name = "suppliers")
public class suppliers {
    public Long getSuppliers_id() {
        return suppliers_id;
    }

    public void setSuppliers_id(Long suppliers_id) {
        this.suppliers_id = suppliers_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDocument_type() {
        return document_type;
    }

    public void setDocument_type(String document_type) {
        this.document_type = document_type;
    }

    public String getDocument_number() {
        return document_number;
    }

    public void setDocument_number(String document_number) {
        this.document_number = document_number;
    }

    public String getTax_condtion() {
        return tax_condtion;
    }

    public void setTax_condtion(String tax_condtion) {
        this.tax_condtion = tax_condtion;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddres() {
        return addres;
    }

    public void setAddres(String addres) {
        this.addres = addres;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public Date getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Date created_at) {
        this.created_at = created_at;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long suppliers_id;
    @NotEmpty
    private String name;
    @NotEmpty
    private String document_type;
    @NotEmpty
    private String document_number;
    @NotEmpty
    private String tax_condtion;
    @NotEmpty
    private String phone;
    @NotEmpty
    private String email;
    @NotEmpty
    private String addres;
    @NotEmpty
    private String notes;
    @NotEmpty
    private String active;
    @NotNull
    @Column(name="crated_at")
    @DateTimeFormat(iso= DateTimeFormat.ISO.DATE)
    private Date created_at;
}
