package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.inventory.domain.entity.TerminalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TerminalRepository extends JpaRepository<TerminalEntity, Long> {

    Optional<TerminalEntity> findByName(String name);

    /** Lists terminals; inactive ones are excluded unless asked for. */
    // Los CAST no son decoracion, y van en CADA aparicion del parametro. Sin ellos, listar SIN
    // termino de busqueda manda el parametro a PostgreSQL como binario y la consulta revienta
    // con "no existe la funcion lower(bytea)". En H2 anda igual, que es por lo que no se vio
    // antes. El por que, y por que no alcanza con castear una sola: docs/OPTIONAL_FILTERS.md.
    @Query("""
           SELECT t FROM TerminalEntity t
           WHERE (CAST(:name AS String) IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
             AND (:includeInactive = TRUE OR t.active = TRUE)
           ORDER BY t.name
           """)
    List<TerminalEntity> search(@Param("name") String name, @Param("includeInactive") boolean includeInactive);
}
