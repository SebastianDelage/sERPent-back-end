package com.empresa.serpent.catalog.repository;


import com.empresa.serpent.catalog.domain.entity.ProductSupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
