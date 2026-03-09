ALTER TABLE transactions
DROP CONSTRAINT ck_transactions_type;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_type
        CHECK (type IN ('SALE', 'EXPENSE', 'PURCHASE', 'ADJUSTMENT', 'TRANSFER'));