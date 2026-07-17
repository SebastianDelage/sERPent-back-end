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

    /** Quick search: partial name, or exact SKU / barcode. */
    @Query("""
           SELECT p FROM ProductEntity p
           WHERE (:name IS NULL
                  OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))
                  OR LOWER(p.sku) = LOWER(:name)
                  OR p.barcode = :name)
             AND (:includeInactive = TRUE OR p.active = TRUE)
           ORDER BY p.name
           """)
    List<ProductEntity> search(
            @Param("name") String name,
            @Param("includeInactive") boolean includeInactive
    );

    /** Barcodes aren't unique by design, so the first active match wins. */
    Optional<ProductEntity> findFirstByBarcodeAndActiveTrue(String barcode);
}