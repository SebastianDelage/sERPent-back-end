package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ProductPaymentAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPaymentAdjustmentRepository
        extends JpaRepository<ProductPaymentAdjustmentEntity, Long> {

    /**
     * The rules that apply to a sale: one query for every product in the cart, so the
     * sale loop stays free of N+1. Inactive rules are left out — a switched-off rule
     * must not touch the price.
     */
    List<ProductPaymentAdjustmentEntity> findByPaymentMethodIdAndProductIdInAndActiveTrue(
            Long paymentMethodId, List<Long> productIds);

    List<ProductPaymentAdjustmentEntity> findByProductId(Long productId);

    boolean existsByProductIdAndPaymentMethodId(Long productId, Long paymentMethodId);

    Optional<ProductPaymentAdjustmentEntity> findByProductIdAndPaymentMethodId(
            Long productId, Long paymentMethodId);
}
