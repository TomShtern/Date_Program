param(
    [int]$Port = 55432,
    [string]$Username = 'datingapp',
    [string]$Password = 'datingapp',
    [string]$Database = 'datingapp'
)

$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'start_local_postgres.ps1') -Port $Port -Superuser $Username -Password $Password -Database $Database

$mavenArgs = @(
    '-Dcheckstyle.skip=true'
    '-Dtest=PostgresqlRuntimeSmokeTest'
    "-Ddatingapp.pgtest.url=jdbc:postgresql://localhost:$Port/$Database"
    "-Ddatingapp.pgtest.username=$Username"
    "-Ddatingapp.pgtest.password=$Password"
    'test'
)

$previousDialect = $env:DATING_APP_DB_DIALECT
$previousUrl = $env:DATING_APP_DB_URL
$previousUsername = $env:DATING_APP_DB_USERNAME
$previousPassword = $env:DATING_APP_DB_PASSWORD

$env:DATING_APP_DB_DIALECT = 'POSTGRESQL'
$env:DATING_APP_DB_URL = "jdbc:postgresql://localhost:$Port/$Database"
$env:DATING_APP_DB_USERNAME = $Username
$env:DATING_APP_DB_PASSWORD = $Password

try {
    & mvn @mavenArgs
} finally {
    $env:DATING_APP_DB_DIALECT = $previousDialect
    $env:DATING_APP_DB_URL = $previousUrl
    $env:DATING_APP_DB_USERNAME = $previousUsername
    $env:DATING_APP_DB_PASSWORD = $previousPassword
}
