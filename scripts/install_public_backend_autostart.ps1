<#
.SYNOPSIS
    Registers (or removes) the Windows scheduled task that runs scripts\start_public_backend.ps1.

.DESCRIPTION
    Default: the task starts when the current user logs on. That is the conservative choice - it
    runs with the user's profile, environment and PATH (java, mvn, pg_ctl), so the behaviour
    matches running the start command by hand.

    -AtBoot: the task starts when Windows boots, before anyone signs in (principal logon type
    S4U, which stores no password). Needs an elevated PowerShell. S4U tasks may not see the
    user's full environment, so verify it with the reboot test in
    docs/guides/public-funnel-runbook.md before relying on it.

    The task restarts on failure (3 times, 1 minute apart), has no execution time limit and
    keeps running on battery. Re-running the script replaces the task.

.PARAMETER PublicUrl
    https://<machine>.<tailnet>.ts.net - the URL compiled into the app.

.PARAMETER ClientIpHeader
    Optional; passed through to start_public_backend.ps1 once the header has been verified.

.PARAMETER TaskName
    Scheduled task name. Default: 'DateProgram Public Backend'.

.PARAMETER AtBoot
    Trigger at system startup instead of user logon.

.PARAMETER StartNow
    Start the task right after registering it.

.PARAMETER Remove
    Unregister the task and exit.
#>
param(
    [string]$PublicUrl,
    [string]$ClientIpHeader,
    [string]$TaskName = 'DateProgram Public Backend',
    [switch]$AtBoot,
    [switch]$StartNow,
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($Remove) {
    if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
        Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Output "[TASK] Removed '$TaskName'."
    } else {
        Write-Output "[TASK] '$TaskName' does not exist."
    }
    return
}

if ([string]::IsNullOrWhiteSpace($PublicUrl) -or $PublicUrl.Trim().TrimEnd('/') -notmatch '^https://[A-Za-z0-9.-]+\.ts\.net$') {
    throw '[CONFIG] -PublicUrl is required and must look like https://<machine>.<tailnet>.ts.net (no port, no path).'
}
$PublicUrl = $PublicUrl.Trim().TrimEnd('/')

$wrapper = Join-Path $PSScriptRoot 'start_public_backend.ps1'
if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "[PREREQ] $wrapper not found."
}
$repoRoot = Split-Path -Parent $PSScriptRoot

$shell = Get-Command 'pwsh' -ErrorAction SilentlyContinue
if (-not $shell) {
    $shell = Get-Command 'powershell.exe' -ErrorAction Stop
}

$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$wrapper`" -PublicUrl `"$PublicUrl`""
if (-not [string]::IsNullOrWhiteSpace($ClientIpHeader)) {
    if ($ClientIpHeader -notmatch '^[A-Za-z0-9-]{1,64}$') {
        throw "[CONFIG] -ClientIpHeader must be a plain header name. Got: $ClientIpHeader"
    }
    $arguments += " -ClientIpHeader `"$ClientIpHeader`""
}

$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute $shell.Source -Argument $arguments -WorkingDirectory $repoRoot
if ($AtBoot) {
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType S4U -RunLevel Limited
} else {
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
    $principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
}
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
$mode = if ($AtBoot) { 'at system startup (S4U)' } else { 'at logon of the current user' }
Write-Output "[TASK] Registered '$TaskName' to run $mode."
Write-Output "[TASK] Action: $($shell.Source) $arguments"
Write-Output "[TASK] Logs: $([Environment]::GetFolderPath('LocalApplicationData'))\DateProgram\logs"

if ($StartNow) {
    Start-ScheduledTask -TaskName $TaskName
    Write-Output "[TASK] Started '$TaskName'."
}
