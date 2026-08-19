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
    @Query("""
           SELECT t FROM TerminalEntity t
           WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%')))
             AND (:includeInactive = TRUE OR t.active = TRUE)
           ORDER BY t.name
           """)
    List<TerminalEntity> search(@Param("name") String name, @Param("includeInactive") boolean includeInactive);
}
