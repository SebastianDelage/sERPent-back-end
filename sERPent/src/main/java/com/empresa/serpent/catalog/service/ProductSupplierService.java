package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.domain.entity.ProductSupplierEntity;
import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.catalog.repository.ProductSupplierRepository;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.catalog.web.dto.request.UpsertProductSupplierRequest;
import com.empresa.serpent.catalog.web.dto.response.ProductSupplierResponse;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Which suppliers a product can be bought from.
 *
 * <p>THE RULE THIS SERVICE OWNS: at most one ACTIVE supplier per product may be preferred.
 * PostgreSQL carries a partial unique index saying the same thing, but H2 has no partial
 * indexes and the two migration sets have to stay identical in effect — so the guarantee
 * lives here, and the index is a second line of defence rather than the source of it.
 *
 * <p>Marking a new preferred DEMOTES the previous one instead of refusing. Unlike the cash
 * payment method, where moving the flag changes what every till count means, here it is an
 * ordinary editorial decision — "we buy this from them now" — and making the user clear the
 * old one first would be ceremony.
 */
@Service
@RequiredArgsConstructor
public class ProductSupplierService {

    private final ProductSupplierRepository repository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public List<ProductSupplierResponse> findByProduct(Long productId) {
        requireProduct(productId);

        return repository.findByProductId(productId).stream()
                // Preferred first, then by name: the one the report proposes is the one the
                // reader is looking for.
                .sorted((a, b) -> {
                    boolean aPreferred = Boolean.TRUE.equals(a.getPreferred());
                    boolean bPreferred = Boolean.TRUE.equals(b.getPreferred());
                    if (aPreferred != bPreferred) {
                        return aPreferred ? -1 : 1;
                    }
                    return a.getSupplierEntity().getName()
                            .compareToIgnoreCase(b.getSupplierEntity().getName());
                })
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductSupplierResponse create(Long productId, UpsertProductSupplierRequest request) {
        ProductEntity product = requireProduct(productId);
        SupplierEntity supplier = requireSupplier(request.supplierId());

        if (repository.existsByProductIdAndSupplierEntityId(productId, request.supplierId())) {
            throw new ConflictException(
                    "El proveedor \"" + supplier.getName() + "\" ya está asociado a este producto.");
        }

        boolean active = request.active() == null || request.active();
        boolean preferred = Boolean.TRUE.equals(request.preferred());

        if (preferred) {
            requireActiveToBePreferred(active, supplier.getName());
            demoteCurrentPreferred(productId, null);
        }

        ProductSupplierEntity entity = ProductSupplierEntity.builder()
                .product(product)
                .supplierEntity(supplier)
                .supplierProductCode(normalizeOptional(request.supplierProductCode()))
                .preferred(preferred)
                .leadTimeDays(request.leadTimeDays())
                .active(active)
                .build();

        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProductSupplierResponse update(Long productId, Long id, UpsertProductSupplierRequest request) {
        requireProduct(productId);

        ProductSupplierEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product supplier not found: " + id));

        if (!entity.getProduct().getId().equals(productId)) {
            throw new NotFoundException("Product supplier not found: " + id);
        }

        boolean active = request.active() == null || request.active();
        boolean preferred = Boolean.TRUE.equals(request.preferred());

        if (preferred) {
            requireActiveToBePreferred(active, entity.getSupplierEntity().getName());
            demoteCurrentPreferred(productId, id);
        }

        entity.setSupplierProductCode(normalizeOptional(request.supplierProductCode()));
        entity.setPreferred(preferred);
        entity.setLeadTimeDays(request.leadTimeDays());
        entity.setActive(active);

        return toResponse(repository.save(entity));
    }

    /**
     * Unlinks a supplier from a product.
     *
     * <p>A hard delete, unlike the rest of the catalog: the link carries no history worth
     * keeping — the purchases that were made from that supplier are recorded on the
     * purchases themselves and are not touched by this. Deactivating instead is available
     * through {@code active} for anyone who wants to keep the code and the lead time around.
     */
    @Transactional
    public void delete(Long productId, Long id) {
        requireProduct(productId);

        ProductSupplierEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product supplier not found: " + id));

        if (!entity.getProduct().getId().equals(productId)) {
            throw new NotFoundException("Product supplier not found: " + id);
        }

        repository.delete(entity);
    }

    /**
     * An inactive supplier cannot be the preferred one: the report would propose someone we
     * decided to stop buying from. Refused rather than silently un-flagged, so the
     * contradiction is answered by whoever created it.
     */
    private void requireActiveToBePreferred(boolean active, String supplierName) {
        if (!active) {
            throw new ValidationException(
                    "No podés marcar como preferido a \"" + supplierName
                            + "\" si el vínculo está inactivo.");
        }
    }

    /** Clears the flag on whoever holds it, so the new one can take it. */
    private void demoteCurrentPreferred(Long productId, Long exceptId) {
        repository.findByProductIdAndPreferredTrue(productId)
                .filter(current -> !current.getId().equals(exceptId))
                .ifPresent(current -> {
                    current.setPreferred(false);
                    repository.save(current);
                });
    }

    private ProductSupplierResponse toResponse(ProductSupplierEntity entity) {
        return new ProductSupplierResponse(
                entity.getId(),
                entity.getProduct().getId(),
                entity.getSupplierEntity().getId(),
                entity.getSupplierEntity().getName(),
                entity.getSupplierProductCode(),
                entity.getPreferred(),
                entity.getLeadTimeDays(),
                entity.getActive()
        );
    }

    private ProductEntity requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    }

    private SupplierEntity requireSupplier(Long supplierId) {
        SupplierEntity supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + supplierId));

        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw new ValidationException(
                    "El proveedor \"" + supplier.getName() + "\" está inactivo.");
        }

        return supplier;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
