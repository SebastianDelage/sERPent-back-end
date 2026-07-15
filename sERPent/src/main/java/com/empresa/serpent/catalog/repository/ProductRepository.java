package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Optional<ProductEntity> findBySku(String sku);

    boolean existsBySku(String sku);

    List<ProductEntity> findByIdIn(Collection<Long> ids);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
           SELECT p FROM ProductEntity p
           WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
             AND (:includeInactive = TRUE OR p.active = TRUE)
           ORDER BY p.name
           """)
    List<ProductEntity> search(
            @Param("name") String name,
            @Param("includeInactive") boolean includeInactive
    );
}