package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.catalog.repository.CustomerRepository;
import com.empresa.serpent.catalog.web.dto.request.CustomerCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.CustomerUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.CustomerResponse;
import com.empresa.serpent.catalog.web.mapper.CustomerMapper;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        validateName(request.name(), null);

        CustomerEntity entity = customerMapper.toEntity(request);

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        normalizeFields(entity);

        CustomerEntity saved = customerRepository.save(entity);
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        validateName(request.name(), id);

        CustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));

        customerMapper.updateEntityFromRequest(request, entity);
        normalizeFields(entity);

        CustomerEntity saved = customerRepository.save(entity);
        return customerMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        CustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + id));

        return customerMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String name, boolean includeInactive) {
        String term = (name == null || name.isBlank()) ? null : name.trim();

        return customerRepository.search(term, includeInactive).stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    private void validateName(String name, Long currentCustomerId) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El nombre del cliente es obligatorio.");
        }

        customerRepository.findByNameIgnoreCase(name.trim())
                .ifPresent(existing -> {
                    if (currentCustomerId == null || !existing.getId().equals(currentCustomerId)) {
                        throw new ConflictException(
                                "Ya existe un cliente con el nombre \"" + name.trim() + "\".");
                    }
                });
    }

    private void normalizeFields(CustomerEntity entity) {
        entity.setName(entity.getName() == null ? null : entity.getName().trim());
        entity.setDocumentType(normalizeOptional(entity.getDocumentType()));
        entity.setDocumentNumber(normalizeOptional(entity.getDocumentNumber()));
        entity.setPhone(normalizeOptional(entity.getPhone()));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
