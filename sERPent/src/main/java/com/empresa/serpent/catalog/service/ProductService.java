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
        validateBarcode(request.barcode(), null);
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
        validateBarcode(request.barcode(), id);
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
    public List<ProductResponse> search(String name, boolean includeInactive) {
        String term = (name == null || name.isBlank()) ? null : name.trim();

        return productRepository.search(term, includeInactive).stream()
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

    private void validateBarcode(String barcode, Long currentProductId) {
        if (barcode == null || barcode.isBlank()) {
            return;
        }
        productRepository.findByBarcode(barcode.trim())
                .ifPresent(existing -> {
                    if (currentProductId == null || !existing.getId().equals(currentProductId)) {
                        throw new ConflictException(
                                "El código de barras \"" + barcode.trim() + "\" ya lo usa el producto \""
                                        + existing.getName() + "\".");
                    }
                });
    }

    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new ValidationException("El código de barras es obligatorio.");
        }

        return productRepository.findFirstByBarcodeAndActiveTrue(barcode.trim())
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ValidationException(
                        "No se encontró ningún producto con el código \"" + barcode.trim() + "\"."));
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