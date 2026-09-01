package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.entity.ScaleBarcodeFormatEntity;
import com.empresa.serpent.catalog.repository.ScaleBarcodeFormatRepository;
import com.empresa.serpent.catalog.web.dto.request.ScaleBarcodeFormatCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.ScaleBarcodeFormatUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.ScaleBarcodeFormatResponse;
import com.empresa.serpent.catalog.web.mapper.ScaleBarcodeFormatMapper;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScaleBarcodeFormatService {

    /** Lengths where the last digit is a standard EAN/UPC check digit. */
    private static final Set<Integer> CHECK_DIGIT_LENGTHS = Set.of(8, 12, 13);

    private final ScaleBarcodeFormatRepository repository;
    private final ScaleBarcodeFormatMapper mapper;

    @Transactional
    public ScaleBarcodeFormatResponse create(ScaleBarcodeFormatCreateRequest request) {
        validate(
                request.name(), request.prefix(), request.totalLength(),
                request.productCodeStart(), request.productCodeLength(),
                request.valueStart(), request.valueLength(),
                request.valueDecimals(), request.validateCheckDigit(), null
        );

        ScaleBarcodeFormatEntity entity = ScaleBarcodeFormatEntity.builder()
                .name(request.name().trim())
                .prefix(request.prefix().trim())
                .totalLength(request.totalLength())
                .productCodeStart(request.productCodeStart())
                .productCodeLength(request.productCodeLength())
                .valueStart(request.valueStart())
                .valueLength(request.valueLength())
                .valueType(request.valueType())
                .valueDecimals(request.valueDecimals())
                .validateCheckDigit(request.validateCheckDigit() == null || request.validateCheckDigit())
                .active(request.active() == null || request.active())
                .build();

        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    public ScaleBarcodeFormatResponse update(Long id, ScaleBarcodeFormatUpdateRequest request) {
        validate(
                request.name(), request.prefix(), request.totalLength(),
                request.productCodeStart(), request.productCodeLength(),
                request.valueStart(), request.valueLength(),
                request.valueDecimals(), request.validateCheckDigit(), id
        );

        ScaleBarcodeFormatEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scale barcode format not found: " + id));

        entity.setName(request.name().trim());
        entity.setPrefix(request.prefix().trim());
        entity.setTotalLength(request.totalLength());
        entity.setProductCodeStart(request.productCodeStart());
        entity.setProductCodeLength(request.productCodeLength());
        entity.setValueStart(request.valueStart());
        entity.setValueLength(request.valueLength());
        entity.setValueType(request.valueType());
        entity.setValueDecimals(request.valueDecimals());
        entity.setValidateCheckDigit(request.validateCheckDigit() == null || request.validateCheckDigit());

        if (request.active() != null) {
            entity.setActive(request.active());
        }

        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public ScaleBarcodeFormatResponse findById(Long id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scale barcode format not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<ScaleBarcodeFormatResponse> search(boolean includeInactive) {
        return repository.search(includeInactive).stream().map(mapper::toResponse).toList();
    }

    /**
     * Everything that would make a format decode WRONG rather than not at all.
     *
     * <p>A format is ten numbers that nobody can check by reading them, and a wrong one
     * does not fail loudly: it reads a valid product code out of the wrong digits and
     * charges a weight that was never on the scale. So the rules that CAN be checked are
     * checked here, and the ones that cannot are what the live tester on the admin screen
     * is for.
     */
    private void validate(
            String name, String prefix, Integer totalLength,
            Integer productCodeStart, Integer productCodeLength,
            Integer valueStart, Integer valueLength,
            Integer valueDecimals, Boolean validateCheckDigit,
            Long currentId
    ) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El nombre del formato es obligatorio.");
        }
        if (prefix == null || !prefix.trim().matches("\\d{1,4}")) {
            throw new ValidationException("El prefijo tiene que ser de 1 a 4 dígitos.");
        }

        String cleanPrefix = prefix.trim();

        int productCodeEnd = productCodeStart + productCodeLength - 1;
        int valueEnd = valueStart + valueLength - 1;

        if (productCodeEnd > totalLength) {
            throw new ValidationException(
                    "El código de producto ocupa hasta la posición " + productCodeEnd
                            + " pero el código tiene " + totalLength + " dígitos.");
        }
        if (valueEnd > totalLength) {
            throw new ValidationException(
                    "El valor ocupa hasta la posición " + valueEnd
                            + " pero el código tiene " + totalLength + " dígitos.");
        }
        if (productCodeStart <= valueEnd && valueStart <= productCodeEnd) {
            throw new ValidationException(
                    "El código de producto (posiciones " + productCodeStart + " a " + productCodeEnd
                            + ") y el valor (posiciones " + valueStart + " a " + valueEnd
                            + ") se pisan. Cada dígito puede ser una cosa sola.");
        }

        int prefixEnd = cleanPrefix.length();
        if (productCodeStart <= prefixEnd) {
            throw new ValidationException(
                    "El código de producto empieza en la posición " + productCodeStart
                            + ", que todavía es parte del prefijo \"" + cleanPrefix + "\".");
        }
        if (valueStart <= prefixEnd) {
            throw new ValidationException(
                    "El valor empieza en la posición " + valueStart
                            + ", que todavía es parte del prefijo \"" + cleanPrefix + "\".");
        }

        if (valueDecimals > valueLength) {
            throw new ValidationException(
                    "El valor tiene " + valueLength + " dígitos y no puede tener "
                            + valueDecimals + " decimales.");
        }

        if (validateCheckDigit == null || validateCheckDigit) {
            if (!CHECK_DIGIT_LENGTHS.contains(totalLength)) {
                throw new ValidationException(
                        "El dígito verificador EAN solo existe en códigos de 8, 12 o 13 dígitos. "
                                + "Si este formato tiene " + totalLength
                                + ", desactivá la validación del verificador.");
            }
            if (productCodeEnd == totalLength || valueEnd == totalLength) {
                throw new ValidationException(
                        "La última posición está ocupada por un campo, así que no puede ser además "
                                + "el dígito verificador. Desactivá la validación del verificador o "
                                + "corregí las posiciones.");
            }
        }

        /*
         Which format claims a scanned code is decided by prefix and total length, so two
         formats sharing both would make the outcome depend on row order. Caught here and
         not only by the UNIQUE constraint, because the constraint's error names a column
         and an index and tells the person nothing about what to do about it.
        */
        repository.findByPrefixAndTotalLength(cleanPrefix, totalLength)
                .ifPresent(existing -> {
                    if (currentId == null || !existing.getId().equals(currentId)) {
                        throw new ConflictException(
                                "El formato \"" + existing.getName() + "\" ya usa el prefijo \""
                                        + cleanPrefix + "\" con " + totalLength + " dígitos. "
                                        + "Dos formatos con el mismo prefijo y el mismo largo no se "
                                        + "pueden distinguir al escanear: cambiá el prefijo, o editá "
                                        + "el que ya existe.");
                    }
                });
    }
}
