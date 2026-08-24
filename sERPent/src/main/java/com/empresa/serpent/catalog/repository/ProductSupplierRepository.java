package com.empresa.serpent.catalog.repository;


import com.empresa.serpent.catalog.domain.entity.ProductSupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSupplierRepository
        extends JpaRepository<ProductSupplierEntity, Long> {

    List<ProductSupplierEntity> findByProductId(Long productId);

    List<ProductSupplierEntity> findByProductIdAndActiveTrue(Long productId);

    List<ProductSupplierEntity> findBySupplierEntityId(Long supplierId);

    Optional<ProductSupplierEntity> findByProductIdAndSupplierEntityId(
            Long productId, Long supplierId);

    Optional<ProductSupplierEntity> findByProductIdAndPreferredTrue(Long productId);

    boolean existsByProductIdAndSupplierEntityId(Long productId, Long supplierId);

    /**
     * The preferred supplier of each of the given products, in one query.
     *
     * <p>For the replenishment report, which needs a supplier per line: asking product by
     * product would be one query per shortage. Products with no preferred supplier are simply
     * absent from the result — they still belong in the report, they just have nobody to
     * propose.
     *
     * <p>Fetches the supplier eagerly because every caller reads its name straight away, and
     * a lazy proxy here would put the N+1 back where the batch just removed it.
     */
    @Query("""
           SELECT ps FROM ProductSupplierEntity ps
           JOIN FETCH ps.supplierEntity
           WHERE ps.product.id IN :productIds
             AND ps.preferred = TRUE
             AND ps.active = TRUE
           """)
    List<ProductSupplierEntity> findPreferredForProducts(@Param("productIds") List<Long> productIds);
}
