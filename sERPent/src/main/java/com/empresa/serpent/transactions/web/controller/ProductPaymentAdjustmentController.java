package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.ProductPaymentAdjustmentService;
import com.empresa.serpent.transactions.web.dto.request.CreateProductPaymentAdjustmentRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdateProductPaymentAdjustmentRequest;
import com.empresa.serpent.transactions.web.dto.response.ProductPaymentAdjustmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-payment-adjustments")
@RequiredArgsConstructor
public class ProductPaymentAdjustmentController {

    private final ProductPaymentAdjustmentService service;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ProductPaymentAdjustmentResponse create(
            @Valid @RequestBody CreateProductPaymentAdjustmentRequest request
    ) {
        return service.create(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
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

    /**
     * The active rules for a cart: one payment method against several products in a
     * single call, so the sale screen can preview surcharged prices without one
     * request per line. Backs the live preview only — confirming a sale still prices
     * it server-side.
     */
    @GetMapping("/for-sale")
    public List<ProductPaymentAdjustmentResponse> findForSale(
            @RequestParam Long paymentMethodId,
            @RequestParam List<Long> productIds
    ) {
        return service.findByPaymentMethodAndProducts(paymentMethodId, productIds);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
