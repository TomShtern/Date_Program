param(
    [string]$BaseDir = (Join-Path $PSScriptRoot 'data\local-postgresql')
)

$ErrorActionPreference = 'Stop'

$dataDir = Join-Path $BaseDir 'data'
$pgVersionFile = Join-Path $dataDir 'PG_VERSION'

if (-not (Test-Path $pgVersionFile)) {
    Write-Output 'Local PostgreSQL data directory not found; nothing to stop.'
    return
}

& pg_ctl -D $dataDir status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Output 'Local PostgreSQL is not running.'
    return
}

& pg_ctl -D $dataDir stop -m fast | Out-String | Write-Output
Write-Output 'Local PostgreSQL stopped.'
