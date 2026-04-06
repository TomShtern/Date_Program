param(
    [string]$BaseDir = (Join-Path $PSScriptRoot 'data\local-postgresql')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$dataDir = Join-Path $BaseDir 'data'
$pgVersionFile = Join-Path $dataDir 'PG_VERSION'

if (-not (Test-Path $pgVersionFile)) {
    Write-Output 'Local PostgreSQL data directory not found; nothing to stop.'
    $global:LASTEXITCODE = 0
    return
}

& pg_ctl -D $dataDir status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Output 'Local PostgreSQL is not running.'
    $global:LASTEXITCODE = 0
    return
}

& pg_ctl -D $dataDir stop -m fast | Out-String | Write-Output
if ($LASTEXITCODE -ne 0) {
    throw "pg_ctl stop failed with exit code $LASTEXITCODE."
}

$global:LASTEXITCODE = 0
Write-Output 'Local PostgreSQL stopped.'
