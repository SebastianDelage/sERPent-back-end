package com.empresa.serpent.catalog.web.controller;

import com.empresa.serpent.catalog.service.ScaleBarcodeFormatService;
import com.empresa.serpent.catalog.web.dto.request.ScaleBarcodeFormatCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.ScaleBarcodeFormatUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.ScaleBarcodeFormatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Defining a scale's layout is configuration and belongs to the owner, so writes are
 * ADMIN-only. Reading is not: the till decodes every scanned label against these formats,
 * and the product form reads them to tell a scale code apart from a barcode. Same split
 * as terminals and payment-method surcharges.
 */
@RestController
@RequestMapping("/api/scale-barcode-formats")
@RequiredArgsConstructor
public class ScaleBarcodeFormatController {

    private final ScaleBarcodeFormatService service;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ScaleBarcodeFormatResponse create(@Valid @RequestBody ScaleBarcodeFormatCreateRequest request) {
        return service.create(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ScaleBarcodeFormatResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ScaleBarcodeFormatUpdateRequest request
    ) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public ScaleBarcodeFormatResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    public List<ScaleBarcodeFormatResponse> search(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return service.search(includeInactive);
    }
}
