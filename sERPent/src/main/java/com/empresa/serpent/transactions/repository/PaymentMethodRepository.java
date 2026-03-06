package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, Long> {

    Optional<PaymentMethodEntity> findByName(String name);

    boolean existsByName(String name);

    List<PaymentMethodEntity> findByActiveTrue();
}
