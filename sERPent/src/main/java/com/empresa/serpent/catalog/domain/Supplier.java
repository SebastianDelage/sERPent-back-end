package com.empresa.serpent.catalog.domain;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suppliers_id",nullable = false,updatable = false)
    private Long id;
    @NotEmpty
    @Column(name = "name",nullable = false)
    private String name;
    @NotEmpty
    @Column(name = "document_type",nullable = false)
    private String documentType;
    @NotEmpty
    @Column(name = "document_number",nullable = false)
    private String documentNumber;
    @NotEmpty
    @Column(name = "tax_condition",nullable = false)
    private String taxCondtion;
    @NotEmpty
    @Column(name = "phone",nullable = false)
    private String phone;
    @NotEmpty
    @Column(name = "email",nullable = false)
    private String email;
    @NotEmpty
    @Column(name = "addres",nullable = false)
    private String addres;
    @NotEmpty
    @Column(name = "notes",nullable = false)
    private String notes;
    @NotEmpty
    @Column(name = "active")
    private String active;
    @NotNull
    @Column(name="crated_at")
    @DateTimeFormat(iso= DateTimeFormat.ISO.DATE)
    private Date created_at;
}
