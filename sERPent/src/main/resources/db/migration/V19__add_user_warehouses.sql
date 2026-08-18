-- V19__add_user_warehouses.sql
-- Which warehouses each user is allowed to operate in. This is the real server-side
-- control: until now every operation took its acting user from a request body field,
-- so there was nothing to check a warehouse against.
--
-- Plain join table (no surrogate key): the pair IS the row, and the composite primary
-- key doubles as the uniqueness guarantee.
--
-- ON DELETE CASCADE on the user side only: removing a user should drop their
-- assignments, but a warehouse that someone is still assigned to must not disappear
-- silently — warehouses are deactivated, never deleted (see WarehouseService).
--
-- The backfill assigns every existing ACTIVE warehouse to every existing user, preserving
-- today's behaviour where anyone could operate anywhere they actually could before (an
-- inactive warehouse was never operable in the first place, so it is not backfilled).
-- On an installation whose warehouses table is still empty, or has only inactive ones,
-- this inserts zero rows, which is correct: no data is invented, and the "at least one
-- warehouse" rule is a write-time validation rather than a retroactive invariant. Such
-- users get their warehouses assigned from the UI.
--
-- NOTE: this file must stay identical in effect to
-- db/migration-h2/V9__add_user_warehouses.sql

CREATE TABLE user_warehouses (
                                 user_id      BIGINT NOT NULL,
                                 warehouse_id BIGINT NOT NULL,

                                 CONSTRAINT pk_user_warehouses
                                     PRIMARY KEY (user_id, warehouse_id),

                                 CONSTRAINT fk_user_warehouses_user
                                     FOREIGN KEY (user_id) REFERENCES users(user_id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT fk_user_warehouses_warehouse
                                     FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id)
);

CREATE INDEX idx_user_warehouses_warehouse ON user_warehouses(warehouse_id);

INSERT INTO user_warehouses (user_id, warehouse_id)
SELECT u.user_id, w.warehouse_id
FROM users u
         CROSS JOIN warehouses w
WHERE w.active = TRUE;
