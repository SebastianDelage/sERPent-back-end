package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.request.UpdateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.response.WarehouseResponse;
import com.empresa.serpent.inventory.web.mapper.WarehouseMapper;
import com.empresa.serpent.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        validateName(request.name(), null);

        WarehouseEntity entity = warehouseMapper.toEntity(request);

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        normalizeName(entity);

        WarehouseEntity saved = warehouseRepository.save(entity);
        return warehouseMapper.toResponse(saved);
    }

    @Transactional
    public WarehouseResponse update(Long id, UpdateWarehouseRequest request) {
        validateName(request.name(), id);

        WarehouseEntity entity = warehouseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + id));

        warehouseMapper.updateEntityFromRequest(request, entity);
        normalizeName(entity);

        WarehouseEntity saved = warehouseRepository.save(entity);
        return warehouseMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id) {
        WarehouseEntity entity = warehouseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + id));

        return warehouseMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAllActive() {
        return warehouseRepository.findByActiveTrue().stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        return warehouseRepository.findAll().stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deactivate(Long id) {
        WarehouseEntity entity = warehouseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + id));

        entity.setActive(false);
        warehouseRepository.save(entity);
    }

    private void validateName(String name, Long currentWarehouseId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        warehouseRepository.findByName(name.trim())
                .ifPresent(existing -> {
                    if (currentWarehouseId == null || !existing.getId().equals(currentWarehouseId)) {
                        throw new IllegalArgumentException("Warehouse already exists: " + name.trim());
                    }
                });
    }

    private void normalizeName(WarehouseEntity entity) {
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
    }
}