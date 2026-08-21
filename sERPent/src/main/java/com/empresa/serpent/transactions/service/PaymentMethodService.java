package com.empresa.serpent.transactions.service;

import com.empresa.serpent.shared.exception.ConflictException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
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
        if (entity.getIsCash() == null) {
            entity.setIsCash(false);
        }

        validateSingleCashMethod(entity.getIsCash(), null);
        normalizeName(entity);

        PaymentMethodEntity saved = paymentMethodRepository.save(entity);
        return paymentMethodMapper.toResponse(saved);
    }

    @Transactional
    public PaymentMethodResponse update(Long id, UpdatePaymentMethodRequest request) {
        validateName(request.name(), id);

        PaymentMethodEntity entity = paymentMethodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment method not found: " + id));

        // Omitting the field leaves the flag alone, same as warehouseIds on a user: an
        // update that does not mention cash is not a request to stop being cash.
        if (request.isCash() != null) {
            validateSingleCashMethod(request.isCash(), id);
        }

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
    public List<PaymentMethodResponse> search(String name, boolean includeInactive) {
        String term = (name == null || name.isBlank()) ? null : name.trim();
        return paymentMethodRepository.search(term, includeInactive).stream()
                .map(paymentMethodMapper::toResponse)
                .toList();
    }

    private void validateName(String name, Long currentPaymentMethodId) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("El nombre del método de pago es obligatorio.");
        }

        paymentMethodRepository.findByName(name.trim())
                .ifPresent(existing -> {
                    if (currentPaymentMethodId == null || !existing.getId().equals(currentPaymentMethodId)) {
                        throw new ConflictException(
                                "Ya existe un método de pago con el nombre \"" + name.trim() + "\".");
                    }
                });
    }

    /**
     * Only one method can be the drawer.
     *
     * <p>Refused rather than silently un-flagging the other one: moving what "cash" means is
     * a decision with consequences for every till count from here on, so it has to be made
     * on purpose, in two steps.
     */
    private void validateSingleCashMethod(Boolean isCash, Long currentPaymentMethodId) {
        if (!Boolean.TRUE.equals(isCash)) {
            return;
        }

        paymentMethodRepository.findByIsCashTrue()
                .filter(existing -> !existing.getId().equals(currentPaymentMethodId))
                .ifPresent(existing -> {
                    throw new ValidationException(
                            "El método de pago \"" + existing.getName() + "\" ya está marcado como efectivo. "
                                    + "Solo puede haber uno, así que primero desmarcá ese.");
                });
    }

    private void normalizeName(PaymentMethodEntity entity) {
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
    }
}