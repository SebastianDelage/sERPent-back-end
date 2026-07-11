package com.empresa.serpent.transactions.service;

import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.web.dto.request.CreatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.request.UpdatePaymentMethodRequest;
import com.empresa.serpent.transactions.web.dto.response.PaymentMethodResponse;
import com.empresa.serpent.transactions.web.mapper.PaymentMethodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodMapper paymentMethodMapper;

    @Transactional
    public PaymentMethodResponse create(CreatePaymentMethodRequest request) {
        validateName(request.name(), null);

        PaymentMethodEntity entity = paymentMethodMapper.toEntity(request);

        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        normalizeName(entity);

        PaymentMethodEntity saved = paymentMethodRepository.save(entity);
        return paymentMethodMapper.toResponse(saved);
    }

    @Transactional
    public PaymentMethodResponse update(Long id, UpdatePaymentMethodRequest request) {
        validateName(request.name(), id);

        PaymentMethodEntity entity = paymentMethodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment method not found: " + id));

        paymentMethodMapper.updateEntityFromRequest(request, entity);
        normalizeName(entity);

        PaymentMethodEntity saved = paymentMethodRepository.save(entity);
        return paymentMethodMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse findById(Long id) {
        PaymentMethodEntity entity = paymentMethodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment method not found: " + id));

        return paymentMethodMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> findAllActive() {
        return paymentMethodRepository.findByActiveTrue().stream()
                .map(paymentMethodMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> findAll() {
        return paymentMethodRepository.findAll().stream()
                .map(paymentMethodMapper::toResponse)
                .toList();
    }

    private void validateName(String name, Long currentPaymentMethodId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        paymentMethodRepository.findByName(name.trim())
                .ifPresent(existing -> {
                    if (currentPaymentMethodId == null || !existing.getId().equals(currentPaymentMethodId)) {
                        throw new IllegalArgumentException("Payment method already exists: " + name.trim());
                    }
                });
    }

    private void normalizeName(PaymentMethodEntity entity) {
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
    }
}