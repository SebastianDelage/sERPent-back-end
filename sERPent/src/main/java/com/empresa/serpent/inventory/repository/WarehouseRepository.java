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
    // Los CAST no son decoracion, y van en CADA aparicion del parametro. Sin ellos, listar SIN
    // termino de busqueda manda el parametro a PostgreSQL como binario y la consulta revienta
    // con "no existe la funcion lower(bytea)". En H2 anda igual, que es por lo que no se vio
    // antes. El por que, y por que no alcanza con castear una sola: docs/OPTIONAL_FILTERS.md.
    @Query("""
           SELECT w FROM WarehouseEntity w
           WHERE (CAST(:name AS String) IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
             AND (:includeInactive = TRUE OR w.active = TRUE)
           ORDER BY w.name
           """)
    List<WarehouseEntity> search(@Param("name") String name, @Param("includeInactive") boolean includeInactive);
}