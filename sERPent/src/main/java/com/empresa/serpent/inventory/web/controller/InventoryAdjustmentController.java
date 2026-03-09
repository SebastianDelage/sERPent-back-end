package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.service.InventoryAdjustmentApplicationService;
import com.empresa.serpent.inventory.web.dto.request.CreateInventoryAdjustmentRequest;
import com.empresa.serpent.inventory.web.dto.response.CreateInventoryAdjustmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentApplicationService inventoryAdjustmentApplicationService;

    @PostMapping
    public CreateInventoryAdjustmentResponse create(@Valid @RequestBody CreateInventoryAdjustmentRequest request) {
        return inventoryAdjustmentApplicationService.createAdjustment(request);
    }
}