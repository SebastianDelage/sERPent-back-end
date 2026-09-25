<#
.SINOPSIS
    Verifica que los respaldos copiados a un pendrive llegaron completos, y recien entonces anota
    la exportacion en estado.json.

.USO
    powershell -NoProfile -ExecutionPolicy Bypass -File marcar-exportacion.ps1 -Unidad F

.PARA QUE ESTA
    Hasta que exista la pantalla de exportacion (que es un hito aparte), la copia al pendrive es
    MANUAL: se copia la carpeta de respaldos con el Explorador. Este script es el que convierte esa
    copia en un dato: COMPRUEBA por SHA-256 que lo que quedo en el pendrive es lo mismo que hay en
    la PC, y si coincide estampa la fecha, cuantos archivos y QUE PENDRIVE fue (etiqueta y numero de
    serie del volumen, no la letra, que cambia de una vez a la otra).

    Sin esta marca, el aviso de "hace 15 dias que no exportas" no tendria ningun dato que mirar y
    avisaria siempre, que es peor que no avisar.

    NO ES "PROMETER QUE COPIE": si algo no coincide, NO estampa nada y dice que falta.

.CODIGOS DE SALIDA
     0  todo lo que hay en la PC esta en el pendrive, verificado, y quedo anotado
    30  la unidad no existe o no esta lista
    31  la unidad NO es extraible (se pide un pendrive a proposito)
    32  faltan respaldos en el pendrive, o alguno no coincide
    33  no se pudo escribir la marca en estado.json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Unidad,
    [string]$CarpetaRespaldos = 'C:\ProgramData\sERPent\respaldos',
    [string]$SubcarpetaEnElPendrive = 'sERPent-respaldos',

    # Para el caso de un disco externo que Windows informa como fijo. Hay que pedirlo a proposito.
    [switch]$AceptarUnidadNoExtraible
)

$ErrorActionPreference = 'Stop'
$Utf8SinBom = New-Object System.Text.UTF8Encoding $false
$estado = Join-Path $CarpetaRespaldos 'estado.json'
$historial = Join-Path $CarpetaRespaldos 'historial.jsonl'
$letra = $Unidad.TrimEnd(':', '\')
$raiz = "${letra}:\"
# Con cadenas y no con Join-Path: si la unidad no existe, Join-Path TIRA ERROR antes de que este
# script pueda contestar "la unidad no existe", y el codigo de salida termina siendo otro.
$destino = $raiz + $SubcarpetaEnElPendrive
$indiceEnElPendrive = $destino + '\sERPent-respaldos.json'

# --- La unidad ------------------------------------------------------------------------------------
$unidadInfo = $null
try { $unidadInfo = New-Object System.IO.DriveInfo $raiz } catch { }
if (-not $unidadInfo -or -not $unidadInfo.IsReady) {
    Write-Host "La unidad $raiz no existe o no esta lista. .Esta conectado el pendrive?"
    exit 30
}
$volumen = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID = '${letra}:'" -ErrorAction SilentlyContinue
$esExtraible = ($unidadInfo.DriveType -eq 'Removable') -or ($volumen -and $volumen.DriveType -eq 2)
Write-Host "Unidad $raiz : tipo $($unidadInfo.DriveType), formato $($unidadInfo.DriveFormat), etiqueta '$($unidadInfo.VolumeLabel)', libre $([math]::Round($unidadInfo.AvailableFreeSpace / 1MB)) MB"
if (-not $esExtraible -and -not $AceptarUnidadNoExtraible) {
    Write-Host "La unidad $raiz NO figura como extraible. Si es un disco externo y sabes lo que haces, volve a correr con -AceptarUnidadNoExtraible."
    exit 31
}

# FAT32 no acepta archivos de mas de 4 GB. Hoy un respaldo pesa 4,5 MB y el limite queda lejos, pero
# el dia que alguien exporte algo grande tiene que enterarse ANTES y no a mitad de camino.
$limiteFat32 = 4GB - 1
$enLaPc = @(Get-ChildItem -LiteralPath $CarpetaRespaldos -Filter '*.dump' -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^serpent_db_\d{4}-\d{2}-\d{2}_\d{6}\.dump$' } | Sort-Object Name)
if ($enLaPc.Count -eq 0) {
    Write-Host "No hay ningun respaldo en $CarpetaRespaldos. .Es la carpeta correcta?"
    exit 32
}
if ($unidadInfo.DriveFormat -eq 'FAT32') {
    $grandes = @($enLaPc | Where-Object { $_.Length -gt $limiteFat32 })
    if ($grandes.Count) {
        Write-Host "El pendrive esta en FAT32, que no acepta archivos de mas de 4 GB, y hay $($grandes.Count) respaldo(s) mas grandes:"
        $grandes | ForEach-Object { Write-Host "    $($_.Name)  $([math]::Round($_.Length / 1GB, 2)) GB" }
        Write-Host 'Formatealo en NTFS o exFAT, o copia esos respaldos a otro lado.'
        exit 32
    }
}

# --- La comprobacion, que es el punto de este script ----------------------------------------------
#
# LA UNICA VERDAD ES LEER LO QUE QUEDO. Medido en la maquina del dueno: al sacar el pendrive a mitad
# de una copia, Windows habia escrito 11 archivos completos aunque el 11 "fallo" (escribe en cache);
# y al reves, un "ok" puede ser cache que todavia no bajo al pendrive. Por eso no se confia en lo
# que dijo el Explorador: se leen los archivos del pendrive y se comparan por SHA-256.
$hashPorNombre = @{}
foreach ($linea in @(if (Test-Path -LiteralPath $historial) { [System.IO.File]::ReadAllLines($historial, $Utf8SinBom) } else { @() })) {
    if (-not $linea.Trim()) { continue }
    try {
        $e = $linea | ConvertFrom-Json
        if ($e.archivo -and $e.sha256) { $hashPorNombre[$e.archivo] = $e.sha256 }
    } catch { }
}

$faltan = @()
$distintos = @()
$verificados = @()
foreach ($archivo in $enLaPc) {
    $copia = Join-Path $destino $archivo.Name
    if (-not (Test-Path -LiteralPath $copia)) { $faltan += $archivo.Name; continue }
    $esperado = $hashPorNombre[$archivo.Name]
    if (-not $esperado) { $esperado = (Get-FileHash -LiteralPath $archivo.FullName -Algorithm SHA256).Hash }
    $obtenido = (Get-FileHash -LiteralPath $copia -Algorithm SHA256).Hash
    if ($obtenido -eq $esperado) { $verificados += $archivo.Name }
    else { $distintos += "$($archivo.Name) (en la PC $($esperado.Substring(0,12))..., en el pendrive $($obtenido.Substring(0,12))...)" }
}

Write-Host ''
Write-Host "Respaldos en la PC:        $($enLaPc.Count)"
Write-Host "Verificados en el pendrive: $($verificados.Count)"
if ($faltan.Count) {
    Write-Host "FALTAN en el pendrive:      $($faltan.Count)"
    $faltan | Select-Object -First 10 | ForEach-Object { Write-Host "    $_" }
}
if ($distintos.Count) {
    Write-Host "NO COINCIDEN:               $($distintos.Count)"
    $distintos | ForEach-Object { Write-Host "    $_" }
}
if ($faltan.Count -or $distintos.Count) {
    Write-Host ''
    Write-Host 'NO se anoto la exportacion: volve a copiar la carpeta entera y corre esto de nuevo.'
    Write-Host "Copia de: $CarpetaRespaldos    a: $destino"
    exit 32
}

# --- El indice que queda EN EL PENDRIVE -----------------------------------------------------------
# Para que el pendrive se explique solo si la PC se pierde entera.
$indice = @{
    exportado = (Get-Date).ToString('o')
    equipo    = $env:COMPUTERNAME
    archivos  = @($verificados | ForEach-Object { @{ archivo = $_; sha256 = $hashPorNombre[$_] } })
}
try {
    [System.IO.File]::WriteAllText($indiceEnElPendrive, ($indice | ConvertTo-Json -Depth 5), $Utf8SinBom)
} catch {
    Write-Host "AVISO: no se pudo escribir el indice en el pendrive: $($_.Exception.Message)"
}

# --- La marca en estado.json ----------------------------------------------------------------------
try {
    $actual = @{}
    if (Test-Path -LiteralPath $estado) {
        $leido = [System.IO.File]::ReadAllText($estado, $Utf8SinBom) | ConvertFrom-Json
        foreach ($p in $leido.PSObject.Properties) { $actual[$p.Name] = $p.Value }
    }
    $actual['ultimaExportacion'] = (Get-Date).ToString('o')
    $actual['ultimaExportacionArchivos'] = $verificados.Count
    $actual['ultimaExportacionVolumen'] = "$($unidadInfo.VolumeLabel) (serie $($volumen.VolumeSerialNumber), $($unidadInfo.DriveFormat))"
    $temporal = "$estado.nuevo"
    [System.IO.File]::WriteAllText($temporal, ($actual | ConvertTo-Json -Depth 6), $Utf8SinBom)
    Move-Item -LiteralPath $temporal -Destination $estado -Force

    $entrada = @{
        momento = (Get-Date).ToString('o'); origen = 'exportacion-manual'; resultado = 'ok'
        archivos = $verificados.Count
        volumen = $actual['ultimaExportacionVolumen']
    }
    [System.IO.File]::AppendAllText($historial, (($entrada | ConvertTo-Json -Compress -Depth 5) + "`n"), $Utf8SinBom)
} catch {
    Write-Host "Los archivos estan bien copiados, pero no se pudo anotar la exportacion: $($_.Exception.Message)"
    exit 33
}

Write-Host ''
Write-Host "Exportacion anotada: $($verificados.Count) respaldos verificados en '$($unidadInfo.VolumeLabel)'."
Write-Host 'Acordate de EXPULSAR el pendrive desde Windows antes de sacarlo.'
exit 0
