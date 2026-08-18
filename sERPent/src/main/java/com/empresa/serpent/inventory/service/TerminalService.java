package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.TerminalEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.TerminalRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.CreateTerminalRequest;
import com.empresa.serpent.inventory.web.dto.request.UpdateTerminalRequest;
import com.empresa.serpent.inventory.web.dto.response.TerminalResponse;
import com.empresa.serpent.inventory.web.mapper.TerminalMapper;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalRepository terminalRepository;
    private final WarehouseRepository warehouseRepository;
    private final TerminalMapper terminalMapper;

    @Transactional
    public TerminalResponse create(CreateTerminalRequest request) {
        validateName(request.name(), null);

        TerminalEntity entity = TerminalEntity.builder()
                .name(request.name().trim())
                .warehouse(resolveActiveWarehouse(request.warehouseId()))
                .active(request.active() == null ? Boolean.TRUE : request.active())
                .build();

        return terminalMapper.toResponse(terminalRepository.save(entity));
    }

    @Transactional
    public TerminalResponse update(Long id, UpdateTerminalRequest request) {
        validateName(request.name(), id);

        TerminalEntity entity = terminalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Terminal not found: " + id));

        entity.setName(request.name().trim());
        entity.setWarehouse(resolveActiveWarehouse(request.warehouseId()));

        if (request.active() != null) {
            entity.setActive(request.active());
        }

        return terminalMapper.toResponse(terminalRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public TerminalResponse findById(Long id) {
        return terminalMapper.toResponse(terminalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Terminal not found: " + id)));
    }

    /** Lists terminals; inactive ones are excluded unless asked for. */
    @Transactional(readOnly = true)
    public List<TerminalResponse> search(boolean includeInactive) {
        return terminalRepository.search(includeInactive).stream()
                .map(terminalMapper::toResponse)
                .toList();
    }

    /**
     * A terminal pointing at a deactivated warehouse could never register anything, so the
     * dead end is refused at configuration time rather than at the till.
     */
    private WarehouseEntity resolveActiveWarehouse(Long warehouseId) {
        WarehouseEntity warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + warehouseId));

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ValidationException(
                    "No podés asociar la terminal al depósito \"" + warehouse.getName()
                            + "\" porque está inactivo.");
        }

        return warehouse;
    }

    private void validateName(String name, Long currentTerminalId) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El nombre de la terminal es obligatorio.");
        }

        terminalRepository.findByName(name.trim())
                .ifPresent(existing -> {
                    if (currentTerminalId == null || !existing.getId().equals(currentTerminalId)) {
                        throw new ConflictException(
                                "Ya existe una terminal con el nombre \"" + name.trim() + "\".");
                    }
                });
    }
}
