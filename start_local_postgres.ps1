param(
    [int]$Port = 55432,
    [string]$BaseDir = (Join-Path $PSScriptRoot 'data\local-postgresql'),
    [string]$Superuser = 'datingapp',
    [PSCredential]$Credential = $null,
    [string]$Database = 'datingapp'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# If no credential provided, create one with default dev credentials
if ($null -eq $Credential) {
    $securePassword = ConvertTo-SecureString -String 'datingapp' -AsPlainText -Force
    $Credential = New-Object PSCredential($Superuser, $securePassword)
}

$dataDir = Join-Path $BaseDir 'data'
$logFile = Join-Path $BaseDir 'postgres.log'
$passwordFile = Join-Path $BaseDir 'superuser-password.txt'
$pgCtlStdOutFile = Join-Path $BaseDir 'pg_ctl-start.stdout.log'
$pgCtlStdErrFile = Join-Path $BaseDir 'pg_ctl-start.stderr.log'
$pgVersionFile = Join-Path $dataDir 'PG_VERSION'
$validatedDatabase = $Database.Trim()
$plainPassword = $Credential.GetNetworkCredential().Password

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

if (-not (Test-Path $pgVersionFile)) {
    Set-Content -Path $passwordFile -Value $plainPassword -NoNewline
    & initdb -D $dataDir -U $Superuser --auth-local=scram-sha-256 --auth-host=scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 | Out-String | Write-Output
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
    $postgresReady = Wait-ForPostgresReady -ProbePort $Port
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
