param(
    [int]$Port = 55432,
    [string]$Username = 'datingapp',
    [PSCredential]$Credential = $null,
    [string]$Database = 'datingapp',
    [int]$StartupTimeoutSeconds = 10,
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

function Set-Or-ClearEnvironmentVariable {
    param(
        [string]$Name,
        [AllowNull()][string]$Value
    )

    if ($null -eq $Value -or $Value -eq '') {
        Remove-Item "Env:$Name" -ErrorAction SilentlyContinue
        return
    }

    Set-Item "Env:$Name" -Value $Value
}

if ($StartupTimeoutSeconds -lt 1) {
    throw 'StartupTimeoutSeconds must be at least 1 second.'
}

# If no credential provided, create one with default dev credentials
if ($null -eq $Credential) {
    $securePassword = ConvertTo-SecureString -String 'datingapp' -AsPlainText -Force
    $Credential = New-Object PSCredential($Username, $securePassword)
}

& (Join-Path $PSScriptRoot 'start_local_postgres.ps1') -Port $Port -Superuser $Credential.UserName -Credential $Credential -Database $Database -StartupTimeoutSeconds $StartupTimeoutSeconds
$startExitCode = $LASTEXITCODE

if ($startExitCode -ne 0) {
    Exit-OrThrow -ExitCode $startExitCode -Message "Local PostgreSQL startup failed with exit code $startExitCode."
}

$plainPassword = $Credential.GetNetworkCredential().Password

$mavenArgs = @(
    '-B'
    '-Dstyle.color=never'
    '-Dcheckstyle.skip=true'
    '-Dtest=PostgresqlRuntimeSmokeTest'
    "-Ddatingapp.pgtest.url=jdbc:postgresql://localhost:$Port/$Database"
    "-Ddatingapp.pgtest.username=$($Credential.UserName)"
    "-Ddatingapp.pgtest.password=$plainPassword"
    'test'
)

$previousDialect = $env:DATING_APP_DB_DIALECT
$previousUrl = $env:DATING_APP_DB_URL
$previousUsername = $env:DATING_APP_DB_USERNAME
$previousPassword = $env:DATING_APP_DB_PASSWORD

Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_DIALECT' -Value 'POSTGRESQL'
Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_URL' -Value "jdbc:postgresql://localhost:$Port/$Database"
Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_USERNAME' -Value $Credential.UserName
Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_PASSWORD' -Value $plainPassword

$mavenExitCode = 0

try {
    # Mirror the live PostgreSQL runtime env while the smoke test Maven fork is running.
    & mvn @mavenArgs
    $mavenExitCode = $LASTEXITCODE
}
finally {
    Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_DIALECT' -Value $previousDialect
    Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_URL' -Value $previousUrl
    Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_USERNAME' -Value $previousUsername
    Set-Or-ClearEnvironmentVariable -Name 'DATING_APP_DB_PASSWORD' -Value $previousPassword
    # Clear plaintext password from memory
    if ($plainPassword) {
        $plainPassword = $null
    }
}

if ($mavenExitCode -ne 0) {
    Exit-OrThrow -ExitCode $mavenExitCode -Message "PostgreSQL smoke Maven run failed with exit code $mavenExitCode."
}
