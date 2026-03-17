package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "product_transformations")
public class ProductTransformationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_transformation_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "notes")
    private String notes;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private TransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    @OneToMany(
            mappedBy = "transformation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<ProductTransformationInputEntity> inputs = new ArrayList<>();

    @OneToMany(
            mappedBy = "transformation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<ProductTransformationOutputEntity> outputs = new ArrayList<>();
}