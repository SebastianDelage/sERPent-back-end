package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.CustomerPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPaymentEntity, Long> {

    List<CustomerPaymentEntity> findByCustomerId(Long customerId);

    @Query("""
           SELECT COALESCE(SUM(p.amount), 0)
           FROM CustomerPaymentEntity p
           WHERE p.customer.id = :customerId
           """)
    BigDecimal sumByCustomerId(@Param("customerId") Long customerId);

    /**
     * Collections in a date range, for reconciling the till.
     *
     * <p>This is the only place these amounts surface as money movements. They are NOT
     * revenue — the sale they settle already counted when it happened — but the cash is
     * physically in the drawer, so without this view the day's count cannot be explained.
     */
    @Query("""
           SELECT p FROM CustomerPaymentEntity p
           WHERE (:dateFrom IS NULL OR p.paymentDate >= :dateFrom)
             AND (:dateTo IS NULL OR p.paymentDate <= :dateTo)
             AND (:paymentMethodId IS NULL OR p.paymentMethod.id = :paymentMethodId)
           ORDER BY p.paymentDate DESC, p.id DESC
           """)
    List<CustomerPaymentEntity> search(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("paymentMethodId") Long paymentMethodId
    );
}
