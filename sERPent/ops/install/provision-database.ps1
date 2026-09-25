<#
.SINOPSIS
    Crea el rol y la base de sERPent, genera sus credenciales y las deja donde las leen el
    backend y el respaldo. Idempotente: correrlo dos veces no cambia nada.

.CONTEXTO / DECISIONES
    NADIE LE PASA UN SECRETO A ESTE SCRIPT. Genera la contraseña del rol y la clave de firma
    él mismo, las escribe, y nunca las devuelve ni las muestra. El instalador lo invoca sin
    saber qué va a generar, y eso es el punto: nadie inventa credenciales, nadie las recuerda,
    no pueden ser débiles, y no queda un paso del runbook que se pueda olvidar.

    EL SUPERUSUARIO NO TIENE CONTRASEÑA. Este script entra como postgres por SSPI: la cuenta de
    Windows que lo corre, mapeada a postgres en pg_ident.conf, y solo desde esta máquina (lo
    configura el instalador; ver sERPent-desktop\installer\code\database.iss). No existe un
    secreto de superusuario que guardar, perder o filtrar (handoff\fase5-decisiones.md, punto 2).

    Si SSPI no lo deja entrar, sale con el CÓDIGO 20 y un mensaje que dice por qué: el instalador
    distingue ese caso de cualquier otra falla.

    Si alguien lo corre a mano contra un PostgreSQL que sí pide contraseña, libpq usa PGPASSWORD
    del entorno como siempre. Nunca como parámetro: el argv de cualquier proceso lo lee
    cualquiera por WMI.

    EL SQL VA POR STDIN DE psql, no por -c, por lo mismo.

    CREDENCIALES ALFANUMÉRICAS, y no es prolijidad: serpent.properties es un .properties de
    Java, donde la barra invertida es carácter de escape. Una contraseña con "\" significaría
    una cosa para Java y otra para cualquier otro lector, y el error aparecería como
    "contraseña incorrecta" sin ninguna pista. 32 caracteres alfanuméricos son ~190 bits: la
    fuerza no es lo que se está resignando.

    RandomNumberGenerator y NO Get-Random: Get-Random no es criptográfico, y una contraseña
    generada con él es adivinable para quien sepa cuándo se instaló.
#>

[CmdletBinding()]
param(
    # Carpeta bin de PostgreSQL. La sabe el instalador porque acaba de instalarlo.
    [Parameter(Mandatory = $true)][string]$PgBin,

    # Puerto de la instancia. El instalador elige 5432 si está libre, o el primero libre
    # desde 5433: en una PC que no es nueva el 5432 puede estar tomado.
    [Parameter(Mandatory = $true)][int]$Port,

    # Cuenta de Windows que va a correr la tarea de respaldo, para saber en qué perfil
    # escribir pgpass.conf. Por defecto, la que está corriendo esto.
    [string]$BackupTaskUser = $env:USERNAME,

    [string]$SuperUser = 'postgres',
    [string]$AppRole = 'serpent_app',
    [string]$Database = 'serpent_db',
    [string]$ConfigFile = 'C:\ProgramData\sERPent\serpent.properties'
)

$ErrorActionPreference = 'Stop'

# Código de salida cuando SSPI no deja entrar. Lo lee database.iss para mostrar el cartel correcto.
$SspiDeniedExitCode = 20

$psql = Join-Path $PgBin 'psql.exe'
if (-not (Test-Path $psql)) {
    throw "No se encontro psql.exe en '$PgBin'."
}

# ============================== FUNCIONES ==============================

<#
    Genera una credencial alfanumerica con el generador criptografico del sistema.

    El rechazo de los bytes >= 248 no es paranoia: 256 no es multiplo de 62, asi que tomar
    "byte % 62" le daria a los primeros 8 caracteres del alfabeto una probabilidad mayor que
    al resto. Descartando el sobrante, todos quedan igual de probables.
#>
function New-AlphanumericSecret {
    param([int]$Length)

    $alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $chars = New-Object char[] $Length
        $byte = New-Object byte[] 1
        for ($i = 0; $i -lt $Length; $i++) {
            do {
                $rng.GetBytes($byte)
            } while ($byte[0] -ge 248)
            $chars[$i] = $alphabet[$byte[0] % 62]
        }
        return -join $chars
    }
    finally {
        $rng.Dispose()
    }
}

<#
    Corre SQL en psql pasandoselo por stdin, nunca por -c. Ver la cabecera.

    Dos detalles que no son adorno:

    PGOPTIONS apaga los NOTICE. PostgreSQL los manda por stderr —"el rol ya recibio membresia
    en..."— y PowerShell convierte cualquier stderr de un ejecutable nativo en un registro de
    error, asi que una corrida perfectamente exitosa terminaba escupiendo lo que parecia una
    falla. Con los avisos apagados, lo que quede en stderr es un problema de verdad.

    Y el ErrorActionPreference local, por lo mismo: con 'Stop' heredado, ese stderr convertido
    aborta el script aunque psql haya devuelto 0. El codigo de salida es la senal que importa,
    y se revisa abajo.

    LA CODIFICACION, medida en un Windows en espanol, porque la solucion obvia no sirve. psql
    escribe DOS codificaciones distintas: los errores que arma el propio cliente —"fallo la
    conexion al servidor", el caso mas comun en una instalacion— salen en la pagina de codigos
    ANSI de Windows (WIN1252), y los que manda el servidor salen en UTF-8. Leidos como los lee
    PowerShell por defecto, los dos quedaban ilegibles, cada uno a su manera: "fall¾ la
    conexi¾n" y "relaci├│n".

    PGCLIENTENCODING=UTF8 NO lo arregla: solo gobierna lo que manda el servidor, y el error de
    conexion ocurre antes de que haya servidor. Lo que si funciona es lo contrario: pedirle al
    servidor la misma pagina ANSI que ya usa el cliente, y decodificar todo con esa. Probado con
    los dos tipos de error: "fallo la conexion al servidor en «localhost»" y "no existe la
    relacion «...»", con sus tildes y sus comillas.

    Y POR ESO NO SE USA LA TUBERIA DE POWERSHELL. Medido: con la consola en la pagina 65001,
    PowerShell 5.1 le antepone el BOM de UTF-8 a lo que manda por la entrada estandar de un
    programa nativo, y psql lo toma como parte de la consulta:

        ERROR: error de sintaxis en o cerca de «ï»¿SELECT»

    No depende de $OutputEncoding (medido: con UTF-8 sin BOM el BOM aparece igual) ni de
    [Console]::OutputEncoding (que este script ya fijaba). Depende de la pagina de codigos de la
    consola, que decide quien nos invoca: el instalador la pone en 65001 para que los acentos de
    los programas lleguen bien al log, y a mano puede ser 850 o 1252.

    QUIEN ESCRIBE EL BOM, medido: no es la tuberia. El StreamWriter que .NET arma para la entrada
    estandar de un proceso usa [Console]::InputEncoding, y cuando esa codificacion tiene preambulo
    —la 65001 lo tiene— el preambulo se escribe solo, al primer contacto con el flujo. Pasa igual
    escribiendo los bytes a mano en BaseStream, porque el getter de BaseStream hace un flush.

    Por eso el arreglo tiene dos partes, y las dos hacen falta:
      1. se deja [Console]::InputEncoding SIN preambulo mientras dura la llamada (misma pagina de
         codigos, otra instancia), y se restaura como estaba;
      2. se lanza psql con System.Diagnostics.Process y SE ESCRIBEN LOS BYTES del SQL a mano, en la
         misma codificacion que se le declara al servidor. Las salidas se decodifican con
         StandardOutputEncoding y StandardErrorEncoding, en vez de tocar [Console]::OutputEncoding.

    Medido con la consola en 65001, 850, 1252 y 437: con esto no hay BOM en ninguna.

    El SQL sigue yendo por la entrada estandar, y no por un archivo temporal: lleva la contrasena
    del rol, y un archivo con la contrasena en el disco es justo lo que este script evita.
#>
function Invoke-Sql {
    param([string]$Sql, [string]$OnDatabase = 'postgres')

    $ansiCodePage = [System.Globalization.CultureInfo]::CurrentCulture.TextInfo.ANSICodePage
    $clientEncoding = [System.Text.Encoding]::GetEncoding($ansiCodePage)

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $psql
    # -w: NUNCA preguntar una contraseña. Sin eso, si la autenticación la pide, psql se queda
    # esperando una respuesta en una ventana que el instalador tiene escondida, para siempre.
    $startInfo.Arguments = "-w -h localhost -p $Port -U `"$SuperUser`" -d `"$OnDatabase`" -v ON_ERROR_STOP=1 -t -A"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = $clientEncoding
    $startInfo.StandardErrorEncoding = $clientEncoding
    $startInfo.EnvironmentVariables['PGOPTIONS'] = '-c client_min_messages=warning'
    $startInfo.EnvironmentVariables['PGCLIENTENCODING'] = "WIN$ansiCodePage"

    # Sin preámbulo mientras dura la llamada, para que no aparezca un BOM delante del SQL. Si no
    # hay consola (el setter falla), tampoco hay preámbulo: no hay nada que arreglar.
    $previousInput = $null
    $inputChanged = $false
    try {
        $previousInput = [Console]::InputEncoding
        if ($previousInput.GetPreamble().Count -gt 0) {
            [Console]::InputEncoding = New-Object System.Text.UTF8Encoding $false
            $inputChanged = $true
        }
    }
    catch {
        $inputChanged = $false
    }

    try {
        $process = [System.Diagnostics.Process]::Start($startInfo)
        # Las dos salidas se leen mientras psql corre: si se esperara a que termine, un mensaje
        # largo llenaría el búfer de una tubería y psql quedaría esperando a que alguien la vacíe.
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()

        # LOS BYTES, a mano: ninguna capa decide por nosotros cómo se codifica la consulta.
        $bytes = $clientEncoding.GetBytes($Sql + "`n")
        $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()

        $process.WaitForExit()
        $output = $outputTask.Result
        $errors = $errorTask.Result
    }
    finally {
        if ($inputChanged) {
            try { [Console]::InputEncoding = $previousInput } catch { }
        }
    }

    if ($process.ExitCode -ne 0) {
        throw "psql fallo: $(($errors + "`n" + $output).Trim())"
    }
    return $output.Trim()
}

<# Lee un .properties simple. Solo lo que escribe este script: una clave por linea. #>
function Read-PropertiesFile {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path $Path)) { return $values }

    foreach ($line in Get-Content -Path $Path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $values[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }
    return $values
}

<#
    Escribe un archivo con secretos y lo deja con permisos apretados.

    DOS COSAS QUE PARECEN DE MAS Y NO LO SON, las dos descubiertas corriendo esto dos veces:

    1. SE DEVUELVE EL PERMISO DE ESCRITURA ANTES DE ESCRIBIR. La ACL que aplica este mismo
       helper le deja al usuario solo lectura — que es lo correcto: la aplicacion no tiene por
       que poder alterar sus propias credenciales. El efecto lateral es que la SEGUNDA corrida
       de este script no podia reescribir el archivo y moria con "Acceso denegado". Un
       instalador que no se puede volver a correr no sirve.

    2. SI EL CONTENIDO YA ES EL QUE TIENE QUE SER, NO SE TOCA. Ademas de ser mas rapido, evita
       reescribir un archivo que esta bien, que es una oportunidad de romperlo por nada.

    Los grupos van por SID: ver el comentario de la seccion 5.
#>
function Write-SecretFile {
    param(
        [string]$Path,
        [string[]]$Lines,
        [string]$ReadUser,
        [System.Text.Encoding]$Encoding
    )

    $desired = ($Lines -join "`r`n")

    if (Test-Path $Path) {
        $current = $null
        try { $current = [System.IO.File]::ReadAllText($Path) } catch { $current = $null }

        if ($null -ne $current -and $current.TrimEnd() -eq $desired.TrimEnd()) {
            return $false
        }

        & icacls $Path /grant "${env:USERNAME}:(F)" *>&1 | Out-Null
    }
    else {
        New-Item -ItemType Directory -Force -Path (Split-Path $Path -Parent) | Out-Null
    }

    [System.IO.File]::WriteAllLines($Path, $Lines, $Encoding)

    $acl = & icacls $Path /inheritance:r /grant:r "*S-1-5-18:(R)" "*S-1-5-32-544:(F)" "${ReadUser}:(R)" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "icacls fallo sobre '$Path': $acl"
    }
    return $true
}


# ============================== EJECUCION ==============================

# --- 0) Entrar como superusuario, o decir por qué no ----------------------------------------
#
# Antes de tocar nada. Si falla la autenticación, el mensaje dice qué cuenta intentó entrar y qué
# hacer; cualquier otra falla (el servidor no responde, por ejemplo) sale como error común.
try {
    Invoke-Sql "SELECT 1;" | Out-Null
}
catch {
    $detail = $_.Exception.Message
    if ($detail -match 'SSPI|autentific|authentication|password|contrase') {
        $account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        Write-Host "No se pudo entrar a PostgreSQL como '$SuperUser' con la cuenta de Windows '$account' (SSPI)."
        Write-Host "Esa cuenta no esta en pg_ident.conf del cluster, o este PostgreSQL no permite SSPI para '$SuperUser'."
        Write-Host "Si el cluster es el de sERPent, volver a correr el instalador como administrador: agrega la cuenta que lo corre."
        Write-Host "Detalle de psql: $detail"
        exit $SspiDeniedExitCode
    }
    throw
}
Write-Host "Conectado como '$SuperUser' (SSPI) en el puerto $Port."

# --- 1) Las credenciales: se generan UNA sola vez, en la primera corrida -------------------
#
# LA IDEMPOTENCIA EMPIEZA ACA. Si el archivo ya existe, sus valores mandan: regenerar la
# contrasena dejaria al rol de la base con la vieja y a la aplicacion sin poder entrar a su
# propia base. Una reinstalacion o una reparacion tienen que poder correr esto sin romper una
# instalacion que funciona.

$existing = Read-PropertiesFile -Path $ConfigFile
$isFirstRun = -not $existing.ContainsKey('DB_PASSWORD')

if ($isFirstRun) {
    $dbPassword = New-AlphanumericSecret -Length 32
    $jwtSecret = New-AlphanumericSecret -Length 64
    Write-Host "Credenciales generadas."
}
else {
    $dbPassword = $existing['DB_PASSWORD']
    $jwtSecret = $existing['JWT_SECRET']
    Write-Host "Ya habia credenciales en $ConfigFile; se reusan."
}

# --- 2) El rol ----------------------------------------------------------------------------
#
# Se crea si no esta, y si esta se le fija la contrasena que dice el archivo. Ese segundo
# caso es el que arregla una instalacion a medias: el archivo escrito pero el rol nunca
# creado, o creado con otra contrasena.

$roleExists = (Invoke-Sql "SELECT 1 FROM pg_roles WHERE rolname = '$AppRole';") -eq '1'

$escapedPassword = $dbPassword.Replace("'", "''")
if ($roleExists) {
    Invoke-Sql "ALTER ROLE $AppRole WITH LOGIN PASSWORD '$escapedPassword';" | Out-Null
    Write-Host "Rol '$AppRole': ya existia, contrasena sincronizada con el archivo."
}
else {
    Invoke-Sql "CREATE ROLE $AppRole WITH LOGIN PASSWORD '$escapedPassword';" | Out-Null
    Write-Host "Rol '$AppRole': creado."
}

# --- 3) La base ---------------------------------------------------------------------------
#
# En una instalacion nueva la base se crea PERTENECIENDO al rol de la aplicacion, que es lo
# que le permite despues dumpearla sin ser superusuario.
#
# Si la base YA EXISTE y es de otro dueno, NO se le cambia el dueno: puede ser una base con
# datos de alguien mas, y cambiar duenos de algo ajeno no es lo que un instalador tiene que
# hacer solo. En su lugar se le da a serpent_app el rol pg_read_all_data, que es exactamente
# lo que PostgreSQL define para un usuario de respaldo. La aplicacion en ese escenario
# igual necesita permisos de escritura, que se conceden abajo.

$dbOwner = Invoke-Sql "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = '$Database';"

if ([string]::IsNullOrEmpty($dbOwner)) {
    Invoke-Sql "CREATE DATABASE $Database OWNER $AppRole;" | Out-Null
    Write-Host "Base '$Database': creada, duena '$AppRole'."
}
elseif ($dbOwner -eq $AppRole) {
    Write-Host "Base '$Database': ya existia y ya es de '$AppRole'."
}
else {
    Write-Host "Base '$Database': ya existia y es de '$dbOwner'. No se le cambia el dueno."
    Invoke-Sql "GRANT pg_read_all_data TO $AppRole;" | Out-Null
    Invoke-Sql "GRANT pg_write_all_data TO $AppRole;" | Out-Null
    Invoke-Sql "GRANT CREATE ON SCHEMA public TO $AppRole;" -OnDatabase $Database | Out-Null
    Write-Host "  Se le dieron a '$AppRole' permisos de lectura, escritura y creacion."
}

# La codificación de la base, siempre (nueva o ya existente). La app guarda texto con tildes y eñes
# y el driver JDBC habla UTF-8: una base en otra codificación rompe en silencio. El locale lo fija
# el clúster (initdb en el instalador, fase5-decisiones.md) y acá solo se anota.
$dbEncoding = Invoke-Sql "SELECT pg_encoding_to_char(encoding) || '|' || datcollate || '|' || datctype FROM pg_database WHERE datname = '$Database';"
$encodingParts = $dbEncoding.Split('|')
if ($encodingParts[0] -ne 'UTF8') {
    throw "La base '$Database' esta en codificacion '$($encodingParts[0])' y tiene que ser UTF8. No se sigue: la app romperia tildes y enes sin avisar."
}
Write-Host "Base '$Database': codificacion $($encodingParts[0]), LC_COLLATE $($encodingParts[1]), LC_CTYPE $($encodingParts[2])."

# --- 4) El archivo de configuracion --------------------------------------------------------
#
# Se escribe solo en la primera corrida. Reescribirlo con los mismos valores seria inofensivo,
# pero tambien es una oportunidad de romper algo que ya andaba, y no compra nada.
#
# PG_BIN no lo usa el backend: lo usa el script de respaldo, que necesita pg_dump.exe y no
# tiene forma de adivinar donde quedo instalado PostgreSQL. Va en el mismo archivo porque es
# la misma instalacion describiendose a si misma, y porque asi hay UN solo archivo con
# permisos apretados en vez de dos. (Odoo hace lo mismo con pg_path en su odoo.conf —
# recuerdo de entrenamiento, no verificado.)

# Los secretos vienen de arriba —generados o releidos—, asi que este bloque los conserva. Lo
# que SI se corrige en una segunda corrida es el resto: un archivo escrito por una version
# anterior del instalador puede no tener DB_PORT o PG_BIN, y sin ellos el respaldo no sabe a
# donde conectarse. Reparar eso es justamente para lo que sirve volver a correr esto.

$configLines = @(
    "# Generado por el instalador de sERPent. No editar a mano.",
    "DB_USERNAME=$AppRole",
    "DB_PASSWORD=$dbPassword",
    "DB_NAME=$Database",
    "DB_PORT=$Port",
    "JWT_SECRET=$jwtSecret",
    "PG_BIN=$PgBin"
)

$configChanged = Write-SecretFile -Path $ConfigFile -Lines $configLines -ReadUser $BackupTaskUser `
    -Encoding ([System.Text.UTF8Encoding]::new($false))

if ($configChanged) {
    Write-Host "Archivo '$ConfigFile': escrito."
}
else {
    Write-Host "Archivo '$ConfigFile': ya estaba como corresponde."
}

# --- 5) Los permisos ------------------------------------------------------------------------
#
# Los aplica Write-SecretFile en cada escritura, y las dos reglas estan medidas:
#
# /inheritance:r NO ES OPCIONAL: un archivo creado en ProgramData nace legible por cualquier
# cuenta de la maquina, por herencia.
#
# Y LOS GRUPOS VAN POR SID: en un Windows en espanol, icacls con "Administrators" devuelve
# "No se efectuo ninguna asignacion entre los nombres de cuenta y los identificadores de
# seguridad". Los SID son los mismos en cualquier idioma.
#   *S-1-5-18     = SYSTEM
#   *S-1-5-32-544 = Administradores

# --- 6) pgpass.conf para el respaldo --------------------------------------------------------
#
# El respaldo corre pg_dump como serpent_app, y libpq lee la contrasena de este archivo.
#
# VA AL LADO DE serpent.properties, NO EN EL PERFIL DE UNA CUENTA (fase 6). El respaldo lo
# dispara el back-end al cerrar caja (cuenta del mostrador), lo repite una tarea de red que corre
# COMO SYSTEM, y a veces lo corre un tecnico a mano: un archivo en el perfil de una cuenta solo
# sirve para una de las tres. El script del respaldo apunta ahi con PGPASSFILE.
#
# Medido (handoff\fase6-decisiones.md): PGPASSFILE funciona apuntando a cualquier ruta, incluso
# con el ACL apretado (SYSTEM, Administradores y la cuenta del respaldo). Lo que viaja al proceso
# hijo es LA RUTA, nunca la contrasena.
#
# Se reemplaza solo la linea de esta base: cualquier otra que el archivo tenga se conserva.

$pgpassPath = Join-Path (Split-Path $ConfigFile -Parent) 'pgpass.conf'
New-Item -ItemType Directory -Force -Path (Split-Path $pgpassPath -Parent) | Out-Null

$entry = "localhost:${Port}:${Database}:${AppRole}:${dbPassword}"
$prefix = "localhost:${Port}:${Database}:${AppRole}:"

$kept = @()
if (Test-Path $pgpassPath) {
    $existingLines = @()
    try { $existingLines = @(Get-Content -Path $pgpassPath) } catch { $existingLines = @() }
    $kept = @($existingLines | Where-Object { $_.Trim() -ne '' -and -not $_.StartsWith($prefix) })
}

# libpq IGNORA el archivo si otros lo pueden leer, asi que la ACL no es prolijidad: sin
# apretarla, pg_dump se comporta como si el archivo no existiera y pide contrasena.
$pgpassChanged = Write-SecretFile -Path $pgpassPath -Lines ($kept + $entry) -ReadUser $BackupTaskUser `
    -Encoding ([System.Text.ASCIIEncoding]::new())

if ($pgpassChanged) {
    Write-Host "pgpass.conf: linea de '$AppRole' escrita en '$pgpassPath'."
}
else {
    Write-Host "pgpass.conf: ya estaba como corresponde."
}

Write-Host ""
Write-Host "Aprovisionamiento completo."
