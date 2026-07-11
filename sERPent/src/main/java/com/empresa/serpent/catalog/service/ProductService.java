package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.web.dto.request.ProductCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.ProductUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.ProductResponse;
import com.empresa.serpent.catalog.web.mapper.ProductMapper;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        validatePrice(request.price());
        validateSku(request.sku(), null);
        validateInventoryConfiguration(
                request.minimumStock(),
                request.reorderPoint(),
                request.reorderQuantity()
        );

        ProductEntity entity = productMapper.toEntity(request);

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        normalizeSku(entity);

        ProductEntity saved = productRepository.save(entity);
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        validatePrice(request.price());
        validateSku(request.sku(), id);
        validateInventoryConfiguration(
                request.minimumStock(),
                request.reorderPoint(),
                request.reorderQuantity()
        );

        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));

        productMapper.updateEntityFromRequest(request, entity);
        normalizeSku(entity);

        ProductEntity saved = productRepository.save(entity);
        return productMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));

        return productMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAllActive() {
        return productRepository.findByActiveTrue().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchActiveByName(String name) {
        if (name == null || name.isBlank()) {
            return findAllActive();
        }

        return productRepository.findByActiveTrueAndNameContainingIgnoreCase(name.trim()).stream()
                .map(productMapper::toResponse)
                .toList();
    }

    private void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new ValidationException("El precio es obligatorio.");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("El precio no puede ser negativo.");
        }
    }

    private void validateSku(String sku, Long currentProductId) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        productRepository.findBySku(sku.trim())
                .ifPresent(existing -> {
                    if (currentProductId == null || !existing.getId().equals(currentProductId)) {
                        throw new ConflictException("Ya existe un producto con el código SKU \"" + sku.trim() + "\".");
                    }
                });
    }

    private void validateInventoryConfiguration(BigDecimal minimumStock, BigDecimal reorderPoint, BigDecimal reorderQuantity) {
        validateNonNegative(minimumStock, "El stock mínimo no puede ser negativo.");
        validateNonNegative(reorderPoint, "El punto de reposición no puede ser negativo.");
        validateNonNegative(reorderQuantity, "La cantidad de reposición no puede ser negativa.");

        if (minimumStock != null && reorderPoint != null && reorderPoint.compareTo(minimumStock) < 0) {
            throw new ValidationException("El punto de reposición no puede ser menor al stock mínimo.");
        }
    }

    private void validateNonNegative(BigDecimal value, String message) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(message);
        }
    }

    private void normalizeSku(ProductEntity entity) {
        if (entity.getSku() != null && entity.getSku().isBlank()) {
            entity.setSku(null);
        }

        if (entity.getSku() != null) {
            entity.setSku(entity.getSku().trim());
        }
    }
}