Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'check_postgresql_runtime_env.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find check_postgresql_runtime_env.ps1 at $scriptUnderTest"
}

$pwshExe = (Get-Command pwsh -ErrorAction Stop).Source

function Write-StubFile {
    param([string]$Path, [string[]]$Lines)

    Set-Content -Path $Path -Value ($Lines -join [Environment]::NewLine)
}

function New-PreflightSandbox {
    param(
        [int]$PgIsReadyExitCode,
        [int]$PsqlExitCode = 0,
        [string]$PsqlOutput = 'datingapp|datingapp|180003',
        [switch]$OmitPsql
    )

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('check-postgresql-runtime-env-test-' + [System.Guid]::NewGuid())
    New-Item -ItemType Directory -Path $tempRoot | Out-Null

    $copiedScript = Join-Path $tempRoot 'check_postgresql_runtime_env.ps1'
    $pgIsReadyCountFile = Join-Path $tempRoot 'pg-isready-count.txt'
    $psqlCountFile = Join-Path $tempRoot 'psql-count.txt'

    Copy-Item -Path $scriptUnderTest -Destination $copiedScript
    Set-Content -Path $pgIsReadyCountFile -Value '0'
    Set-Content -Path $psqlCountFile -Value '0'
    Set-Content -Path (Join-Path $tempRoot '.env') -Value @(
        'DATING_APP_DB_DIALECT=POSTGRESQL'
        'DATING_APP_DB_URL=jdbc:postgresql://localhost:55432/datingapp'
        'DATING_APP_DB_USERNAME=datingapp'
        'DATING_APP_DB_PASSWORD=datingapp'
    )

    foreach ($commandName in 'pg_ctl', 'createdb', 'initdb') {
        Write-StubFile -Path (Join-Path $tempRoot ($commandName + '.cmd')) -Lines @(
            '@echo off'
            'exit /b 0'
        )
    }

    Write-StubFile -Path (Join-Path $tempRoot 'pg_isready.cmd') -Lines @(
        '@echo off'
        "set COUNT_FILE=$pgIsReadyCountFile"
        'set /p COUNT=<"%COUNT_FILE%"'
        'if "%COUNT%"=="" set COUNT=0'
        'set /a COUNT=%COUNT%+1'
        '>"%COUNT_FILE%" echo %COUNT%'
        "exit /b $PgIsReadyExitCode"
    )

    if (-not $OmitPsql) {
        $escapedPsqlOutput = $PsqlOutput -replace '\|', '^|'
        Write-StubFile -Path (Join-Path $tempRoot 'psql.cmd') -Lines @(
            '@echo off'
            "set COUNT_FILE=$psqlCountFile"
            'set /p COUNT=<"%COUNT_FILE%"'
            'if "%COUNT%"=="" set COUNT=0'
            'set /a COUNT=%COUNT%+1'
            '>"%COUNT_FILE%" echo %COUNT%'
            "echo $escapedPsqlOutput"
            "exit /b $PsqlExitCode"
        )
    }

    return [pscustomobject]@{
        TempRoot           = $tempRoot
        CopiedScript       = $copiedScript
        PgIsReadyCountFile = $pgIsReadyCountFile
        PsqlCountFile      = $psqlCountFile
    }
}

function Invoke-PreflightScript {
    param($Sandbox)

    $previousPath = $env:PATH
    $system32Path = Join-Path $env:SystemRoot 'System32'
    $previousComSpec = $env:ComSpec
    try {
        $env:PATH = "$($Sandbox.TempRoot);$system32Path"
        $env:ComSpec = Join-Path $system32Path 'cmd.exe'
        $output = & $pwshExe -NoProfile -ExecutionPolicy Bypass -File $Sandbox.CopiedScript 2>&1 | Out-String
        $exitCode = $LASTEXITCODE
        return [pscustomobject]@{
            Output   = $output
            ExitCode = $exitCode
        }
    }
    finally {
        $env:PATH = $previousPath
        $env:ComSpec = $previousComSpec
    }
}

function Remove-PreflightSandbox {
    param($Sandbox)

    Remove-Item -Path $Sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

function Get-CountValue {
    param([string]$Path)

    return [int]((Get-Content -Path $Path -Raw).Trim())
}

function Assert-PreflightPassesOnHappyPath {
    $sandbox = New-PreflightSandbox -PgIsReadyExitCode 0
    try {
        $result = Invoke-PreflightScript -Sandbox $sandbox

        if ($result.ExitCode -ne 0) {
            throw "Expected preflight success, observed exit code $($result.ExitCode). Output: $($result.Output)"
        }

        if ($result.Output -notmatch 'PostgreSQL runtime preflight passed') {
            throw 'Expected success output from the preflight script.'
        }
    }
    finally {
        Remove-PreflightSandbox -Sandbox $sandbox
    }
}

function Assert-PreflightFailsWhenServerIsNotReachable {
    $sandbox = New-PreflightSandbox -PgIsReadyExitCode 1
    try {
        $pgIsReadyCountFile = $sandbox.PgIsReadyCountFile
        $psqlCountFile = $sandbox.PsqlCountFile
        $result = Invoke-PreflightScript -Sandbox $sandbox

        if ($result.ExitCode -ne 2) {
            throw "Expected exit code 2 when PostgreSQL is unreachable, observed $($result.ExitCode). Output: $($result.Output)"
        }

        if ($result.Output -notmatch 'start_local_postgres\.ps1') {
            throw 'Expected unreachable-server output to point at start_local_postgres.ps1.'
        }

        if ((Get-CountValue -Path $pgIsReadyCountFile) -ne 1) {
            throw 'Expected pg_isready to be called exactly once when PostgreSQL is unreachable.'
        }

        if ((Get-CountValue -Path $psqlCountFile) -ne 0) {
            throw 'Expected psql to be skipped when PostgreSQL is unreachable.'
        }
    }
    finally {
        Remove-PreflightSandbox -Sandbox $sandbox
    }
}

function Assert-PreflightFailsWhenRequiredBinaryIsMissing {
    $sandbox = New-PreflightSandbox -PgIsReadyExitCode 0 -OmitPsql
    try {
        $result = Invoke-PreflightScript -Sandbox $sandbox

        if ($result.ExitCode -ne 1) {
            throw "Expected exit code 1 when a required binary is missing, observed $($result.ExitCode). Output: $($result.Output)"
        }

        if ($result.Output -notmatch 'Missing required PostgreSQL CLI tools: psql') {
            throw 'Expected missing-binary output to name psql.'
        }
    }
    finally {
        Remove-PreflightSandbox -Sandbox $sandbox
    }
}

Assert-PreflightPassesOnHappyPath
Assert-PreflightFailsWhenServerIsNotReachable
Assert-PreflightFailsWhenRequiredBinaryIsMissing

Write-Output 'CheckPostgresqlRuntimeEnvScriptTest passed.'