param(
    [int]$Port = 55432,
    [string]$BaseDir = (Join-Path $PSScriptRoot 'data\local-postgresql'),
    [string]$Superuser = 'datingapp',
    [PSCredential]$Credential = $null,
    [string]$Database = 'datingapp',
    [int]$StartupTimeoutSeconds = 10
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($StartupTimeoutSeconds -lt 1) {
    throw 'StartupTimeoutSeconds must be at least 1 second.'
}

# If no credential provided, create one with default dev credentials
if ($null -eq $Credential) {
    $securePassword = ConvertTo-SecureString -String 'datingapp' -AsPlainText -Force
    $Credential = New-Object PSCredential($Superuser, $securePassword)
}

$dataDir = Join-Path $BaseDir 'data'
$logFile = Join-Path $BaseDir 'postgres.log'
$passwordFile = Join-Path $BaseDir 'superuser-password.txt'
$postgresAutoConfigFile = Join-Path $dataDir 'postgresql.auto.conf'
$pgCtlStdOutFile = Join-Path $BaseDir 'pg_ctl-start.stdout.log'
$pgCtlStdErrFile = Join-Path $BaseDir 'pg_ctl-start.stderr.log'
$pgVersionFile = Join-Path $dataDir 'PG_VERSION'
$validatedDatabase = $Database.Trim()
$plainPassword = $Credential.GetNetworkCredential().Password
$quotedSuperuser = '"' + ($Superuser -replace '"', '""') + '"'
$quotedDatabase = '"' + ($validatedDatabase -replace '"', '""') + '"'

if ($validatedDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw ('Database name must match ^[A-Za-z_][A-Za-z0-9_]*$: ' + $Database)
}

New-Item -ItemType Directory -Force -Path $BaseDir | Out-Null

function Test-PostgresReady {
    param([int]$ProbePort)

    & pg_isready -h localhost -p $ProbePort 2>$null | Out-Null
    $probeExitCode = $LASTEXITCODE

    if ($probeExitCode -eq 0) {
        return $true
    }

    if ($probeExitCode -in 1, 2, 3) {
        $global:LASTEXITCODE = 0
        return $false
    }

    throw "pg_isready failed with exit code $probeExitCode."
}

function Wait-ForPostgresReady {
    param(
        [int]$ProbePort,
        [int]$Attempts = 12,
        [int]$DelayMilliseconds = 250
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        if (Test-PostgresReady -ProbePort $ProbePort) {
            return $true
        }

        if ($attempt -lt $Attempts) {
            [System.Threading.Thread]::Sleep($DelayMilliseconds)
        }
    }

    return $false
}

function Get-ConfigValueFromLines {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Name
    )

    $pattern = '^\s*' + [regex]::Escape($Name) + '\s*=\s*(?<value>.+?)\s*$'
    foreach ($line in $Lines) {
        if ($line -match $pattern) {
            return $Matches['value'].Trim()
        }
    }

    return $null
}

function Set-ConfigValueInLines {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Name,
        [string]$Value
    )

    $pattern = '^\s*' + [regex]::Escape($Name) + '\s*='
    $newLine = "$Name = $Value"
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        if ($Lines[$index] -match $pattern) {
            if ($Lines[$index] -eq $newLine) {
                return $false
            }

            $Lines[$index] = $newLine
            return $true
        }
    }

    $Lines.Add($newLine)
    return $true
}

function Merge-SharedPreloadLibraries {
    param([AllowNull()][string]$RawValue)

    $libraries = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($RawValue)) {
        $trimmedValue = $RawValue.Trim()
        if ($trimmedValue.StartsWith("'") -and $trimmedValue.EndsWith("'")) {
            $trimmedValue = $trimmedValue.Substring(1, $trimmedValue.Length - 2)
        }

        foreach ($library in ($trimmedValue -split ',')) {
            $candidate = $library.Trim().Trim("'")
            if (-not [string]::IsNullOrWhiteSpace($candidate) -and -not $libraries.Contains($candidate)) {
                $libraries.Add($candidate)
            }
        }
    }

    if (-not $libraries.Contains('pg_stat_statements')) {
        $libraries.Add('pg_stat_statements')
    }

    return "'" + ($libraries -join ', ') + "'"
}

function Ensure-LocalObservabilityConfig {
    param([string]$ConfigPath)

    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $ConfigPath) {
        $lines.AddRange([string[]](Get-Content -Path $ConfigPath))
    }

    $sharedPreloadLibraries = Merge-SharedPreloadLibraries -RawValue (Get-ConfigValueFromLines -Lines $lines -Name 'shared_preload_libraries')
    $changed = $false
    if (Set-ConfigValueInLines -Lines $lines -Name 'shared_preload_libraries' -Value $sharedPreloadLibraries) {
        $changed = $true
    }
    if (Set-ConfigValueInLines -Lines $lines -Name 'compute_query_id' -Value 'on') {
        $changed = $true
    }

    if ($changed) {
        Set-Content -Path $ConfigPath -Value $lines
    }

    return $changed
}

$serverWasRunning = Test-PostgresReady -ProbePort $Port
if (-not (Test-Path $pgVersionFile)) {
    Set-Content -Path $passwordFile -Value $plainPassword -NoNewline
    & initdb -D $dataDir -U $Superuser --auth-local=scram-sha-256 --auth-host=scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 | Out-String | Write-Output
}

$observabilityConfigChanged = Ensure-LocalObservabilityConfig -ConfigPath $postgresAutoConfigFile

if ($serverWasRunning -and $observabilityConfigChanged) {
    & pg_ctl -D $dataDir stop -m fast | Out-String | Write-Output
    if ($LASTEXITCODE -ne 0) {
        throw "pg_ctl stop failed with exit code $LASTEXITCODE while applying PostgreSQL observability settings."
    }
}

if (-not (Test-PostgresReady -ProbePort $Port)) {
    # Launch pg_ctl as a separate process and redirect its streams to files instead of
    # the parent PowerShell stdout/stderr handles. On Windows, postgres.exe can inherit
    # those handles, which makes captured script runs appear hung even after the server
    # is ready.
    Remove-Item -Path $pgCtlStdOutFile, $pgCtlStdErrFile -ErrorAction SilentlyContinue
    # Keep the full -o payload as one quoted argument so pg_ctl does not treat -h as its own switch.
    $pgCtlArguments = "-D `"$dataDir`" -l `"$logFile`" -o `"-p $Port -h localhost`" start"
    $pgCtlProcess = Start-Process -FilePath 'pg_ctl' -ArgumentList $pgCtlArguments -PassThru -WindowStyle Hidden -RedirectStandardOutput $pgCtlStdOutFile -RedirectStandardError $pgCtlStdErrFile
    # Treat readiness as the success condition; waiting on pg_ctl itself can hang on Windows.
    $probeDelayMs = 250
    $postgresReady = Wait-ForPostgresReady -ProbePort $Port -Attempts ($StartupTimeoutSeconds * 1000 / $probeDelayMs) -DelayMilliseconds $probeDelayMs
    $pgCtlExited = $pgCtlProcess.WaitForExit(1000)
    $startExitCode = if ($pgCtlExited) { $pgCtlProcess.ExitCode } else { $null }

    if (-not $postgresReady) {
        $pgCtlErrorOutput = if (Test-Path $pgCtlStdErrFile) {
            (Get-Content -Path $pgCtlStdErrFile -Tail 20 | Out-String).Trim()
        }
        else {
            ''
        }

        if ($null -eq $startExitCode) {
            throw 'pg_ctl start did not finish and PostgreSQL never became ready.'
        }

        if ([string]::IsNullOrWhiteSpace($pgCtlErrorOutput)) {
            throw "pg_ctl start failed with exit code $startExitCode."
        }

        throw "pg_ctl start failed with exit code $startExitCode. $pgCtlErrorOutput"
    }

    if ($pgCtlExited) {
        Remove-Item -Path $pgCtlStdOutFile, $pgCtlStdErrFile -ErrorAction SilentlyContinue
    }
}

& pg_isready -h localhost -p $Port | Out-String | Write-Output
$env:PGPASSWORD = $plainPassword
try {
    $databaseExistsOutput =
    & psql "host=localhost port=$Port dbname=postgres user=$Superuser connect_timeout=5" -w -X -tAc "SELECT 1 FROM pg_database WHERE datname = '$validatedDatabase'" 2>$null | Out-String
    $psqlExitCode = $LASTEXITCODE

    if ($psqlExitCode -ne 0) {
        throw "psql failed with exit code $psqlExitCode while checking whether the database exists."
    }

    $databaseExists = $databaseExistsOutput.Trim()
    if ($databaseExists -ne '1') {
        & createdb -h localhost -p $Port -U $Superuser $validatedDatabase | Out-String | Write-Output
        if ($LASTEXITCODE -ne 0) {
            throw "createdb failed with exit code $LASTEXITCODE."
        }
    }

    & psql "host=localhost port=$Port dbname=$validatedDatabase user=$Superuser connect_timeout=5" -w -X -v ON_ERROR_STOP=1 -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" | Out-String | Write-Output
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE while enabling pg_stat_statements."
    }

    $roleDefaultsSql = @"
ALTER ROLE $quotedSuperuser IN DATABASE $quotedDatabase SET search_path = public;
ALTER ROLE $quotedSuperuser IN DATABASE $quotedDatabase SET statement_timeout = '30s';
ALTER ROLE $quotedSuperuser IN DATABASE $quotedDatabase SET lock_timeout = '5s';
ALTER ROLE $quotedSuperuser IN DATABASE $quotedDatabase SET idle_in_transaction_session_timeout = '5min';
"@
    & psql "host=localhost port=$Port dbname=$validatedDatabase user=$Superuser connect_timeout=5" -w -X -v ON_ERROR_STOP=1 -c $roleDefaultsSql | Out-String | Write-Output
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE while applying local role defaults."
    }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    # Clear plaintext password from memory
    $plainPassword = $null
}

$global:LASTEXITCODE = 0
Write-Output "Local PostgreSQL ready at localhost:$Port"
Write-Output "Database: $validatedDatabase"
Write-Output "Data directory: $dataDir"
Write-Output "Log file: $logFile"
