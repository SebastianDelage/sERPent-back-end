package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Lists customers with optional name filter; inactive ones are excluded unless asked for. */
    @Query("""
           SELECT c FROM CustomerEntity c
           WHERE (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')))
             AND (:includeInactive = TRUE OR c.active = TRUE)
           ORDER BY c.name
           """)
    List<CustomerEntity> search(
            @Param("name") String name,
            @Param("includeInactive") boolean includeInactive
    );
}
