-- V25__add_cash_count.sql
-- Till reconciliation (arqueo de caja): the shift count that used to live in a spreadsheet.
--
-- A close is a PHOTO, not a period that gets locked. Nothing is frozen against further
-- edits, and no other feature has to ask permission from it. The only thing a close does
-- to the future is move the anchor: the next close covers from this one's closed_at.
--
-- THE RULE THAT MUST NOT BE BROKEN: the expected amounts are frozen INTO the close and
-- never recomputed. A close is what the till was believed to hold at that moment; if a
-- later correction changes what today's query would say, the historical record must not
-- move with it. That is why the lines carry their own copy of the method name and the
-- cash flag rather than reading them through the foreign key.
--
-- SCOPE IS PER BRANCH, NOT PER TERMINAL. Two cashiers sharing one branch share one anchor,
-- so the second close of a shift sweeps in whatever the first one already counted. That is
-- a deliberate call: today there is one terminal per branch, and tying the count to a
-- terminal before that case exists would model a problem nobody has. When a branch really
-- runs two tills at once, this is the decision to revisit.
--
-- NOTE: this file must stay identical in effect to
-- db/migration-h2/V15__add_cash_count.sql

-- =========================
-- 1) WHICH METHOD IS CASH
-- =========================
-- Payment methods are editable catalog data, so "cash" cannot be recognised by its name:
-- the owner may rename it, translate it, or spell it differently. It gets an explicit flag,
-- and only the cash one drains the drawer when an expense or a supplier gets paid.
--
-- At most ONE method may carry this flag. That is enforced in PaymentMethodService rather
-- than here, because expressing it in SQL needs a partial unique index and H2 has none —
-- and these two files have to stay identical in effect.
ALTER TABLE payment_methods
    ADD COLUMN is_cash BOOLEAN NOT NULL DEFAULT FALSE;

-- A GUESS, not a fact. It saves the owner one click on a fresh install and is wrong the
-- moment someone named their cash method something else. The screen shows the flag so it
-- can be corrected, and the expected-amounts endpoint refuses to answer while no method
-- carries it — an arqueo that reports zero when it actually does not know is worse than
-- one that refuses. Only the lowest id matches, so the "at most one" rule holds even if
-- several names look like cash.
UPDATE payment_methods
SET is_cash = TRUE
WHERE payment_method_id = (
    SELECT MIN(payment_method_id)
    FROM payment_methods
    WHERE LOWER(name) IN ('efectivo', 'cash', 'contado')
);

-- =========================
-- 2) WHERE THE MONEY MOVED
-- =========================
-- Current-account movements had no branch. Without one a payment collected at Norte would
-- land in every branch's count or in none, and both are wrong, so the count could not be
-- per branch at all. Nullable because the rows that already exist genuinely do not know;
-- the service requires it from here on, and the count reports the unattributable ones
-- instead of guessing a branch for them.
ALTER TABLE customer_payments
    ADD COLUMN warehouse_id BIGINT;

ALTER TABLE customer_payments
    ADD CONSTRAINT fk_customer_payments_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id);

CREATE INDEX idx_customer_payments_warehouse ON customer_payments(warehouse_id);

ALTER TABLE supplier_payments
    ADD COLUMN warehouse_id BIGINT;

ALTER TABLE supplier_payments
    ADD CONSTRAINT fk_supplier_payments_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id);

CREATE INDEX idx_supplier_payments_warehouse ON supplier_payments(warehouse_id);

-- =========================
-- 3) THE CLOSE
-- =========================
CREATE TABLE cash_counts (
                             cash_count_id        BIGSERIAL PRIMARY KEY,
                             warehouse_id         BIGINT NOT NULL,
                             created_by_user_id   BIGINT NOT NULL,

                             -- The moment counted, and the anchor the next close reads.
                             closed_at            TIMESTAMP NOT NULL,

                             -- Where this close started summing. NULL means "from the first
                             -- record there is", which is what the very first close of a
                             -- branch covers. Stored rather than re-derived from the previous
                             -- close so the record explains itself years later, whatever the
                             -- anchoring logic looks like by then.
                             period_from          TIMESTAMP,

                             -- Cash put in the drawer to make change at the start of the
                             -- shift. Part of the cash line's expected amount, and kept
                             -- separately because it is the one figure the cashier typed
                             -- rather than the system deriving it. Zero is a valid answer.
                             opening_float        NUMERIC(19,4) NOT NULL,

                             -- Money that moved in the period but could not be attributed to
                             -- any payment method: returns and expenses recorded before the
                             -- method was asked for. NOT folded into any line — nobody knows
                             -- which method they belong to — but recorded here so a close
                             -- whose numbers looked off still says why.
                             unattributed_amount  NUMERIC(19,4) NOT NULL DEFAULT 0,
                             unattributed_count   INTEGER NOT NULL DEFAULT 0,

                             note                 TEXT,
                             created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_cash_counts_warehouse
                                 FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),

                             CONSTRAINT fk_cash_counts_user
                                 FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),

                             CONSTRAINT ck_cash_counts_opening_float_not_negative
                                 CHECK (opening_float >= 0),

                             CONSTRAINT ck_cash_counts_period_order
                                 CHECK (period_from IS NULL OR period_from <= closed_at)
);

-- The anchor lookup: newest close for a branch. Also the listing's sort order.
CREATE INDEX idx_cash_counts_warehouse_closed_at ON cash_counts(warehouse_id, closed_at DESC);

-- =========================
-- 4) THE COUNT, PER METHOD
-- =========================
-- One row per payment method that had movement or was counted. A child table because the
-- methods are catalog data: they get added and retired, so they cannot be columns.
CREATE TABLE cash_count_lines (
                                  cash_count_line_id  BIGSERIAL PRIMARY KEY,
                                  cash_count_id       BIGINT NOT NULL,

                                  -- Kept for traceability, but deliberately NOT what the
                                  -- close reads to display itself.
                                  payment_method_id   BIGINT NOT NULL,

                                  -- Frozen copies. A method renamed from "Cash" to "Efectivo",
                                  -- or un-flagged as cash, must not retroactively change what
                                  -- a close from last March says it counted.
                                  payment_method_name VARCHAR(80) NOT NULL,
                                  is_cash             BOOLEAN NOT NULL,

                                  expected_amount     NUMERIC(19,4) NOT NULL,
                                  counted_amount      NUMERIC(19,4) NOT NULL,

                                  -- counted - expected. Stored rather than derived on read:
                                  -- it is the number the owner acted on, and it is part of
                                  -- the photo.
                                  difference_amount   NUMERIC(19,4) NOT NULL,

                                  CONSTRAINT fk_cash_count_lines_cash_count
                                      FOREIGN KEY (cash_count_id) REFERENCES cash_counts(cash_count_id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_cash_count_lines_payment_method
                                      FOREIGN KEY (payment_method_id) REFERENCES payment_methods(payment_method_id),

                                  CONSTRAINT ux_cash_count_lines_method
                                      UNIQUE (cash_count_id, payment_method_id)
);

CREATE INDEX idx_cash_count_lines_cash_count ON cash_count_lines(cash_count_id);
