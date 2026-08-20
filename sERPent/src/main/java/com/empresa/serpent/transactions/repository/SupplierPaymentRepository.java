package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.SupplierPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPaymentEntity, Long> {

    List<SupplierPaymentEntity> findBySupplierId(Long supplierId);

    @Query("""
           SELECT COALESCE(SUM(p.amount), 0)
           FROM SupplierPaymentEntity p
           WHERE p.supplier.id = :supplierId
           """)
    BigDecimal sumBySupplierId(@Param("supplierId") Long supplierId);

    /**
     * Payments made in a date range, for reconciling the till.
     *
     * <p>These are NOT expenses — the purchase already hit the result when the goods came
     * in — but the cash physically left, so the day's count needs somewhere to see it.
     */
    @Query("""
           SELECT p FROM SupplierPaymentEntity p
           WHERE (:dateFrom IS NULL OR p.paymentDate >= :dateFrom)
             AND (:dateTo IS NULL OR p.paymentDate <= :dateTo)
             AND (:paymentMethodId IS NULL OR p.paymentMethod.id = :paymentMethodId)
           ORDER BY p.paymentDate DESC, p.id DESC
           """)
    List<SupplierPaymentEntity> search(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("paymentMethodId") Long paymentMethodId
    );
}
