# El respaldo de sERPent: qué hace solo y qué hay que hacer a mano

**Desde la fase 6, el respaldo lo arma el instalador.** Este documento dice qué quedó instalado,
qué tiene que hacer la persona que atiende la tienda, y qué tiene que hacer quien la visita.

Reemplaza al runbook anterior, que hablaba de `D:\Backups\sERPent`, de OneDrive y de programar la
tarea a mano. **Nada de eso aplica**: la PC de la tienda tiene una sola unidad y no tiene internet.

---

## Lo que quedó instalado, sin que nadie haga nada

| Qué | Dónde |
|---|---|
| Los respaldos | `C:\ProgramData\sERPent\respaldos` |
| El registro de lo que pasó | `estado.json` y `historial.jsonl`, en esa misma carpeta |
| El script que respalda | `C:\Program Files\sERPent\ops\backup\backup-serpent-db.ps1` |
| La tarea de red | "sERPent - Respaldo diario", **como SYSTEM**, todos los días a las 14:00 |

**Cuándo se respalda:**
1. **Al cerrar caja**, que es lo que marca el fin del día. Lo dispara el propio sERPent, en segundo
   plano: el cierre de caja no espera.
2. **Cuando alguien aprieta "respaldar ahora"** en la app.
3. **A las 14:00, solo si ese día todavía no hubo respaldo.** Es la red para el día que no cierran
   caja. Si la PC estaba apagada a esa hora, la tarea se dispara sola pocos minutos después de
   prenderla (medido: 3,4 minutos).

**El primer respaldo de una instalación nueva no sale, y está bien.** El instalador registra la
tarea y la corre una vez para probar que ejecuta de verdad, pero en ese momento la base está creada
y **vacía**: las tablas las crea sERPent la primera vez que arranca. La tarea contesta 12 ("no había
nada que respaldar") y lo deja anotado. El primer respaldo con datos sale solo, al primer cierre de
caja, o a las 14:00 del día siguiente.

**Cuánto se guarda:** todos los respaldos de los últimos 90 días; después, uno por semana hasta el
año; después, uno por mes, para siempre. Lo viejo se borra **solo después** de que el respaldo nuevo
salió bien y pasó su verificación.

**Cuánto ocupa:** un respaldo pesa unos 4,5 MB con un año de operación y crece ~4,2 MB por año. Todo
el esquema de retención ocupa 1,7 GB a los cinco años.

---

## LA COPIA AL PENDRIVE. Es un paso obligatorio, no un recordatorio

> **Por qué:** hasta que exista la pantalla de exportación, **un disco muerto se lleva la base y los
> respaldos juntos**. Los respaldos viven en el mismo disco que la base: sirven para volver atrás de
> un error, no para sobrevivir a un disco roto. El pendrive es la única copia que está afuera.

**Cuándo:** en cada visita a la tienda, y **como mínimo cada 15 días**.

**Cómo:**

1. Conectar un pendrive.
2. Copiar **la carpeta de respaldos entera** con el Explorador de Windows:
   de `C:\ProgramData\sERPent\respaldos` a una carpeta `sERPent-respaldos` en el pendrive.
3. **Verificar que llegaron los archivos** y anotar la fecha. Eso lo hace este comando, que además
   compara uno por uno por SHA-256 y **no anota nada si algo no coincide**:

   ```
   powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Program Files\sERPent\ops\backup\marcar-exportacion.ps1" -Unidad F
   ```

   (cambiar `F` por la letra que le tocó al pendrive)

4. **Expulsar el pendrive desde Windows** antes de sacarlo. Windows escribe en caché: sacarlo de un
   tirón puede dejar archivos a medias aunque la copia "haya terminado". Medido: sacándolo a mitad
   de una copia quedaron 11 archivos completos y el que estaba en curso se perdió sin aviso.

**Qué contesta el comando:**

| Sale con | Qué pasó |
|---|---|
| 0 | Todo lo que hay en la PC está en el pendrive, verificado, y quedó anotada la exportación |
| 30 | La unidad no existe o no está lista. ¿Está conectado el pendrive? |
| 31 | Esa unidad no es extraíble. Si es un disco externo, agregar `-AceptarUnidadNoExtraible` |
| 32 | **Faltan archivos o alguno no coincide.** No anotó nada: copiar de nuevo y repetir |
| 33 | Los archivos están bien, pero no pudo anotar la exportación |

**Si el pendrive está en FAT32**, no acepta archivos de más de 4 GB. Hoy un respaldo pesa 4,5 MB, así
que no molesta; el día que moleste, el comando lo dice antes de empezar y hay que formatearlo en NTFS
o exFAT.

---

## Qué mira el que atiende la tienda

La app avisa sola:
- **a los 15 días sin exportar**, y después todos los días, hasta que alguien exporte;
- **cuando el disco se está llenando**;
- **si un día no hubo respaldo**.

Nada de eso reemplaza mirar el estado de vez en cuando.

---

## Cómo saber si está todo bien, en 30 segundos

```
powershell -NoProfile -Command "Get-Content 'C:\ProgramData\sERPent\respaldos\estado.json' -Encoding UTF8"
```

Qué mirar:

| Campo | Qué quiere decir |
|---|---|
| `ultimoRespaldoOk` | Cuándo salió bien el último respaldo. Si es de hace más de un día, algo pasa |
| `ultimoIntentoResultado` | `ok`, `fallo` o `nada-que-respaldar`. Si dice `fallo`, `ultimoIntentoDetalle` dice por qué |
| `ultimaExportacion` | La última copia al pendrive **verificada**. Si está vacío, nunca se exportó |
| `ultimaPruebaDeRestauracionOk` | La prueba profunda mensual: lee y descomprime el respaldo entero |
| `espacioLibreBytes` | Lo que queda en el disco |
| `ultimoRespaldoPoda` | `ok`, o `con problemas` si no pudo borrar respaldos viejos (no es grave, pero conviene mirar) |
| `ultimoIntentoObjetosEnBase` | Cuántas tablas y demás objetos tenía la base en el último intento. `0` recién instalado; `no se pudo preguntar` si la base no contestó |

**Cómo leer los nombres**, que no son decorativos: todo lo que empieza con `ultimoIntento` es de la
**última corrida**, salga como salga, y se reescribe entera cada vez. Lo que empieza con
`ultimoRespaldo`, `ultimaPrueba` o `ultimaExportacion` es de **la última vez que salió bien** y
sobrevive a propósito a los intentos que fallan, que es justo cuando más se lo mira.

Y el detalle de cada intento, uno por línea:

```
powershell -NoProfile -Command "Get-Content 'C:\ProgramData\sERPent\respaldos\historial.jsonl' -Encoding UTF8 -Tail 5"
```

---

## Si algo falla

**El respaldo falla todos los días.** Correrlo a mano y leer lo que dice:

```
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Program Files\sERPent\ops\backup\backup-serpent-db.ps1" -Origen manual
```

| Sale con | Qué pasó |
|---|---|
| 0 | Respaldo hecho y verificado |
| 11 | No hacía falta: ya había uno de hoy (solo con `-SoloSiFaltaHoy`) |
| 12 | No había nada que respaldar: la base todavía no tiene ninguna tabla. Es lo normal recién instalado, antes de que sERPent arranque por primera vez |
| 20 | Ya hay otro respaldo corriendo |
| 21 | No se pudo leer `serpent.properties` |
| 22 | Falló `pg_dump`. El motivo va en el historial |
| 23 | El respaldo salió pero **no pasó la verificación**. No se borró nada |
| 24 | El respaldo salió pero no se pudo escribir el registro |

**La tarea de red no corre.** Ver cuándo corrió por última vez y con qué resultado:

```
powershell -NoProfile -Command "Get-ScheduledTaskInfo -TaskName 'sERPent - Respaldo diario' | Format-List LastRunTime, LastTaskResult, NextRunTime"
```

`LastTaskResult` 0 es "respaldó", 11 es "no hacía falta" y 12 es "la base todavía no tiene tablas".
La tarea corre **como SYSTEM**: no depende
de que nadie inicie sesión ni de permisos de una cuenta.

**Hay que restaurar.** Está en `RESTORE-RUNBOOK.md`. Dos cosas que no se pueden olvidar:
- los respaldos están comprimidos con **zstd**, así que se restauran **con los binarios de sERPent**
  (`C:\Program Files\sERPent-postgresql\18\bin`), no con cualquier PostgreSQL;
- antes de restaurar sobre la base viva, **hacer un respaldo a mano** de lo que hay.
