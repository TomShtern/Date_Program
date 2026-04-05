param(
    [int]$Port = 55432,
    [string]$BaseDir = (Join-Path $PSScriptRoot 'data\local-postgresql'),
    [string]$Superuser = 'datingapp',
    [string]$Password = 'datingapp',
    [string]$Database = 'datingapp'
)

$ErrorActionPreference = 'Stop'

$dataDir = Join-Path $BaseDir 'data'
$logFile = Join-Path $BaseDir 'postgres.log'
$passwordFile = Join-Path $BaseDir 'superuser-password.txt'
$pgVersionFile = Join-Path $dataDir 'PG_VERSION'
$validatedDatabase = $Database.Trim()

if ($validatedDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw ('Database name must match ^[A-Za-z_][A-Za-z0-9_]*$: ' + $Database)
}

New-Item -ItemType Directory -Force -Path $BaseDir | Out-Null

function Test-PostgresReady {
    param([int]$ProbePort)

    & pg_isready -h localhost -p $ProbePort 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

if (-not (Test-Path $pgVersionFile)) {
    Set-Content -Path $passwordFile -Value $Password -NoNewline
    & initdb -D $dataDir -U $Superuser --auth-local=scram-sha-256 --auth-host=scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 | Out-String | Write-Output
}

if (-not (Test-PostgresReady -ProbePort $Port)) {
    & pg_ctl -D $dataDir -l $logFile -o " -p $Port -h localhost" -w start | Out-String | Write-Output
}

& pg_isready -h localhost -p $Port | Out-String | Write-Output
$env:PGPASSWORD = $Password
try {
    $databaseExistsOutput =
        & psql "host=localhost port=$Port dbname=postgres user=$Superuser connect_timeout=5" -w -X -tAc "SELECT 1 FROM pg_database WHERE datname = '$validatedDatabase'" 2>$null | Out-String
    $databaseExists = $databaseExistsOutput.Trim()
    if ($databaseExists -ne '1') {
        & createdb -h localhost -p $Port -U $Superuser $validatedDatabase | Out-String | Write-Output
    }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Output "Local PostgreSQL ready at localhost:$Port"
Write-Output "Database: $validatedDatabase"
Write-Output "Data directory: $dataDir"
Write-Output "Log file: $logFile"
