package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SaleReturnRepository extends JpaRepository<SaleReturnEntity, Long> {

    Optional<SaleReturnEntity> findByTransactionId(Long transactionId);

    List<SaleReturnEntity> findByOriginalSaleId(Long originalSaleId);
}