package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Optional<ProductEntity> findBySku(String sku);

    boolean existsBySku(String sku);

    List<ProductEntity> findByActiveTrue();

    List<ProductEntity> findByActiveTrueAndNameContainingIgnoreCase(String name);

    List<ProductEntity> findByIdIn(Collection<Long> ids);

    boolean existsByNameIgnoreCase(String name);
}