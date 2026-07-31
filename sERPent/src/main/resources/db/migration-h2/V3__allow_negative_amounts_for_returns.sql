-- V3__allow_negative_amounts_for_returns.sql
-- Sale returns now carry a real amount, stored negative (money going out) so it
-- subtracts in any aggregation. The existing non-negative CHECKs would reject that,
-- so they are replaced by type-scoped ones that still keep SALE (and every other
-- type) strictly non-negative.
--
-- transaction_details has no type column of its own and a CHECK cannot reach into
-- another table, so the parent's type is denormalized into the row. It is written
-- by TransactionDetailEntity's @PrePersist and never updated (the parent's type is
-- immutable), so the mirror cannot drift.
--
-- NOTE: this file must stay identical in effect to
-- db/migration/V14__allow_negative_amounts_for_returns.sql

-- =========================
-- 1) TRANSACTIONS: SCOPE THE TOTAL SIGN BY TYPE
-- =========================
ALTER TABLE transactions
DROP CONSTRAINT ck_transactions_total_nonneg;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_total_sign
        CHECK (
            (type = 'RETURN' AND total <= 0)
                OR (type <> 'RETURN' AND total >= 0)
            );

-- =========================
-- 2) TRANSACTION_DETAILS: DENORMALIZE THE PARENT TYPE
-- =========================
ALTER TABLE transaction_details
    ADD COLUMN transaction_type VARCHAR(30);

UPDATE transaction_details d
SET transaction_type = (
    SELECT t.type FROM transactions t WHERE t.transaction_id = d.transaction_id
);

ALTER TABLE transaction_details
    ALTER COLUMN transaction_type SET NOT NULL;

ALTER TABLE transaction_details
    ADD CONSTRAINT ck_transaction_details_type
        CHECK (
            transaction_type IN (
                                 'SALE',
                                 'EXPENSE',
                                 'PURCHASE',
                                 'ADJUSTMENT',
                                 'TRANSFER',
                                 'RETURN',
                                 'TRANSFORMATION'
                )
            );

-- =========================
-- 3) TRANSACTION_DETAILS: SCOPE THE AMOUNT SIGNS BY TYPE
-- =========================
ALTER TABLE transaction_details
DROP CONSTRAINT ck_transaction_details_unit_price_nonneg;

ALTER TABLE transaction_details
DROP CONSTRAINT ck_transaction_details_subtotal_nonneg;

ALTER TABLE transaction_details
    ADD CONSTRAINT ck_transaction_details_unit_price_sign
        CHECK (
            (transaction_type = 'RETURN' AND unit_price <= 0)
                OR (transaction_type <> 'RETURN' AND unit_price >= 0)
            );

ALTER TABLE transaction_details
    ADD CONSTRAINT ck_transaction_details_subtotal_sign
        CHECK (
            (transaction_type = 'RETURN' AND subtotal <= 0)
                OR (transaction_type <> 'RETURN' AND subtotal >= 0)
            );
