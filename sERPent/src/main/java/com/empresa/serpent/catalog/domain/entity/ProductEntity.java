package com.empresa.serpent.catalog.domain.entity;

import com.empresa.serpent.catalog.domain.enums.UnitOfMeasure;
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private UnitOfMeasure unitOfMeasure = UnitOfMeasure.UNIT;


    @Column(name = "sku", length = 80, unique = true)
    private String sku;

    @Column(name = "barcode", length = 50)
    private String barcode;

    /**
     * The product number that the SCALE prints inside a weighed label — the one the scale
     * listing shows as "C:". A different thing from the barcode above (a product can have
     * both) and a different thing from the PLU: on the Kretz at the shop, milanesa is PLU 1
     * and code 16. Stored with leading zeros stripped, so the 000016 read off a label and
     * the 16 typed off the listing are one value and the UNIQUE constraint means something.
     */
    @Column(name = "scale_code", length = 20)
    private String scaleCode;

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