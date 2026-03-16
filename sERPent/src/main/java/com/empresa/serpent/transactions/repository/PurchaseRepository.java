package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.PurchaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<PurchaseEntity, Long> {

    Optional<PurchaseEntity> findByTransactionId(Long transactionId);

    Optional<PurchaseEntity> findByReceiptNumberIgnoreCase(String receiptNumber);

    boolean existsByReceiptNumberIgnoreCase(String receiptNumber);
}