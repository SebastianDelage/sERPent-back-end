-- V7__seed_dev_test_user.sql
-- H2-only: fixed test login for local development, so devs don't have to depend on the
-- protected admin (user_id 1). Never add this to db/migration (Postgres/production).

INSERT INTO users (user_id, name, last_name, username, password_hash, email, active, created_at)
VALUES
    (2, 'Claude Code (pruebas)', NULL, 'claudecode', '$2a$10$QqmbSDhsMOUaHAZsDZz9Yes8sA1uMFU2bAqzIkrqhx1JzeX0C/d0W', 'claudecode@serpent.local', TRUE, CURRENT_TIMESTAMP);
