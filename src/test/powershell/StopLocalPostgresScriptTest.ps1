Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'stop_local_postgres.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find stop_local_postgres.ps1 at $scriptUnderTest"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("stop-local-postgres-test-" + [System.Guid]::NewGuid())
$baseDir = Join-Path $tempRoot 'postgres-root'
$dataDir = Join-Path $baseDir 'data'
$pgVersionFile = Join-Path $dataDir 'PG_VERSION'
$pgCtlCountFile = Join-Path $tempRoot 'pg-ctl-count.txt'
$originalPath = $env:PATH

New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
Set-Content -Path $pgVersionFile -Value '18'
Set-Content -Path $pgCtlCountFile -Value '0'

Set-Content -Path (Join-Path $tempRoot 'pg_ctl.cmd') -Value @"
@echo off
set COUNT_FILE=$pgCtlCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
exit /b 1
"@

try {
    $env:PATH = "$tempRoot;$originalPath"

    $output = & pwsh -NoProfile -ExecutionPolicy Bypass -File $scriptUnderTest -BaseDir $baseDir 2>&1 | Out-String

    if ($LASTEXITCODE -ne 0) {
        throw "Expected stop_local_postgres.ps1 to exit with code 0 when PostgreSQL is already stopped, but observed $LASTEXITCODE."
    }

    if ($output -notmatch 'Local PostgreSQL is not running\.') {
        throw 'Expected stop_local_postgres.ps1 to report that PostgreSQL is not running.'
    }

    if ([int]((Get-Content -Path $pgCtlCountFile -Raw).Trim()) -ne 1) {
        throw 'Expected stop_local_postgres.ps1 to check status exactly once on the no-op stop path.'
    }
}
finally {
    $env:PATH = $originalPath
    Remove-Item -Path $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output 'StopLocalPostgresScriptTest passed.'