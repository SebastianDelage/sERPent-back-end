package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.TerminalEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.TerminalRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which warehouse an operation acts on, and whether the acting user may act there.
 *
 * <p>Every operation that moves stock funnels through here, so the three rules stay in one
 * place: the warehouse must exist, it must be active, and it must be one of the acting
 * user's assigned warehouses.
 */
@Service
@RequiredArgsConstructor
public class WarehouseAccessService {

    private final WarehouseRepository warehouseRepository;
    private final TerminalRepository terminalRepository;

    /**
     * Resolves the warehouse for an operation and authorizes the acting user against it.
     *
     * <p>When {@code terminalId} is present the warehouse comes from the terminal and the
     * request's own {@code warehouseId} is ignored — that is the whole point of a terminal:
     * the machine decides where it registers, not the operator. The user's assignment is
     * still checked against the terminal's warehouse, because the terminal is an operational
     * convenience and not a security control (a client can name any terminal it likes).
     */
    @Transactional(readOnly = true)
    public WarehouseEntity resolveForOperation(Long terminalId, Long requestWarehouseId, UserEntity actingUser) {
        WarehouseEntity warehouse = terminalId != null
                ? warehouseOfTerminal(terminalId)
                : warehouseFromRequest(requestWarehouseId);

        requireActive(warehouse);
        requireAssigned(warehouse, actingUser);

        return warehouse;
    }

    /**
     * Authorizes an already-loaded warehouse. Used by the transfer flow, which loads its two
     * warehouses itself and only authorizes the source.
     */
    public void requireAssigned(WarehouseEntity warehouse, UserEntity actingUser) {
        boolean assigned = actingUser.getWarehouses().stream()
                .anyMatch(assignedWarehouse -> assignedWarehouse.getId().equals(warehouse.getId()));

        if (!assigned) {
            throw new ForbiddenException(
                    "No tenés permiso para operar en el depósito \"" + warehouse.getName() + "\".");
        }
    }

    private WarehouseEntity warehouseOfTerminal(Long terminalId) {
        TerminalEntity terminal = terminalRepository.findById(terminalId)
                .orElseThrow(() -> new NotFoundException("Terminal not found: " + terminalId));

        if (!Boolean.TRUE.equals(terminal.getActive())) {
            throw new ValidationException(
                    "La terminal \"" + terminal.getName() + "\" está inactiva.");
        }

        return terminal.getWarehouse();
    }

    private WarehouseEntity warehouseFromRequest(Long warehouseId) {
        if (warehouseId == null) {
            throw new ValidationException("Tenés que indicar el depósito de la operación.");
        }

        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + warehouseId));
    }

    private void requireActive(WarehouseEntity warehouse) {
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ValidationException("El depósito seleccionado está inactivo.");
        }
    }
}
