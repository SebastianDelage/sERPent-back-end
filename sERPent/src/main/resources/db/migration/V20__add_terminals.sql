-- V20__add_terminals.sql
-- A registered point of sale: a named machine bound to one warehouse, so an operation
-- sent from it records against that warehouse without the operator picking one.
--
-- IMPORTANT — a terminal is an operational convenience, NOT a security control. The
-- terminal id travels as an ordinary optional request field, so a client can name any
-- terminal it likes. What actually constrains the operation is the user_warehouses
-- assignment, which is checked against the terminal's warehouse just the same. Making
-- the terminal itself a control would require a per-terminal credential presented at
-- pairing time, which this table deliberately does not model.
--
-- `active` follows the same convention as the rest of the catalog (products, suppliers,
-- warehouses): rows are deactivated, never deleted, because they are referenced by
-- history. An inactive terminal is rejected at operation time, like an inactive
-- warehouse.
--
-- NOTE: this file must stay identical in effect to
-- db/migration-h2/V10__add_terminals.sql

CREATE TABLE terminals (
                           terminal_id  BIGSERIAL PRIMARY KEY,
                           name         VARCHAR(120) NOT NULL,
                           warehouse_id BIGINT       NOT NULL,
                           active       BOOLEAN      NOT NULL DEFAULT TRUE,
                           created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

                           CONSTRAINT ux_terminals_name UNIQUE (name),

                           CONSTRAINT fk_terminals_warehouse
                               FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id)
);

CREATE INDEX idx_terminals_warehouse ON terminals(warehouse_id);
