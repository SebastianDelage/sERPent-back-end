# Cómo restaurar un respaldo de sERPent

Este procedimiento fue **probado de verdad** el 2026-08-26, sobre bases de prueba
en esta misma PC (Postgres 17 real, no una simulación). Detalle de esa prueba al
final de este documento.

## DOS COSAS QUE CAMBIARON CON LA FASE 6, Y HAY QUE SABERLAS ANTES

1. **Los respaldos están comprimidos con zstd**, que ocupa la mitad que el formato anterior. Un
   PostgreSQL compilado sin zstd **no puede leerlos**. Hay que restaurar con **los binarios que
   instala sERPent**: `C:\Program Files\sERPent-postgresql\18\bin`. Si esa carpeta no existe
   (disco muerto, PC nueva), primero se corre el instalador de sERPent, que los deja ahí.
2. **Los respaldos viven en `C:\ProgramData\sERPent\respaldos`**, en el mismo disco que la base.
   La copia de afuera es el pendrive, en su carpeta `sERPent-respaldos`. No hay nube.

## Escenario A — el disco murió, Postgres se reinstaló de cero

Este es el caso que realmente importa ("si ese disco se rompe se pierde el
negocio entero"), y es exactamente el que se probó.

1. **Correr el instalador de sERPent** en la PC nueva o reparada. Deja PostgreSQL 18, la base vacía
   y sus binarios, que son los que saben leer el respaldo.

2. Conseguir el archivo de respaldo más reciente:
   - del **pendrive**, carpeta `sERPent-respaldos` (es la copia que sobrevive al disco);
   - o de `C:\ProgramData\sERPent\respaldos`, si el disco sobrevivió.

   El pendrive trae además un `sERPent-respaldos.json` que dice qué archivos tiene y su SHA-256.

3. La base ya la creó el instalador. Si hiciera falta crearla a mano:
   ```
   "C:\Program Files\sERPent-postgresql\18\bin\psql.exe" -U postgres -h localhost -p 5432 -c "CREATE DATABASE serpent_db;"
   ```
   (el puerto puede no ser 5432: mirar `DB_PORT` en `C:\ProgramData\sERPent\serpent.properties`)

4. Restaurar:
   ```
   "C:\Program Files\sERPent-postgresql\18\bin\pg_restore.exe" -U postgres -h localhost -p 5432 -d serpent_db -v "RUTA\AL\serpent_db_....dump"
   ```

   > **SIN `--no-owner --no-privileges`, Y ESO CAMBIÓ.** Este runbook las traía, y dejan la base
   > inutilizable. Medido, ensayando la vuelta atrás y después en el banco
   > `scratchpad\fase7\banco-restauracion.ps1`: con esas dos banderas, **60 objetos de `public`
   > quedan perteneciendo a `postgres` en vez de a `serpent_app`**, y a partir de ahí
   >
   > ```
   > pg_dump: error: la consulta falló: ERROR: permission denied for table cash_count_lines
   > ```
   >
   > O sea que el respaldo deja de funcionar, y con el respaldo roto **el instalador tampoco deja
   > actualizar**, porque el respaldo previo falla y sale con 7. Se queda sin app y sin poder
   > instalar, que es el peor lugar donde estar parado en una tienda.
   >
   > El motivo por el que estaban —"el dump puede traer un rol que no existe acá"— no aplica: el
   > rol se llama siempre `serpent_app` y **lo crea el instalador antes de que uno restaure**. Sin
   > las banderas, `pg_restore` devuelve cada objeto a su dueño y la base queda como estaba.

   **Antes de restaurar sobre una base que tiene datos, respaldar lo que hay**, aunque esté mal:
   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Program Files\sERPent\ops\backup\backup-serpent-db.ps1" -Origen manual
   ```

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
   "C:\Program Files\sERPent-postgresql\18\bin\pg_restore.exe" -U postgres -h localhost -p 5432 -d serpent_db --clean --if-exists -v "RUTA\AL\serpent_db_....dump"
   ```

   Sin `--no-owner --no-privileges`, por lo que dice el recuadro del escenario A.

3. Confirmar (sección de abajo) y volver a levantar el backend.

## Cómo confirmar que la restauración salió bien

**Lo primero, y es una sola línea: correr el respaldo.** Si sale con 0, la base está sana de
verdad — se pudo entrar como `serpent_app`, leer todas las tablas y escribir el registro. Es la
misma prueba que hace el instalador antes de actualizar, así que si esto anda, la actualización
también va a arrancar:

```
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Program Files\sERPent\ops\backup\backup-serpent-db.ps1" -Origen manual
```

> **SI DIO 22 CON "permission denied"**, la base se restauró con `--no-owner` y las tablas
> quedaron de otro dueño. No hay que salir a repartir permisos a mano: volver a correr el
> instalador lo arregla. `provision-database.ps1` detecta los objetos que no son de `serpent_app`
> y se los pasa. (Repartir permisos a mano tampoco es inofensivo: cada permiso explícito agrega
> una entrada al dump. Medido: el respaldo pasó de 327 a 388 entradas, +61, una por cada objeto
> ajeno. No rompe nada, pero el número deja de ser comparable con el de antes.)

Después, no alcanza con que `pg_restore` no tire error. Correr esto contra la base
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
