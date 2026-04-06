param(
    [int]$Port = 55432,
    [string]$Username = 'datingapp',
    [PSCredential]$Credential = $null,
    [string]$Database = 'datingapp',
    [switch]$ThrowOnFailure
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Exit-OrThrow {
    param(
        [int]$ExitCode,
        [string]$Message
    )

    $global:LASTEXITCODE = $ExitCode
    if ($ThrowOnFailure) {
        throw $Message
    }
    exit $ExitCode
}

# If no credential provided, create one with default dev credentials
if ($null -eq $Credential) {
    $securePassword = ConvertTo-SecureString -String 'datingapp' -AsPlainText -Force
    $Credential = New-Object PSCredential($Username, $securePassword)
}

& (Join-Path $PSScriptRoot 'start_local_postgres.ps1') -Port $Port -Superuser $Credential.UserName -Credential $Credential -Database $Database
$startExitCode = $LASTEXITCODE

if ($startExitCode -ne 0) {
    Exit-OrThrow -ExitCode $startExitCode -Message "Local PostgreSQL startup failed with exit code $startExitCode."
}

$plainPassword = $Credential.GetNetworkCredential().Password
try {
    $mavenArgs = @(
        '-Dcheckstyle.skip=true'
        '-Dtest=PostgresqlRuntimeSmokeTest'
        "-Ddatingapp.pgtest.url=jdbc:postgresql://localhost:$Port/$Database"
        "-Ddatingapp.pgtest.username=$($Credential.UserName)"
        "-Ddatingapp.pgtest.password=$plainPassword"
        'test'
    )
} catch {
    throw "Failed to extract credentials: $_"
}

$previousDialect = $env:DATING_APP_DB_DIALECT
$previousUrl = $env:DATING_APP_DB_URL
$previousUsername = $env:DATING_APP_DB_USERNAME
$previousPassword = $env:DATING_APP_DB_PASSWORD

$env:DATING_APP_DB_DIALECT = 'POSTGRESQL'
$env:DATING_APP_DB_URL = "jdbc:postgresql://localhost:$Port/$Database"
$env:DATING_APP_DB_USERNAME = $Credential.UserName
$env:DATING_APP_DB_PASSWORD = $plainPassword

$mavenExitCode = 0

try {
    # Mirror the live PostgreSQL runtime env while the smoke test Maven fork is running.
    & mvn @mavenArgs
    $mavenExitCode = $LASTEXITCODE
} finally {
    $env:DATING_APP_DB_DIALECT = $previousDialect
    $env:DATING_APP_DB_URL = $previousUrl
    $env:DATING_APP_DB_USERNAME = $previousUsername
    $env:DATING_APP_DB_PASSWORD = $previousPassword
    # Clear plaintext password from memory
    if ($plainPassword) {
        $plainPassword = $null
    }
}

if ($mavenExitCode -ne 0) {
    Exit-OrThrow -ExitCode $mavenExitCode -Message "PostgreSQL smoke Maven run failed with exit code $mavenExitCode."
}
