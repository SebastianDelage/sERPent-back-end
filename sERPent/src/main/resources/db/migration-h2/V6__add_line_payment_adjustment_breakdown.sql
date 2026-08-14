-- V6__add_line_payment_adjustment_breakdown.sql
-- Freezes how a sale line's payment-method surcharge (mechanism 2) was arrived at, so
-- the sale detail can show "3900 + 11% (Transfer) = 4329" long after the catalog price
-- or the rule changed. unit_price keeps holding the effective price and is unchanged;
-- these columns are additive and exist only to explain it.
--
-- The method name is frozen rather than read through the transaction's payment method:
-- renaming a method later would otherwise rewrite the caption of every past sale.
--
-- All three are NULL together when no rule applied, which is both the ordinary sale and
-- every row written before this migration — ADD COLUMN leaves existing rows NULL, and
-- the all-null branch of the CHECK accepts them, so the migration cannot fail on
-- pre-existing data.
--
-- Not to be confused with the sale-wide manual adjustment on `sales` (V4): that is one
-- figure for the whole sale, this is per line.
--
-- NOTE: this file must stay identical in effect to
-- db/migration/V17__add_line_payment_adjustment_breakdown.sql

ALTER TABLE transaction_details
    ADD COLUMN base_unit_price NUMERIC(19,4);

ALTER TABLE transaction_details
    ADD COLUMN applied_percentage NUMERIC(9,4);

ALTER TABLE transaction_details
    ADD COLUMN applied_method_name VARCHAR(80);

ALTER TABLE transaction_details
    ADD CONSTRAINT ck_transaction_details_adjustment_breakdown
        CHECK (
            (
                base_unit_price IS NULL
                    AND applied_percentage IS NULL
                    AND applied_method_name IS NULL
                )
                OR (
                base_unit_price IS NOT NULL
                    AND applied_percentage IS NOT NULL
                    AND applied_method_name IS NOT NULL
                )
            );
