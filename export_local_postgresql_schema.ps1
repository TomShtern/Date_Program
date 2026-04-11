param(
    [string]$OutputPath = (Join-Path $PSScriptRoot 'postgresql-public-schema-snapshot.sql'),
    [string]$DatabaseServer = 'localhost',
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
    if ($ThrowOnFailure) {
        throw $Message
    }

    exit $ExitCode
}

function Get-EnvFileValues {
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

function Resolve-Setting {
    param(
        [string]$Name,
        [hashtable]$DotEnvValues,
        [string]$DefaultValue
    )

    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue
    }

    if ($DotEnvValues.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($DotEnvValues[$Name])) {
        return $DotEnvValues[$Name]
    }

    return $DefaultValue
}

$pgDump = Get-Command pg_dump -ErrorAction SilentlyContinue
if ($null -eq $pgDump) {
    Exit-OrThrow -ExitCode 1 -Message 'pg_dump was not found on PATH.'
}

$dotEnvValues = Get-EnvFileValues -Path $DotEnvPath
$resolvedUrl = Resolve-Setting -Name 'DATING_APP_DB_URL' -DotEnvValues $dotEnvValues -DefaultValue "jdbc:postgresql://$DatabaseServer`:$Port/$Database"
$resolvedUsername = Resolve-Setting -Name 'DATING_APP_DB_USERNAME' -DotEnvValues $dotEnvValues -DefaultValue $Username
$resolvedPassword = Resolve-Setting -Name 'DATING_APP_DB_PASSWORD' -DotEnvValues $dotEnvValues -DefaultValue ''

$match = [regex]::Match($resolvedUrl, '^jdbc:postgresql://(?<host>[^:/?#]+)(:(?<port>\d+))?/(?<database>[^?;]+)')
if ($match.Success) {
    $DatabaseServer = $match.Groups['host'].Value
    if ($match.Groups['port'].Success) {
        $Port = [int]$match.Groups['port'].Value
    }
    $Database = $match.Groups['database'].Value
}
$Username = $resolvedUsername

if ([string]::IsNullOrWhiteSpace($resolvedPassword)) {
    Exit-OrThrow -ExitCode 2 -Message 'DATING_APP_DB_PASSWORD is required to export the local PostgreSQL schema snapshot.'
}

$env:PGPASSWORD = $resolvedPassword
try {
    $parentDirectory = Split-Path -Parent $OutputPath
    if (-not [string]::IsNullOrWhiteSpace($parentDirectory)) {
        New-Item -ItemType Directory -Force -Path $parentDirectory | Out-Null
    }

    & pg_dump --schema-only --schema public --no-owner --no-privileges --host $DatabaseServer --port $Port --username $Username --dbname $Database --file $OutputPath
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Exit-OrThrow -ExitCode $exitCode -Message ("pg_dump failed with exit code {0}." -f $exitCode)
    }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

$global:LASTEXITCODE = 0
Write-Output ('Exported PostgreSQL public schema snapshot to: ' + $OutputPath)
Write-Output ('Source database: ' + $Database)
Write-Output ('PostgreSQL version used by pg_dump: ' + (& pg_dump --version | Out-String).Trim())
