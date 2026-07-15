package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<SupplierEntity, Long> {

    Optional<SupplierEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Lists suppliers with optional name filter; inactive ones are excluded unless asked for. */
    @Query("""
           SELECT s FROM SupplierEntity s
           WHERE (:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')))
             AND (:includeInactive = TRUE OR s.active = TRUE)
           ORDER BY s.name
           """)
    List<SupplierEntity> search(
            @Param("name") String name,
            @Param("includeInactive") boolean includeInactive
    );
}