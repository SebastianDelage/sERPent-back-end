package com.empresa.serpent.catalog.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A named customer, needed to carry a current-account balance.
 *
 * <p>Optional on a sale: most sales are counter sales, which keep using the free-text
 * customer name on the sale itself. A record here is only required to sell on credit —
 * free text cannot owe money.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_number", length = 40)
    private String documentNumber;

    @Column(name = "phone", length = 50)
    private String phone;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
