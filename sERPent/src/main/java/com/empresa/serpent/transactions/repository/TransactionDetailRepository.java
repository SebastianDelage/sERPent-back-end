package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.TransactionDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionDetailRepository extends JpaRepository<TransactionDetailEntity, Long> {

    List<TransactionDetailEntity> findByTransactionId(Long transactionId);

    List<TransactionDetailEntity> findByProductId(Long productId);

    List<TransactionDetailEntity> findByTransactionIdAndProductId(Long transactionId, Long productId);
}
