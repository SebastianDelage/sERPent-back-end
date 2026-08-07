-- V16__add_product_payment_adjustments.sql
-- Per-product price adjustment for one payment method: tobacco surcharged when paid
-- by card, for example. Percentage only, direction carried by the sign (negative
-- discounts, positive surcharges) — the same single-sign convention as the
-- sale-level manual adjustment, so there is no separate discount/surcharge flag.
--
-- One rule per (product, payment method). `active` lets a rule be switched off
-- without deleting it, since surcharges change over time and the row is history.
--
-- The -100 floor is not cosmetic: a percentage below it would drive the sale line's
-- unit price negative, which would then violate ck_transaction_details_unit_price_sign
-- (added in V14) with an opaque failure far from its cause.
--
-- NOTE: this file must stay identical in effect to
-- db/migration-h2/V5__add_product_payment_adjustments.sql

CREATE TABLE product_payment_adjustments (
                                             product_payment_adjustment_id BIGSERIAL PRIMARY KEY,
                                             product_id            BIGINT NOT NULL,
                                             payment_method_id     BIGINT NOT NULL,
                                             adjustment_percentage NUMERIC(9,4) NOT NULL,
                                             active                BOOLEAN NOT NULL DEFAULT TRUE,

                                             CONSTRAINT fk_ppa_product
                                                 FOREIGN KEY (product_id) REFERENCES products(product_id),

                                             CONSTRAINT fk_ppa_payment_method
                                                 FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_method_id),

                                             CONSTRAINT ux_ppa_product_payment_method
                                                 UNIQUE (product_id, payment_method_id),

                                             CONSTRAINT ck_ppa_percentage_floor
                                                 CHECK (adjustment_percentage >= -100)
);

CREATE INDEX idx_ppa_payment_method ON product_payment_adjustments(payment_method_id);
