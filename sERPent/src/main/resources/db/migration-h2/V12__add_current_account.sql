-- V12__add_current_account.sql
-- Current accounts for customers and suppliers, modelled BY BALANCE, not by allocating
-- payments to documents.
--
-- A credit sale adds to the customer's balance; a payment subtracts from it. Nothing ties
-- a payment to a particular sale, which is what makes partial payments free: the customer
-- pays what they can, the balance drops by that much, and no one has to decide which
-- invoice it settled. Same thing mirrored for suppliers.
--
-- THE RULE THAT MUST NOT BE BROKEN: collecting a debt is NOT a new sale, and paying a
-- supplier is NOT an expense. The sale and the purchase already hit the result when they
-- happened; the payment only moves cash. That is why payments live in their own tables and
-- are NOT transactions: every sales aggregation filters on transactions.type, so a payment
-- that is not a transaction can never leak into revenue by someone forgetting a filter.
--
-- sales.customer_id already existed as a bare BIGINT with no foreign key, and was never
-- populated — the sale form only ever sent customer_name/customer_document as free text.
-- It is promoted to a real foreign key here, which is safe precisely because it is empty.
-- The free-text fields stay: counter sales still have no customer record, and most sales
-- are counter sales.
--
-- on_credit is an EXPLICIT flag on both sales and purchases, deliberately not inferred
-- from "has no payment method". For purchases that distinction matters: a purchase with no
-- payment method has been allowed since V7 and means nothing in particular, so reading it
-- as "on credit" would retroactively reinterpret existing rows. Existing rows keep their
-- meaning: on_credit defaults to FALSE everywhere.
--
-- NOTE: this file must stay identical in effect to
-- db/migration/V22__add_current_account.sql

-- =========================
-- 1) CUSTOMERS
-- =========================
-- Deliberately narrower than suppliers: a customer needs to be identifiable and
-- reachable to collect from, and nothing more. No tax condition, no address — this is a
-- shop that fies to regulars, not an invoicing system.
CREATE TABLE customers (
                           customer_id     BIGSERIAL PRIMARY KEY,
                           name            VARCHAR(150) NOT NULL,
                           document_type   VARCHAR(30),
                           document_number VARCHAR(40),
                           phone           VARCHAR(50),
                           active          BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT ux_customers_name UNIQUE (name)
);

-- =========================
-- 2) SALES: real customer link + the credit flag
-- =========================
ALTER TABLE sales
    ADD CONSTRAINT fk_sales_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id);

ALTER TABLE sales
    ADD COLUMN on_credit BOOLEAN NOT NULL DEFAULT FALSE;

-- A sale on credit is owed by someone in particular: free text is not enough to carry a
-- balance, so it must name a real customer.
ALTER TABLE sales
    ADD CONSTRAINT ck_sales_credit_requires_customer
        CHECK (on_credit = FALSE OR customer_id IS NOT NULL);

CREATE INDEX idx_sales_customer ON sales(customer_id);

-- =========================
-- 3) PURCHASES: the credit flag
-- =========================
ALTER TABLE purchases
    ADD COLUMN on_credit BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE purchases
    ADD CONSTRAINT ck_purchases_credit_requires_supplier
        CHECK (on_credit = FALSE OR supplier_id IS NOT NULL);

-- =========================
-- 4) CUSTOMER PAYMENTS
-- =========================
-- Money coming in against a balance. Not a sale: it must never reach the sales reports.
CREATE TABLE customer_payments (
                                   customer_payment_id BIGSERIAL PRIMARY KEY,
                                   customer_id         BIGINT NOT NULL,
                                   payment_method_id   BIGINT NOT NULL,
                                   amount              NUMERIC(19,4) NOT NULL,
                                   payment_date        DATE NOT NULL,
                                   note                TEXT,
                                   created_by_user_id  BIGINT NOT NULL,
                                   created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_customer_payments_customer
                                       FOREIGN KEY (customer_id) REFERENCES customers(customer_id),

                                   CONSTRAINT fk_customer_payments_payment_method
                                       FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_method_id),

                                   CONSTRAINT fk_customer_payments_user
                                       FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),

                                   CONSTRAINT ck_customer_payments_amount_positive
                                       CHECK (amount > 0)
);

CREATE INDEX idx_customer_payments_customer ON customer_payments(customer_id);
CREATE INDEX idx_customer_payments_date ON customer_payments(payment_date);

-- =========================
-- 5) SUPPLIER PAYMENTS
-- =========================
-- Money going out against a balance. Not an expense: expenses live in their own table and
-- this one is deliberately not it.
CREATE TABLE supplier_payments (
                                   supplier_payment_id BIGSERIAL PRIMARY KEY,
                                   supplier_id         BIGINT NOT NULL,
                                   payment_method_id   BIGINT NOT NULL,
                                   amount              NUMERIC(19,4) NOT NULL,
                                   payment_date        DATE NOT NULL,
                                   note                TEXT,
                                   created_by_user_id  BIGINT NOT NULL,
                                   created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_supplier_payments_supplier
                                       FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),

                                   CONSTRAINT fk_supplier_payments_payment_method
                                       FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_method_id),

                                   CONSTRAINT fk_supplier_payments_user
                                       FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),

                                   CONSTRAINT ck_supplier_payments_amount_positive
                                       CHECK (amount > 0)
);

CREATE INDEX idx_supplier_payments_supplier ON supplier_payments(supplier_id);
CREATE INDEX idx_supplier_payments_date ON supplier_payments(payment_date);
