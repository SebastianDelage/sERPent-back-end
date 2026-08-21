package com.empresa.serpent.cashcount.service;

import com.empresa.serpent.cashcount.domain.entity.CashCountEntity;
import com.empresa.serpent.cashcount.repository.CashCountRepository;
import com.empresa.serpent.cashcount.repository.CashCountSourceRepository;
import com.empresa.serpent.cashcount.repository.projection.MethodAmountProjection;
import com.empresa.serpent.cashcount.web.dto.response.ExpectedCashCountMethodResponse;
import com.empresa.serpent.cashcount.web.dto.response.ExpectedCashCountResponse;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the till should be holding right now, for the shift in progress.
 *
 * <p>The period runs from the branch's last close to this instant. There are no configured
 * shift hours: a shift is simply "since somebody last counted", which is how the shop
 * actually works.
 *
 * <p>WHY THE OUTFLOWS ONLY TOUCH CASH: an expense or a supplier payment made by transfer
 * does not come out of the drawer, and it does not reverse anything the posnet will report
 * either. What a cashier counts for a non-cash method is the money that came IN through it
 * during the shift. So sales, collections and refunds apply to every method, while the
 * opening float and the three outflows apply only to the one method flagged as cash.
 */
@Service
@RequiredArgsConstructor
public class ExpectedCashCountService {

    private final CashCountSourceRepository sourceRepository;
    private final CashCountRepository cashCountRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseScopeService warehouseScopeService;

    /**
     * The expected figures for a branch's open shift.
     *
     * <p>{@code openingFloat} is the cash left in the drawer to make change. It is an input
     * because only the person opening the shift knows it — nothing in the system records it
     * until a close stores it.
     */
    @Transactional(readOnly = true)
    public ExpectedCashCountResponse getExpected(Long warehouseId, BigDecimal openingFloat) {
        // Authorization rides on the read scope: an EMPLOYEE naming a branch that is not
        // theirs is refused here, before any figure is computed.
        warehouseScopeService.resolve(warehouseId);

        WarehouseEntity warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found: " + warehouseId));

        LocalDateTime periodTo = LocalDateTime.now();
        LocalDateTime periodFrom = lastCloseOf(warehouseId).orElse(null);
        BigDecimal float_ = normalize(openingFloat == null ? BigDecimal.ZERO : openingFloat);

        return build(warehouse, periodFrom, periodTo, float_);
    }

    /** Where the open shift starts, or empty when this branch has never been counted. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastCloseOf(Long warehouseId) {
        return cashCountRepository.findFirstByWarehouseIdOrderByClosedAtDescIdDesc(warehouseId)
                .map(CashCountEntity::getClosedAt);
    }

    /**
     * The whole computation, shared by the read endpoint and by the close.
     *
     * <p>The close calls this again with its own {@code periodTo} rather than trusting the
     * figures the client last saw: what gets frozen has to be what the server computed at
     * the moment of closing, not what a screen was showing some minutes earlier.
     */
    @Transactional(readOnly = true)
    public ExpectedCashCountResponse build(WarehouseEntity warehouse,
                                           LocalDateTime periodFrom,
                                           LocalDateTime periodTo,
                                           BigDecimal openingFloat) {
        Long warehouseId = warehouse.getId();

        Map<Long, BigDecimal> sales = byMethod(sourceRepository.sumSalesByMethod(warehouseId, periodFrom, periodTo));
        Map<Long, BigDecimal> customerPayments = byMethod(sourceRepository.sumCustomerPaymentsByMethod(warehouseId, periodFrom, periodTo));
        Map<Long, BigDecimal> returns = byMethod(sourceRepository.sumReturnsByMethod(warehouseId, periodFrom, periodTo));
        Map<Long, BigDecimal> supplierPayments = byMethod(sourceRepository.sumSupplierPaymentsByMethod(warehouseId, periodFrom, periodTo));
        Map<Long, BigDecimal> expenses = byMethod(sourceRepository.sumExpensesByMethod(warehouseId, periodFrom, periodTo));
        Map<Long, BigDecimal> purchases = byMethod(sourceRepository.sumPurchasesByMethod(warehouseId, periodFrom, periodTo));

        Optional<PaymentMethodEntity> cashMethod = paymentMethodRepository.findByIsCashTrue();
        Long cashMethodId = cashMethod.map(PaymentMethodEntity::getId).orElse(null);

        List<PaymentMethodEntity> methods = methodsToShow(sales, customerPayments, returns, cashMethodId);

        List<ExpectedCashCountMethodResponse> rows = new ArrayList<>();
        for (PaymentMethodEntity method : methods) {
            boolean isCash = method.getId().equals(cashMethodId);

            BigDecimal methodSales = amount(sales, method.getId());
            BigDecimal methodCustomerPayments = amount(customerPayments, method.getId());
            // Already stored negative, so it adds rather than subtracts.
            BigDecimal methodReturns = amount(returns, method.getId());

            // Only the drawer is drained by these; see the class javadoc.
            BigDecimal methodSupplierPayments = isCash ? amount(supplierPayments, method.getId()) : BigDecimal.ZERO;
            BigDecimal methodExpenses = isCash ? amount(expenses, method.getId()) : BigDecimal.ZERO;
            BigDecimal methodPurchases = isCash ? amount(purchases, method.getId()) : BigDecimal.ZERO;
            BigDecimal methodFloat = isCash ? openingFloat : BigDecimal.ZERO;

            BigDecimal expected = methodFloat
                    .add(methodSales)
                    .add(methodCustomerPayments)
                    .add(methodReturns)
                    .subtract(methodSupplierPayments)
                    .subtract(methodExpenses)
                    .subtract(methodPurchases);

            rows.add(new ExpectedCashCountMethodResponse(
                    method.getId(),
                    method.getName(),
                    isCash,
                    normalize(methodFloat),
                    normalize(methodSales),
                    normalize(methodCustomerPayments),
                    normalize(methodReturns),
                    normalize(methodSupplierPayments),
                    normalize(methodExpenses),
                    normalize(methodPurchases),
                    normalize(expected)
            ));
        }

        BigDecimal unattributedAmount = normalize(
                nullToZero(sourceRepository.sumUnattributedReturns(warehouseId, periodFrom, periodTo))
                        .add(nullToZero(sourceRepository.sumUnattributedExpenses(warehouseId, periodFrom, periodTo))));

        long unattributedCount =
                sourceRepository.countUnattributedReturns(warehouseId, periodFrom, periodTo)
                        + sourceRepository.countUnattributedExpenses(warehouseId, periodFrom, periodTo);

        return new ExpectedCashCountResponse(
                warehouseId,
                warehouse.getName(),
                periodFrom,
                periodTo,
                rows,
                cashMethodId != null,
                unattributedAmount,
                unattributedCount,
                warnings(cashMethodId, periodFrom, unattributedCount, unattributedAmount)
        );
    }

    /**
     * Which methods get a row.
     *
     * <p>Every active method, so a cashier can report a posnet batch the system did not
     * expect, plus any retired method that still had movement in the period — closing a
     * method mid-shift must not hide what already went through it. Cash is always present
     * when configured, because the opening float lives there even on a shift with no sales.
     */
    private List<PaymentMethodEntity> methodsToShow(Map<Long, BigDecimal> sales,
                                                    Map<Long, BigDecimal> customerPayments,
                                                    Map<Long, BigDecimal> returns,
                                                    Long cashMethodId) {
        Map<Long, PaymentMethodEntity> byId = new LinkedHashMap<>();

        paymentMethodRepository.search(null, false)
                .forEach(method -> byId.put(method.getId(), method));

        List<Long> withMovement = new ArrayList<>();
        withMovement.addAll(sales.keySet());
        withMovement.addAll(customerPayments.keySet());
        withMovement.addAll(returns.keySet());
        if (cashMethodId != null) {
            withMovement.add(cashMethodId);
        }

        withMovement.stream()
                .filter(id -> !byId.containsKey(id))
                .distinct()
                .forEach(id -> paymentMethodRepository.findById(id)
                        .ifPresent(method -> byId.put(id, method)));

        // Cash first: it is the one the cashier actually counts by hand.
        return byId.values().stream()
                .sorted((a, b) -> {
                    boolean aCash = a.getId().equals(cashMethodId);
                    boolean bCash = b.getId().equals(cashMethodId);
                    if (aCash != bCash) {
                        return aCash ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .toList();
    }

    /**
     * Everything that makes these numbers less than the whole story, in plain Spanish.
     *
     * <p>The missing-cash-method case is the one that matters most: without it the drawer
     * cannot be told apart from the card terminal, and reporting zero would look like an
     * answer. Saying so is the answer.
     */
    private List<String> warnings(Long cashMethodId,
                                  LocalDateTime periodFrom,
                                  long unattributedCount,
                                  BigDecimal unattributedAmount) {
        List<String> warnings = new ArrayList<>();

        if (cashMethodId == null) {
            warnings.add("Todavía no hay ningún método de pago marcado como efectivo, así que no se "
                    + "puede calcular lo esperado en el cajón. Pedile a quien administra el sistema "
                    + "que marque cuál es el efectivo en Métodos de pago.");
        }

        if (periodFrom == null) {
            warnings.add("Es el primer cierre de esta sucursal, así que el período abarca todo lo "
                    + "registrado hasta ahora. A partir del próximo, va a arrancar en este cierre.");
        }

        if (unattributedCount > 0) {
            warnings.add("Hay " + unattributedCount + " movimiento" + (unattributedCount == 1 ? "" : "s")
                    + " por " + unattributedAmount.toPlainString() + " que no dice" + (unattributedCount == 1 ? "" : "n")
                    + " con qué método se pagó, porque se registraron antes de que se pidiera. "
                    + "No están sumados en ninguna fila y pueden explicar una diferencia.");
        }

        return warnings;
    }

    private Map<Long, BigDecimal> byMethod(List<MethodAmountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                MethodAmountProjection::getPaymentMethodId,
                row -> nullToZero(row.getAmount()),
                BigDecimal::add,
                LinkedHashMap::new));
    }

    private BigDecimal amount(Map<Long, BigDecimal> source, Long methodId) {
        return source.getOrDefault(methodId, BigDecimal.ZERO);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Matches the NUMERIC(19,4) money columns, so nothing drifts by scale alone. */
    static BigDecimal normalize(BigDecimal value) {
        return nullToZero(value).setScale(4, RoundingMode.HALF_UP);
    }
}
