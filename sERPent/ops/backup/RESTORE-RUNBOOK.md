# Cómo restaurar un respaldo de sERPent

Este procedimiento fue **probado de verdad** el 2026-08-26, sobre bases de prueba
en esta misma PC (Postgres 17 real, no una simulación). Detalle de esa prueba al
final de este documento.

## Escenario A — el disco murió, Postgres se reinstaló de cero

Este es el caso que realmente importa ("si ese disco se rompe se pierde el
negocio entero"), y es exactamente el que se probó.

1. Instalar PostgreSQL 17 en la PC nueva/reparada.

2. Conseguir el archivo de respaldo más reciente. Va a estar en uno de estos
   dos lugares (usar el que esté disponible):
   - `D:\Backups\sERPent\Diario\serpent_db_AAAA-MM-DD_HHMMSS.dump` (si el disco D
     sobrevivió)
   - La carpeta de OneDrive/Drive sincronizada en otra PC o en la web del
     servicio, carpeta `sERPent-Backups`

3. Crear la base vacía:
   ```
   "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -c "CREATE DATABASE serpent_db;"
   ```

4. Restaurar:
   ```
   "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe" -U postgres -h localhost -d serpent_db --no-owner --no-privileges -v "RUTA\AL\serpent_db_....dump"
   ```
   `--no-owner --no-privileges`: el dump puede traer el rol `postgres` de la PC
   vieja; con estas opciones todo se crea con el rol que uno usa para restaurar,
   sin pelearse con roles que no existen en la instalación nueva.

5. Confirmar que los datos están (ver la sección "Cómo confirmar" abajo).

6. Apuntar `application-prod.yml` / las variables de entorno a esta base
   (debería ser lo mismo de siempre si el nombre de la base no cambió).

## Escenario B — la base actual tiene datos malos, hay que volver a un respaldo bueno

Distinto del A porque acá la base YA EXISTE y tiene contenido que hay que
reemplazar, no crear desde cero.

1. Cortar cualquier cosa que esté usando la base (parar el backend).

2. Restaurar con `--clean --if-exists`, que borra cada objeto antes de
   recrearlo:
   ```
   "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe" -U postgres -h localhost -d serpent_db --clean --if-exists --no-owner --no-privileges -v "RUTA\AL\serpent_db_....dump"
   ```

3. Confirmar (sección de abajo) y volver a levantar el backend.

## Cómo confirmar que la restauración salió bien

No alcanza con que `pg_restore` no tire error. Correr esto contra la base
restaurada:

```sql
-- Tiene que dar 29 (o el numero de tablas del momento del respaldo)
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';

-- Tiene que dar 26 (o las migraciones que existan a esa fecha), todas success=t
SELECT COUNT(*) FROM flyway_schema_history WHERE success = true;

-- Un par de tablas con datos reales, para no quedarse solo con el esquema vacío
SELECT COUNT(*) FROM products;
SELECT COUNT(*) FROM transactions;
```

Si estos números coinciden con lo esperado (o con lo que decía el archivo de
log del día de ese respaldo), la restauración es buena.

---

## La prueba real que se hizo (2026-08-26)

No se tocó `serpent_db`. Se armó una base de prueba aparte para probar el
mecanismo de punta a punta:

1. `CREATE DATABASE serpent_backup_test_source` + Flyway completo (26
   migraciones) → 29 tablas.
2. Se insertó una fila marcadora única en `warehouses`
   (`MARCADOR-RESPALDO-20260826-182025`).
3. `pg_dump -Fc` de esa base, usando `pgpass.conf` — **sin ninguna contraseña
   en la consola ni en ningún script**. 124 KB, código de salida 0.
4. `CREATE DATABASE serpent_backup_test_target` (vacía).
5. `pg_restore --no-owner --no-privileges` de ese dump contra la base vacía.
   Código de salida 0, se vieron en la consola las 29 tablas y todas las FK
   creándose sin error.
6. Verificación:
   - 29 tablas en la base restaurada (igual que el origen).
   - La fila marcadora estaba, exacta: mismo `warehouse_id`, mismo nombre,
     `active = t`.
   - 26 filas en `flyway_schema_history`, todas `success = true`.
7. Limpieza: se borraron las dos bases de prueba, el archivo del dump, y el
   `pgpass.conf` de prueba. `serpent_db` no se tocó en ningún momento —
   se verificó antes y después que seguía con sus 26 migraciones intactas.

Esto prueba el mecanismo (`pg_dump -Fc` → `pg_restore`) de punta a punta, con
Postgres real. No prueba específicamente el escenario B (`--clean`), que usa
el mismo mecanismo con una opción más: si se quiere, se puede repetir la
prueba con ese flag antes de dar el visto bueno final.
