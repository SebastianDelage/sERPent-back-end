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
    // Los CAST no son decoracion, y van en CADA aparicion del parametro. Sin ellos, listar SIN
    // termino de busqueda manda el parametro a PostgreSQL como binario y la consulta revienta
    // con "no existe la funcion lower(bytea)". En H2 anda igual, que es por lo que no se vio
    // antes. El por que, y por que no alcanza con castear una sola: docs/OPTIONAL_FILTERS.md.
    @Query("""
           SELECT p FROM ProductEntity p
           WHERE (CAST(:name AS String) IS NULL
                  OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%'))
                  OR LOWER(p.sku) = LOWER(CAST(:name AS String))
                  OR p.barcode = :name)
             AND (:includeInactive = TRUE OR p.active = TRUE)
           ORDER BY p.name
           """)
    List<ProductEntity> search(
            @Param("name") String name,
            @Param("includeInactive") boolean includeInactive
    );

    /** Active-only lookup, used when scanning a barcode at sale time. */
    Optional<ProductEntity> findFirstByBarcodeAndActiveTrue(String barcode);

    /** Used to enforce barcode uniqueness across active and inactive products alike. */
    Optional<ProductEntity> findByBarcode(String barcode);

    /** Used to enforce scale-code uniqueness. Always called with the normalized form. */
    Optional<ProductEntity> findByScaleCode(String scaleCode);
}