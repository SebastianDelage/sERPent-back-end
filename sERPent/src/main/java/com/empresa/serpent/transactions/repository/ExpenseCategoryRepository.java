package com.empresa.serpent.transactions.repository;

import com.empresa.serpent.transactions.domain.entity.ExpenseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, Long> {

    Optional<ExpenseCategoryEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Lists categories with optional name filter; inactive ones are excluded unless asked for. */
    @Query("""
           SELECT c FROM ExpenseCategoryEntity c
           WHERE (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')))
             AND (:includeInactive = TRUE OR c.active = TRUE)
           ORDER BY c.name
           """)
    List<ExpenseCategoryEntity> search(
            @Param("name") String name,
            @Param("includeInactive") boolean includeInactive
    );
}