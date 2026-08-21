package com.empresa.serpent.cashcount.service;

import com.empresa.serpent.cashcount.domain.entity.CashCountEntity;
import com.empresa.serpent.cashcount.domain.entity.CashCountLineEntity;
import com.empresa.serpent.cashcount.repository.CashCountRepository;
import com.empresa.serpent.cashcount.web.dto.request.CashCountLineRequest;
import com.empresa.serpent.cashcount.web.dto.request.CreateCashCountRequest;
import com.empresa.serpent.cashcount.web.dto.response.CashCountLineResponse;
import com.empresa.serpent.cashcount.web.dto.response.CashCountResponse;
import com.empresa.serpent.cashcount.web.dto.response.ExpectedCashCountMethodResponse;
import com.empresa.serpent.cashcount.web.dto.response.ExpectedCashCountResponse;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.service.WarehouseAccessService;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.shared.security.AuthenticatedUserService;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.users.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recording and reading till counts.
 *
 * <p>A count is a photo: saving one locks nothing and blocks nothing. The only lasting
 * effect is that the next count of the same branch starts where this one ended.
 */
@Service
@RequiredArgsConstructor
public class CashCountService {

    private final CashCountRepository cashCountRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ExpectedCashCountService expectedCashCountService;
    private final WarehouseAccessService warehouseAccessService;
    private final WarehouseScopeService warehouseScopeService;
    private final AuthenticatedUserService authenticatedUserService;

    /**
     * Closes the till for a branch.
     *
     * <p>The expected figures are computed HERE and frozen, never taken from the request.
     * What the record states is what the server believed at the moment of closing, not what
     * some screen was showing a few minutes ago.
     */
    @Transactional
    public CashCountResponse create(CreateCashCountRequest request) {
        UserEntity createdBy = authenticatedUserService.requireCurrentUser();

        // Closing a till is an operation on a branch, not a report about it: authorized the
        // same way a sale is, and through a terminal when there is one.
        WarehouseEntity warehouse = warehouseAccessService.resolveForOperation(
                request.terminalId(), request.warehouseId(), createdBy);

        LocalDateTime closedAt = LocalDateTime.now();
        LocalDateTime periodFrom = expectedCashCountService.lastCloseOf(warehouse.getId()).orElse(null);

        BigDecimal openingFloat = ExpectedCashCountService.normalize(request.openingFloat());

        ExpectedCashCountResponse expected =
                expectedCashCountService.build(warehouse, periodFrom, closedAt, openingFloat);

        /*
         Refused rather than counted as zero. With no method flagged as cash, the drawer
         cannot be told apart from the card terminal, so every cash figure would be a
         confident-looking guess. A count that says "I do not know" is worth more than one
         that says zero.
         */
        if (!expected.cashConfigured()) {
            throw new ValidationException(
                    "No se puede cerrar la caja porque ningún método de pago está marcado como "
                            + "efectivo. Pedile a quien administra el sistema que lo configure en "
                            + "Métodos de pago.");
        }

        Map<Long, BigDecimal> countedByMethod = countedByMethod(request.countedAmounts());
        rejectUnknownMethods(countedByMethod, expected);

        CashCountEntity cashCount = CashCountEntity.builder()
                .warehouse(warehouse)
                .createdByUserEntity(createdBy)
                .closedAt(closedAt)
                .periodFrom(periodFrom)
                .openingFloat(openingFloat)
                .unattributedAmount(expected.unattributedAmount())
                .unattributedCount((int) expected.unattributedCount())
                .note(normalizeOptional(request.note()))
                .lines(new ArrayList<>())
                .build();

        for (ExpectedCashCountMethodResponse row : expected.methods()) {
            // A method the cashier did not report is a method they counted nothing in,
            // which is what an untouched posnet means.
            BigDecimal counted = ExpectedCashCountService.normalize(
                    countedByMethod.getOrDefault(row.paymentMethodId(), BigDecimal.ZERO));

            PaymentMethodEntity method = paymentMethodRepository.findById(row.paymentMethodId())
                    .orElseThrow(() -> new NotFoundException(
                            "Payment method not found: " + row.paymentMethodId()));

            cashCount.getLines().add(CashCountLineEntity.builder()
                    .cashCount(cashCount)
                    .paymentMethod(method)
                    // Frozen copies: the catalog may change, this record may not.
                    .paymentMethodName(row.paymentMethodName())
                    .isCash(row.isCash())
                    .expectedAmount(row.expectedAmount())
                    .countedAmount(counted)
                    .differenceAmount(counted.subtract(row.expectedAmount()))
                    .build());
        }

        return toResponse(cashCountRepository.save(cashCount));
    }

    /** The history, newest first, restricted to the branches the caller may see. */
    @Transactional(readOnly = true)
    public Page<CashCountResponse> search(Long warehouseId, Pageable pageable) {
        WarehouseScope scope = warehouseScopeService.resolve(warehouseId);

        if (scope.seesNothing()) {
            return Page.empty(pageable);
        }

        return cashCountRepository
                .search(scope.unrestricted(), scope.warehouseIds(), warehouseId, pageable)
                .map(this::toResponse);
    }

    /**
     * One count in full.
     *
     * <p>Re-checks the branch rather than trusting that whoever has the id was allowed to
     * list it: restricting the listing and leaving the detail open moves the leak one click
     * away instead of closing it.
     */
    @Transactional(readOnly = true)
    public CashCountResponse findById(Long id) {
        CashCountEntity cashCount = cashCountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cash count not found: " + id));

        WarehouseScope scope = warehouseScopeService.resolve(null);
        boolean visible = scope.unrestricted()
                || scope.warehouseIds().contains(cashCount.getWarehouse().getId());

        if (!visible) {
            throw new ForbiddenException("No tenés permiso para ver el cierre de esa sucursal.");
        }

        return toResponse(cashCount);
    }

    private Map<Long, BigDecimal> countedByMethod(List<CashCountLineRequest> lines) {
        Map<Long, BigDecimal> counted = new HashMap<>();

        for (CashCountLineRequest line : lines) {
            // Summed rather than overwritten: two entries for one method is a client bug,
            // and dropping one of them silently would hide money.
            counted.merge(line.paymentMethodId(), line.countedAmount(), BigDecimal::add);
        }

        return counted;
    }

    /**
     * A counted method the expected figures never mentioned is refused.
     *
     * <p>It means the client is counting something this branch cannot have moved — a stale
     * form, or a method deleted mid-shift. Storing it would put a line in the record whose
     * expected amount nobody ever computed.
     */
    private void rejectUnknownMethods(Map<Long, BigDecimal> counted, ExpectedCashCountResponse expected) {
        List<Long> known = expected.methods().stream()
                .map(ExpectedCashCountMethodResponse::paymentMethodId)
                .toList();

        List<Long> unknown = counted.keySet().stream()
                .filter(id -> !known.contains(id))
                .toList();

        if (!unknown.isEmpty()) {
            throw new ValidationException(
                    "Estás informando un método de pago que no corresponde a este cierre. "
                            + "Actualizá la pantalla y volvé a intentarlo.");
        }
    }

    private CashCountResponse toResponse(CashCountEntity cashCount) {
        List<CashCountLineResponse> lines = cashCount.getLines().stream()
                .sorted((a, b) -> {
                    if (!a.getIsCash().equals(b.getIsCash())) {
                        return Boolean.TRUE.equals(a.getIsCash()) ? -1 : 1;
                    }
                    return a.getPaymentMethodName().compareToIgnoreCase(b.getPaymentMethodName());
                })
                .map(line -> new CashCountLineResponse(
                        line.getPaymentMethod().getId(),
                        line.getPaymentMethodName(),
                        Boolean.TRUE.equals(line.getIsCash()),
                        line.getExpectedAmount(),
                        line.getCountedAmount(),
                        line.getDifferenceAmount()))
                .toList();

        BigDecimal totalExpected = sum(lines, CashCountLineResponse::expectedAmount);
        BigDecimal totalCounted = sum(lines, CashCountLineResponse::countedAmount);

        UserEntity user = cashCount.getCreatedByUserEntity();

        return new CashCountResponse(
                cashCount.getId(),
                cashCount.getWarehouse().getId(),
                cashCount.getWarehouse().getName(),
                user == null ? null : fullName(user),
                cashCount.getClosedAt(),
                cashCount.getPeriodFrom(),
                cashCount.getOpeningFloat(),
                lines,
                totalExpected,
                totalCounted,
                totalCounted.subtract(totalExpected),
                cashCount.getUnattributedAmount(),
                cashCount.getUnattributedCount(),
                cashCount.getNote()
        );
    }

    private BigDecimal sum(List<CashCountLineResponse> lines,
                           java.util.function.Function<CashCountLineResponse, BigDecimal> field) {
        return lines.stream()
                .map(field)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String fullName(UserEntity user) {
        return user.getLastName() == null || user.getLastName().isBlank()
                ? user.getName()
                : user.getName() + " " + user.getLastName();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
