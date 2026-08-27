# Plan de respaldo — sERPent

Estado: **aprobado e implementado**, con tres ajustes sobre esta propuesta
original (ver `INSTALL-RUNBOOK.md` para la instalación paso a paso y el
detalle de qué se probó de verdad, incluida la limitación encontrada al
probar la tarea programada en el entorno de desarrollo):

1. La tarea programada corre EXISTA O NO SESIÓN INICIADA (esta propuesta
   había dejado la decisión abierta; quedó resuelta a favor de esa opción).
2. `$CloudStagingDir` es una ruta a ajustar explícitamente, no algo que se
   resuelve solo — OneDrive hay que instalarlo en la PC del local con una
   cuenta de Joaquin, no la de esta PC de desarrollo.
3. Se confirmó que nada queda en `C:` (ni la carpeta de OneDrive, que por
   defecto sí caía ahí) y se estimó el espacio necesario para un año.

Este documento queda como registro de la propuesta original y su
razonamiento. Para instalar, usar `INSTALL-RUNBOOK.md`.

## Datos de esta PC, relevados antes de proponer nada

- Postgres 17, datos en `C:\Program Files\PostgreSQL\17\data` → **viven en C:**.
- `serpent_db` pesa **10 MB** hoy (dataset joven; va a crecer).
- Disco `C:`: 110 GB totales, **13.6 GB libres** — ajustado, no usar para respaldos.
- Disco `D:`: 931 GB totales, **80 GB libres** — disco físicamente distinto al
  de la base, con margen de sobra. Lo propongo como destino local.
- OneDrive ya está instalado y con sesión iniciada en esta PC
  (`$env:OneDrive = C:\Users\ignac\OneDrive`) — cero instalación adicional.

---

## 0. Qué servicio de nube, y el problema de la acumulación

**Propongo OneDrive.** Con un matiz importante primero:

Los backups son dumps comprimidos (`-Fc`) que cambian de contenido casi entero
cada día, incluso si el negocio subyacente cambió poco: la compresión reordena
los bytes, así que la ventaja de "solo subo lo que cambió" que ofrecen Dropbox
u otros clientes con sync a nivel de bloque **no aplica acá** — cada backup se
sube entero, todos los días, sin importar el cliente. Por eso elijo por
simplicidad y no por eficiencia de subida.

Con eso descartado como criterio, OneDrive gana por lo más simple: ya está
instalado, ya tiene sesión iniciada, no hay que crear una cuenta nueva ni
enseñarle a nadie un programa nuevo. 5 GB gratis. Con el tamaño de dump de hoy
(cientos de KB a pocos MB) sobra por años. Alternativa si en algún momento se
necesita más lugar: Google Drive (15 GB gratis), mismo mecanismo, un poco más
de instalación.

**El problema real, que sí hay que resolver:** con "internet ocasional, cuando
alguien comparte datos desde el celular", pueden pasar semanas sin que la
carpeta sincronizada suba nada. Mientras tanto, los dumps se siguen
escribiendo ahí — eso es correcto y deseado, es justo el mecanismo pedido
("acumular sin quejarse"). El riesgo NO es que se acumulen: es que un script
de rotación mal diseñado **borre localmente un backup que todavía no llegó a
subir**, y en ese momento esa copia de nube deja de existir para siempre, sin
que nadie lo note hasta que hace falta.

Por eso las dos carpetas (punto 3) tienen retención distinta: la de la nube es
mucho más generosa (90 días parejos) precisamente para darle margen a un mes
o dos sin conexión antes de que el script borre algo que OneDrive nunca llegó
a ver. Con el tamaño de dump actual, 90 días de acumulación sin subir pesan
unos pocos MB — no hay problema de espacio hoy. Si la base crece mucho (dejo
dicho el umbral: si el dump diario supera ~50 MB, 90 días ya son ~4.5 GB, hay
que revisar la ventana o el plan gratuito de OneDrive).

---

## 1. Script de respaldo

`backup-serpent-db.ps1` (adjunto). PowerShell porque Task Scheduler lo integra
nativamente sin depender de nada más instalado.

**Formato: `-Fc` (custom de Postgres), no SQL plano.** Comprimido, y sobre
todo: permite `pg_restore -j N` en paralelo (restaura más rápido cuanto más
grande sea la base) y restauración selectiva de una tabla puntual si algún día
hace falta sin tocar el resto. El SQL plano no ofrece ninguna de las dos.

**Credenciales: `pgpass.conf`, no en el script.** Verificado que `pg_dump`
funciona sin `PGPASSWORD` ni contraseña en ningún archivo del proyecto —
ver la sección de verificación.

Qué hace cada corrida:
1. `pg_dump -Fc` a la carpeta local.
2. Si el archivo pesa menos de 10 KB, lo trata como fallo (un dump de este
   esquema nunca debería pesar tan poco — señal de corte o error silencioso).
3. Copia el mismo archivo a la carpeta sincronizada por OneDrive.
4. Poda ambas carpetas (ver punto 3).
5. Escribe una fila en un CSV de log y actualiza un archivo de estado legible
   (puntos 5 y 6).

## 2. Programador de tareas de Windows

`setup-scheduled-task.ps1` (adjunto) — se corre **una sola vez**, a mano, como
administrador, para instalar la tarea. No es el script diario.

- Disparo diario a las 22:00 (ajustable).
- `-StartWhenAvailable`: si la PC estaba apagada a esa hora, corre apenas se
  prende. Esto es lo que responde directamente al pedido.
- `IgnoreNew` en instancias múltiples (no se superpone si la corrida anterior
  sigue viva), 3 reintentos con 15 minutos de por medio si falla.

**Decisión que dejo para vos, porque depende del uso real de la PC:** la
cuenta bajo la que corre. La opción simple (default en el script) es "solo si
el usuario tiene sesión iniciada" — no pide contraseña, pero si la PC alguna
noche queda en la pantalla de login, esa noche no respalda. La opción robusta
corre exista o no sesión iniciada, pero exige guardar la contraseña de
Windows del usuario en la tarea programada. Dejé las dos escritas en el
script (una activa, una comentada) — decime cuál corresponde a cómo se deja
esa PC de noche.

## 3. Rotación

**Local (`D:\Backups\sERPent\Diario`):** 14 diarios + el primero de cada mes
durante 12 meses. Motivo del corte en 14: "si algo se rompió hace una semana
y nadie lo notó" tiene el doble de margen antes de perder la copia buena. El
ancla mensual cubre el caso de que haga falta algo de hace 6 meses por una
razón que no sea un desastre (una auditoría, un reclamo viejo).

**Nube (`OneDrive\sERPent-Backups`):** 90 días parejos, sin poda mensual.
Deliberadamente más floja que la local — ver punto 0 sobre por qué achicar
esto es el error real a evitar acá.

Los números de arriba están dimensionados para el tamaño de dump de **hoy**
(decenas a cientos de KB). Si la base crece un orden de magnitud, conviene
revisar la ventana de 90 días contra el espacio libre real antes de que se
vuelva un problema.

## 4. Restauración — PROBADA, no solo descripta

`RESTORE-RUNBOOK.md` (adjunto) tiene el procedimiento completo para los dos
escenarios (disco nuevo desde cero / reemplazar una base con datos malos), y
el detalle exacto de la prueba real que se hizo hoy contra Postgres real:

- Base de prueba con las 26 migraciones reales aplicadas (29 tablas).
- Fila marcadora única insertada.
- `pg_dump -Fc` con `pgpass.conf`, sin contraseña en ningún lado.
- Base de prueba nueva, vacía.
- `pg_restore` contra esa base vacía.
- Confirmado: mismas 29 tablas, la fila marcadora exacta presente, 26
  migraciones registradas como exitosas.
- Todo lo de prueba borrado al final. `serpent_db` no se tocó en ningún
  momento — confirmado antes y después.

**Además probé el script de respaldo completo**, no solo los comandos sueltos
de `pg_dump`/`pg_restore`: lo corrí contra una base y carpetas de prueba tal
como correría en producción, encontré y arreglé un bug real (sintaxis
inválida en la escritura del CSV que solo aparece la primera vez que se
escribe el log), y después probé específicamente que la poda de retención
borra lo que tiene que borrar — con archivos de fecha manipulada a propósito
(uno de 20 días sin ancla: se borró; el ancla del día 1 del mes: sobrevivió;
uno de 100 días en la carpeta de nube: se borró) — no me alcanzó con que el
script "no tirara error", confirmé que hace lo que dice que hace.

## 5. Registro de cada corrida

CSV en `D:\Backups\sERPent\backup-log.csv`, una fila por corrida: fecha,
resultado (OK/FALLO), archivo, tamaño en MB, duración, y el detalle del error
si falló. Se abre con Excel sin ninguna preparación.

## 6. Cómo lo revisa Joaquin sin abrir nada técnico

Un archivo de texto, `D:\Backups\sERPent\ESTADO-ULTIMO-RESPALDO.txt`,
reescrito en cada corrida, con 4-5 líneas en español:

```
=== ESTADO DEL RESPALDO DE sERPent ===

Ultimo intento: 26/08/2026 22:00
Resultado:      OK
Tamano:         2.3 MB

Respaldos esperando subir a la nube: 3
(si este numero crece dia tras dia, hace mucho que no hay internet o
 el cliente de sincronizacion dejo de funcionar - revisar el icono
 de OneDrive/Drive en la barra de tareas)
```

Con eso alcanza para ver de un vistazo si anoche corrió y si salió bien.

**Para la nube específicamente**, la señal más simple y ya construida por
Windows es el ícono de OneDrive en la bandeja del sistema: nube con tilde =
todo subido, flechas girando = subiendo, exclamación roja = error. No hace
falta ninguna herramienta nueva — ya está ahí. El contador de "esperando
subir" del archivo de estado es el complemento: si ese número crece día tras
día en vez de mantenerse bajo, confirma lo que el ícono ya insinúa.

---

## Verificación realizada — comandos exactos

Todo esto se corrió en esta PC, contra Postgres 17 real, sin tocar `serpent_db`:

```powershell
# Confirmar herramientas y tamaño real de la base
& "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe" --version
psql -U postgres -h localhost -d serpent_db -c "SELECT pg_size_pretty(pg_database_size('serpent_db'));"

# pgpass.conf, sin contraseña en ningún script
"localhost:5432:*:postgres:1234" | Out-File "$env:APPDATA\postgresql\pgpass.conf" -Encoding ascii -NoNewline

# Prueba de restauración de punta a punta (bases de prueba, no serpent_db)
psql -U postgres -h localhost -c "CREATE DATABASE serpent_backup_test_source;"
./mvnw.cmd flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/serpent_backup_test_source ...
psql ... -c "INSERT INTO warehouses (name, active) VALUES ('MARCADOR-RESPALDO-...', TRUE);"
pg_dump -U postgres -h localhost -d serpent_backup_test_source -Fc -f serpent_test_....dump
psql -U postgres -h localhost -c "CREATE DATABASE serpent_backup_test_target;"
pg_restore -U postgres -h localhost -d serpent_backup_test_target --no-owner --no-privileges -v serpent_test_....dump
psql ... -c "SELECT warehouse_id, name, active FROM warehouses WHERE name = 'MARCADOR-RESPALDO-...';"
# → fila encontrada, exacta

# Smoke test del script completo (no solo los comandos sueltos)
psql -U postgres -h localhost -c "CREATE DATABASE serpent_backup_smoketest;"
./mvnw.cmd flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/serpent_backup_smoketest ...
powershell -File backup-serpent-db.smoketest.ps1   # con paths y DB de prueba
# -> encontró un bug real (Export-Csv -Encoding UTF8:$bool), lo arreglé, reproduje

# Prueba de poda con archivos de fecha manipulada
(Get-Item old20.dump).LastWriteTime = (Get-Date).AddDays(-20)      # sin ancla
(Get-Item oldanchor.dump).LastWriteTime = <dia 1 de un mes reciente>  # con ancla
(Get-Item oldcloud.dump).LastWriteTime = (Get-Date).AddDays(-100)  # en la carpeta nube
powershell -File backup-serpent-db.smoketest.ps1
# -> old20 borrado, oldanchor sobrevive, oldcloud borrado. Todo lo reciente intacto.

# Limpieza
psql -U postgres -h localhost -c "DROP DATABASE serpent_backup_test_source;"
psql -U postgres -h localhost -c "DROP DATABASE serpent_backup_test_target;"
psql -U postgres -h localhost -c "DROP DATABASE serpent_backup_smoketest;"
Remove-Item "$env:APPDATA\postgresql\pgpass.conf"
# confirmado: serpent_db sigue con sus 26 migraciones, sin tocar
```

---

## Qué falta para pasar de "propuesta" a "instalado"

1. Confirmar la letra de disco real del destino local en la PC del local
   (asumí `D:`, ajustar en el script si es otra).
2. Decidir la cuenta de la tarea programada (punto 2, arriba).
3. Correr `setup-scheduled-task.ps1` una vez, en esa PC, como administrador.
4. Crear el `pgpass.conf` real en esa PC (una línea, mecanismo ya probado).
5. Opcional: repetir la prueba de restauración con `--clean` (escenario B del
   runbook), que usa el mismo mecanismo ya probado con una opción más.

Nada de esto lo hago sin que lo confirmes.
