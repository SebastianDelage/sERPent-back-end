package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<SupplierEntity,Long> {

    Optional<SupplierEntity> findByName(String name);

    List<SupplierEntity> findByNameContainingIgnoreCase(String name);

    List<SupplierEntity> findByActiveTrue();

    boolean existsByName(String name);

}
