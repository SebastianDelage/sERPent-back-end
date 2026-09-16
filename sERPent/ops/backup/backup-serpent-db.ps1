<#
.SINOPSIS
    Respaldo diario de sERPent (Postgres) a dos destinos: uno local rápido para
    restaurar, y uno "de espera" para que un cliente de sincronización (OneDrive,
    Google Drive, etc.) lo suba a la nube cuando haya internet.

.CONTEXTO / DECISIONES
    - Formato -Fc (custom, comprimido): permite pg_restore con -j (paralelo) y
      restauración selectiva de tablas si algún día hace falta. El formato plano
      (-Fp, SQL de texto) es más lento de restaurar y no admite eso; para "restaurar
      rápido" el custom format es la elección correcta.
    - Las credenciales de Postgres NO están en este archivo. La contraseña viene de
      pgpass.conf (mecanismo estándar de libpq en Windows), que ahora escribe el instalador
      en el perfil de la cuenta que corre la tarea programada. El resto —dónde está
      PostgreSQL, en qué puerto, con qué usuario y contra qué base— sale del mismo
      serpent.properties que lee el backend.
    - Se conecta como serpent_app, el rol de la aplicación, NO como el superusuario postgres.
      serpent_app puede dumpear serpent_db, así que hay un solo secreto en juego en vez de dos
      que pueden desincronizarse.
    - Las dos carpetas de destino tienen rotación DISTINTA a propósito (ver abajo):
      la local es agresiva porque el disco es limitado y "restaurar rápido" no
      necesita historia larga; la de la nube es floja porque no hay forma de saber
      desde este script si OneDrive ya subió un archivo, y borrar algo que todavía
      no subió sería perderlo para siempre. Ver PLAN.md, punto 3.
    - $CloudStagingDir NO se resuelve solo vía $env:OneDrive: esa variable
      apunta a la cuenta de OneDrive de la PC donde se ESCRIBIÓ este archivo,
      que durante el desarrollo fue una PC distinta a la del local. En la PC
      del local, OneDrive hay que instalarlo y loguearlo con una cuenta de
      Joaquin (no del negocio) — ver INSTALL-RUNBOOK.md, paso 2. La ruta de
      abajo es un valor de ejemplo que HAY que ajustar a esa instalación real.
#>

# ============================== CONFIGURACIÓN ==============================
# Lo que puede cambiar según la PC vive acá arriba, y lo que depende de CÓMO quedó instalado
# PostgreSQL se lee del archivo que escribió el instalador.

# El archivo de configuración de la máquina, el mismo que lee el backend.
$ConfigFile = "C:\ProgramData\sERPent\serpent.properties"

# Disco DISTINTO al de los datos de Postgres (que en esta PC vive en C:).
# AJUSTAR a la letra de unidad real del disco/pendrive del local.
$LocalBackupDir = "D:\Backups\sERPent\Diario"

# Carpeta dentro de la sincronizada por el cliente de nube. AJUSTAR SIEMPRE a
# la ruta real de OneDrive (o Google Drive) en la PC del local — no hay forma
# de resolver esto solo, porque depende de qué cuenta se loguea ahí. Ver
# INSTALL-RUNBOOK.md para cómo instalar OneDrive con la cuenta de Joaquin y
# moverlo a D: (por defecto OneDrive se instala dentro de C:\Users\<usuario>,
# y en esta PC C: tiene poco espacio libre — ver punto 3 del runbook).
# Ejemplo una vez instalado y movido: "D:\OneDrive\sERPent-Backups"
$CloudStagingDir = "D:\OneDrive\sERPent-Backups"

$LogFile        = "D:\Backups\sERPent\backup-log.csv"
$StatusFile     = "D:\Backups\sERPent\ESTADO-ULTIMO-RESPALDO.txt"

# Retención LOCAL: 14 diarios + el primero de cada mes durante 12 meses.
$LocalDailyKeepDays   = 14
$LocalMonthlyKeepMonths = 12

# Retención NUBE: 90 días parejos, sin poda por antigüedad de mes. Generoso a
# propósito — ver PLAN.md, punto 3, sobre por qué NO conviene achicar esto.
$CloudKeepDays = 90

# Piso de sanidad: un dump de este esquema no debería pesar menos que esto.
# Si pesa menos, algo salió mal aunque pg_dump haya devuelto código 0.
$MinExpectedBytes = 10KB

# =============================== EJECUCIÓN ==================================

$ErrorActionPreference = "Stop"

<#
    Lee el .properties que escribió el instalador.

    NADA DE ESTO SE PUEDE HARDCODEAR, y ya se pagó por hacerlo: este script decía
    "C:\Program Files\PostgreSQL\17\bin", y la máquina de desarrollo tiene la 18 — o sea que
    el respaldo NO corría ahí y nadie se había enterado, porque un script programado que
    falla no le avisa a nadie más que a su propio archivo de estado.

    El puerto tampoco: el instalador crea una instancia propia y elige 5432 si está libre, o
    el primero libre desde 5433 si no. En una PC que no es nueva, el 5432 puede estar tomado.
#>
function Read-InstallSettings {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "No se encontro el archivo de configuracion '$Path'. Lo escribe el instalador de sERPent; sin el, este script no sabe donde esta PostgreSQL ni con que usuario entrar."
    }

    $values = @{}
    foreach ($line in Get-Content -Path $Path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $values[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }

    foreach ($key in @('PG_BIN', 'DB_PORT', 'DB_NAME', 'DB_USERNAME')) {
        if (-not $values.ContainsKey($key)) {
            throw "Al archivo '$Path' le falta '$key'. Volve a correr provision-database.ps1, que lo completa."
        }
    }
    return $values
}

$settings = Read-InstallSettings -Path $ConfigFile

$PgBin      = $settings['PG_BIN']
$PgHost     = "localhost"
$PgPort     = [int]$settings['DB_PORT']
$PgDatabase = $settings['DB_NAME']

# EL ROL DE LA APLICACIÓN, NO EL SUPERUSUARIO. serpent_app puede dumpear serpent_db, así que
# el respaldo no necesita la contraseña de postgres: queda un solo secreto, el mismo que usa
# la aplicación, y ya no hay dos credenciales que puedan desincronizarse.
$PgUser     = $settings['DB_USERNAME']
$start = Get-Date
$timestamp = $start.ToString("yyyy-MM-dd_HHmmss")
$fileName = "serpent_db_$timestamp.dump"

New-Item -ItemType Directory -Force -Path $LocalBackupDir | Out-Null
New-Item -ItemType Directory -Force -Path $CloudStagingDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $LogFile) | Out-Null

$localDumpPath = Join-Path $LocalBackupDir $fileName

function Write-LogRow {
    param([string]$Result, [string]$Detail, [long]$SizeBytes = 0)
    $row = [PSCustomObject]@{
        Fecha      = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        Resultado  = $Result
        Archivo    = $fileName
        TamanoMB   = if ($SizeBytes -gt 0) { [math]::Round($SizeBytes / 1MB, 2) } else { 0 }
        DuracionSeg = [math]::Round(((Get-Date) - $start).TotalSeconds, 1)
        Detalle    = $Detail
    }
    # Export-Csv -Append no vuelve a escribir el encabezado si el archivo ya
    # existe (se probó: el smoke test generó tres corridas seguidas y el CSV
    # quedó con un solo encabezado).
    $row | Export-Csv -Path $LogFile -Append -NoTypeInformation -Encoding UTF8
}

function Write-StatusFile {
    param([string]$Result, [string]$Detail, [long]$SizeBytes = 0)
    $cloudPending = (Get-ChildItem -Path $CloudStagingDir -Filter "*.dump" -ErrorAction SilentlyContinue).Count
    $sizeMB = if ($SizeBytes -gt 0) { [math]::Round($SizeBytes / 1MB, 1) } else { 0 }
    $lines = @(
        "=== ESTADO DEL RESPALDO DE sERPent ==="
        ""
        "Ultimo intento: $((Get-Date).ToString('dd/MM/yyyy HH:mm'))"
        "Resultado:      $Result"
        "Tamano:         $sizeMB MB"
        ""
        "Respaldos esperando subir a la nube: $cloudPending"
        "(si este numero crece dia tras dia, hace mucho que no hay internet o"
        " el cliente de sincronizacion dejo de funcionar - revisar el icono"
        " de OneDrive/Drive en la barra de tareas)"
        ""
        "Detalle: $Detail"
    )
    $lines | Out-File -FilePath $StatusFile -Encoding UTF8
}

try {
    # --- 1) Dump ---
    $pgDumpExe = Join-Path $PgBin "pg_dump.exe"
    & $pgDumpExe -h $PgHost -p $PgPort -U $PgUser -d $PgDatabase -Fc -f $localDumpPath
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump devolvio codigo $LASTEXITCODE"
    }

    $size = (Get-Item $localDumpPath).Length
    if ($size -lt $MinExpectedBytes) {
        throw "el dump peso $size bytes, menos del piso esperado ($MinExpectedBytes). Puede estar truncado."
    }

    # --- 2) Copiar a la carpeta de espera para la nube ---
    Copy-Item -Path $localDumpPath -Destination (Join-Path $CloudStagingDir $fileName) -Force

    # --- 3) Rotacion LOCAL: 14 diarios + 1ro de cada mes durante 12 meses ---
    $localCutoff = $start.AddDays(-$LocalDailyKeepDays)
    $monthlyCutoff = $start.AddMonths(-$LocalMonthlyKeepMonths)
    Get-ChildItem -Path $LocalBackupDir -Filter "serpent_db_*.dump" | ForEach-Object {
        if ($_.LastWriteTime -lt $localCutoff) {
            $isMonthlyAnchor = ($_.LastWriteTime.Day -eq 1) -and ($_.LastWriteTime -ge $monthlyCutoff)
            if (-not $isMonthlyAnchor) {
                Remove-Item $_.FullName -Force
            }
        }
    }

    # --- 4) Rotacion NUBE: 90 dias parejos ---
    $cloudCutoff = $start.AddDays(-$CloudKeepDays)
    Get-ChildItem -Path $CloudStagingDir -Filter "serpent_db_*.dump" | Where-Object {
        $_.LastWriteTime -lt $cloudCutoff
    } | Remove-Item -Force

    # --- 5) Registro ---
    Write-LogRow -Result "OK" -Detail "respaldo y copia a la nube completados" -SizeBytes $size
    Write-StatusFile -Result "OK" -Detail "respaldo completado sin errores" -SizeBytes $size

    exit 0
}
catch {
    $msg = $_.Exception.Message
    Write-LogRow -Result "FALLO" -Detail $msg
    Write-StatusFile -Result "FALLO - $msg" -Detail $msg
    exit 1
}
