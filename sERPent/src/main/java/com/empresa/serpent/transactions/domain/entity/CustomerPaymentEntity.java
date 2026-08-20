package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.users.domain.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A customer paying down their balance.
 *
 * <p>NOT a {@link TransactionEntity}, and deliberately so. Collecting a debt is not a
 * sale: the sale already hit the result when it happened, and counting the collection
 * again would inflate revenue. Every sales aggregation filters on
 * {@code transactions.type}, so keeping payments out of that table means no future query
 * can pull them into revenue by forgetting a filter.
 *
 * <p>It is not allocated to any particular sale either — the balance is what matters, so a
 * partial payment is just a smaller number here.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "customer_payments")
public class CustomerPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_payment_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    /** Required: this is real cash moving, and the till has to be able to explain it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethodEntity paymentMethod;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserEntity createdByUserEntity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
