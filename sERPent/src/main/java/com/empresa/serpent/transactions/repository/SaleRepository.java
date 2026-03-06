package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<SaleEntity, Long> {

    Optional<SaleEntity> findByTransactionId(Long transactionId);

    Optional<SaleEntity> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<SaleEntity> findByCustomerNameContainingIgnoreCase(String customerName);

    List<SaleEntity> findByCustomerDocument(String customerDocument);

    List<SaleEntity> findByDueDate(LocalDate dueDate);
}
