package com.empresa.serpent.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    /*
     SKU is optional because not every product necessarily has a barcode
     or internal SKU code. Some businesses may sell items individually
     (for example bags, accessories or loose items) without barcode scanning.

     If present, it must be unique.
     */
    @Column(name = "sku", length = 80, unique = true)
    private String sku;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "minimum_stock", precision = 12, scale = 3)
    private BigDecimal minimumStock;

    @Column(name = "reorder_point", precision = 12, scale = 3)
    private BigDecimal reorderPoint;

    @Column(name = "reorder_quantity", precision = 12, scale = 3)
    private BigDecimal reorderQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}