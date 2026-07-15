package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, Long> {

    Optional<PaymentMethodEntity> findByName(String name);

    boolean existsByName(String name);

    /** Lists payment methods; inactive ones are excluded unless asked for. */
    @Query("""
           SELECT pm FROM PaymentMethodEntity pm
           WHERE (:includeInactive = TRUE OR pm.active = TRUE)
           ORDER BY pm.name
           """)
    List<PaymentMethodEntity> search(@Param("includeInactive") boolean includeInactive);
}