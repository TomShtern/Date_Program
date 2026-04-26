param(
    [string]$ServerHost = 'localhost',
    [int]$Port = 55432,
    [string]$Username = 'datingapp',
    [string]$Database = 'datingapp',
    [string]$DotEnvPath = (Join-Path $PSScriptRoot '.env'),
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
    Write-CheckLine -Icon '❌' -Message $Message
    if ($ThrowOnFailure) {
        throw $Message
    }
    exit $ExitCode
}

function Parse-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return @{}
    }

    $values = @{}
    foreach ($rawLine in Get-Content -Path $Path) {
        if ($null -eq $rawLine) {
            continue
        }

        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }

        if ($line.StartsWith('export ')) {
            $line = $line.Substring('export '.Length).Trim()
        }

        $equalsIndex = $line.IndexOf('=')
        if ($equalsIndex -le 0) {
            continue
        }

        $key = $line.Substring(0, $equalsIndex).Trim()
        $value = $line.Substring($equalsIndex + 1).Trim()
        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        $values[$key] = $value
    }

    return $values
}

function Get-ResolvedEnvValue {
    param(
        [string]$Name,
        [hashtable]$DotEnvValues
    )

    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue
    }

    if ($DotEnvValues.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($DotEnvValues[$Name])) {
        return $DotEnvValues[$Name]
    }

    return $null
}

function Resolve-EffectiveConnectionSettings {
    param(
        [string]$DefaultHost,
        [int]$DefaultPort,
        [string]$DefaultUsername,
        [string]$DefaultDatabase,
        [hashtable]$DotEnvValues
    )

    $resolvedUrl = Get-ResolvedEnvValue -Name 'DATING_APP_DB_URL' -DotEnvValues $DotEnvValues
    $resolvedUsername = Get-ResolvedEnvValue -Name 'DATING_APP_DB_USERNAME' -DotEnvValues $DotEnvValues
    $resolvedPassword = Get-ResolvedEnvValue -Name 'DATING_APP_DB_PASSWORD' -DotEnvValues $DotEnvValues
    $resolvedDialect = Get-ResolvedEnvValue -Name 'DATING_APP_DB_DIALECT' -DotEnvValues $DotEnvValues

    $effectiveHost = $DefaultHost
    $effectivePort = $DefaultPort
    $effectiveDatabase = $DefaultDatabase

    if (-not [string]::IsNullOrWhiteSpace($resolvedUrl)) {
        $match = [regex]::Match($resolvedUrl, '^jdbc:postgresql://(?<host>[^:/?#]+)(:(?<port>\d+))?/(?<database>[^?;]+)')
        if ($match.Success) {
            $effectiveHost = $match.Groups['host'].Value
            if ($match.Groups['port'].Success) {
                $effectivePort = [int]$match.Groups['port'].Value
            }
            $effectiveDatabase = $match.Groups['database'].Value
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($resolvedUsername)) {
        $DefaultUsername = $resolvedUsername
    }

    return [pscustomobject]@{
        Host     = $effectiveHost
        Port     = $effectivePort
        Username = $DefaultUsername
        Database = $effectiveDatabase
        Password = $resolvedPassword
        Dialect  = $resolvedDialect
        Url      = $resolvedUrl
    }
}

function Write-CheckLine {
    param(
        [string]$Icon,
        [string]$Message
    )

    Write-Output ("$Icon $Message")
}

$requiredCommands = @('pg_ctl', 'pg_isready', 'psql', 'createdb', 'initdb')
$resolvedCommands = @{}
$missingCommands = New-Object System.Collections.Generic.List[string]

foreach ($commandName in $requiredCommands) {
    $resolvedCommand = Get-Command $commandName -ErrorAction SilentlyContinue
    if ($null -eq $resolvedCommand) {
        $missingCommands.Add($commandName)
        continue
    }

    $resolvedCommands[$commandName] = $resolvedCommand.Source
    Write-CheckLine -Icon '✅' -Message ("Found {0}: {1}" -f $commandName, $resolvedCommand.Source)
}

if ($missingCommands.Count -gt 0) {
    $hint = @(
        "[ENV] Missing required PostgreSQL CLI tools: " + ($missingCommands -join ', ')
        "  Hint: Install PostgreSQL client tools and ensure they are on PATH."
        "  Hint: On Windows, add the PostgreSQL bin directory to your system PATH."
    ) -join "`n"
    Exit-OrThrow -ExitCode 1 -Message $hint
}

$dotEnvValues = Parse-DotEnv -Path $DotEnvPath
if (Test-Path $DotEnvPath) {
    Write-CheckLine -Icon '✅' -Message ("Found .env file: {0}" -f $DotEnvPath)
}
else {
    Write-CheckLine -Icon 'ℹ️' -Message ("No .env file found at {0}; relying on process environment/defaults." -f $DotEnvPath)
}

$effectiveSettings = Resolve-EffectiveConnectionSettings -DefaultHost $ServerHost -DefaultPort $Port -DefaultUsername $Username -DefaultDatabase $Database -DotEnvValues $dotEnvValues

if (-not [string]::IsNullOrWhiteSpace($effectiveSettings.Dialect)) {
    Write-CheckLine -Icon '✅' -Message ("Resolved database dialect: {0}" -f $effectiveSettings.Dialect)
}

$resolvedTargetMessage = "Resolved local PostgreSQL target: host={0} port={1} database={2} username={3}" -f $effectiveSettings.Host, $effectiveSettings.Port, $effectiveSettings.Database, $effectiveSettings.Username
Write-CheckLine -Icon '✅' -Message $resolvedTargetMessage

& pg_isready -h $effectiveSettings.Host -p $effectiveSettings.Port -d $effectiveSettings.Database -U $effectiveSettings.Username 2>$null | Out-Null
$pgIsReadyExitCode = $LASTEXITCODE

if ($pgIsReadyExitCode -ne 0) {
    if ($pgIsReadyExitCode -in 1, 2, 3) {
        $hint = @(
            ("[CONNECTIVITY] PostgreSQL is not reachable at {0}:{1}." -f $effectiveSettings.Host, $effectiveSettings.Port)
            "  Hint: Run .\start_local_postgres.ps1 to start the local server, then retry this preflight."
            "  Hint: Check if port $($effectiveSettings.Port) is blocked by a firewall or already used by another service."
        ) -join "`n"
        Exit-OrThrow -ExitCode 2 -Message $hint
    }

    Exit-OrThrow -ExitCode 2 -Message ("[CONNECTIVITY] pg_isready failed with exit code {0}." -f $pgIsReadyExitCode)
}

Write-CheckLine -Icon '✅' -Message ("PostgreSQL is accepting connections at {0}:{1}." -f $effectiveSettings.Host, $effectiveSettings.Port)

if ([string]::IsNullOrWhiteSpace($effectiveSettings.Password)) {
    $hint = @(
        '[AUTH] Database password is not configured.'
        '  Hint: Set DATING_APP_DB_PASSWORD in .env or the process environment.'
        '  Hint: Copy .env.example to .env and fill in the password value.'
    ) -join "`n"
    Exit-OrThrow -ExitCode 3 -Message $hint
}

$env:PGPASSWORD = $effectiveSettings.Password
try {
    $psqlArguments = @(
        "host=$($effectiveSettings.Host) port=$($effectiveSettings.Port) dbname=$($effectiveSettings.Database) user=$($effectiveSettings.Username) connect_timeout=5"
        '-w'
        '-X'
        '-tAc'
        "SELECT current_database() || '|' || current_user || '|' || current_setting('server_version_num')"
    )
    $connectionOutput = & psql @psqlArguments 2>&1 | Out-String
    $psqlExitCode = $LASTEXITCODE

    if ($psqlExitCode -ne 0) {
        Exit-OrThrow -ExitCode 4 -Message ("[AUTH] psql connection check failed with exit code {0}. {1}" -f $psqlExitCode, $connectionOutput.Trim())
    }

    $trimmedOutput = $connectionOutput.Trim()
    Write-CheckLine -Icon '✅' -Message ("psql login succeeded: {0}" -f $trimmedOutput)
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-CheckLine -Icon '✅' -Message 'PostgreSQL runtime preflight passed.'
$global:LASTEXITCODE = 0