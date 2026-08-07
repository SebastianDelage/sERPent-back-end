package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.ProductPaymentAdjustmentService;
import com.empresa.serpent.transactions.web.dto.request.CreateProductPaymentAdjustmentRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdateProductPaymentAdjustmentRequest;
import com.empresa.serpent.transactions.web.dto.response.ProductPaymentAdjustmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-payment-adjustments")
@RequiredArgsConstructor
public class ProductPaymentAdjustmentController {

    private final ProductPaymentAdjustmentService service;

    @PostMapping
    public ProductPaymentAdjustmentResponse create(
            @Valid @RequestBody CreateProductPaymentAdjustmentRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductPaymentAdjustmentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductPaymentAdjustmentRequest request
    ) {
        return service.update(id, request);
    }

    /** The rules configured for one product, active and inactive alike. */
    @GetMapping
    public List<ProductPaymentAdjustmentResponse> findByProduct(@RequestParam Long productId) {
        return service.findByProduct(productId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
