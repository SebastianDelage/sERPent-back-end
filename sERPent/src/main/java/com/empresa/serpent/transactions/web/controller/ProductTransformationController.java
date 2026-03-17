package com.empresa.serpent.transactions.web.controller;

import com.empresa.serpent.transactions.service.ProductTransformationApplicationService;
import com.empresa.serpent.transactions.web.dto.request.CreateProductTransformationRequest;
import com.empresa.serpent.transactions.web.dto.response.CreateProductTransformationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transformations")
@RequiredArgsConstructor
public class ProductTransformationController {

    private final ProductTransformationApplicationService productTransformationApplicationService;

    @PostMapping
    public CreateProductTransformationResponse create(@Valid @RequestBody CreateProductTransformationRequest request) {
        return productTransformationApplicationService.createTransformation(request);
    }
}