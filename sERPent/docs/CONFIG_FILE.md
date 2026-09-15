# El archivo de configuración de la máquina

**Dónde:** `C:\ProgramData\sERPent\serpent.properties`
**Quién lo escribe:** el instalador, con credenciales generadas al azar.
**Quién lo lee:** el backend (perfil `prod`) y, para poder avisar antes, el shell de Electron.

```properties
DB_USERNAME=serpent_app
DB_PASSWORD=<32 caracteres alfanuméricos al azar>
JWT_SECRET=<64 caracteres alfanuméricos al azar>
```

---

## Por qué existe

Hasta ahora esos tres valores salían de variables de entorno. Eso funciona y está bien defendido
—si falta uno, el arranque falla en vez de seguir con algo equivocado—, pero **las variables de
entorno son una convención de servidores, no de software de escritorio**: nadie va a abrir las
propiedades del sistema en el mostrador, y ninguno de los ERPs del mercado se las pide al que
instala.

El instalador genera las credenciales él mismo. Nadie las inventa, nadie las recuerda, no pueden
ser débiles, y no queda un paso del runbook que se pueda olvidar. Para eso el backend tiene que
poder leer un archivo.

## Dónde vive, y por qué no en Program Files

`C:\ProgramData\sERPent\` — la carpeta que Windows define para datos de aplicación **por
máquina** (no por usuario), que es exactamente lo que esto es.

No en `Program Files` porque esa carpeta es para el programa, y Windows la quiere de solo
lectura después de instalar. Poner ahí un archivo que puede tener que reescribirse —rotar la
clave de firma, cambiar la contraseña de la base— obliga a elevar permisos cada vez, y en las
versiones viejas de Windows disparaba la virtualización de archivos, que hace que cada usuario
termine viendo una copia distinta sin enterarse.

Medido en esta máquina (Windows 11 Pro, cuenta **sin** elevar):

- crear `C:\ProgramData\sERPent\` **no pide elevación** — la ACL heredada le da a `Usuarios`
  permiso de crear carpetas;
- y por lo mismo, un archivo creado ahí **nace legible por todos los usuarios de la máquina**
  (`BUILTIN\Usuarios:(I)(RX)` heredado). Por eso los permisos hay que apretarlos a mano — ver
  abajo, no es opcional.

## La precedencia: gana la variable de entorno

`application-prod.yml` importa el archivo con `spring.config.import`. En el orden de property
sources de Spring Boot, las variables de entorno están **por encima** de cualquier archivo de
configuración, importado o no.

Medido contra el backend real corriendo con PostgreSQL 18.6, poniendo un usuario distinto en cada
origen y mirando a quién nombra el error de autenticación:

| qué había | qué usó |
|---|---|
| solo el archivo | `usuario_del_archivo` |
| el archivo **y** la variable de entorno | **`usuario_del_entorno`** |
| ninguno de los dos | no arranca: `Could not resolve placeholder 'JWT_SECRET'` |

Es el orden que corresponde: el archivo es la configuración instalada, y la variable es la forma
de pisarla sin tocarla. Un desarrollador exporta las tres variables y trabaja sin instalar nada;
la máquina del local no tiene ninguna variable puesta y usa el archivo.

**Las claves del archivo se llaman igual que las variables** (`DB_USERNAME`, no
`spring.datasource.username`) a propósito: así los dos orígenes compiten por el mismo nombre y la
precedencia la resuelve Spring sola, sin una línea de código que la implemente ni que se pueda
equivocar.

## Los permisos

El archivo tiene la contraseña de la base y la clave con la que se firman las sesiones. Va en
claro, con permisos restringidos, y documentado como tal — ver "Por qué no está cifrado".

Lo que tiene que correr el instalador, ya elevado:

```powershell
icacls "C:\ProgramData\sERPent\serpent.properties" /inheritance:r `
  /grant:r "*S-1-5-18:(R)" "*S-1-5-32-544:(F)" "<usuario del mostrador>:(R)"
```

- `/inheritance:r` **borra la ACL heredada**, que es la que daba lectura a todos los usuarios.
  Sin esta parte el resto no sirve de nada.
- `*S-1-5-18` es SYSTEM y `*S-1-5-32-544` es el grupo Administradores.

**Los grupos van por SID y no por nombre, y eso está medido, no es prolijidad.** En este Windows
en español:

```
icacls ... /grant "Administrators:(F)"
  → No se efectuó ninguna asignación entre los nombres de cuenta y los identificadores de seguridad
icacls ... /grant "*S-1-5-32-544:(F)"
  → archivo procesado correctamente
```

Un instalador escrito con los nombres en inglés falla en la máquina del local y no en la de
desarrollo si esa está en inglés. Los SID son los mismos en cualquier idioma.

**Al usuario del mostrador se le da lectura nominal**, no `Usuarios`. La diferencia importa poco
en una PC de una sola cuenta y no cuesta nada: el instalador sabe con qué cuenta se lo está
corriendo. Si por lo que sea no se la puede nombrar, el reemplazo es `*S-1-5-32-545` (grupo
Usuarios) y hay que anotar que ahí cualquier cuenta local lo lee.

Si la aplicación termina corriendo con otro usuario de Windows, el archivo deja de poder leerse:
para eso está el tercer cartel de más abajo, que dice exactamente eso en vez de "no arrancó".

### Por qué no está cifrado

Porque no hay forma de hacerlo que agregue seguridad de verdad acá:

- **Cifrarlo con una clave que vive al lado** no protege nada: quien lee un archivo lee el otro.
- **DPAPI**, que es el mecanismo correcto de Windows, no se puede llamar desde Java sin sumar una
  dependencia nativa (JNA o un JNI propio). Descartado por el costo, no por la idea.
- **EFS** (`cipher /e`) sí viene con Windows y no necesita dependencias, pero cifra **por
  usuario**: si se pierde el perfil o el certificado, el archivo no se recupera ni con la
  contraseña de administrador. Cambia un problema manejable —alguien con acceso a la máquina lee
  la contraseña— por uno peor: la app deja de arrancar sola y sin aviso. Además EFS no está en
  las ediciones Home de Windows, y no sabemos qué edición va a tener la PC del local.

Un archivo en claro con la ACL apretada es honesto sobre lo que protege: **a quien ya entró a la
máquina con otra cuenta**. Contra alguien con la contraseña de administrador de esa PC no protege
nada, y ningún esquema local lo haría.

## Qué cambia en cada perfil

| perfil | base | de dónde salen las credenciales | qué cambió |
|---|---|---|---|
| `dev` | H2 en memoria | de ningún lado: H2 no pide y la clave JWT tiene fallback en `application-dev.yml` | **nada** |
| `test` | H2 en memoria | igual que dev, fijadas en `application-test.properties` | **nada** |
| `pgcheck` | PostgreSQL local | `PGCHECK_DB_*` o `DB_*`, del entorno | **nada** |
| `prod` | PostgreSQL local | entorno, y si no, el archivo | **lee el archivo** |

El `spring.config.import` vive **solo** en `application-prod.yml`. Los otros perfiles ni miran la
ruta, así que no hay forma de que un archivo mal escrito en `ProgramData` afecte a los tests.

**Trabajar como hasta ahora sigue funcionando igual.** La configuración de IntelliJ que define
`DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` no se toca: esas variables le ganan al archivo, y si
el archivo no existe tampoco pasa nada, porque el import es `optional:`.

**Ojo con una trampa nueva en la máquina de desarrollo:** si alguna vez se instala sERPent acá,
el archivo de `ProgramData` va a quedar escrito, y correr el perfil prod **sin** las variables va
a tomar esas credenciales en lugar de fallar. No es un bug —es la precedencia funcionando— pero
explica un "¿por qué se conecta con otro usuario?" que si no desconcierta.

## El backup

**Qué hace hoy** `ops/backup/backup-serpent-db.ps1`: un `pg_dump -Fc` de `serpent_db` a dos
destinos —`D:\Backups\sERPent\Diario`, que es otro disco que el de los datos, y una carpeta que
OneDrive sube a la nube—. Las credenciales de `pg_dump` **no están en el script**: salen de
`%APPDATA%\postgresql\pgpass.conf`, con el usuario `postgres`.

**Decisión: el archivo de configuración NO entra al backup.** No porque no importe, sino porque
nada de lo que tiene adentro es irrecuperable:

| valor | si se pierde |
|---|---|
| `DB_USERNAME` | es un nombre fijo, no un secreto |
| `DB_PASSWORD` | el instalador genera otra y se la asigna al rol. El dump **no contiene contraseñas de roles**: `pg_dump -Fc` de una base no exporta los roles del servidor |
| `JWT_SECRET` | se cierran las sesiones abiertas y cada uno vuelve a entrar. Duran 8 horas igual |

La recuperación después de perder el disco es **reinstalar y restaurar el dump**, y en ese camino
las credenciales viejas no hacen falta en ningún momento.

Y hay una razón para que ADEMÁS no entre: **una de las dos copias va a OneDrive**. Meter ahí la
clave de firma de sesiones significa que quien tenga acceso a esa cuenta puede fabricar una sesión
válida de cualquier usuario. El dump ya viaja, pero el dump tiene hashes bcrypt, que no es lo
mismo. La copia local tampoco es buen lugar: el runbook contempla que `D:` sea un pendrive.

**Las dos condiciones de las que depende esta decisión**, y que hay que respetar en el instalador:

1. **El instalador tiene que poder (re)asignarle la contraseña al rol de la base.** Es la misma
   capacidad que necesita para instalar la primera vez. Si algún día no la tuviera, el archivo
   pasa a ser irreemplazable y esta decisión hay que darla vuelta.

2. **La aplicación tiene que usar un rol propio —`serpent_app`— y no el superusuario `postgres`.**
   Esto no es preferencia: el backup se autentica como `postgres` con la contraseña guardada en
   `pgpass.conf`. Si el instalador generara una contraseña al azar para `postgres`, el
   `pgpass.conf` quedaría viejo y **el respaldo nocturno empezaría a fallar** sin que la
   aplicación se entere de nada. Con un rol aparte, el instalador genera y rota lo suyo sin tocar
   lo que usa el backup.

   Hoy la aplicación se conecta como `postgres` (es lo que dice la configuración de IntelliJ).
   Cambiar eso es trabajo del instalador, no de este paso.

## Lo que el instalador tiene que hacer

1. Generar `DB_PASSWORD` (32 caracteres) y `JWT_SECRET` (64 caracteres) **alfanuméricos**.
2. Crear el rol `serpent_app` con esa contraseña, y la base `serpent_db` de la que sea dueño.
3. Escribir `C:\ProgramData\sERPent\serpent.properties` con las tres claves.
4. Correr el `icacls` de más arriba, con SIDs.
5. No tocar `pgpass.conf` ni la contraseña de `postgres`.

**Por qué alfanuméricos y no con símbolos.** Porque el archivo es un `.properties` de Java, donde
la barra invertida es un carácter de escape: una contraseña con `\` significaría una cosa para
Java y otra para cualquier otro lector, y el error aparecería como "contraseña incorrecta" sin
ninguna pista. Los símbolos tampoco agregan fuerza acá — 32 caracteres alfanuméricos son unos 190
bits de entropía, y lo que protege a esta base no es la fuerza de la contraseña sino que no sale
de la máquina. El mismo motivo vale para no meter `#`, `:` ni `=` en los valores.

## Cómo lo resuelven los tres ERPs del mercado

*Esto es recuerdo de entrenamiento, no algo que haya podido verificar desde esta máquina. Vale
como orientación, no como cita.*

- **SAP Business One** pide los datos de conexión en el asistente de instalación y los guarda
  cifrados; el usuario de la base no lo elige quien instala.
- **Dynamics 365 Business Central** los deja en el archivo de configuración del servicio, bajo la
  carpeta del programa, protegido por los permisos de esa carpeta.
- **Odoo** en Windows escribe un `odoo.conf` con la contraseña en claro, y eso es justamente lo
  que se le critica a su instalador — no por el archivo en claro en sí, sino porque queda con
  permisos abiertos y con la contraseña del superusuario de Postgres adentro.

De los tres, el que más se parece a esto es Odoo, con las dos diferencias que son las que le
critican: acá la ACL se aprieta explícitamente, y la contraseña que guarda es la de un rol de la
aplicación, no la del superusuario.
