package com.empresa.serpent.catalog.web.controller;

import com.empresa.serpent.catalog.service.CustomerService;
import com.empresa.serpent.catalog.web.dto.request.CustomerCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.CustomerUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.CustomerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public CustomerResponse create(@Valid @RequestBody CustomerCreateRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request
    ) {
        return customerService.update(id, request);
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @GetMapping
    public List<CustomerResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return customerService.search(name, includeInactive);
    }
}
