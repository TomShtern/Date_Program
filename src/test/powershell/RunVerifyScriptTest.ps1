Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'run_verify.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find run_verify.ps1 at $scriptUnderTest"
}

function New-VerifyScriptSandbox {
    param(
        [int]$StartExitCode = 0,
        [int]$MavenExitCode,
        [int]$SmokeExitCode
    )

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("run-verify-test-" + [System.Guid]::NewGuid())
    New-Item -ItemType Directory -Path $tempRoot | Out-Null

    $copiedScript = Join-Path $tempRoot 'run_verify.ps1'
    $startCountFile = Join-Path $tempRoot 'start-count.txt'
    $mavenCountFile = Join-Path $tempRoot 'mvn-count.txt'
    $smokeCountFile = Join-Path $tempRoot 'smoke-count.txt'
    $stopCountFile = Join-Path $tempRoot 'stop-count.txt'
    $stubStart = Join-Path $tempRoot 'start_local_postgres.ps1'
    $stubMaven = Join-Path $tempRoot 'mvn.cmd'
    $stubSmoke = Join-Path $tempRoot 'run_postgresql_smoke.ps1'
    $stubStop = Join-Path $tempRoot 'stop_local_postgres.ps1'

    Copy-Item -Path $scriptUnderTest -Destination $copiedScript

    Set-Content -Path $startCountFile -Value '0'
    Set-Content -Path $mavenCountFile -Value '0'
    Set-Content -Path $smokeCountFile -Value '0'
    Set-Content -Path $stopCountFile -Value '0'

    Set-Content -Path $stubStart -Value @"
Set-StrictMode -Version Latest

`$countFile = Join-Path `$PSScriptRoot 'start-count.txt'
`$count = 0
if (Test-Path `$countFile) {
    `$count = [int]((Get-Content -Path `$countFile -Raw).Trim())
}

Set-Content -Path `$countFile -Value ([string]((`$count + 1)))
`$global:LASTEXITCODE = $StartExitCode
return
"@

    Set-Content -Path $stubMaven -Value @"
@echo off
set COUNT_FILE=$mavenCountFile
set COUNT=0
if exist "%COUNT_FILE%" for /f usebackq %%A in ("%COUNT_FILE%") do set COUNT=%%A
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
exit /b $MavenExitCode
"@

    Set-Content -Path $stubSmoke -Value @"
param([switch]`$ThrowOnFailure)
Set-StrictMode -Version Latest

`$countFile = Join-Path `$PSScriptRoot 'smoke-count.txt'

`$count = 0
if (Test-Path `$countFile) {
    `$count = [int]((Get-Content -Path `$countFile -Raw).Trim())
}

Set-Content -Path `$countFile -Value ([string]((`$count + 1)))

if (`$ThrowOnFailure -and $SmokeExitCode -ne 0) {
    `$global:LASTEXITCODE = $SmokeExitCode
    throw "Smoke failed with exit code $SmokeExitCode."
}

exit $SmokeExitCode
"@

    Set-Content -Path $stubStop -Value @"
Set-StrictMode -Version Latest

`$countFile = Join-Path `$PSScriptRoot 'stop-count.txt'

`$count = 0
if (Test-Path `$countFile) {
    `$count = [int]((Get-Content -Path `$countFile -Raw).Trim())
}

Set-Content -Path `$countFile -Value ([string]((`$count + 1)))
"@

    return [pscustomobject]@{
        TempRoot       = $tempRoot
        CopiedScript   = $copiedScript
        StartCountFile = $startCountFile
        MavenCountFile = $mavenCountFile
        SmokeCountFile = $smokeCountFile
        StopCountFile  = $stopCountFile
    }
}

function Initialize-VerifyScriptEnvironment {
    param($Sandbox, [string]$OriginalPath)

    $env:PATH = "$($Sandbox.TempRoot);$OriginalPath"
}

function Restore-VerifyScriptEnvironment {
    param($Sandbox, [string]$OriginalPath)

    $env:PATH = $OriginalPath
    Remove-Item -Path $Sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

function Get-CountValue {
    param([string]$Path)

    return [int]((Get-Content -Path $Path -Raw).Trim())
}

function Assert-VerifyScriptSuccess {
    $sandbox = New-VerifyScriptSandbox -MavenExitCode 0 -SmokeExitCode 0
    $originalPath = $env:PATH

    try {
        Initialize-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath

        & pwsh -NoProfile -ExecutionPolicy Bypass -File $sandbox.CopiedScript | Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw "Expected run_verify.ps1 to exit with code 0 on the happy path, but observed $LASTEXITCODE."
        }

        if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke start_local_postgres.ps1 exactly once on the happy path.'
        }

        if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke Maven exactly once on the happy path.'
        }

        if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke PostgreSQL smoke exactly once on the happy path.'
        }

        if ((Get-CountValue -Path $sandbox.StopCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke stop_local_postgres.ps1 exactly once on the happy path.'
        }
    }
    finally {
        Restore-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath
    }
}

function Assert-VerifyScriptPropagatesMavenFailure {
    $sandbox = New-VerifyScriptSandbox -MavenExitCode 19 -SmokeExitCode 0
    $originalPath = $env:PATH

    try {
        Initialize-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath

        & pwsh -NoProfile -ExecutionPolicy Bypass -File $sandbox.CopiedScript | Out-Null

        if ($LASTEXITCODE -ne 19) {
            throw "Expected run_verify.ps1 to exit with code 19 when Maven fails, but observed $LASTEXITCODE."
        }

        if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke start_local_postgres.ps1 before Maven fails.'
        }

        if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke Maven exactly once when Maven fails.'
        }

        if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 0) {
            throw 'Expected run_verify.ps1 to skip PostgreSQL smoke when Maven fails.'
        }

        if ((Get-CountValue -Path $sandbox.StopCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to still invoke stop_local_postgres.ps1 when Maven fails.'
        }
    }
    finally {
        Restore-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath
    }
}

function Assert-VerifyScriptPropagatesSmokeFailure {
    $sandbox = New-VerifyScriptSandbox -MavenExitCode 0 -SmokeExitCode 23
    $originalPath = $env:PATH

    try {
        Initialize-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath

        & pwsh -NoProfile -ExecutionPolicy Bypass -File $sandbox.CopiedScript | Out-Null

        if ($LASTEXITCODE -ne 23) {
            throw "Expected run_verify.ps1 to exit with code 23 when smoke fails, but observed $LASTEXITCODE."
        }

        if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke start_local_postgres.ps1 before smoke fails.'
        }

        if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke Maven exactly once when smoke fails.'
        }

        if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke PostgreSQL smoke exactly once when smoke fails.'
        }

        if ((Get-CountValue -Path $sandbox.StopCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke stop_local_postgres.ps1 when smoke fails.'
        }
    }
    finally {
        Restore-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath
    }
}

function Assert-VerifyScriptPropagatesStartupFailure {
    $sandbox = New-VerifyScriptSandbox -StartExitCode 17 -MavenExitCode 0 -SmokeExitCode 0
    $originalPath = $env:PATH

    try {
        Initialize-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath

        & pwsh -NoProfile -ExecutionPolicy Bypass -File $sandbox.CopiedScript | Out-Null

        if ($LASTEXITCODE -ne 17) {
            throw "Expected run_verify.ps1 to exit with code 17 when startup fails, but observed $LASTEXITCODE."
        }

        if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to invoke start_local_postgres.ps1 exactly once when startup fails.'
        }

        if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 0) {
            throw 'Expected run_verify.ps1 to skip Maven when startup fails.'
        }

        if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 0) {
            throw 'Expected run_verify.ps1 to skip PostgreSQL smoke when startup fails.'
        }

        if ((Get-CountValue -Path $sandbox.StopCountFile) -ne 1) {
            throw 'Expected run_verify.ps1 to still invoke stop_local_postgres.ps1 when startup fails.'
        }
    }
    finally {
        Restore-VerifyScriptEnvironment -Sandbox $sandbox -OriginalPath $originalPath
    }
}

Assert-VerifyScriptSuccess
Assert-VerifyScriptPropagatesStartupFailure
Assert-VerifyScriptPropagatesMavenFailure
Assert-VerifyScriptPropagatesSmokeFailure

Write-Output 'RunVerifyScriptTest passed.'