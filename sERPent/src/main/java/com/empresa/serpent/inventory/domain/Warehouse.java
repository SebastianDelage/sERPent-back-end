package com.empresa.serpent.inventory.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Date;
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "warehouse")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "warehouse_id",nullable = false,updatable = false)
    private Long id;
    @NotEmpty
    @Column(name = "name",length = 100,unique = true)
    private String name;
    @Column(name = "active")
    private Boolean active;
    @Column(name = "crated_at")
    @NotEmpty
    private Date cratedAt;

}
