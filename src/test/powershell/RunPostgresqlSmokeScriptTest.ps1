Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'run_postgresql_smoke.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find run_postgresql_smoke.ps1 at $scriptUnderTest"
}

function Write-StubFile {
    param([string]$Path, [string[]]$Lines)

    Set-Content -Path $Path -Value ($Lines -join [Environment]::NewLine)
}

function New-SmokeScriptSandbox {
    param(
        [int]$MavenExitCode,
        [int]$StartExitCode = 0
    )

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("run-postgresql-smoke-test-" + [System.Guid]::NewGuid())
    New-Item -ItemType Directory -Path $tempRoot | Out-Null

    $copiedScript = Join-Path $tempRoot 'run_postgresql_smoke.ps1'
    $stubStartScript = Join-Path $tempRoot 'start_local_postgres.ps1'
    $startCountFile = Join-Path $tempRoot 'start-count.txt'
    $mavenCountFile = Join-Path $tempRoot 'mvn-count.txt'
    $originalPath = $env:PATH

    Copy-Item -Path $scriptUnderTest -Destination $copiedScript
    Set-Content -Path $startCountFile -Value '0'
    Set-Content -Path $mavenCountFile -Value '0'

    Write-StubFile -Path $stubStartScript -Lines @(
        'param(',
        '    [int]$Port,',
        '    [string]$Superuser,',
        '    [PSCredential]$Credential,',
        '    [string]$Database',
        ')',
        '$countFile = Join-Path $PSScriptRoot ''start-count.txt''',
        '$count = 0',
        'if (Test-Path $countFile) {',
        '    $count = [int]((Get-Content -Path $countFile -Raw).Trim())',
        '}',
        'Set-Content -Path $countFile -Value ([string](($count + 1)))',
        "& cmd /c exit $StartExitCode | Out-Null"
    )

    Write-StubFile -Path (Join-Path $tempRoot 'mvn.cmd') -Lines @(
        '@echo off',
        "set COUNT_FILE=$mavenCountFile",
        'set /p COUNT=<"%COUNT_FILE%"',
        'if "%COUNT%"=="" set COUNT=0',
        'set /a COUNT=%COUNT%+1',
        '>"%COUNT_FILE%" echo %COUNT%',
        "exit /b $MavenExitCode"
    )

    return [pscustomobject]@{
        TempRoot       = $tempRoot
        CopiedScript   = $copiedScript
        StartCountFile = $startCountFile
        MavenCountFile = $mavenCountFile
        OriginalPath   = $originalPath
    }
}

function Get-CountValue {
    param([string]$Path)

    return [int]((Get-Content -Path $Path -Raw).Trim())
}

function Use-Sandbox {
    param($Sandbox, [scriptblock]$Body)

    try {
        $env:PATH = "$($Sandbox.TempRoot);$($Sandbox.OriginalPath)"
        $env:DATING_APP_DB_DIALECT = 'H2'
        $env:DATING_APP_DB_URL = 'jdbc:h2:./data/dating'
        $env:DATING_APP_DB_USERNAME = 'sa'
        Remove-Item Env:DATING_APP_DB_PASSWORD -ErrorAction SilentlyContinue
        & $Body
    }
    finally {
        $env:PATH = $Sandbox.OriginalPath
        Remove-Item Env:DATING_APP_DB_DIALECT -ErrorAction SilentlyContinue
        Remove-Item Env:DATING_APP_DB_URL -ErrorAction SilentlyContinue
        Remove-Item Env:DATING_APP_DB_USERNAME -ErrorAction SilentlyContinue
        Remove-Item Env:DATING_APP_DB_PASSWORD -ErrorAction SilentlyContinue
        Remove-Item -Path $Sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$successSandbox = New-SmokeScriptSandbox -MavenExitCode 0
Use-Sandbox $successSandbox {
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $successSandbox.CopiedScript | Out-Null

    if ((Get-CountValue -Path $successSandbox.MavenCountFile) -ne 1) {
        throw 'Expected one Maven invocation on happy path.'
    }

    if ((Get-CountValue -Path $successSandbox.StartCountFile) -ne 1) {
        throw 'Expected one start-helper invocation on happy path.'
    }
}

$startFailureSandbox = New-SmokeScriptSandbox -MavenExitCode 0 -StartExitCode 17
Use-Sandbox $startFailureSandbox {
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $startFailureSandbox.CopiedScript | Out-Null

    if ($LASTEXITCODE -ne 17) {
        throw "Expected exit 17 when start helper fails, observed $LASTEXITCODE."
    }

    if ((Get-CountValue -Path $startFailureSandbox.MavenCountFile) -ne 0) {
        throw 'Expected Maven to be skipped when start helper fails.'
    }

    if ((Get-CountValue -Path $startFailureSandbox.StartCountFile) -ne 1) {
        throw 'Expected one start-helper invocation when it fails.'
    }
}

$mavenFailureSandbox = New-SmokeScriptSandbox -MavenExitCode 23
Use-Sandbox $mavenFailureSandbox {
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $mavenFailureSandbox.CopiedScript | Out-Null

    if ($LASTEXITCODE -ne 23) {
        throw "Expected exit 23 when Maven fails, observed $LASTEXITCODE."
    }

    if ((Get-CountValue -Path $mavenFailureSandbox.MavenCountFile) -ne 1) {
        throw 'Expected one Maven invocation when Maven fails.'
    }
}

Write-Output 'RunPostgresqlSmokeScriptTest passed.'