-- V7__seed_dev_test_user.sql
--
-- Login fijo para desarrollo local, para no depender del admin protegido (user_id 1).
--
-- Vive en db/seed-dev y no en db/migration-h2 porque es dato de demostración: el perfil de
-- test no carga esta carpeta. Mientras estaba entre las migraciones, esta fila chocaba contra
-- los tests que crean sus propios usuarios —clave primaria duplicada sobre 'claudecode'— y
-- era una de las razones por las que la suite no podía correr las migraciones de verdad.
--
-- Nunca agregar esto a db/migration (Postgres/producción).

INSERT INTO users (user_id, name, last_name, username, password_hash, email, active, created_at)
VALUES
    (2, 'Claude Code (pruebas)', NULL, 'claudecode', '$2a$10$QqmbSDhsMOUaHAZsDZz9Yes8sA1uMFU2bAqzIkrqhx1JzeX0C/d0W', 'claudecode@serpent.local', TRUE, CURRENT_TIMESTAMP);
