package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long> {

    Optional<WarehouseEntity> findByName(String name);

    /** Used by the offline bootstrap sync, which only ever needs active warehouses. */
    List<WarehouseEntity> findByActiveTrue();

    boolean existsByName(String name);

    /** Lists warehouses; inactive ones are excluded unless asked for. */
    @Query("""
           SELECT w FROM WarehouseEntity w
           WHERE (:includeInactive = TRUE OR w.active = TRUE)
           ORDER BY w.name
           """)
    List<WarehouseEntity> search(@Param("includeInactive") boolean includeInactive);
}