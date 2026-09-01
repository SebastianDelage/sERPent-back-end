package com.empresa.serpent.catalog.repository;

import com.empresa.serpent.catalog.domain.entity.ScaleBarcodeFormatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScaleBarcodeFormatRepository extends JpaRepository<ScaleBarcodeFormatEntity, Long> {

    /**
     * The pair that decides which format claims a scanned code. Checked in the service so
     * the clash can be explained ("ya lo usa X"); the UNIQUE constraint behind it is the
     * net for anything that writes without going through the service.
     */
    Optional<ScaleBarcodeFormatEntity> findByPrefixAndTotalLength(String prefix, Integer totalLength);

    @Query("""
           SELECT f FROM ScaleBarcodeFormatEntity f
           WHERE (:includeInactive = TRUE OR f.active = TRUE)
           ORDER BY LENGTH(f.prefix) DESC, f.prefix, f.id
           """)
    List<ScaleBarcodeFormatEntity> search(@Param("includeInactive") boolean includeInactive);
}
