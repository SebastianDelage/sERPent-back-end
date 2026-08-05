-- V4__add_sale_adjustment.sql
-- Manual adjustment (discount or surcharge) on a sale total. It lives on the sale
-- header, not as a transaction line: it is money, not a stock movement.
--
-- Direction is carried by the sign, so there is no separate discount/surcharge flag:
-- a negative value discounts, a positive value surcharges. adjustment_amount is the
-- resolved figure, frozen at creation time and never recomputed.
--
-- A discount cannot drive the sale total below zero. That is already enforced at the
-- database level by ck_transactions_total_sign (added in V3), which keeps every
-- non-RETURN transaction total non-negative, so no extra constraint is needed here.
--
-- NOTE: this file must stay identical in effect to
-- db/migration/V15__add_sale_adjustment.sql

ALTER TABLE sales
    ADD COLUMN adjustment_type VARCHAR(20) NOT NULL DEFAULT 'NONE';

ALTER TABLE sales
    ADD COLUMN adjustment_value NUMERIC(19,4) NOT NULL DEFAULT 0;

ALTER TABLE sales
    ADD COLUMN adjustment_amount NUMERIC(19,4) NOT NULL DEFAULT 0;

ALTER TABLE sales
    ADD CONSTRAINT ck_sales_adjustment_type
        CHECK (adjustment_type IN ('NONE', 'PERCENTAGE', 'FIXED'));

ALTER TABLE sales
    ADD CONSTRAINT ck_sales_adjustment_none_zero
        CHECK (
            adjustment_type <> 'NONE'
                OR (adjustment_amount = 0 AND adjustment_value = 0)
            );
