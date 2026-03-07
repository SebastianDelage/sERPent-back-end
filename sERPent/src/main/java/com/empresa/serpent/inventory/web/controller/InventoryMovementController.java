package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.service.InventoryMovementQueryService;
import com.empresa.serpent.inventory.web.dto.filter.InventoryMovementFilter;
import com.empresa.serpent.inventory.web.dto.response.InventoryMovementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/inventory-movements")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final InventoryMovementQueryService inventoryMovementQueryService;

    @GetMapping
    public Page<InventoryMovementResponse> findAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime dateTo,
            Pageable pageable
    ) {
        InventoryMovementFilter filter = new InventoryMovementFilter(
                productId,
                warehouseId,
                transactionId,
                movementType,
                dateFrom,
                dateTo
        );

        return inventoryMovementQueryService.search(filter, pageable);
    }

    @GetMapping("/{id}")
    public InventoryMovementResponse findById(@PathVariable Long id) {
        return inventoryMovementQueryService.getById(id);
    }
}
