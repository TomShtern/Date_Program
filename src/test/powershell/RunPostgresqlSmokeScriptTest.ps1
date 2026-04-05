Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'run_postgresql_smoke.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find run_postgresql_smoke.ps1 at $scriptUnderTest"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("run-postgresql-smoke-test-" + [System.Guid]::NewGuid())
New-Item -ItemType Directory -Path $tempRoot | Out-Null

$copiedScript = Join-Path $tempRoot 'run_postgresql_smoke.ps1'
$stubStartScript = Join-Path $tempRoot 'start_local_postgres.ps1'
$invocationCountFile = Join-Path $tempRoot 'mvn-count.txt'
$stubMaven = Join-Path $tempRoot 'mvn.cmd'
$originalPath = $env:PATH

try {
    Copy-Item -Path $scriptUnderTest -Destination $copiedScript
    Set-Content -Path $stubStartScript -Value @'
param(
    [int]$Port,
    [string]$Superuser,
    [string]$Password,
    [string]$Database
)

Write-Output "Stub PostgreSQL ready at localhost:$Port for $Database"
'@
    Set-Content -Path $invocationCountFile -Value '0'
    Set-Content -Path $stubMaven -Value @"
@echo off
set COUNT_FILE=$invocationCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
exit /b 0
"@

    $env:PATH = "$tempRoot;$originalPath"
    $env:DATING_APP_DB_DIALECT = 'H2'
    $env:DATING_APP_DB_URL = 'jdbc:h2:./data/dating'
    $env:DATING_APP_DB_USERNAME = 'sa'
    Remove-Item Env:DATING_APP_DB_PASSWORD -ErrorAction SilentlyContinue

    & $copiedScript

    $mavenInvocationCount = [int](Get-Content -Path $invocationCountFile -Raw).Trim()
    if ($mavenInvocationCount -ne 1) {
        throw "Expected run_postgresql_smoke.ps1 to invoke Maven exactly once, but observed $mavenInvocationCount invocations."
    }
} finally {
    $env:PATH = $originalPath
    Remove-Item Env:DATING_APP_DB_DIALECT -ErrorAction SilentlyContinue
    Remove-Item Env:DATING_APP_DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:DATING_APP_DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:DATING_APP_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item -Path $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Output 'RunPostgresqlSmokeScriptTest passed.'