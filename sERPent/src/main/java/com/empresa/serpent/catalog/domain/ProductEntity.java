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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /*
     FUTURE INVENTORY CONFIGURATION

     In a future version of sERPent the Product entity should include
     inventory control fields such as:

         minimumStock
         reorderPoint
         reorderQuantity

     These fields will allow the system to:

         - detect low stock automatically
         - suggest replenishment orders
         - support warehouse planning

     Once implemented, StockQueryService.lowStock() should compare the
     current product stock against Product.minimumStock instead of using
     a dynamic threshold parameter.
     */
}