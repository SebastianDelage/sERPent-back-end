<#
.SINOPSIS
    Respaldo de la base de sERPent al disco, con retención abuelo-padre-hijo y registro en archivos.

.USO
    powershell -NoProfile -ExecutionPolicy Bypass -File backup-serpent-db.ps1 -Origen cierre-de-caja
    powershell -NoProfile -ExecutionPolicy Bypass -File backup-serpent-db.ps1 -Origen tarea -SoloSiFaltaHoy

.CONTEXTO / DECISIONES (handoff\fase6-decisiones.md)
    DONDE. Todo en el disco, en C:\ProgramData\sERPent\respaldos. La PC de la tienda tiene UNA
    SOLA unidad y no hay internet: la copia de afuera es un pendrive que se lleva una persona.
    Estos respaldos sirven para volver atras de un error; NO sobreviven a un disco roto.

    FORMATO. pg_dump -Fc -Z zstd:3. Medido con un anio de operacion de una polleria (65.700
    ventas, 204.400 renglones): 4,45 MB contra 8,7 MB de gzip, y mas rapido (1,0 s contra 1,6 s).
    SE RESTAURA CON NUESTROS BINARIOS: un PostgreSQL compilado sin zstd no puede leerlo.

    NO TRABA LAS VENTAS. Medido: una venta registrada mientras corria el respaldo tardo 115 ms,
    contra 193 ms sin respaldo corriendo. pg_dump saca una foto consistente sin frenar a nadie.

    CREDENCIALES. Se entra como serpent_app (el rol de la app), con PGPASSFILE apuntando al
    pgpass.conf que escribe el instalador en C:\ProgramData\sERPent. Medido: funciona igual con
    el ACL apretado, y no depende del perfil de ninguna cuenta, asi que anda corriendo como
    SYSTEM (la tarea de red), como la cuenta del mostrador (el cierre de caja) o a mano.
    LA CONTRASENA NO PASA POR EL ENTORNO NI POR argv: lo unico que viaja es la ruta del archivo.

    CORRE COMO SYSTEM. Nada de rutas relativas ni de %USERPROFILE%: todo sale de
    serpent.properties o de parametros.

    EL REGISTRO VA EN ARCHIVOS, NO EN LA BASE. Si el registro viviera en la base y lo que se
    perdio es la base, no serviria justo cuando hace falta.

.CODIGOS DE SALIDA
     0  respaldo hecho y verificado
    11  no hacia falta (con -SoloSiFaltaHoy, ya habia un respaldo de hoy)
    12  no habia nada que respaldar: la base todavia no tiene ninguna tabla
    20  ya hay otro respaldo corriendo
    21  no se pudo leer la configuracion
    22  pg_dump fallo
    23  el respaldo salio pero NO paso la verificacion (no se borra nada)
    24  no se pudo escribir el registro
#>
[CmdletBinding()]
param(
    [ValidateSet('cierre-de-caja', 'manual', 'tarea')][string]$Origen = 'manual',

    # Solo respalda si hoy no hubo un respaldo exitoso. Lo usa la tarea de red.
    [switch]$SoloSiFaltaHoy,

    [string]$ConfigFile = 'C:\ProgramData\sERPent\serpent.properties',
    [string]$CarpetaRespaldos = 'C:\ProgramData\sERPent\respaldos',
    [string]$PgPassFile = 'C:\ProgramData\sERPent\pgpass.conf'
)

$ErrorActionPreference = 'Stop'

# La retencion, en un solo lugar y sin numeros escondidos en el codigo de abajo.
$DiasQueSeGuardanTodos = 90       # todos los respaldos de los ultimos 90 dias
$DiasDeSemanales       = 365      # entre 90 dias y un anio, el primero de cada semana
                                  # mas viejos que un anio, el primero de cada mes, para siempre
$PatronDeNombre = '^serpent_db_\d{4}-\d{2}-\d{2}_\d{6}\.dump$'

$inicio = Get-Date
$sello = $inicio.ToString('yyyy-MM-dd_HHmmss')
$archivoRespaldo = Join-Path $CarpetaRespaldos "serpent_db_$sello.dump"
$archivoParcial = "$archivoRespaldo.parcial"
$historial = Join-Path $CarpetaRespaldos 'historial.jsonl'
$estado = Join-Path $CarpetaRespaldos 'estado.json'
$candado = Join-Path $CarpetaRespaldos 'respaldo-en-curso.lock'

# UTF-8 SIN BOM para todo lo que lee otro programa: es la regla que dejo la fase 5.
$Utf8SinBom = New-Object System.Text.UTF8Encoding $false

function Write-TextoSinBom {
    param([string]$Path, [string]$Texto)
    [System.IO.File]::WriteAllText($Path, $Texto, $Utf8SinBom)
}

<#
    Convierte lo que escribio un programa en texto legible, SIN suponer su codificacion.

    Medido, y por las malas: initdb escribe en ANSI y pg_dump en UTF-8, en la misma maquina. Una
    sola suposicion deja la mitad de los mensajes ilegibles ("Â¿EstÃ¡ el servidor...").

    Como funciona: la salida se lee con Latin-1, que no pierde ningun byte (cada byte es un
    caracter), se vuelve a los bytes originales, y se prueba UTF-8 ESTRICTO. Si decodifica, era
    UTF-8; si no, se lee con la pagina ANSI. Es la misma regla que ya usa el instalador.
#>
function ConvertTo-TextoLegible {
    param([string]$LeidoComoLatin1)
    if (-not $LeidoComoLatin1) { return '' }
    $bytes = [System.Text.Encoding]::GetEncoding(28591).GetBytes($LeidoComoLatin1)
    try {
        $estricto = New-Object System.Text.UTF8Encoding($false, $true)
        return $estricto.GetString($bytes)
    } catch {
        $ansi = [System.Text.Encoding]::GetEncoding([System.Globalization.CultureInfo]::CurrentCulture.TextInfo.ANSICodePage)
        return $ansi.GetString($bytes)
    }
}

<#
    Corre un programa y devuelve su codigo y su salida ya legible.

    Ni tuberia ni [Console]::OutputEncoding: los dos ya mordieron.
#>
function Invoke-Programa {
    param([string]$Ruta, [string[]]$Argumentos)

    # Latin-1 para LEER: no pierde bytes. La codificacion de verdad la decide ConvertTo-TextoLegible.
    $sinPerdida = [System.Text.Encoding]::GetEncoding(28591)
    $inicioProceso = New-Object System.Diagnostics.ProcessStartInfo
    $inicioProceso.FileName = $Ruta
    $inicioProceso.Arguments = ($Argumentos | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' '
    $inicioProceso.UseShellExecute = $false
    $inicioProceso.CreateNoWindow = $true
    $inicioProceso.RedirectStandardOutput = $true
    $inicioProceso.RedirectStandardError = $true
    $inicioProceso.StandardOutputEncoding = $sinPerdida
    $inicioProceso.StandardErrorEncoding = $sinPerdida
    # La ruta del archivo de contrasenas, NO la contrasena.
    $inicioProceso.EnvironmentVariables['PGPASSFILE'] = $PgPassFile
    # Y se le pide al servidor la MISMA pagina ANSI con la que se lee la salida. Sin esto, los
    # mensajes del servidor vienen en UTF-8, se leen como ANSI y quedan ilegibles: es la misma
    # lección de provision-database.ps1, y el banco la volvio a agarrar ("Â¿EstÃ¡ el servidor...").
    $inicioProceso.EnvironmentVariables['PGCLIENTENCODING'] = 'WIN' + [System.Globalization.CultureInfo]::CurrentCulture.TextInfo.ANSICodePage

    $proceso = [System.Diagnostics.Process]::Start($inicioProceso)
    $tareaSalida = $proceso.StandardOutput.ReadToEndAsync()
    $tareaError = $proceso.StandardError.ReadToEndAsync()
    $proceso.WaitForExit()
    return @{
        Codigo = $proceso.ExitCode
        Salida = (ConvertTo-TextoLegible $tareaSalida.Result)
        Error  = (ConvertTo-TextoLegible $tareaError.Result).Trim()
    }
}

<#
    EL MOTIVO DE UNA FALLA, CON LOS RENGLONES QUE HACEN FALTA PARA ENTENDERLO.

    Antes se guardaba SOLO EL ULTIMO RENGLON, y con la base apagada el registro terminaba diciendo
    "<tab>¿Esta el servidor en ejecucion en ese host y aceptando conexiones TCP/IP?": una
    repregunta suelta, sin el renglon de arriba —el que dice que fallo la conexion y a que puerto—
    que es el que explica algo. Los programas de PostgreSQL parten el error en varios renglones y
    el ultimo suele ser la sugerencia, no la causa.

    Se pegan todos los renglones con texto, cada uno sin los espacios del principio (los de
    continuacion vienen sangrados con un tabulador), en una sola linea, porque el destino es una
    linea de JSON y una linea de consola. Si queda muy largo se corta AL FINAL: la causa esta
    adelante.
#>
function Format-MotivoDeError {
    param([string]$Salida, [string]$SiNoDijoNada = 'sin mensaje', [int]$Maximo = 500)

    $lineas = @($Salida -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($lineas.Count -eq 0) { return $SiNoDijoNada }
    $texto = $lineas -join ' '
    if ($texto.Length -gt $Maximo) { $texto = $texto.Substring(0, $Maximo) + '...' }
    return $texto
}

function Read-Configuracion {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "No se encontro '$Path'. Lo escribe el instalador de sERPent; sin el, este script no sabe donde esta PostgreSQL ni con que usuario entrar."
    }
    $valores = @{}
    foreach ($linea in Get-Content -LiteralPath $Path) {
        $limpia = $linea.Trim()
        if ($limpia -eq '' -or $limpia.StartsWith('#') -or $limpia.StartsWith('!')) { continue }
        $corte = $limpia.IndexOf('=')
        if ($corte -lt 1) { continue }
        $valores[$limpia.Substring(0, $corte).Trim()] = $limpia.Substring($corte + 1).Trim()
    }
    foreach ($clave in 'PG_BIN', 'DB_PORT', 'DB_NAME', 'DB_USERNAME') {
        if (-not $valores.ContainsKey($clave)) {
            throw "Al archivo '$Path' le falta '$clave'. Volve a correr provision-database.ps1, que lo completa."
        }
    }
    return $valores
}

<#
    Una linea por intento en historial.jsonl, y el resumen en estado.json.

    estado.json se escribe ENTERO Y DE UNA (archivo temporal y reemplazo), para que la app nunca
    lea medio archivo.
#>
function Write-Registro {
    param([hashtable]$Entrada, [hashtable]$CambiosDeEstado)

    $linea = ($Entrada | ConvertTo-Json -Compress -Depth 6)
    [System.IO.File]::AppendAllText($historial, $linea + "`n", $Utf8SinBom)

    $actual = @{}
    if (Test-Path -LiteralPath $estado) {
        try {
            $leido = [System.IO.File]::ReadAllText($estado, $Utf8SinBom) | ConvertFrom-Json
            foreach ($p in $leido.PSObject.Properties) { $actual[$p.Name] = $p.Value }
        } catch {
            # Un estado.json ilegible no puede frenar un respaldo: se reescribe entero.
            $actual = @{}
        }
    }
    foreach ($k in $CambiosDeEstado.Keys) { $actual[$k] = $CambiosDeEstado[$k] }
    $temporal = "$estado.nuevo"
    Write-TextoSinBom -Path $temporal -Texto (($actual | ConvertTo-Json -Depth 6))
    Move-Item -LiteralPath $temporal -Destination $estado -Force
}

function Get-RespaldosEnDisco {
    return @(Get-ChildItem -LiteralPath $CarpetaRespaldos -Filter '*.dump' -File -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match $PatronDeNombre } |
             Sort-Object Name)
}

function Get-FechaDeNombre {
    param([string]$Nombre)
    if ($Nombre -match '^serpent_db_(\d{4})-(\d{2})-(\d{2})_(\d{2})(\d{2})(\d{2})\.dump$') {
        return Get-Date -Year $Matches[1] -Month $Matches[2] -Day $Matches[3] `
                        -Hour $Matches[4] -Minute $Matches[5] -Second $Matches[6]
    }
    return $null
}

<#
    Que se conserva y que se borra. Devuelve los nombres a borrar, sin borrar nada.

    Se separa de la accion a proposito: asi la regla se prueba sola, con fechas fabricadas, sin
    tocar ningun archivo.
#>
function Get-RespaldosAPodar {
    param([string[]]$Nombres, [datetime]$Hoy)

    $porNombre = @{}
    foreach ($n in $Nombres) {
        $f = Get-FechaDeNombre $n
        if ($f) { $porNombre[$n] = $f }
    }
    if ($porNombre.Count -eq 0) { return @() }

    $ordenados = $porNombre.Keys | Sort-Object { $porNombre[$_] }
    $masNuevo = $ordenados | Select-Object -Last 1
    $semanasVistas = @{}
    $mesesVistos = @{}
    $aBorrar = @()

    foreach ($n in $ordenados) {
        $fecha = $porNombre[$n]
        $dias = ($Hoy.Date - $fecha.Date).Days
        if ($n -eq $masNuevo) { continue }                 # el mas nuevo no se borra NUNCA
        if ($dias -le $DiasQueSeGuardanTodos) { continue } # los ultimos 90 dias, todos

        if ($dias -le $DiasDeSemanales) {
            # La semana se identifica por SU LUNES, y no con [System.Globalization.ISOWeek], que no
            # existe en PowerShell 5.1 (es de .NET Core): lo agarro el banco, y en la tienda habria
            # reventado la poda. El lunes tampoco tiene el lio de las semanas a caballo de dos anios.
            $semana = $fecha.Date.AddDays(-(([int]$fecha.DayOfWeek + 6) % 7)).ToString('yyyy-MM-dd')
            if (-not $semanasVistas.ContainsKey($semana)) { $semanasVistas[$semana] = $n; continue }
            $aBorrar += $n
        } else {
            $mes = $fecha.ToString('yyyy-MM')
            if (-not $mesesVistos.ContainsKey($mes)) { $mesesVistos[$mes] = $n; continue }
            $aBorrar += $n
        }
    }
    return $aBorrar
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

<#
    EL BLOQUE DE "ESTA CORRIDA" DE estado.json, ENTERO Y DE UN SOLO LUGAR.

    Por que existe: estado.json se arma mezclando lo nuevo sobre lo que habia, para que los datos
    del ultimo respaldo BUENO sobrevivan a los intentos que fallan. Eso es a proposito, pero tiene
    el filo del otro lado: un campo que una rama escribe y otra no QUEDA CON EL VALOR DE LA CORRIDA
    ANTERIOR. Paso de verdad en la VM: despues de un respaldo bueno de una base con 60 objetos,
    estado.json decia "objetosEnBase: 0", el valor de la instalacion, cuando todavia no habia
    tablas. No rompia nada ese dia; engaña a quien lo lea dentro de seis meses, y estado.json es
    justo el archivo que la app va a leer para decidir si avisa.

    El arreglo no es acordarse de escribir el campo en las cinco ramas —eso vuelve a romperse en la
    sexta—: es que NINGUNA rama arme el bloque a mano. Todas piden este, que trae todos los campos
    del intento, siempre, y encima le agregan lo suyo.

    LOS TRES GRUPOS DE CAMPOS, y se distinguen por el nombre a proposito:
      ultimoIntento*   lo de ESTA corrida. Se reescribe entero en cada corrida, salga como salga.
      espacioLibreBytes / respaldosEnDisco / bytesEnDisco
                       como esta el disco AHORA. Se vuelven a medir en cada corrida.
      ultimoRespaldo* / ultimaPrueba* / ultimaExportacion*
                       sobreviven A PROPOSITO: son del ultimo que salio bien, y tienen que seguir
                       ahi cuando el de hoy falla, que es cuando mas se los mira.
#>
function New-EstadoDelIntento {
    param([string]$Resultado, [string]$Detalle = '', $ObjetosEnBase = 'no se pregunto')

    $enDisco = Get-RespaldosEnDisco
    return @{
        ultimoIntento              = $inicio.ToString('o')
        ultimoIntentoResultado     = $Resultado
        ultimoIntentoDetalle       = $Detalle
        ultimoIntentoObjetosEnBase = $ObjetosEnBase
        respaldosEnDisco           = $enDisco.Count
        bytesEnDisco               = [long](($enDisco | Measure-Object Length -Sum).Sum)
        espacioLibreBytes          = (New-Object System.IO.DriveInfo ([System.IO.Path]::GetPathRoot($CarpetaRespaldos))).AvailableFreeSpace
    }
}

# Lo que la sonda tiene para decir, en una sola forma, para que vaya igual al registro y al estado.
function Format-ObjetosEnBase {
    param($Sonda)
    if (-not $Sonda) { return 'no se pregunto' }
    if ($Sonda.Sabe) { return $Sonda.Objetos }
    return 'no se pudo preguntar'
}

<#
    CUANTOS OBJETOS TIENE LA BASE, preguntado ANTES de respaldar.

    Por que existe: en una instalacion nueva la base esta CREADA PERO VACIA. Las tablas las crea
    sERPent (Flyway) la primera vez que arranca, y eso pasa despues del instalador. Sin esta
    pregunta, el primer respaldo sale sin un solo objeto y la verificacion lo rechaza —
    correctamente, porque un respaldo sin objetos de una base con datos es una desgracia. El que
    estaba mal era el momento, no el chequeo.

    TRES RESPUESTAS Y NO DOS: un numero, o "no se pudo preguntar". Nunca se devuelve 0 por no haber
    podido mirar: eso convertiria una consulta fallida en un "no hay nada que respaldar", que es
    justo la respuesta correcta por el motivo equivocado.

    Que se cuenta: tablas, particionadas, vistas, vistas materializadas, secuencias y tablas
    foraneas, en los esquemas que pg_dump se lleva. Se dejan afuera information_schema y todo lo
    que empieza con "pg_", que ademas de pg_catalog cubre pg_toast y los pg_temp_N de otras
    sesiones: si se contaran, la tabla temporal de cualquiera haria parecer que la base tiene algo.
#>
function Get-ObjetosEnBase {
    param([string]$PsqlPath, [int]$Puerto, [string]$Base, [string]$Usuario)

    if (-not (Test-Path -LiteralPath $PsqlPath)) {
        return @{ Sabe = $false; Objetos = $null; Detalle = "no se encontro psql.exe en '$PsqlPath'" }
    }
    $consulta = "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE c.relkind IN ('r','p','v','m','S','f') " +
                "AND n.nspname <> 'information_schema' AND n.nspname NOT LIKE 'pg\_%'"
    $r = Invoke-Programa -Ruta $PsqlPath -Argumentos @(
        '-w', '-X', '-t', '-A', '-h', 'localhost', '-p', "$Puerto", '-U', $Usuario, '-d', $Base, '-c', $consulta)
    $texto = ($r.Salida -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First 1)
    if ($r.Codigo -ne 0 -or $texto -notmatch '^\s*\d+\s*$') {
        $motivo = Format-MotivoDeError -Salida $r.Error -SiNoDijoNada "psql salio con $($r.Codigo)"
        return @{ Sabe = $false; Objetos = $null; Detalle = $motivo }
    }
    return @{ Sabe = $true; Objetos = [int]$texto.Trim(); Detalle = '' }
}

<#
    LA REGLA DE LA VERIFICACION, sola y sin tocar nada, para poder probarla con numeros fabricados.

    Se separa a proposito, igual que la regla de la poda: el caso que importa —un respaldo que sale
    VACIO de una base que SI tenia objetos— no se puede provocar en un banco sin romper algo de
    verdad, pero la regla que lo atrapa si se puede probar.

    $ObjetosEnBase es $null cuando no se pudo preguntar, y entonces un respaldo vacio se rechaza
    igual: ante la duda, no se da por bueno.
#>
function Get-VeredictoDeVerificacion {
    param([int]$CodigoPgRestore, [int]$Entradas, $ObjetosEnBase)

    if ($CodigoPgRestore -ne 0) {
        # Las llaves no son decoracion: sin ellas PowerShell lee "$CodigoPgRestore:" como una
        # variable con unidad (estilo $env:), y el archivo entero deja de compilar.
        return @{ Ok = $false; Motivo = "pg_restore --list salio con ${CodigoPgRestore}: el archivo no se puede leer" }
    }
    if ($Entradas -gt 0) {
        return @{ Ok = $true; Motivo = "$Entradas entradas" }
    }
    if ($null -eq $ObjetosEnBase) {
        return @{ Ok = $false; Motivo = 'el respaldo no contiene NINGUN objeto y no se pudo preguntarle a la base cuantos tenia' }
    }
    if ($ObjetosEnBase -gt 0) {
        return @{ Ok = $false; Motivo = "la base tiene $ObjetosEnBase objetos pero el respaldo no trajo NINGUNO" }
    }
    return @{ Ok = $false; Motivo = 'el respaldo no contiene NINGUN objeto' }
}

# ==================================================================================================
# El respaldo
# ==================================================================================================
New-Item -ItemType Directory -Force -Path $CarpetaRespaldos | Out-Null

# UN RESPALDO A LA VEZ. El candado es un archivo abierto en exclusiva: si el proceso muere, el
# sistema lo suelta solo, cosa que un archivo "marca" no hace.
$tomado = $null
try {
    $tomado = [System.IO.File]::Open($candado, 'OpenOrCreate', 'ReadWrite', 'None')
} catch {
    Write-Host 'Ya hay un respaldo corriendo: este pedido no hace nada.'
    exit 20
}

try {
    try {
        $config = Read-Configuracion -Path $ConfigFile
    } catch {
        Write-Host "No se pudo leer la configuracion: $($_.Exception.Message)"
        exit 21
    }
    $pgDump = Join-Path $config['PG_BIN'] 'pg_dump.exe'
    $pgRestore = Join-Path $config['PG_BIN'] 'pg_restore.exe'
    $puerto = [int]$config['DB_PORT']
    $base = $config['DB_NAME']
    $usuario = $config['DB_USERNAME']

    # -SoloSiFaltaHoy: la tarea de red no repite lo que ya hizo el cierre de caja.
    if ($SoloSiFaltaHoy) {
        $ultimoDeHoy = $null
        if (Test-Path -LiteralPath $estado) {
            try {
                $leido = [System.IO.File]::ReadAllText($estado, $Utf8SinBom) | ConvertFrom-Json
                $ultimoDeHoy = $leido.ultimoRespaldoOk
            } catch { $ultimoDeHoy = $null }
        }
        if ($ultimoDeHoy -and ([datetime]$ultimoDeHoy).Date -eq $inicio.Date) {
            Write-Host "Hoy ya hubo un respaldo ($ultimoDeHoy): la tarea no hace nada."
            Write-Registro -Entrada @{
                momento = $inicio.ToString('o'); origen = $Origen; resultado = 'no-hacia-falta'
                detalle = "ya habia un respaldo de hoy: $ultimoDeHoy"
            } -CambiosDeEstado (New-EstadoDelIntento -Resultado 'no-hacia-falta' `
                                    -Detalle "ya habia un respaldo de hoy: $ultimoDeHoy")
            exit 11
        }
    }

    if (-not (Test-Path -LiteralPath $PgPassFile)) {
        Write-Host "AVISO: no esta $PgPassFile. Si la base pide contrasena, pg_dump no va a poder entrar."
    }

    # --- ?HAY ALGO PARA RESPALDAR? ---------------------------------------------------------------
    # Se le pregunta A LA BASE antes de respaldar, y no se deduce del respaldo despues. La
    # diferencia no es de estilo: una base recien creada (instalacion nueva, todavia sin Flyway) y
    # una base con datos cuyo respaldo salio vacio se ven IGUAL mirando solo el archivo, y son
    # cosas opuestas. La primera es normal; la segunda es una emergencia.
    $sonda = Get-ObjetosEnBase -PsqlPath (Join-Path $config['PG_BIN'] 'psql.exe') -Puerto $puerto -Base $base -Usuario $usuario
    if (-not $sonda.Sabe) {
        # No se pudo preguntar: NO se asume que este vacia. Se respalda igual y, si el respaldo sale
        # sin objetos, la verificacion lo rechaza.
        Write-Host "AVISO: no se pudo contar los objetos de la base ($($sonda.Detalle)). Se respalda igual."
    }
    elseif ($sonda.Objetos -eq 0) {
        Write-Host 'La base todavia no tiene ninguna tabla: no hay nada que respaldar.'
        Write-Registro -Entrada @{
            momento = $inicio.ToString('o'); origen = $Origen; resultado = 'nada-que-respaldar'
            objetosEnBase = 0
            detalle = 'la base existe pero no tiene ninguna tabla todavia; las crea sERPent la primera vez que arranca'
        } -CambiosDeEstado (New-EstadoDelIntento -Resultado 'nada-que-respaldar' `
                                -Detalle 'la base todavia no tiene tablas' -ObjetosEnBase 0)
        exit 12
    }

    # --- pg_dump, a un nombre PARCIAL ---------------------------------------------------------
    # Nunca se escribe directo sobre el nombre definitivo: un archivo a medias con nombre bueno
    # es peor que ninguno, porque parece un respaldo.
    $reloj = [System.Diagnostics.Stopwatch]::StartNew()
    $dump = Invoke-Programa -Ruta $pgDump -Argumentos @(
        '-w', '-h', 'localhost', '-p', "$puerto", '-U', $usuario, '-d', $base,
        '-Fc', '-Z', 'zstd:3', '-f', $archivoParcial)
    $reloj.Stop()

    if ($dump.Codigo -ne 0) {
        $motivo = Format-MotivoDeError -Salida $dump.Error
        Write-Host "pg_dump fallo con codigo $($dump.Codigo): $motivo"
        Remove-Item -LiteralPath $archivoParcial -Force -ErrorAction SilentlyContinue
        Write-Registro -Entrada @{
            momento = $inicio.ToString('o'); origen = $Origen; resultado = 'fallo'
            paso = 'pg_dump'; codigo = $dump.Codigo; detalle = $motivo
            objetosEnBase = (Format-ObjetosEnBase $sonda)
        } -CambiosDeEstado (New-EstadoDelIntento -Resultado 'fallo' -Detalle $motivo `
                                -ObjetosEnBase (Format-ObjetosEnBase $sonda))
        exit 22
    }

    # --- La verificacion, ANTES de darlo por bueno ---------------------------------------------
    # pg_restore --list lee el indice del archivo: tarda menos de un segundo y detecta un archivo
    # cortado o alterado (medido: un dump con bytes cambiados devuelve codigo 1).
    $verificacion = Invoke-Programa -Ruta $pgRestore -Argumentos @('--list', $archivoParcial)
    $entradas = @($verificacion.Salida -split "`r?`n" | Where-Object { $_ -and -not $_.StartsWith(';') }).Count
    $veredicto = Get-VeredictoDeVerificacion -CodigoPgRestore $verificacion.Codigo -Entradas $entradas `
                                             -ObjetosEnBase $(if ($sonda.Sabe) { $sonda.Objetos } else { $null })
    if (-not $veredicto.Ok) {
        $motivo = $veredicto.Motivo
        if ($verificacion.Error) { $motivo = $motivo + ': ' + (Format-MotivoDeError -Salida $verificacion.Error) }
        Write-Host "El respaldo NO paso la verificacion: $motivo"
        Write-Registro -Entrada @{
            momento = $inicio.ToString('o'); origen = $Origen; resultado = 'fallo'
            paso = 'verificacion'; codigo = $verificacion.Codigo; detalle = $motivo
            objetosEnBase = (Format-ObjetosEnBase $sonda)
            archivo = [System.IO.Path]::GetFileName($archivoParcial)
        } -CambiosDeEstado (New-EstadoDelIntento -Resultado 'fallo' -Detalle $motivo `
                                -ObjetosEnBase (Format-ObjetosEnBase $sonda))
        # El archivo se deja con nombre .parcial: no se borra, por si sirve para diagnosticar, y
        # no se poda nada.
        exit 23
    }

    Move-Item -LiteralPath $archivoParcial -Destination $archivoRespaldo -Force
    $tamanio = (Get-Item -LiteralPath $archivoRespaldo).Length
    $hash = Get-Sha256 -Path $archivoRespaldo

    # --- La poda, DESPUES de tener un respaldo nuevo y verificado -------------------------------
    $podados = @()
    $noPodados = @()
    foreach ($nombre in (Get-RespaldosAPodar -Nombres (Get-RespaldosEnDisco).Name -Hoy $inicio)) {
        try {
            Remove-Item -LiteralPath (Join-Path $CarpetaRespaldos $nombre) -Force -ErrorAction Stop
            $podados += $nombre
        } catch {
            $noPodados += "$nombre ($($_.Exception.Message))"
        }
    }
    $resultadoPoda = if ($noPodados.Count) { 'con problemas' } else { 'ok' }

    $entrada = @{
        momento    = $inicio.ToString('o')
        origen     = $Origen
        resultado  = 'ok'
        archivo    = [System.IO.Path]::GetFileName($archivoRespaldo)
        bytes      = $tamanio
        sha256     = $hash
        segundos   = [math]::Round($reloj.Elapsed.TotalSeconds, 1)
        entradasVerificadas = $entradas
        objetosEnBase = (Format-ObjetosEnBase $sonda)
        poda       = $resultadoPoda
        podados    = $podados
        noPodados  = $noPodados
    }
    # El bloque del intento entero, y encima lo del respaldo bueno, que es lo unico que sobrevive
    # a los intentos que fallan. "ultimoRespaldoPoda" lleva ese prefijo por eso: es del respaldo,
    # no del intento, y tiene que quedar aunque el de manana falle.
    $cambios = New-EstadoDelIntento -Resultado 'ok' -ObjetosEnBase (Format-ObjetosEnBase $sonda)
    $cambios['ultimoRespaldoOk']      = $inicio.ToString('o')
    $cambios['ultimoRespaldoArchivo'] = $entrada.archivo
    $cambios['ultimoRespaldoBytes']   = $tamanio
    $cambios['ultimoRespaldoPoda']    = $resultadoPoda
    try {
        Write-Registro -Entrada $entrada -CambiosDeEstado $cambios
    } catch {
        Write-Host "El respaldo salio bien pero no se pudo escribir el registro: $($_.Exception.Message)"
        exit 24
    }

    # --- La prueba PROFUNDA, una vez por mes -------------------------------------------------------
    #
    # La verificacion de todos los dias (pg_restore --list) lee el INDICE del respaldo. Una vez por
    # mes se lee y descomprime EL ARCHIVO ENTERO, con "pg_restore -f NUL": si el archivo esta
    # cortado o corrompido en el medio, esto lo encuentra y el indice no.
    #
    # POR QUE NO SE RESTAURA EN UNA BASE DESCARTABLE, que era el plan: lo agarro el banco. El
    # respaldo entra como serpent_app, el rol de la app, y ese rol NO PUEDE CREAR BASES ("se ha
    # denegado el permiso para crear la base de datos"). Darle CREATEDB para una prueba seria
    # agrandar los permisos del rol que usa la app todos los dias. La restauracion completa de
    # verdad queda como paso del runbook, que la hace un tecnico entrando como postgres.
    #
    # Medido: leer el archivo entero tarda centesimas con un respaldo chico, y un archivo con bytes
    # cambiados hace fallar a pg_restore (llego a devolver 0xC0000409, o sea que se cae). Cualquier
    # codigo distinto de 0 se toma como falla.
    #
    # Va aca y no en una tarea aparte ni en un planificador: este script ya corre todos los dias,
    # asi que no hace falta inventar otro mecanismo que pueda fallar en silencio.
    $ultimaPrueba = $null
    if (Test-Path -LiteralPath $estado) {
        try {
            $leido = [System.IO.File]::ReadAllText($estado, $Utf8SinBom) | ConvertFrom-Json
            $ultimaPrueba = $leido.ultimaPruebaDeRestauracion
        } catch { $ultimaPrueba = $null }
    }
    $tocaProbar = (-not $ultimaPrueba) -or (([datetime]$ultimaPrueba) -lt $inicio.AddDays(-30))
    if ($tocaProbar) {
        $relojPrueba = [System.Diagnostics.Stopwatch]::StartNew()
        $profunda = Invoke-Programa -Ruta $pgRestore -Argumentos @('-f', 'NUL', $archivoRespaldo)
        $relojPrueba.Stop()
        $pruebaOk = ($profunda.Codigo -eq 0)
        $detallePrueba = "pg_restore -f NUL salio con $($profunda.Codigo) en $([math]::Round($relojPrueba.Elapsed.TotalSeconds, 1)) s"
        if (-not $pruebaOk -and $profunda.Error) {
            $detallePrueba = $detallePrueba + ': ' + (Format-MotivoDeError -Salida $profunda.Error)
        }
        Write-Host "Prueba profunda del respaldo: $(if ($pruebaOk) { 'ok' } else { 'FALLO' }) ($detallePrueba)"
        Write-Registro -Entrada @{
            momento = (Get-Date).ToString('o'); origen = $Origen
            resultado = $(if ($pruebaOk) { 'prueba-profunda-ok' } else { 'prueba-profunda-fallo' })
            archivo = $entrada.archivo; detalle = $detallePrueba
        } -CambiosDeEstado @{
            ultimaPruebaDeRestauracion = (Get-Date).ToString('o')
            ultimaPruebaDeRestauracionOk = $pruebaOk
            ultimaPruebaDeRestauracionDetalle = $detallePrueba
        }
    }

    Write-Host "Respaldo listo: $($entrada.archivo), $([math]::Round($tamanio / 1MB, 2)) MB en $($entrada.segundos) s."
    Write-Host "Verificado: $entradas entradas. Poda: $resultadoPoda$(if ($podados.Count) { " ($($podados.Count) borrados)" })."
    if ($noPodados.Count) { Write-Host "AVISO: no se pudieron borrar $($noPodados.Count) respaldos viejos. El respaldo de hoy esta bien igual." }
    exit 0
}
finally {
    if ($tomado) { $tomado.Close(); Remove-Item -LiteralPath $candado -Force -ErrorAction SilentlyContinue }
}
