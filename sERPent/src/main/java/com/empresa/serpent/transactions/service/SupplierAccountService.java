package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.PurchaseEntity;
import com.empresa.serpent.transactions.domain.entity.SupplierPaymentEntity;
import com.empresa.serpent.transactions.domain.enums.AccountMovementType;
import com.empresa.serpent.transactions.repository.PurchaseRepository;
import com.empresa.serpent.transactions.repository.SupplierPaymentRepository;
import com.empresa.serpent.transactions.web.dto.response.AccountStatementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A supplier's current account, mirroring {@link CustomerAccountService}: credit purchases
 * raise what we owe, payments lower it, and nothing is allocated to a particular purchase.
 *
 * <p>Paying a supplier is NOT an expense. The purchase already hit the result when the
 * goods came in; booking the payment again would count the same money twice. Payments are
 * their own entity and are not {@code ExpenseEntity}, so they cannot reach the expense
 * listing at all.
 *
 * <p>There is no returns leg here because the system does not model purchase returns. If
 * it ever does, it belongs in this statement the way sale returns belong in the customer's.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierAccountService {

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;

    /** The full statement: every movement in order, each with the balance it left behind. */
    public AccountStatementResponse getStatement(Long supplierId) {
        SupplierEntity supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier not found: " + supplierId));

        List<AccountStatementBuilder.Movement> movements = new ArrayList<>();

        for (PurchaseEntity purchase : purchaseRepository.findCreditPurchasesBySupplierId(supplierId)) {
            movements.add(new AccountStatementBuilder.Movement(
                    purchase.getTransaction().getDate().toLocalDate(),
                    AccountStatementBuilder.Order.CHARGE,
                    purchase.getId(),
                    AccountMovementType.CREDIT_PURCHASE,
                    describePurchase(purchase),
                    purchase.getTransaction().getTotal()));
        }

        for (SupplierPaymentEntity payment : supplierPaymentRepository.findBySupplierId(supplierId)) {
            movements.add(new AccountStatementBuilder.Movement(
                    payment.getPaymentDate(),
                    AccountStatementBuilder.Order.PAYMENT,
                    payment.getId(),
                    AccountMovementType.SUPPLIER_PAYMENT,
                    describePayment(payment),
                    payment.getAmount().negate()));
        }

        return AccountStatementBuilder.build(supplier.getId(), supplier.getName(), movements);
    }

    /** The balance on its own, for listings that show many suppliers at once. */
    public BigDecimal getBalance(Long supplierId) {
        return purchaseRepository.sumCreditPurchasesBySupplierId(supplierId)
                .subtract(supplierPaymentRepository.sumBySupplierId(supplierId));
    }

    private String describePurchase(PurchaseEntity purchase) {
        String receipt = purchase.getReceiptNumber();
        return receipt != null && !receipt.isBlank()
                ? "Compra #" + purchase.getId() + " (comprobante " + receipt + ")."
                : "Compra #" + purchase.getId() + ".";
    }

    private String describePayment(SupplierPaymentEntity payment) {
        String base = "Pago en " + payment.getPaymentMethod().getName() + ".";
        String note = payment.getNote();
        return note != null && !note.isBlank() ? base + " " + note.trim() : base;
    }
}
