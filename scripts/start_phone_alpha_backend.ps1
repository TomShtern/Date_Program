<#
.SYNOPSIS
    One-command startup for phone-alpha backend LAN testing.

.DESCRIPTION
    Starts/checks local PostgreSQL, compiles if needed, builds the runtime classpath,
    detects the laptop LAN IP, starts the REST API server on 0.0.0.0:7070, verifies
    /api/health from localhost and LAN, and prints the Flutter base URL + variable names.

    The bind is always 0.0.0.0 with a mandatory shared secret. Never switch it to loopback behind
    a same-machine tunnel: a loopback bind disables the shared-secret check, and a tunnel
    connects from 127.0.0.1, so internet requests would pass the localhost guard.

    Press Ctrl+C to stop the REST server. PostgreSQL is left running.
    Run .\scripts/stop_local_postgres.ps1 to stop PostgreSQL.

.PARAMETER Port
    REST API server port. Default: 7070.

.PARAMETER SharedSecret
    LAN shared secret for non-loopback requests. Resolution order: -SharedSecret, then the
    DATING_APP_REST_SHARED_SECRET environment variable, then the file
    %LOCALAPPDATA%\DateProgram\rest-shared-secret.txt (create it once with
    scripts\new_rest_shared_secret.ps1). A secret must be supplied; none is built in, and the
    value is never printed.

.PARAMETER PublicUrl
    Public HTTPS base URL that phones use (for example https://<machine>.<tailnet>.ts.net).
    Sets DATING_APP_PHOTO_PUBLIC_BASE_URL for the server process so photo URLs in API responses
    start with https:// instead of the http:// scheme the server sees behind a TLS-terminating
    tunnel. Must be https:// with no path.

.PARAMETER ClientIpHeader
    Name of the header the local tunnel uses to pass the real client address. Sets
    DATING_APP_REST_CLIENT_IP_HEADER so the rate limiter charges each client separately. It is
    honored only for requests whose socket peer is loopback. Leave unset until the header has
    been verified (docs/guides/public-funnel-runbook.md); falls back to the existing
    DATING_APP_REST_CLIENT_IP_HEADER variable.

.PARAMETER LogDirectory
    When set, the server's stdout and stderr are written to backend.out.log and
    backend.err.log in this directory (recreated on every start) instead of the console.

.PARAMETER AllowedOrigins
    CORS allowed origins (comma-separated or multiple values). Falls back to
    DATING_APP_REST_ALLOWED_ORIGINS env var.

.PARAMETER HealthCheckTimeoutSeconds
    Seconds to wait for /api/health to respond after server start. Default: 15.
#>
param(
    [int]$Port = 7070,
    [string]$SharedSecret,
    [string[]]$AllowedOrigins = @(),
    [int]$HealthCheckTimeoutSeconds = 15,
    [string]$PublicUrl,
    [string]$ClientIpHeader,
    [string]$LogDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Set-Location (Split-Path -Parent $PSScriptRoot)

# ── Resolve effective settings ──────────────────────────────────────────
$secretFile = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DateProgram\rest-shared-secret.txt'

$effectiveSharedSecret = if ($PSBoundParameters.ContainsKey('SharedSecret')) {
    $SharedSecret
} elseif ($env:DATING_APP_REST_SHARED_SECRET) {
    $env:DATING_APP_REST_SHARED_SECRET
} elseif (Test-Path -LiteralPath $secretFile) {
    (Get-Content -LiteralPath $secretFile -Raw).Trim()
} else {
    $null
}

if ([string]::IsNullOrWhiteSpace($effectiveSharedSecret)) {
    throw "[CONFIG] No LAN shared secret found. Pass -SharedSecret, set DATING_APP_REST_SHARED_SECRET, or create the secret file once with scripts\new_rest_shared_secret.ps1 (expected at $secretFile)."
}

$effectivePublicUrl = $null
if (-not [string]::IsNullOrWhiteSpace($PublicUrl)) {
    $effectivePublicUrl = $PublicUrl.Trim().TrimEnd('/')
    if ($effectivePublicUrl -notmatch '^https://[A-Za-z0-9.-]+(:\d+)?$') {
        throw "[CONFIG] -PublicUrl must be an https:// base URL with no path, for example https://machine.tailnet.ts.net. Got: $effectivePublicUrl"
    }
}

$effectiveClientIpHeader = if (-not [string]::IsNullOrWhiteSpace($ClientIpHeader)) {
    $ClientIpHeader.Trim()
} elseif ($env:DATING_APP_REST_CLIENT_IP_HEADER) {
    $env:DATING_APP_REST_CLIENT_IP_HEADER
} else {
    $null
}

$effectiveAllowedOrigins = if ($AllowedOrigins.Count -gt 0) {
    $AllowedOrigins -join ','
} elseif ($env:DATING_APP_REST_ALLOWED_ORIGINS) {
    $env:DATING_APP_REST_ALLOWED_ORIGINS
} else {
    $null
}

# ── Prerequisites ───────────────────────────────────────────────────────
function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "[PREREQ] $Name is not on PATH."
    }
}

Assert-Command 'java'
Assert-Command 'mvn'

$checkScript = Join-Path $PSScriptRoot 'check_postgresql_runtime_env.ps1'
$startScript = Join-Path $PSScriptRoot 'start_local_postgres.ps1'

if (-not (Test-Path $checkScript)) {
    throw "[PREREQ] $checkScript not found."
}
if (-not (Test-Path $startScript)) {
    throw "[PREREQ] $startScript not found."
}

# ── Helper: detect LAN IP ───────────────────────────────────────────────
function Get-LanIpAddress {
    try {
        $ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object {
                $_.IPAddress -notlike '127.*' -and
                $_.IPAddress -notlike '169.254.*' -and
                $_.PrefixOrigin -ne 'WellKnown'
            } |
            Select-Object -ExpandProperty IPAddress -First 1
        if ($ip) { return $ip }
    } catch {}

    try {
        $output = & ipconfig 2>$null | Out-String
        $matches = [regex]::Matches($output, 'IPv4 Address[.\s]*:\s*([0-9.]+)')
        foreach ($m in $matches) {
            $candidate = $m.Groups[1].Value
            if ($candidate -notlike '127.*' -and $candidate -notlike '169.254.*') {
                return $candidate
            }
        }
    } catch {}

    return $null
}

# ── Helper: compile check ───────────────────────────────────────────────
function Test-CompileNeeded {
    $mainClassFile = 'target\classes\datingapp\app\api\RestApiServer.class'
    if (-not (Test-Path $mainClassFile)) {
        Write-Output '[BUILD] target\classes missing. Maven compile required.'
        return $true
    }

    $classFileTime = (Get-Item $mainClassFile).LastWriteTime
    $pomTime = (Get-Item 'pom.xml').LastWriteTime
    if ($pomTime -gt $classFileTime) {
        Write-Output '[BUILD] pom.xml is newer than compiled classes. Maven compile required.'
        return $true
    }

    $newestJava = Get-ChildItem -Path 'src\main\java' -Recurse -Filter '*.java' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($newestJava -and $newestJava.LastWriteTime -gt $classFileTime) {
        Write-Output "[BUILD] Source file '$($newestJava.Name)' is newer than compiled classes. Maven compile required."
        return $true
    }

    return $false
}

# ── Helper: build classpath ─────────────────────────────────────────────
function Build-RuntimeClasspath {
    $cpFile = 'target\runtime-classpath.txt'
    $needsBuild = $false
    if (-not (Test-Path $cpFile)) {
        $needsBuild = $true
    } else {
        $cpFileTime = (Get-Item $cpFile).LastWriteTime
        $pomTime = (Get-Item 'pom.xml').LastWriteTime
        if ($pomTime -gt $cpFileTime) {
            $needsBuild = $true
        }
    }

    if ($needsBuild) {
        Write-Output '[BUILD] Building runtime classpath...'
        & mvn -q dependency:build-classpath "-Dmdep.outputFile=$cpFile" "-Dmdep.pathSeparator=;" "-Dmdep.includeScope=runtime"
        if ($LASTEXITCODE -ne 0) {
            throw "[BUILD] Maven dependency:build-classpath failed with exit code $LASTEXITCODE."
        }
    }

    $runtimeCp = (Get-Content $cpFile -Raw).Trim()
    return "target\classes;$runtimeCp"
}

# ── Helper: health check with polling ───────────────────────────────────
function Test-HealthEndpoint {
    param(
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-WebRequest -Uri $Url -Method GET -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                return $true
            }
        } catch {
            if ($_.Exception -is [System.Management.Automation.PipelineStoppedException]) {
                throw
            }
            # Connection refused or timeout — keep polling
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

# ── 1. PostgreSQL preflight ─────────────────────────────────────────────
Write-Output '[POSTGRESQL] Running preflight check...'
& $checkScript
$preflightExit = $LASTEXITCODE
if ($preflightExit -ne 0) {
    Write-Output '[POSTGRESQL] Preflight failed. Starting local PostgreSQL...'
    & $startScript
    if ($LASTEXITCODE -ne 0) {
        throw "[POSTGRESQL] Failed to start local PostgreSQL (exit code $LASTEXITCODE)."
    }
    & $checkScript
    if ($LASTEXITCODE -ne 0) {
        throw '[POSTGRESQL] Preflight still failing after startup attempt.'
    }
}
Write-Output '[POSTGRESQL] Ready.'

# ── 2. Compile if needed ────────────────────────────────────────────────
if (Test-CompileNeeded) {
    Write-Output '[BUILD] Running mvn -q compile...'
    & mvn -q compile
    if ($LASTEXITCODE -ne 0) {
        throw "[BUILD] Maven compile failed with exit code $LASTEXITCODE."
    }
    Write-Output '[BUILD] Compile complete.'
}

# ── 3. Build runtime classpath ──────────────────────────────────────────
$cp = Build-RuntimeClasspath

# ── 4. Detect LAN IP ────────────────────────────────────────────────────
$lanIp = Get-LanIpAddress
if (-not $lanIp) {
    Write-Warning '[NETWORK] Could not detect LAN IP address automatically. LAN health check will be skipped.'
}

# ── 5. Prepare Java arguments ───────────────────────────────────────────
$javaArgs = @(
    '--enable-preview'
    '--enable-native-access=ALL-UNNAMED'
    '-cp'
    $cp
    'datingapp.app.api.RestApiServer'
    '--host=0.0.0.0'
    "--port=$Port"
)

if ($effectiveAllowedOrigins) {
    $javaArgs += "--allowed-origins=$effectiveAllowedOrigins"
}

# ── 6. Start REST server ────────────────────────────────────────────────
Write-Output "[REST] Starting REST API server on 0.0.0.0:$Port ..."
Write-Output '[REST] LAN shared-secret protection is configured.'
if ($effectivePublicUrl) {
    Write-Output "[REST] Photo URLs will use the public base URL $effectivePublicUrl."
}
if ($effectiveClientIpHeader) {
    Write-Output "[REST] Rate limiting keys on the $effectiveClientIpHeader header for loopback peers."
}

$startProcessArgs = @{
    FilePath     = 'java'
    ArgumentList = $javaArgs
    PassThru     = $true
    NoNewWindow  = $true
}
if ($LogDirectory) {
    New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
    $startProcessArgs['RedirectStandardOutput'] = Join-Path $LogDirectory 'backend.out.log'
    $startProcessArgs['RedirectStandardError'] = Join-Path $LogDirectory 'backend.err.log'
    Write-Output "[REST] Server output goes to $LogDirectory."
}

# Child processes inherit the environment at launch; set the values just for that moment.
$childEnvironment = @{
    DATING_APP_REST_SHARED_SECRET = $effectiveSharedSecret
}
if ($effectivePublicUrl) {
    $childEnvironment['DATING_APP_PHOTO_PUBLIC_BASE_URL'] = $effectivePublicUrl
}
if ($effectiveClientIpHeader) {
    $childEnvironment['DATING_APP_REST_CLIENT_IP_HEADER'] = $effectiveClientIpHeader
}
$previousEnvironment = @{}
foreach ($name in $childEnvironment.Keys) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    foreach ($name in $childEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $childEnvironment[$name], 'Process')
    }
    $proc = Start-Process @startProcessArgs
} finally {
    foreach ($name in $childEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
}

# ── 7. Verify health ────────────────────────────────────────────────────
$healthLocal = "http://localhost:$Port/api/health"
$healthLan = if ($lanIp) { "http://${lanIp}:$Port/api/health" } else { $null }

Write-Output "[VERIFY] Checking $healthLocal ..."
$localOk = Test-HealthEndpoint -Url $healthLocal -TimeoutSeconds $HealthCheckTimeoutSeconds
if (-not $localOk) {
    if ($proc -and !$proc.HasExited) {
        Stop-Process -InputObject $proc -Force -ErrorAction SilentlyContinue
    }
    throw "[VERIFY] Health check failed on localhost:$Port within ${HealthCheckTimeoutSeconds}s."
}
Write-Output '[VERIFY] localhost health OK (200).'

if ($healthLan) {
    Write-Output "[VERIFY] Checking $healthLan ..."
    $lanOk = Test-HealthEndpoint -Url $healthLan -TimeoutSeconds $HealthCheckTimeoutSeconds
    if (-not $lanOk) {
        Write-Warning "[VERIFY] LAN health check failed on $healthLan. This may be a Windows Firewall issue."
    } else {
        Write-Output '[VERIFY] LAN health OK (200).'
    }
}

if ($effectivePublicUrl) {
    $healthPublic = "$effectivePublicUrl/api/health"
    Write-Output "[VERIFY] Checking $healthPublic ..."
    $publicOk = Test-HealthEndpoint -Url $healthPublic -TimeoutSeconds $HealthCheckTimeoutSeconds
    if ($publicOk) {
        Write-Output '[VERIFY] Public HTTPS health OK (200).'
    } else {
        Write-Warning "[VERIFY] $healthPublic did not answer 200 yet. Check the tunnel (tailscale funnel status). Public DNS can take up to 10 minutes on a first setup."
    }
}

# ── 8. Print Flutter instructions ───────────────────────────────────────
Write-Output ''
Write-Output '========================================'
Write-Output '  Phone-alpha backend is ready!'
Write-Output '========================================'
Write-Output "  Local URL:    http://localhost:$Port"
if ($lanIp) {
    Write-Output "  LAN URL:      http://${lanIp}:$Port"
}
Write-Output ''
Write-Output '  Required header for non-health requests:'
Write-Output '    X-DatingApp-Shared-Secret: <your locally configured shared secret>'
Write-Output ''
Write-Output '  Configure Flutter locally with these values (do not commit the shared secret):'
if ($effectivePublicUrl) {
    Write-Output "    --dart-define=DATING_APP_API_BASE_URL=$effectivePublicUrl"
} elseif ($lanIp) {
    Write-Output "    --dart-define=DATING_APP_API_BASE_URL=http://$($lanIp):$Port"
}
Write-Output '    --dart-define=DATING_APP_SHARED_SECRET=<the same locally configured shared secret>'
Write-Output ''
Write-Output '  Press Ctrl+C to stop the REST server.'
Write-Output '  PostgreSQL will remain running.'
Write-Output '  Run .\scripts/stop_local_postgres.ps1 to stop PostgreSQL.'
Write-Output '========================================'

# ── 9. Block until server exits ─────────────────────────────────────────
try {
    while (-not $proc.HasExited) {
        Start-Sleep -Milliseconds 500
    }
} catch {
    Write-Output ''
    Write-Output '[REST] Server stopped by user (Ctrl+C).'
} finally {
    if ($proc -and !$proc.HasExited) {
        Stop-Process -InputObject $proc -Force -ErrorAction SilentlyContinue
    }
}

Write-Output '[REST] Server process exited.'
