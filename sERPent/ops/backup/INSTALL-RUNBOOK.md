# Instalación del respaldo en la PC del local

Para Joaquin. Seguir en orden. Cada paso dice cómo confirmar que salió bien
antes de pasar al siguiente.

---

## Antes de empezar: qué NO puede quedar en C:

Esta PC tiene poco espacio libre en `C:` (13,6 GB al momento de escribir
esto). Nada de lo que sigue puede terminar ahí:

| Cosa | Dónde tiene que quedar |
|---|---|
| Respaldos locales diarios | `D:\Backups\sERPent\Diario` |
| Carpeta que sube a la nube | `D:\OneDrive\...` (OneDrive movido a D:, paso 2) |
| Log y estado del respaldo | `D:\Backups\sERPent\` |
| Logs de la aplicación sERPent | `D:` — fijar la variable `SERPENT_LOG_DIR` |
| Base de datos de Postgres | Ya vive en `C:\Program Files\PostgreSQL\17\data` de fábrica. **No se mueve como parte de esto** — moverla es una operación aparte, más delicada, fuera de este trabajo. Si `C:` se queda sin espacio en el futuro, es la primera candidata a revisar. |

**Cuánto espacio hace falta en D: para un año**, con una estimación
conservadora (la base hoy pesa 10 MB recién armada; esto asume que crece a
lo largo del año — si crece menos, todo lo de abajo es un techo, no un
piso):

- Asumiendo que la base llega a ~500 MB hacia fin de año (estimación, no un
  dato medido — conviene revisar esto a los 2-3 meses de uso real y ajustar
  si hace falta) y que el dump comprimido pesa ~60% de eso en el peor caso:
  **~300 MB el archivo más pesado del año**.
- Local (14 diarios + 12 anclas mensuales = 26 archivos, la mayoría mucho
  más chicos que el peor caso): **techo de ~8 GB**.
- Nube en espera (90 días parejos): **techo de ~27 GB**.
- Logs de la aplicación: acotados por diseño a 500 MB (rotación configurada
  en `application-prod.yml`).
- **Total: bajo 40 GB en el peor caso**, para un disco que en la PC de
  desarrollo tiene 80 GB libres. Confirmar el espacio libre real en `D:` de
  la PC del local antes de instalar (`Get-Volume`), y si es sensiblemente
  menor a 40 GB, avisar antes de seguir.

---

## Paso 1 — Carpetas y el script

Como administrador, en PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path "D:\Backups\sERPent\Diario"
New-Item -ItemType Directory -Force -Path "D:\Backups\sERPent\Scripts"
```

Copiar `backup-serpent-db.ps1` y `setup-scheduled-task.ps1` (están en
`ops/backup/` del repositorio) a `D:\Backups\sERPent\Scripts\`.

Abrir `backup-serpent-db.ps1` con el Bloc de notas y revisar, cerca del
principio, que estas líneas tengan el valor correcto para esta PC:

```powershell
$LocalBackupDir = "D:\Backups\sERPent\Diario"
$CloudStagingDir = "D:\OneDrive\sERPent-Backups"   # se ajusta en el paso 2
```

## Paso 2 — OneDrive, con la cuenta de Joaquin

**Importante: la cuenta que se loguea acá tiene que ser de Joaquin, personal,
no una cuenta del negocio.** Es la que va a tener acceso a los respaldos
desde cualquier otro lado si hace falta.

1. Instalar OneDrive si no está (`https://www.microsoft.com/microsoft-365/onedrive/download`).
2. Al configurarlo, loguear con la cuenta de Joaquin.
3. **Mover la carpeta de OneDrive a D:**, porque por defecto se instala
   dentro de `C:\Users\...`, y ya vimos que ahí sobra poco lugar:
   - Clic derecho en el ícono de OneDrive (bandeja del sistema, abajo a la
     derecha) → Configuración → Cuenta → **Cambiar ubicación de la carpeta**.
   - Elegir `D:\OneDrive`.
4. Dentro de esa carpeta, crear `D:\OneDrive\sERPent-Backups`.
5. Confirmar que `$CloudStagingDir` en `backup-serpent-db.ps1` apunta
   exactamente ahí (ya debería, del paso 1).

**Cómo confirmar que OneDrive sincroniza de verdad:** poner cualquier
archivo de prueba en `D:\OneDrive\sERPent-Backups`, y ver que el ícono de
OneDrive en la bandeja pase de "sincronizando" (flechas girando) a "al día"
(nube con tilde). Si en un rato no cambia y hay internet, algo está mal con
el login de OneDrive — resolverlo antes de seguir.

## Paso 3 — Credenciales de Postgres, sin contraseña en ningún archivo

Como el usuario de Windows con el que va a correr el respaldo (ver paso 4),
crear el archivo (probado que funciona sin pedir contraseña en ningún otro
lado):

```powershell
New-Item -ItemType Directory -Force -Path "$env:APPDATA\postgresql"
"localhost:5432:*:postgres:LA_CONTRASEÑA_REAL_DE_POSTGRES" | Out-File -FilePath "$env:APPDATA\postgresql\pgpass.conf" -Encoding ascii -NoNewline
```

(Reemplazar `LA_CONTRASEÑA_REAL_DE_POSTGRES` por la que corresponda a esta
instalación — no necesariamente `1234`, que era solo la de desarrollo.)

## Paso 4 — Programar la tarea

Como **administrador**, en PowerShell:

```powershell
cd "D:\Backups\sERPent\Scripts"
.\setup-scheduled-task.ps1
```

Va a pedir la contraseña de Windows de la cuenta que corre el respaldo (la
de Joaquin, la que queda en esta PC). Esa contraseña la guarda Windows,
cifrada — no queda en ningún archivo de este proyecto.

**Verificación obligatoria del propio script**, apenas termina:

```powershell
Get-ScheduledTaskInfo -TaskName "sERPent - Respaldo diario"
```

`LastTaskResult` tiene que ser `0` y `LastRunTime` NO puede ser
`30/11/1999` (ese valor significa "nunca corrió"). Si se queda así, es el
problema documentado adentro de `setup-scheduled-task.ps1`
("Iniciar sesión como tarea por lotes" — la solución está ahí mismo).

## Paso 5 — Probarlo YA, sin esperar a la noche

```powershell
Start-ScheduledTask -TaskName "sERPent - Respaldo diario"
Start-Sleep -Seconds 15
Get-ScheduledTaskInfo -TaskName "sERPent - Respaldo diario"
Get-Content "D:\Backups\sERPent\ESTADO-ULTIMO-RESPALDO.txt"
Get-ChildItem "D:\Backups\sERPent\Diario"
```

Tiene que aparecer un archivo `serpent_db_....dump`, `ESTADO-ULTIMO-RESPALDO.txt`
tiene que decir `Resultado: OK`, y con un tamaño mayor a 0.

**Si se queda "corriendo" para siempre y nunca aparece el archivo:** no es
necesariamente el script — puede ser un problema de la cuenta/sesión con la
que corre la tarea en esta PC en particular. Revisar:
- Que `pgpass.conf` (paso 3) esté en el perfil de la MISMA cuenta que quedó
  configurada en el paso 4.
- Que esa cuenta pueda conectarse a Postgres a mano (`psql -U postgres -h
  localhost` desde una sesión de esa cuenta).
- El Visor de eventos de Windows, registro de Seguridad, buscando inicios de
  sesión (tipo 4, "por lotes") de esa cuenta alrededor de la hora del intento.

## Paso 6 — Confirmar al otro día (y todos los días después)

Sin abrir nada técnico: abrir `D:\Backups\sERPent\ESTADO-ULTIMO-RESPALDO.txt`
con el Bloc de notas. Tiene que decir la fecha de anoche y `Resultado: OK`.

Para la nube: mirar el ícono de OneDrive en la bandeja del sistema. Nube con
tilde = al día. Si el número de "Respaldos esperando subir a la nube" en ese
mismo archivo crece día tras día, confirma que hace rato no hay internet o
que OneDrive dejó de sincronizar.

---

## RESTAURACIÓN — leer esto con calma aunque el negocio esté parado

Dos escenarios. Elegir el que corresponda.

### A) El disco de la PC murió, hay que levantar todo de cero

Postgres nuevo, instalado de cero, base vacía.

**Paso 1.** Conseguir el respaldo más reciente que exista. Buscar primero en
`D:\Backups\sERPent\Diario\` (si ese disco sobrevivió). Si no, entrar a
OneDrive desde OTRA computadora o desde el celular, carpeta
`sERPent-Backups`, y bajar el archivo `.dump` más nuevo.

**Paso 2.** Abrir PowerShell como administrador. Crear la base:

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -c "CREATE DATABASE serpent_db;"
```

Va a pedir la contraseña de Postgres. Escribirla y Enter.

**Paso 3.** Restaurar (reemplazar la ruta del archivo por la real):

```powershell
& "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe" -U postgres -h localhost -d serpent_db --no-owner --no-privileges -v "RUTA\AL\serpent_db_2026-08-27_220000.dump"
```

Esto tarda. Va a mostrar muchas líneas "creando..." — es normal, no hay que
interrumpirlo.

**Paso 4.** Confirmar que funcionó (copiar y pegar tal cual, una por una):

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d serpent_db -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
```
Tiene que dar un número parecido a 29 (puede haber crecido con el tiempo,
pero no puede dar 0 ni un número chico).

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d serpent_db -c "SELECT COUNT(*) FROM products;"
```
Tiene que dar más de 0 si había productos cargados.

Si los dos números tienen sentido, la restauración salió bien. Seguir con la
instalación normal de la aplicación (backend + frontend), que ahora va a
encontrar la base con los datos.

### B) La base actual tiene datos rotos o mal cargados, hay que volver a un respaldo bueno

Acá la base YA EXISTE y hay que reemplazar su contenido, no crearla.

**Paso 1.** Cerrar la aplicación sERPent (que nadie esté usándola).

**Paso 2.** Restaurar con `--clean`, que borra cada cosa antes de recrearla:

```powershell
& "C:\Program Files\PostgreSQL\17\bin\pg_restore.exe" -U postgres -h localhost -d serpent_db --clean --if-exists --no-owner --no-privileges -v "RUTA\AL\serpent_db_....dump"
```

**Paso 3.** Confirmar con las mismas dos consultas del Escenario A, Paso 4.

**Paso 4.** Volver a abrir la aplicación.

---

## Este procedimiento fue probado de verdad

El mecanismo (`pg_dump` → `pg_restore`) se probó de punta a punta el
2026-08-26, contra Postgres real, sobre bases descartables (nunca contra
`serpent_db`): se armó una base de prueba con el esquema completo, se le
insertó una fila marcadora, se la respaldó, se restauró en una base vacía
nueva, y se confirmó que la fila marcadora y las 29 tablas estaban intactas.
El detalle línea por línea está en `RESTORE-RUNBOOK.md`, en esta misma
carpeta.

**Lo que no se pudo probar en el entorno de desarrollo**: la ejecución en
vivo de la tarea programada de Windows, tanto en la modalidad "sin sesión
iniciada" como, más en general, cualquier ejecución disparada por el propio
servicio de Task Scheduler. En el entorno donde se desarrolló esto, un
proceso lanzado por Task Scheduler queda colgado antes incluso de intentar
conectarse a Postgres — se confirmó que no es un problema del script (la
misma línea de comando, lanzada por fuera de Task Scheduler, funciona
perfecto) ni de `pg_dump` en sí. Todo indica que es una restricción del
entorno de desarrollo (una sandbox), no algo que vaya a pasar en una PC de
Windows normal — pero como no se pudo confirmar eso último de forma
concluyente, **el Paso 5 de este documento (probar la tarea apenas se
instala) no es opcional**: es la verificación que faltó hacer acá, y tiene
que hacerse en la PC real antes de dar esto por andando.
