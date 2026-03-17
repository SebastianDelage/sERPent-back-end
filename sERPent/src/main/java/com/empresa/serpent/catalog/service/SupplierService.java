package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.catalog.web.dto.request.SupplierCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.SupplierUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.SupplierResponse;
import com.empresa.serpent.catalog.web.mapper.SupplierMapper;
import com.empresa.serpent.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        validateName(request.name(), null);

        SupplierEntity entity = supplierMapper.toEntity(request);

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        normalizeFields(entity);

        SupplierEntity saved = supplierRepository.save(entity);
        return supplierMapper.toResponse(saved);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierUpdateRequest request) {
        validateName(request.name(), id);

        SupplierEntity entity = supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + id));

        supplierMapper.updateEntityFromRequest(request, entity);
        normalizeFields(entity);

        SupplierEntity saved = supplierRepository.save(entity);
        return supplierMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SupplierResponse findById(Long id) {
        SupplierEntity entity = supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + id));

        return supplierMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> findAllActive() {
        return supplierRepository.findByActiveTrue().stream()
                .map(supplierMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> searchActiveByName(String name) {
        if (name == null || name.isBlank()) {
            return findAllActive();
        }

        return supplierRepository.findByActiveTrueAndNameContainingIgnoreCase(name.trim()).stream()
                .map(supplierMapper::toResponse)
                .toList();
    }

    private void validateName(String name, Long currentSupplierId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name cannot be blank");
        }

        supplierRepository.findByNameIgnoreCase(name.trim())
                .ifPresent(existing -> {
                    if (currentSupplierId == null || !existing.getId().equals(currentSupplierId)) {
                        throw new IllegalArgumentException("Supplier name already exists: " + name.trim());
                    }
                });
    }

    private void normalizeFields(SupplierEntity entity) {
        entity.setName(normalizeRequired(entity.getName()));
        entity.setDocumentType(normalizeOptional(entity.getDocumentType()));
        entity.setDocumentNumber(normalizeOptional(entity.getDocumentNumber()));
        entity.setTaxCondition(normalizeOptional(entity.getTaxCondition()));
        entity.setPhone(normalizeOptional(entity.getPhone()));
        entity.setEmail(normalizeOptional(entity.getEmail()));
        entity.setAddress(normalizeOptional(entity.getAddress()));
        entity.setNotes(normalizeOptional(entity.getNotes()));
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}