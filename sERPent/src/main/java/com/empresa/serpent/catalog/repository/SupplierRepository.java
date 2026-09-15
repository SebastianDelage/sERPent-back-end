package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<SupplierEntity, Long> {

    Optional<SupplierEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Lists suppliers with optional name filter; inactive ones are excluded unless asked for. */
    // Los CAST no son decoracion, y van en CADA aparicion del parametro. Sin ellos, listar SIN
    // termino de busqueda manda el parametro a PostgreSQL como binario y la consulta revienta
    // con "no existe la funcion lower(bytea)". En H2 anda igual, que es por lo que no se vio
    // antes. El por que, y por que no alcanza con castear una sola: docs/OPTIONAL_FILTERS.md.
    @Query("""
           SELECT s FROM SupplierEntity s
           WHERE (CAST(:name AS String) IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
             AND (:includeInactive = TRUE OR s.active = TRUE)
           ORDER BY s.name
           """)
    List<SupplierEntity> search(
            @Param("name") String name,
            @Param("includeInactive") boolean includeInactive
    );
}