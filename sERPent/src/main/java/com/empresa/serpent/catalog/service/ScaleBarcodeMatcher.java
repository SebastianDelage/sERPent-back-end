package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.entity.ScaleBarcodeFormatEntity;
import com.empresa.serpent.catalog.repository.ScaleBarcodeFormatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;

/**
 * Which configured format, if any, claims a given code.
 *
 * <p>The till decodes in the browser so a scan does not wait on the network. This class
 * exists for the other side of the same rule: the API must not accept a scale label typed
 * into a product's barcode field just because the request skipped the form. Same
 * longest-prefix-wins rule as the frontend decoder; if one moves, both move.
 */
@Component
@RequiredArgsConstructor
public class ScaleBarcodeMatcher {

    private final ScaleBarcodeFormatRepository repository;

    /**
     * The active format that claims this code, if any.
     *
     * <p>Longest prefix wins, so a format for "20" beats a broader one for "2" without
     * anybody having to maintain a priority column.
     */
    @Transactional(readOnly = true)
    public Optional<ScaleBarcodeFormatEntity> match(String code) {
        if (code == null || !code.trim().matches("\\d+")) {
            return Optional.empty();
        }

        String clean = code.trim();

        return repository.search(false).stream()
                .filter(f -> clean.length() == f.getTotalLength() && clean.startsWith(f.getPrefix()))
                .max(Comparator.comparingInt(f -> f.getPrefix().length()));
    }
}
