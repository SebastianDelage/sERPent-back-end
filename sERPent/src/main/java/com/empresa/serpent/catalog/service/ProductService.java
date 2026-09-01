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
    private final ProductReorderOverrideGuard warehouseOverrides;
    private final ScaleBarcodeMatcher scaleBarcodeMatcher;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        validatePrice(request.price());
        validateSku(request.sku(), null);
        validateBarcode(request.barcode(), null);
        validateScaleCode(request.scaleCode(), null);
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
        normalizeScaleCode(entity);

        ProductEntity saved = productRepository.save(entity);
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        validatePrice(request.price());
        validateSku(request.sku(), id);
        validateBarcode(request.barcode(), id);
        validateScaleCode(request.scaleCode(), id);
        validateInventoryConfiguration(
                request.minimumStock(),
                request.reorderPoint(),
                request.reorderQuantity()
        );

        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));

        /*
         The product's own figures are the fallback for every warehouse that does not
         override them, so moving them can break a warehouse that is consistent right now
         and is not being edited: raising the minimum under a warehouse that owns only its
         reorder point, or lowering the reorder point under one that owns only its minimum.
         Checked before the change lands, because the alternative is a report that fires
         after the floor has already been breached — wrong in a way nobody would notice.
        */
        warehouseOverrides.validateOverridesAgainst(
                id, request.minimumStock(), request.reorderPoint());

        productMapper.updateEntityFromRequest(request, entity);
        normalizeSku(entity);
        normalizeScaleCode(entity);

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

        /*
         A scale label is not a barcode, and the two fields are one keystroke apart. Left
         in the wrong field it would never scan: at the till a scale label is decoded and
         looked up by scale code, so this value would sit here matching nothing forever.
         Pointing at the right field beats a silent dead end — and it translates, because
         copying the whole 13 digits across is the mistake this message is preventing.
        */
        scaleBarcodeMatcher.match(barcode.trim()).ifPresent(format -> {
            String productCode = format.getProductCodeStart() + format.getProductCodeLength() - 1
                    <= barcode.trim().length()
                    ? barcode.trim().substring(
                            format.getProductCodeStart() - 1,
                            format.getProductCodeStart() - 1 + format.getProductCodeLength())
                    : "";
            String hint = productCode.isEmpty()
                    ? ""
                    : " Según el formato \"" + format.getName() + "\", el código de producto de esta "
                            + "etiqueta es " + productCode + " (o sea, " + stripLeadingZeros(productCode) + ").";

            throw new ValidationException(
                    "\"" + barcode.trim() + "\" es un código de balanza, no un código de barras. "
                            + "Va en el campo \"Código de balanza\"." + hint);
        });

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

    private void validateScaleCode(String scaleCode, Long currentProductId) {
        if (scaleCode == null || scaleCode.isBlank()) {
            return;
        }
        if (!scaleCode.trim().matches("\\d+")) {
            throw new ValidationException("El código de balanza tiene que ser un número.");
        }

        String normalized = stripLeadingZeros(scaleCode.trim());

        productRepository.findByScaleCode(normalized)
                .ifPresent(existing -> {
                    if (currentProductId == null || !existing.getId().equals(currentProductId)) {
                        throw new ConflictException(
                                "El código de balanza \"" + normalized + "\" ya lo usa el producto \""
                                        + existing.getName() + "\".");
                    }
                });
    }

    /**
     * "000016" off a label and "16" off the scale listing are the same product code. Both
     * collapse to "16" here, which is what makes the UNIQUE constraint and the lookup at
     * the till agree on what "the same code" means.
     */
    private String stripLeadingZeros(String digits) {
        String stripped = digits.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private void normalizeScaleCode(ProductEntity entity) {
        if (entity.getScaleCode() == null || entity.getScaleCode().isBlank()) {
            entity.setScaleCode(null);
            return;
        }
        entity.setScaleCode(stripLeadingZeros(entity.getScaleCode().trim()));
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