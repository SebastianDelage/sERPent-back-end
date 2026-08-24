package com.empresa.serpent.catalog.web.controller;

import com.empresa.serpent.catalog.service.ProductSupplierService;
import com.empresa.serpent.catalog.web.dto.request.UpsertProductSupplierRequest;
import com.empresa.serpent.catalog.web.dto.response.ProductSupplierResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Which suppliers a product can be bought from. Scoped under the product, like the
 * per-warehouse reorder overrides: the link has no meaning on its own.
 *
 * <p>Writes are ADMIN, reads are open — the same split as the rest of the catalog. An
 * employee looking at the replenishment report needs to see who to buy from; deciding who
 * that is, is the owner's call.
 */
@RestController
@RequestMapping("/api/products/{productId}/suppliers")
@RequiredArgsConstructor
public class ProductSupplierController {

    private final ProductSupplierService service;

    @GetMapping
    public List<ProductSupplierResponse> findByProduct(@PathVariable Long productId) {
        return service.findByProduct(productId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ProductSupplierResponse create(
            @PathVariable Long productId,
            @Valid @RequestBody UpsertProductSupplierRequest request
    ) {
        return service.create(productId, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ProductSupplierResponse update(
            @PathVariable Long productId,
            @PathVariable Long id,
            @Valid @RequestBody UpsertProductSupplierRequest request
    ) {
        return service.update(productId, id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long productId, @PathVariable Long id) {
        service.delete(productId, id);
    }
}
