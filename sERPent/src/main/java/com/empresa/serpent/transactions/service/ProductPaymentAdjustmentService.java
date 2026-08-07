package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.domain.entity.ProductPaymentAdjustmentEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.ProductPaymentAdjustmentRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateProductPaymentAdjustmentRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdateProductPaymentAdjustmentRequest;
import com.empresa.serpent.transactions.web.dto.response.ProductPaymentAdjustmentResponse;
import com.empresa.serpent.transactions.web.mapper.ProductPaymentAdjustmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Manages the per-product, per-payment-method price rules the sale loop reads. */
@Service
@RequiredArgsConstructor
public class ProductPaymentAdjustmentService {

    /** Below this the line's unit price would go negative. */
    private static final BigDecimal PERCENTAGE_FLOOR = new BigDecimal("-100");

    private final ProductPaymentAdjustmentRepository repository;
    private final ProductRepository productRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ProductPaymentAdjustmentMapper mapper;

    @Transactional
    public ProductPaymentAdjustmentResponse create(CreateProductPaymentAdjustmentRequest request) {

        validatePercentage(request.adjustmentPercentage());

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product not found: " + request.productId()));

        PaymentMethodEntity paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() ->
                        new NotFoundException("Payment method not found: " + request.paymentMethodId()));

        if (repository.existsByProductIdAndPaymentMethodId(request.productId(), request.paymentMethodId())) {
            throw new ConflictException(
                    "El producto \"" + product.getName() + "\" ya tiene una regla para el método de pago \""
                            + paymentMethod.getName() + "\"."
            );
        }

        ProductPaymentAdjustmentEntity entity = ProductPaymentAdjustmentEntity.builder()
                .product(product)
                .paymentMethod(paymentMethod)
                .adjustmentPercentage(request.adjustmentPercentage())
                .active(request.active() == null || request.active())
                .build();

        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    public ProductPaymentAdjustmentResponse update(Long id, UpdateProductPaymentAdjustmentRequest request) {

        validatePercentage(request.adjustmentPercentage());

        ProductPaymentAdjustmentEntity entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product payment adjustment not found: " + id));

        entity.setAdjustmentPercentage(request.adjustmentPercentage());
        entity.setActive(request.active());

        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ProductPaymentAdjustmentResponse> findByProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new NotFoundException("Product not found: " + productId);
        }
        return mapper.toResponseList(repository.findByProductId(productId));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Product payment adjustment not found: " + id);
        }
        repository.deleteById(id);
    }

    private void validatePercentage(BigDecimal percentage) {
        if (percentage.compareTo(PERCENTAGE_FLOOR) < 0) {
            throw new ValidationException("Un descuento no puede superar el 100% del precio del producto.");
        }
    }
}
