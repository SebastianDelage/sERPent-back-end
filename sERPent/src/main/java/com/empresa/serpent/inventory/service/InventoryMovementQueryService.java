package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryMovementSpecifications;
import com.empresa.serpent.inventory.web.dto.filter.InventoryMovementFilter;
import com.empresa.serpent.inventory.web.dto.response.InventoryMovementResponse;
import com.empresa.serpent.inventory.web.mapper.InventoryMovementMapper;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryMovementQueryService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final WarehouseScopeService warehouseScopeService;

    public Page<InventoryMovementResponse> search(InventoryMovementFilter filter, Pageable pageable) {
        WarehouseScope scope = warehouseScopeService.resolve(filter.warehouseId());
        if (scope.seesNothing()) {
            return Page.empty(pageable);
        }

        return inventoryMovementRepository
                .findAll(
                        InventoryMovementSpecifications.fromFilter(filter)
                                .and(InventoryMovementSpecifications.withinScope(scope)),
                        pageable)
                .map(inventoryMovementMapper::toResponse);
    }

    /**
     * A single movement, refused when it belongs to a branch the caller may not see.
     *
     * <p>Checked after loading rather than by scoping the lookup: the two cases are "does
     * not exist" and "not yours", and telling them apart is worth one comparison. Without
     * this, walking the ids would hand over every branch's movements one at a time — the
     * listing being scoped means nothing if the detail is not.
     */
    public InventoryMovementResponse getById(Long id) {
        InventoryMovementEntity entity = inventoryMovementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory movement not found: " + id));

        WarehouseScope scope = warehouseScopeService.resolve(null);
        if (!scope.unrestricted() && !scope.warehouseIds().contains(entity.getWarehouse().getId())) {
            throw new ForbiddenException("No tenés permiso para ver los datos de ese depósito.");
        }

        return inventoryMovementMapper.toResponse(entity);
    }
}
