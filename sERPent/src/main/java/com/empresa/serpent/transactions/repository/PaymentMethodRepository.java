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

    /**
     * The method that represents the drawer, if the owner configured one.
     *
     * <p>Optional rather than a list because at most one method may carry the flag — see
     * {@code PaymentMethodService.validateSingleCashMethod}. Empty means the till count
     * cannot tell cash apart yet, which callers must report rather than treat as zero.
     */
    Optional<PaymentMethodEntity> findByIsCashTrue();

    /** Lists payment methods; inactive ones are excluded unless asked for. */
    @Query("""
           SELECT pm FROM PaymentMethodEntity pm
           WHERE (:name IS NULL OR LOWER(pm.name) LIKE LOWER(CONCAT('%', :name, '%')))
             AND (:includeInactive = TRUE OR pm.active = TRUE)
           ORDER BY pm.name
           """)
    List<PaymentMethodEntity> search(@Param("name") String name, @Param("includeInactive") boolean includeInactive);
}