package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.ProductEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "product_transformation_inputs")
public class ProductTransformationInputEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_transformation_input_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "description")
    private String description;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_transformation_id", nullable = false)
    private ProductTransformationEntity transformation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;
}