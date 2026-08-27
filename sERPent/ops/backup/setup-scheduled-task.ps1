<#
.SINOPSIS
    Registra la tarea programada de Windows para el respaldo diario. SE CORRE
    UNA SOLA VEZ, a mano, como administrador, en la PC del local. No es el
    script que corre todos los dias -- ese es backup-serpent-db.ps1.

.DECISION TOMADA: corre EXISTA O NO SESION INICIADA
    En un comercio la PC queda prendida y bloqueada, o la sesion se cierra
    sola -- un respaldo que solo corre con sesion abierta es un respaldo que
    un dia deja de correr sin que nadie lo note. Por eso este script pide la
    contraseña de Windows de la cuenta que va a correr la tarea (la guarda
    Windows, cifrada, no este archivo) y registra el tipo de logon "Password",
    que no depende de que haya una sesion interactiva.

.HALLAZGO IMPORTANTE, probado de verdad durante el desarrollo
    Una cuenta de Windows NUEVA, que nunca antes corrio nada como tarea
    programada, puede no tener el derecho "Iniciar sesion como tarea por
    lotes" (SeBatchLogonRight). Sin ese derecho la tarea se registra sin
    error, pero NUNCA EJECUTA NADA -- ni tira una excepcion visible, solo
    queda "Lista" para siempre sin correr. Se probo esto de punta a punta con
    una cuenta de prueba: sin el derecho, ni siquiera una tarea trivial
    (`echo hola`) corria; se lo otorgo explicitamente via la politica de
    seguridad local, y ahi si funciono.
    Windows normalmente otorga este derecho solo cuando se configura la tarea
    a traves de la interfaz grafica del Programador de tareas (con la
    contraseña tipeada ahi). Si DESPUES de correr este script la tarea sigue
    sin ejecutar (columna "Ultimo resultado" nunca cambia del valor inicial),
    revisar: Directiva de seguridad local > Directivas locales > Asignacion
    de derechos de usuario > "Iniciar sesion como tarea por lotes" -- la
    cuenta usada tiene que estar en esa lista. Si no esta, agregarla ahi
    a mano resuelve el problema.
#>

$TaskName   = "sERPent - Respaldo diario"
$ScriptPath = "D:\Backups\sERPent\Scripts\backup-serpent-db.ps1"   # AJUSTAR si se mueve
$Hora       = "22:00"

$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ScriptPath`""

$trigger = New-ScheduledTaskTrigger -Daily -At $Hora

# StartWhenAvailable: si la PC estaba apagada a las 22:00, corre apenas se
# prende (no espera al dia siguiente a las 22:00 de nuevo). Esto es lo que
# responde al punto 2 del pedido original.
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2) `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 15) `
    -DontStopIfGoingOnBatteries `
    -AllowStartIfOnBatteries

$cred = Get-Credential -Message "Usuario y contraseña de Windows de la cuenta que va a correr el respaldo (la de Joaquin, la que queda logueada en esta PC)"

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -User $cred.UserName `
    -Password $cred.GetNetworkCredential().Password `
    -RunLevel Limited `
    -Description "Respaldo diario de la base de sERPent (pg_dump) a disco local y carpeta sincronizada con la nube. Corre exista o no sesion iniciada."

Write-Output "Tarea '$TaskName' registrada. Proximo disparo: $Hora todos los dias, corra o no haya sesion iniciada."
Write-Output "Para probarla ahora mismo sin esperar: Start-ScheduledTask -TaskName '$TaskName'"
Write-Output ""
Write-Output "IMPORTANTE: revisar en unos segundos con"
Write-Output "  Get-ScheduledTaskInfo -TaskName '$TaskName'"
Write-Output "que 'LastTaskResult' sea 0 y 'LastRunTime' ya no sea 30/11/1999 (el valor de 'nunca corrio')."
Write-Output "Si se queda en ese estado, ver la seccion HALLAZGO IMPORTANTE arriba de este script."

<#
--- Alternativa, solo si la cuenta NO puede quedar con la contraseña guardada
(politica de la empresa, etc.): correr solo con sesion iniciada. Mas simple,
pero un respaldo que depende de que alguien tenga la sesion abierta es un
respaldo que un dia va a fallar sin que nadie lo note -- por eso no es la
opcion por defecto de este script.

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Respaldo diario de la base de sERPent (pg_dump) a disco local y carpeta sincronizada con la nube." `
    -RunLevel Highest
#>
