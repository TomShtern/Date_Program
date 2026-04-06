Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'start_local_postgres.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find start_local_postgres.ps1 at $scriptUnderTest"
}

function New-StartScriptSandbox {
    param(
        [int[]]$PgIsReadyExitCodes = @(0),
        [int]$PgCtlStartExitCode = 0,
        [int]$PsqlExitCode = 0,
        [string]$PsqlOutput = '1',
        [int]$CreatedbExitCode = 0
    )

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("start-local-postgres-test-" + [System.Guid]::NewGuid())
    $baseDir = Join-Path $tempRoot 'postgres-root'
    $dataDir = Join-Path $baseDir 'data'
    $pgVersionFile = Join-Path $dataDir 'PG_VERSION'
    $pgIsReadyCountFile = Join-Path $tempRoot 'pg-isready-count.txt'
    $pgCtlCountFile = Join-Path $tempRoot 'pg-ctl-count.txt'
    $pgCtlArgsFile = Join-Path $tempRoot 'pg-ctl-args.txt'
    $psqlCountFile = Join-Path $tempRoot 'psql-count.txt'
    $createdbCountFile = Join-Path $tempRoot 'createdb-count.txt'
    $originalPath = $env:PATH

    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    Set-Content -Path $pgVersionFile -Value '18'
    Set-Content -Path $pgIsReadyCountFile -Value '0'
    Set-Content -Path $pgCtlCountFile -Value '0'
    Set-Content -Path $pgCtlArgsFile -Value ''
    Set-Content -Path $psqlCountFile -Value '0'
    Set-Content -Path $createdbCountFile -Value '0'

    $firstProbeExitCode = $PgIsReadyExitCodes[0]
    $secondProbeExitCode = if ($PgIsReadyExitCodes.Count -gt 1) { $PgIsReadyExitCodes[1] } else { $firstProbeExitCode }

    Set-Content -Path (Join-Path $tempRoot 'pg_isready.cmd') -Value @"
@echo off
set COUNT_FILE=$pgIsReadyCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
if %COUNT%==1 exit /b $firstProbeExitCode
exit /b $secondProbeExitCode
"@

    Set-Content -Path (Join-Path $tempRoot 'pg_ctl.cmd') -Value @"
@echo off
set COUNT_FILE=$pgCtlCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
set "ARGS_FILE=$pgCtlArgsFile"
type nul >"%ARGS_FILE%"
setlocal EnableExtensions
set INDEX=0
:recordArgs
if "%~1"=="" goto doneArgs
set /a INDEX=%INDEX%+1
>>"%ARGS_FILE%" echo %INDEX%=%~1
shift
goto recordArgs
:doneArgs
exit /b $PgCtlStartExitCode
"@

    Set-Content -Path (Join-Path $tempRoot 'psql.cmd') -Value @"
@echo off
set COUNT_FILE=$psqlCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
echo $PsqlOutput
exit /b $PsqlExitCode
"@

    Set-Content -Path (Join-Path $tempRoot 'createdb.cmd') -Value @"
@echo off
set COUNT_FILE=$createdbCountFile
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
exit /b $CreatedbExitCode
"@

    return [pscustomobject]@{
        TempRoot           = $tempRoot
        BaseDir            = $baseDir
        PgIsReadyCountFile = $pgIsReadyCountFile
        PgCtlCountFile     = $pgCtlCountFile
        PgCtlArgsFile      = $pgCtlArgsFile
        PsqlCountFile      = $psqlCountFile
        CreatedbCountFile  = $createdbCountFile
        OriginalPath       = $originalPath
    }
}

function Initialize-StartScriptEnvironment {
    param($Sandbox)

    $env:PATH = "$($Sandbox.TempRoot);$($Sandbox.OriginalPath)"
}

function Restore-StartScriptEnvironment {
    param($Sandbox)

    $env:PATH = $Sandbox.OriginalPath
    Remove-Item -Path $Sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

function Get-CountValue {
    param([string]$Path)

    return [int]((Get-Content -Path $Path -Raw).Trim())
}

function Assert-StartScriptRejectsUnsafeDatabaseName {
    $sandbox = New-StartScriptSandbox

    try {
        Initialize-StartScriptEnvironment -Sandbox $sandbox

        $threw = $false
        $output = ''
        try {
            $output = & $scriptUnderTest -BaseDir $sandbox.BaseDir -Database "bad-name';DROP DATABASE postgres;--" 2>&1 | Out-String
        }
        catch {
            $threw = $true
            $output = $_.Exception.Message
        }

        if ($threw -eq $false) {
            throw 'Expected start_local_postgres.ps1 to reject an unsafe database name.'
        }

        if ($output -notmatch 'Database name must match') {
            throw 'Expected start_local_postgres.ps1 to report the database-name validation failure.'
        }

        if ((Get-CountValue -Path $sandbox.PgIsReadyCountFile) -ne 0) {
            throw 'Expected invalid database name validation to happen before invoking pg_isready.'
        }

        if ((Get-CountValue -Path $sandbox.PgCtlCountFile) -ne 0) {
            throw 'Expected invalid database name validation to happen before invoking pg_ctl.'
        }

        if ((Get-CountValue -Path $sandbox.PsqlCountFile) -ne 0) {
            throw 'Expected invalid database name validation to happen before invoking psql.'
        }

        if ((Get-CountValue -Path $sandbox.CreatedbCountFile) -ne 0) {
            throw 'Expected invalid database name validation to happen before invoking createdb.'
        }
    }
    finally {
        Restore-StartScriptEnvironment -Sandbox $sandbox
    }
}

function Assert-StartScriptAcceptsAlreadyRunningDatabase {
    $sandbox = New-StartScriptSandbox -PgIsReadyExitCodes @(1, 0) -PgCtlStartExitCode 1 -PsqlOutput '1'

    try {
        Initialize-StartScriptEnvironment -Sandbox $sandbox

        & $scriptUnderTest -BaseDir $sandbox.BaseDir -Database 'datingapp' | Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw "Expected start_local_postgres.ps1 to exit with code 0 when the server is already running, but observed $LASTEXITCODE."
        }

        if ((Get-CountValue -Path $sandbox.PgCtlCountFile) -ne 1) {
            throw 'Expected start_local_postgres.ps1 to invoke pg_ctl exactly once when the server is already running.'
        }

        if ((Get-CountValue -Path $sandbox.CreatedbCountFile) -ne 0) {
            throw 'Expected start_local_postgres.ps1 to skip createdb when the database already exists.'
        }
    }
    finally {
        Restore-StartScriptEnvironment -Sandbox $sandbox
    }
}

function Assert-StartScriptAcceptsSuccessfulPgCtlStartWhenDatabaseBecomesReady {
    $sandbox = New-StartScriptSandbox -PgIsReadyExitCodes @(1, 0) -PgCtlStartExitCode 0 -PsqlOutput '1'

    try {
        Initialize-StartScriptEnvironment -Sandbox $sandbox

        & $scriptUnderTest -BaseDir $sandbox.BaseDir -Database 'datingapp' | Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw "Expected start_local_postgres.ps1 to exit with code 0 when pg_ctl start succeeds, but observed $LASTEXITCODE."
        }

        if ((Get-CountValue -Path $sandbox.PgCtlCountFile) -ne 1) {
            throw 'Expected start_local_postgres.ps1 to invoke pg_ctl exactly once when startup is required.'
        }

        if ((Get-CountValue -Path $sandbox.PsqlCountFile) -ne 1) {
            throw 'Expected start_local_postgres.ps1 to continue to the database-exists check after successful pg_ctl start.'
        }
    }
    finally {
        Restore-StartScriptEnvironment -Sandbox $sandbox
    }
}

function Assert-StartScriptPassesServerOptionsToPgCtlAsSingleArgument {
    $sandbox = New-StartScriptSandbox -PgIsReadyExitCodes @(1, 0) -PgCtlStartExitCode 0 -PsqlOutput '1'

    try {
        Initialize-StartScriptEnvironment -Sandbox $sandbox

        & $scriptUnderTest -BaseDir $sandbox.BaseDir -Database 'datingapp' | Out-Null

        $pgCtlArgs = Get-Content -Path $sandbox.PgCtlArgsFile

        if ($pgCtlArgs.Count -ne 7) {
            throw "Expected pg_ctl to receive 7 arguments, but observed $($pgCtlArgs.Count): $($pgCtlArgs -join '; ')."
        }

        if ($pgCtlArgs[4] -ne '5=-o') {
            throw "Expected pg_ctl argument 5 to be -o, but observed '$($pgCtlArgs[4])'."
        }

        if ($pgCtlArgs[5] -ne '6=-p 55432 -h localhost') {
            throw "Expected pg_ctl to receive the full postgres server options as one argument, but observed '$($pgCtlArgs[5])'."
        }

        if ($pgCtlArgs[6] -ne '7=start') {
            throw "Expected pg_ctl start argument after the -o payload, but observed '$($pgCtlArgs -join '; ')'."
        }
    }
    finally {
        Restore-StartScriptEnvironment -Sandbox $sandbox
    }
}

function Assert-StartScriptFailsWhenPgCtlReturnsSuccessButDatabaseNeverBecomesReady {
    $sandbox = New-StartScriptSandbox -PgIsReadyExitCodes @(1, 1) -PgCtlStartExitCode 0 -PsqlOutput '1'

    try {
        Initialize-StartScriptEnvironment -Sandbox $sandbox

        $threw = $false
        try {
            & $scriptUnderTest -BaseDir $sandbox.BaseDir -Database 'datingapp' | Out-Null
        }
        catch {
            $threw = $true
            if ($_.Exception.Message -notmatch 'pg_ctl start failed with exit code 0') {
                throw
            }
        }

        if ($threw -eq $false) {
            throw 'Expected start_local_postgres.ps1 to fail when pg_ctl returns success but PostgreSQL never becomes ready.'
        }

        if ((Get-CountValue -Path $sandbox.PsqlCountFile) -ne 0) {
            throw 'Expected start_local_postgres.ps1 to stop before psql when PostgreSQL never becomes ready.'
        }
    }
    finally {
        Restore-StartScriptEnvironment -Sandbox $sandbox
    }
}

function Assert-StartScriptFailsWhenStartupNeverBecomesReady {
    $sandbox = New-StartScriptSandbox -PgIsReadyExitCodes @(1, 1) -PgCtlStartExitCode 2 -PsqlOutput '1'

    try {
        Initialize-StartScriptEnvironment -Sandbox $sandbox

        $threw = $false
        try {
            & $scriptUnderTest -BaseDir $sandbox.BaseDir -Database 'datingapp' | Out-Null
        }
        catch {
            $threw = $true
            if ($_.Exception.Message -notmatch 'pg_ctl start failed with exit code 2') {
                throw
            }
        }

        if ($threw -eq $false) {
            throw 'Expected start_local_postgres.ps1 to fail when PostgreSQL never becomes ready after startup.'
        }

        if ((Get-CountValue -Path $sandbox.PgCtlCountFile) -ne 1) {
            throw 'Expected start_local_postgres.ps1 to invoke pg_ctl exactly once on startup failure.'
        }

        if ((Get-CountValue -Path $sandbox.CreatedbCountFile) -ne 0) {
            throw 'Expected start_local_postgres.ps1 to stop before createdb when startup fails.'
        }
    }
    finally {
        Restore-StartScriptEnvironment -Sandbox $sandbox
    }
}

function Assert-StartScriptDoesNotHangWhenPgCtlLeavesBackgroundChildAttachedToStdout {
    $sandbox = New-StartScriptSandbox -PgIsReadyExitCodes @(1, 0) -PgCtlStartExitCode 0 -PsqlOutput '1'
    $process = $null
    $stdoutPath = Join-Path $sandbox.TempRoot 'stdout.txt'
    $stderrPath = Join-Path $sandbox.TempRoot 'stderr.txt'
    $pgCtlStub = Join-Path $sandbox.TempRoot 'pg_ctl.cmd'

    Set-Content -Path $pgCtlStub -Value @"
@echo off
set COUNT_FILE=$($sandbox.PgCtlCountFile)
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
echo PostgreSQL starting...
start "" /b pwsh -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 4"
exit /b 0
"@

    try {
        Initialize-StartScriptEnvironment -Sandbox $sandbox

        $process = Start-Process -FilePath 'pwsh' -ArgumentList @(
            '-NoProfile'
            '-ExecutionPolicy'
            'Bypass'
            '-File'
            $scriptUnderTest
            '-BaseDir'
            $sandbox.BaseDir
            '-Database'
            'datingapp'
        ) -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru -WindowStyle Hidden

        if (-not $process.WaitForExit(2500)) {
            $stdout = if (Test-Path $stdoutPath) { Get-Content -Path $stdoutPath -Raw } else { '' }
            $stderr = if (Test-Path $stderrPath) { Get-Content -Path $stderrPath -Raw } else { '' }
            & cmd.exe /c "taskkill /T /F /PID $($process.Id)" >$null 2>&1
            throw "Expected start_local_postgres.ps1 to finish without hanging when pg_ctl leaves a background child attached to stdout. Stdout: $stdout`nStderr: $stderr"
        }

        if ($process.ExitCode -ne 0) {
            $stdout = if (Test-Path $stdoutPath) { Get-Content -Path $stdoutPath -Raw } else { '' }
            $stderr = if (Test-Path $stderrPath) { Get-Content -Path $stderrPath -Raw } else { '' }
            throw "Expected start_local_postgres.ps1 to exit with code 0 when pg_ctl succeeds, but observed $($process.ExitCode). Stdout: $stdout`nStderr: $stderr"
        }
    }
    finally {
        if ($process -and -not $process.HasExited) {
            & cmd.exe /c "taskkill /T /F /PID $($process.Id)" >$null 2>&1
        }

        Restore-StartScriptEnvironment -Sandbox $sandbox
    }
}

Assert-StartScriptRejectsUnsafeDatabaseName
Assert-StartScriptAcceptsAlreadyRunningDatabase
Assert-StartScriptAcceptsSuccessfulPgCtlStartWhenDatabaseBecomesReady
Assert-StartScriptPassesServerOptionsToPgCtlAsSingleArgument
Assert-StartScriptFailsWhenPgCtlReturnsSuccessButDatabaseNeverBecomesReady
Assert-StartScriptFailsWhenStartupNeverBecomesReady
Assert-StartScriptDoesNotHangWhenPgCtlLeavesBackgroundChildAttachedToStdout

Write-Output 'StartLocalPostgresScriptTest passed.'
