package com.empresa.serpent.transactions.domain.enums;

/** What a line in a current-account statement represents. */
public enum AccountMovementType {

    /** A sale taken on account: raises what the customer owes. */
    CREDIT_SALE,

    /** Goods coming back from a credit sale: lowers what the customer owes. */
    SALE_RETURN,

    /** The customer paying down their balance. */
    CUSTOMER_PAYMENT,

    /** A purchase taken on account: raises what we owe the supplier. */
    CREDIT_PURCHASE,

    /** Us paying down what we owe the supplier. */
    SUPPLIER_PAYMENT
}
