package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryMovementSpecifications;
import com.empresa.serpent.inventory.web.dto.filter.InventoryMovementFilter;
import com.empresa.serpent.inventory.web.dto.response.InventoryMovementResponse;
import com.empresa.serpent.inventory.web.mapper.InventoryMovementMapper;
import com.empresa.serpent.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryMovementQueryService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMovementMapper inventoryMovementMapper;

    public Page<InventoryMovementResponse> search(InventoryMovementFilter filter, Pageable pageable) {
        return inventoryMovementRepository
                .findAll(InventoryMovementSpecifications.fromFilter(filter), pageable)
                .map(inventoryMovementMapper::toResponse);
    }

    public InventoryMovementResponse getById(Long id) {
        InventoryMovementEntity entity = inventoryMovementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory movement not found: " + id));

        return inventoryMovementMapper.toResponse(entity);
    }
}
