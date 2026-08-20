-- V23__add_expense_warehouse.sql
-- Optional branch attribution for expenses.
--
-- NULLABLE ON PURPOSE, and a NULL is not a missing value. Some expenses belong to one
-- location (rent, electricity, a service call) and some belong to the company (the
-- accountant, insurance). Forcing a branch on the second kind would invent a fact nobody
-- knows. An expense with no warehouse is a GENERAL expense, which is a legitimate state.
--
-- No backfill. Every existing row becomes general, which is what those rows actually are:
-- nobody chose a branch when they were loaded, so assigning one now would be guessing.
--
-- Note there is no NOT NULL variant to grow into later: as long as company-wide expenses
-- exist, this column has to stay nullable.
--
-- NOTE: this file must stay identical in effect to
-- db/migration-h2/V13__add_expense_warehouse.sql

ALTER TABLE expenses
    ADD COLUMN warehouse_id BIGINT;

ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id);

CREATE INDEX idx_expenses_warehouse ON expenses(warehouse_id);
