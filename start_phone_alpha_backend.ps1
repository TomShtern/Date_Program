<#
.SYNOPSIS
    One-command startup for phone-alpha backend LAN testing.

.DESCRIPTION
    Starts/checks local PostgreSQL, compiles if needed, builds the runtime classpath,
    detects the laptop LAN IP, starts the REST API server on 0.0.0.0:7070, verifies
    /api/health from localhost and LAN, and prints the Flutter base URL + headers.

    Press Ctrl+C to stop the REST server. PostgreSQL is left running.
    Run .\stop_local_postgres.ps1 to stop PostgreSQL.

.PARAMETER Port
    REST API server port. Default: 7070.

.PARAMETER SharedSecret
    LAN shared secret for non-loopback requests. Overrides DATING_APP_REST_SHARED_SECRET
    env var if explicitly provided. Default: lan-dev-secret.

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
    [int]$HealthCheckTimeoutSeconds = 15
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Set-Location $PSScriptRoot

# ── Resolve effective settings ──────────────────────────────────────────
$effectiveSharedSecret = if ($PSBoundParameters.ContainsKey('SharedSecret')) {
    $SharedSecret
} elseif ($env:DATING_APP_REST_SHARED_SECRET) {
    $env:DATING_APP_REST_SHARED_SECRET
} else {
    'lan-dev-secret'
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
    "--shared-secret=$effectiveSharedSecret"
)

if ($effectiveAllowedOrigins) {
    $javaArgs += "--allowed-origins=$effectiveAllowedOrigins"
}

# ── 6. Start REST server ────────────────────────────────────────────────
Write-Output "[REST] Starting REST API server on 0.0.0.0:$Port ..."
Write-Output "[REST] Shared secret: $effectiveSharedSecret"

$proc = Start-Process -FilePath 'java' -ArgumentList $javaArgs -PassThru -NoNewWindow

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
Write-Output "    X-DatingApp-Shared-Secret: $effectiveSharedSecret"
Write-Output ''
if ($lanIp) {
    Write-Output '  Flutter dart-define example:'
    Write-Output "    --dart-define=API_BASE_URL=http://$($lanIp):$Port"
    Write-Output "    --dart-define=API_SHARED_SECRET=$effectiveSharedSecret"
    Write-Output ''
}
Write-Output '  Press Ctrl+C to stop the REST server.'
Write-Output '  PostgreSQL will remain running.'
Write-Output '  Run .\stop_local_postgres.ps1 to stop PostgreSQL.'
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
