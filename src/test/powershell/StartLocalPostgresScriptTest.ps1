Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'start_local_postgres.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find start_local_postgres.ps1 at $scriptUnderTest"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("start-local-postgres-test-" + [System.Guid]::NewGuid())
$baseDir = Join-Path $tempRoot 'postgres-root'
$dataDir = Join-Path $baseDir 'data'
$pgVersionFile = Join-Path $dataDir 'PG_VERSION'
$psqlCountFile = Join-Path $tempRoot 'psql-count.txt'
$createdbCountFile = Join-Path $tempRoot 'createdb-count.txt'
$originalPath = $env:PATH

New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
Set-Content -Path $pgVersionFile -Value '18'
Set-Content -Path $psqlCountFile -Value '0'
Set-Content -Path $createdbCountFile -Value '0'

Set-Content -Path (Join-Path $tempRoot 'pg_isready.cmd') -Value @'
@echo off
exit /b 0
'@

Set-Content -Path (Join-Path $tempRoot 'psql.cmd') -Value @"
@echo off
set COUNT_FILE=$psqlCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
echo 1
exit /b 0
"@

Set-Content -Path (Join-Path $tempRoot 'createdb.cmd') -Value @"
@echo off
set COUNT_FILE=$createdbCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
exit /b 0
"@

try {
    $env:PATH = "$tempRoot;$originalPath"

    $threw = $false
    try {
        & $scriptUnderTest -BaseDir $baseDir -Database "bad-name';DROP DATABASE postgres;--"
    } catch {
        $threw = $true
        if ($_.Exception.Message -notmatch 'Database name must match') {
            throw
        }
    }

    if (-not $threw) {
        throw 'Expected start_local_postgres.ps1 to reject an unsafe database name.'
    }

    if ([int](Get-Content -Path $psqlCountFile -Raw).Trim() -ne 0) {
        throw 'Expected invalid database name validation to happen before invoking psql.'
    }

    if ([int](Get-Content -Path $createdbCountFile -Raw).Trim() -ne 0) {
        throw 'Expected invalid database name validation to happen before invoking createdb.'
    }
} finally {
    $env:PATH = $originalPath
    Remove-Item -Path $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output 'StartLocalPostgresScriptTest passed.'