package com.empresa.serpent.inventory.web.controller;

import com.empresa.serpent.inventory.service.WarehouseService;
import com.empresa.serpent.inventory.web.dto.request.CreateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.request.UpdateWarehouseRequest;
import com.empresa.serpent.inventory.web.dto.response.WarehouseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    public WarehouseResponse create(@Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    public WarehouseResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request
    ) {
        return warehouseService.update(id, request);
    }

    @GetMapping("/{id}")
    public WarehouseResponse findById(@PathVariable Long id) {
        return warehouseService.findById(id);
    }

    @GetMapping
    public List<WarehouseResponse> findAllActive() {
        return warehouseService.findAllActive();
    }

    @GetMapping("/all")
    public List<WarehouseResponse> findAll() {
        return warehouseService.findAll();
    }

    @PatchMapping("/{id}/deactivate")
    public void deactivate(@PathVariable Long id) {
        warehouseService.deactivate(id);
    }
}