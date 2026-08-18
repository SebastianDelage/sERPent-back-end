package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.request.UpdateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.response.WarehouseResponse;
import com.empresa.serpent.inventory.web.mapper.WarehouseMapper;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    private final UserRepository userRepository;

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

        if (Boolean.TRUE.equals(entity.getActive()) && Boolean.FALSE.equals(request.active())) {
            requireNobodyDependsOnIt(entity);
        }

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
    public List<WarehouseResponse> search(boolean includeInactive) {
        return warehouseRepository.search(includeInactive).stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    /**
     * Deactivating a warehouse silently strips operating rights from anyone assigned only to
     * it: they would keep looking correctly configured while every operation got rejected.
     * Same spirit as the protected admin user — the system refuses to create the dead end,
     * and names who is in the way so the fix is obvious.
     */
    private void requireNobodyDependsOnIt(WarehouseEntity warehouse) {
        List<UserEntity> affected = userRepository.findActiveUsersWhoseOnlyWarehouseIs(warehouse.getId());

        if (affected.isEmpty()) {
            return;
        }

        String names = affected.stream()
                .map(UserEntity::getUsername)
                .collect(Collectors.joining(", "));

        throw new ValidationException(
                "No podés desactivar el depósito \"" + warehouse.getName() + "\" porque es el único "
                        + "asignado a: " + names + ". Asignales otro depósito antes de desactivarlo.");
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