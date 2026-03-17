package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<SupplierEntity, Long> {

    Optional<SupplierEntity> findByNameIgnoreCase(String name);

    List<SupplierEntity> findByActiveTrue();

    List<SupplierEntity> findByActiveTrueAndNameContainingIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}