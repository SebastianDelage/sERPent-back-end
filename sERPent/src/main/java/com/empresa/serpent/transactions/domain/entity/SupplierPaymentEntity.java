package com.empresa.serpent.transactions.domain.entity;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.users.domain.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Paying down what we owe a supplier.
 *
 * <p>NOT an {@link ExpenseEntity}, and deliberately so. The purchase already hit the
 * result when the goods came in; booking the payment as an expense would count the same
 * money twice. Expenses live in their own table and this is not it, so a supplier payment
 * cannot reach the expense listing at all.
 *
 * <p>Mirrors {@link CustomerPaymentEntity}: balance-based, not allocated to any particular
 * purchase.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "supplier_payments")
public class SupplierPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_payment_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;

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
