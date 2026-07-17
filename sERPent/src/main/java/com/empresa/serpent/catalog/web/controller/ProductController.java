package com.empresa.serpent.catalog.web.controller;

import com.empresa.serpent.catalog.service.ProductService;
import com.empresa.serpent.catalog.web.dto.request.ProductCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.ProductUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ProductResponse create(@Valid @RequestBody ProductCreateRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return productService.update(id, request);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @GetMapping
    public List<ProductResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return productService.search(name, includeInactive);
    }

    @GetMapping("/barcode/{barcode}")
    public ProductResponse findByBarcode(@PathVariable String barcode) {
        return productService.findByBarcode(barcode);
    }
}