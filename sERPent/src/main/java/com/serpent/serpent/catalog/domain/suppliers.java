package com.serpent.serpent.catalog.domain;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Entity
@Table(name = "suppliers")
public class suppliers {
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
