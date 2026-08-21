package com.empresa.serpent.transactions.repository.projection;

/** One (transaction, branch) pair, for the history listing's branch column. */
public interface TransactionWarehouseProjection {

    Long getTransactionId();

    String getWarehouseName();
}
