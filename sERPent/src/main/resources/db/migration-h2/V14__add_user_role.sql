-- V14__add_user_role.sql
-- Roles: what a user may DO, as opposed to user_warehouses, which says WHERE they may do it.
-- The two are orthogonal and neither substitutes for the other.
--
-- EXISTING USERS BECOME ADMIN. Defaulting them to EMPLOYEE would lock every current account
-- out of the catalog, the user list and the reports the moment this migration runs, and the
-- owner's own account is among them — there would be nobody left able to hand out roles.
-- Starting permissive and narrowing by hand is recoverable; locking everyone out is not.
--
-- The DEFAULT stays ADMIN only for the length of this migration; the application always
-- writes the role explicitly, and new users get whatever the form says.
--
-- NOTE: this file must stay identical in effect to
-- db/migration/V24__add_user_role.sql

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'EMPLOYEE'));
