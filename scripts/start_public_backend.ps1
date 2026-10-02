<#
.SYNOPSIS
    The one start command for the public (Tailscale Funnel) backend. Also the target of the
    auto-start scheduled task.

.DESCRIPTION
    1. Starts local PostgreSQL by running scripts\start_local_postgres.ps1 directly, then proves
       the port is accepting connections. (The preflight in start_phone_alpha_backend.ps1 can
       report ready while PostgreSQL is not listening, so it is not trusted on its own.)
    2. Applies the Tailscale Funnel configuration (idempotent) via setup_tailscale_funnel.ps1,
       retrying while the Tailscale service finishes coming online after a boot.
    3. Runs start_phone_alpha_backend.ps1 with -PublicUrl, bound to 0.0.0.0 with the shared
       secret. This blocks for as long as the server runs.

    A transcript and the server output are kept under %LOCALAPPDATA%\DateProgram\logs. No secret
    is printed or logged.

    Exits non-zero when the server stops, so the scheduled task's restart policy brings it back.

.PARAMETER PublicUrl
    https://<machine>.<tailnet>.ts.net - the URL compiled into the app.

.PARAMETER ClientIpHeader
    Header carrying the real client address from the tunnel. Leave unset until it has been
    verified with scripts\probe_funnel_headers.ps1 (runbook, section B).

.PARAMETER Port
    REST API port. Default: 7070.

.PARAMETER PostgresPort
    Local PostgreSQL port. Default: 55432 (the repository default).
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$PublicUrl,
    [string]$ClientIpHeader,
    [int]$Port = 7070,
    [int]$PostgresPort = 55432,
    [int]$FunnelAttempts = 10,
    [int]$FunnelRetrySeconds = 15
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Set-Location (Split-Path -Parent $PSScriptRoot)

$logDirectory = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DateProgram\logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
Get-ChildItem -LiteralPath $logDirectory -Filter 'boot-*.log' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-14) } |
    Remove-Item -Force -ErrorAction SilentlyContinue
$transcriptStarted = $false
try {
    Start-Transcript -Path (Join-Path $logDirectory ("boot-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))) | Out-Null
    $transcriptStarted = $true
} catch {
    Write-Warning "Could not start a transcript: $($_.Exception.Message)"
}

function Test-TcpPort {
    param([int]$TargetPort)

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        return $client.ConnectAsync('localhost', $TargetPort).Wait(2000)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Stop-StaleBackend {
    param([int]$ListenPort)

    # A stopped scheduled task can leave its java child behind and holding the port.
    # Only a process whose command line is this REST server is touched.
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $ListenPort -ErrorAction SilentlyContinue
    foreach ($listener in @($listeners)) {
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)" -ErrorAction SilentlyContinue
        if ($details -and $details.CommandLine -match 'datingapp\.app\.api\.RestApiServer') {
            Write-Output "[CLEANUP] Stopping stale REST server process $($listener.OwningProcess)."
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
        } elseif ($details) {
            throw "[CLEANUP] Port $ListenPort is held by $($details.Name) (pid $($listener.OwningProcess)), which is not this REST server."
        }
    }
}

$global:LASTEXITCODE = 0
$exitCode = 1
try {
    # ── 1. PostgreSQL ───────────────────────────────────────────────────
    Write-Output '[POSTGRESQL] Starting local PostgreSQL ...'
    & (Join-Path $PSScriptRoot 'start_local_postgres.ps1') -Port $PostgresPort
    if ($LASTEXITCODE -ne 0) {
        throw "[POSTGRESQL] start_local_postgres.ps1 failed with exit code $LASTEXITCODE."
    }
    if (-not (Test-TcpPort -TargetPort $PostgresPort)) {
        throw "[POSTGRESQL] Nothing is listening on localhost:$PostgresPort after startup."
    }
    Write-Output "[POSTGRESQL] Listening on localhost:$PostgresPort."

    # ── 2. Tailscale Funnel ─────────────────────────────────────────────
    $funnelOk = $false
    for ($attempt = 1; $attempt -le $FunnelAttempts -and -not $funnelOk; $attempt++) {
        try {
            & (Join-Path $PSScriptRoot 'setup_tailscale_funnel.ps1') -Port $Port -PublicUrl $PublicUrl
            $funnelOk = $true
        } catch {
            Write-Warning "[FUNNEL] Attempt $attempt of $FunnelAttempts failed: $($_.Exception.Message)"
            if ($attempt -lt $FunnelAttempts) {
                Start-Sleep -Seconds $FunnelRetrySeconds
            }
        }
    }
    if (-not $funnelOk) {
        # The backend is still worth running for LAN use; the public URL stays down until this is fixed.
        Write-Warning '[FUNNEL] Funnel is NOT confirmed. Starting the backend anyway. Fix Tailscale and re-run this script.'
    }

    # ── 3. Backend (blocks while the server runs) ───────────────────────
    Stop-StaleBackend -ListenPort $Port
    $backendArgs = @{
        Port         = $Port
        PublicUrl    = $PublicUrl
        LogDirectory = $logDirectory
    }
    if ($ClientIpHeader) {
        $backendArgs['ClientIpHeader'] = $ClientIpHeader
    }
    & (Join-Path $PSScriptRoot 'start_phone_alpha_backend.ps1') @backendArgs
    Write-Warning '[REST] The backend stopped.'
} catch {
    Write-Output "[ERROR] $($_.Exception.Message)"
} finally {
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
}

# Reaching this point always means the server is not running; report failure so the task restarts.
exit $exitCode
