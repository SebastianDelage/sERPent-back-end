package com.empresa.serpent.transactions.service;

import com.empresa.serpent.catalog.domain.entity.CustomerEntity;
import com.empresa.serpent.catalog.repository.CustomerRepository;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.transactions.domain.entity.CustomerPaymentEntity;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.SaleReturnEntity;
import com.empresa.serpent.transactions.domain.enums.AccountMovementType;
import com.empresa.serpent.transactions.repository.CustomerPaymentRepository;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.SaleReturnRepository;
import com.empresa.serpent.transactions.web.dto.response.AccountStatementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer's current account, kept BY BALANCE rather than by allocating payments to
 * particular sales.
 *
 * <p>Three things move the balance: credit sales raise it, returns against those sales
 * lower it, and payments lower it. Nothing here settles a specific sale, which is what
 * makes a partial payment ordinary — it is simply a smaller number.
 *
 * <p>Collecting is NOT a sale. The sale hit the result when it happened; the payment only
 * moves cash. That is why payments are their own entity and never a transaction, and why
 * nothing in this class touches the sales reports.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAccountService {

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final CustomerPaymentRepository customerPaymentRepository;

    /**
     * The full statement: every movement in order, each with the balance it left behind.
     *
     * <p>The running balance is derived from the same rows that are returned, so the
     * headline figure and the detail can never disagree.
     */
    public AccountStatementResponse getStatement(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

        List<AccountStatementBuilder.Movement> movements = new ArrayList<>();

        for (SaleEntity sale : saleRepository.findCreditSalesByCustomerId(customerId)) {
            movements.add(new AccountStatementBuilder.Movement(
                    sale.getTransaction().getDate().toLocalDate(),
                    AccountStatementBuilder.Order.CHARGE,
                    sale.getId(),
                    AccountMovementType.CREDIT_SALE,
                    describeSale(sale),
                    sale.getTransaction().getTotal()));
        }

        // Stored negative already, so it lowers the balance by being added like the rest.
        for (SaleReturnEntity saleReturn : saleReturnRepository.findAgainstCreditSalesByCustomerId(customerId)) {
            movements.add(new AccountStatementBuilder.Movement(
                    saleReturn.getTransaction().getDate().toLocalDate(),
                    AccountStatementBuilder.Order.REVERSAL,
                    saleReturn.getId(),
                    AccountMovementType.SALE_RETURN,
                    "Devolución de la venta #" + saleReturn.getOriginalSale().getId() + ".",
                    saleReturn.getTransaction().getTotal()));
        }

        for (CustomerPaymentEntity payment : customerPaymentRepository.findByCustomerId(customerId)) {
            movements.add(new AccountStatementBuilder.Movement(
                    payment.getPaymentDate(),
                    AccountStatementBuilder.Order.PAYMENT,
                    payment.getId(),
                    AccountMovementType.CUSTOMER_PAYMENT,
                    describePayment(payment),
                    payment.getAmount().negate()));
        }

        return AccountStatementBuilder.build(customer.getId(), customer.getName(), movements);
    }

    /**
     * The balance on its own, for listings that show many customers at once.
     *
     * <p>Three sums instead of loading every movement. It agrees with
     * {@link #getStatement(Long)} because it adds the same three terms.
     */
    public BigDecimal getBalance(Long customerId) {
        return saleRepository.sumCreditSalesByCustomerId(customerId)
                .add(saleReturnRepository.sumAgainstCreditSalesByCustomerId(customerId))
                .subtract(customerPaymentRepository.sumByCustomerId(customerId));
    }

    private String describeSale(SaleEntity sale) {
        String invoice = sale.getInvoiceNumber();
        return invoice != null && !invoice.isBlank()
                ? "Venta #" + sale.getId() + " (comprobante " + invoice + ")."
                : "Venta #" + sale.getId() + ".";
    }

    private String describePayment(CustomerPaymentEntity payment) {
        String base = "Cobro en " + payment.getPaymentMethod().getName() + ".";
        String note = payment.getNote();
        return note != null && !note.isBlank() ? base + " " + note.trim() : base;
    }

}
