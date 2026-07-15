package com.empresa.serpent.catalog.web.controller;

import com.empresa.serpent.catalog.service.SupplierService;
import com.empresa.serpent.catalog.web.dto.request.SupplierCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.SupplierUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.SupplierResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    public SupplierResponse create(@Valid @RequestBody SupplierCreateRequest request) {
        return supplierService.create(request);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierUpdateRequest request
    ) {
        return supplierService.update(id, request);
    }

    @GetMapping("/{id}")
    public SupplierResponse findById(@PathVariable Long id) {
        return supplierService.findById(id);
    }

    @GetMapping
    public List<SupplierResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return supplierService.search(name, includeInactive);
    }
}