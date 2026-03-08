package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long> {

    Optional<WarehouseEntity> findByName(String name);

    List<WarehouseEntity> findByActiveTrue();

    boolean existsByName(String name);
}