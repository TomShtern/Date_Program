Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$scriptUnderTest = Join-Path $repoRoot 'reset_local_postgres.ps1'

if (-not (Test-Path $scriptUnderTest)) {
    throw "Could not find reset_local_postgres.ps1 at $scriptUnderTest"
}

function Write-StubFile {
    param(
        [string]$Path,
        [string]$Content
    )

    Set-Content -Path $Path -Value $Content -Encoding UTF8
}

function New-ResetScriptSandbox {
    param(
        [int]$StartExitCode = 0,
        [int]$MavenExitCode = 0,
        [int]$SmokeExitCode = 0
    )

    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("reset-local-postgres-test-" + [System.Guid]::NewGuid())
    New-Item -ItemType Directory -Path $tempRoot | Out-Null

    $copiedScript = Join-Path $tempRoot 'reset_local_postgres.ps1'
    $originalPath = $env:PATH

    $startCountFile = Join-Path $tempRoot 'start-count.txt'
    $startArgsFile = Join-Path $tempRoot 'start-args.txt'
    $psqlCountFile = Join-Path $tempRoot 'psql-count.txt'
    $psqlArgsFile = Join-Path $tempRoot 'psql-args.log'
    $mavenCountFile = Join-Path $tempRoot 'mvn-count.txt'
    $mavenArgsFile = Join-Path $tempRoot 'mvn-args.log'
    $mavenEnvFile = Join-Path $tempRoot 'mvn-env.log'
    $smokeCountFile = Join-Path $tempRoot 'smoke-count.txt'
    $smokeArgsFile = Join-Path $tempRoot 'smoke-args.log'

    Copy-Item -Path $scriptUnderTest -Destination $copiedScript
    Set-Content -Path $startCountFile -Value '0'
    Set-Content -Path $psqlCountFile -Value '0'
    Set-Content -Path $mavenCountFile -Value '0'
    Set-Content -Path $smokeCountFile -Value '0'
    Set-Content -Path $startArgsFile -Value ''
    Set-Content -Path $psqlArgsFile -Value ''
    Set-Content -Path $mavenArgsFile -Value ''
    Set-Content -Path $mavenEnvFile -Value ''
    Set-Content -Path $smokeArgsFile -Value ''

    Write-StubFile -Path (Join-Path $tempRoot 'start_local_postgres.ps1') -Content @"
param(
    [int]`$Port,
    [string]`$BaseDir,
    [string]`$Superuser,
    [PSCredential]`$Credential,
    [string]`$Database,
    [int]`$StartupTimeoutSeconds
)
Set-StrictMode -Version Latest
`$countFile = Join-Path `$PSScriptRoot 'start-count.txt'
`$count = [int]((Get-Content -Path `$countFile -Raw).Trim())
Set-Content -Path `$countFile -Value ([string](`$count + 1))
Set-Content -Path (Join-Path `$PSScriptRoot 'start-args.txt') -Value @(
    "Port=`$Port"
    "BaseDir=`$BaseDir"
    "Superuser=`$Superuser"
    "Database=`$Database"
    "StartupTimeoutSeconds=`$StartupTimeoutSeconds"
)
`$global:LASTEXITCODE = $StartExitCode
return
"@

    Write-StubFile -Path (Join-Path $tempRoot 'run_postgresql_smoke.ps1') -Content @"
param(
    [int]`$Port,
    [string]`$Username,
    [PSCredential]`$Credential,
    [string]`$Database,
    [switch]`$ThrowOnFailure
)
Set-StrictMode -Version Latest
`$countFile = Join-Path `$PSScriptRoot 'smoke-count.txt'
`$count = [int]((Get-Content -Path `$countFile -Raw).Trim())
Set-Content -Path `$countFile -Value ([string](`$count + 1))
Add-Content -Path (Join-Path `$PSScriptRoot 'smoke-args.log') -Value ("Port={0};Username={1};Database={2};ThrowOnFailure={3}" -f `$Port, `$Username, `$Database, `$ThrowOnFailure.IsPresent)
if (`$ThrowOnFailure -and $SmokeExitCode -ne 0) {
    `$global:LASTEXITCODE = $SmokeExitCode
    throw 'Smoke failed.'
}
`$global:LASTEXITCODE = $SmokeExitCode
return
"@

    Write-StubFile -Path (Join-Path $tempRoot 'psql.cmd') -Content @"
@echo off
setlocal EnableExtensions
set "COUNT_FILE=$psqlCountFile"
set "ARGS_FILE=$psqlArgsFile"
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
>>"%ARGS_FILE%" echo %*
exit /b 0
"@

    Write-StubFile -Path (Join-Path $tempRoot 'mvn.cmd') -Content @"
@echo off
setlocal EnableExtensions
set "COUNT_FILE=$mavenCountFile"
set "ARGS_FILE=$mavenArgsFile"
set "ENV_FILE=$mavenEnvFile"
set /p COUNT=<"%COUNT_FILE%"
if "%COUNT%"=="" set COUNT=0
set /a COUNT=%COUNT%+1
>"%COUNT_FILE%" echo %COUNT%
>"%ARGS_FILE%" echo %*
>"%ENV_FILE%" echo DATING_APP_DB_DIALECT=%DATING_APP_DB_DIALECT%
>>"%ENV_FILE%" echo DATING_APP_DB_URL=%DATING_APP_DB_URL%
>>"%ENV_FILE%" echo DATING_APP_DB_USERNAME=%DATING_APP_DB_USERNAME%
>>"%ENV_FILE%" echo DATING_APP_DB_PASSWORD=%DATING_APP_DB_PASSWORD%
exit /b $MavenExitCode
"@

    [pscustomobject]@{
        TempRoot       = $tempRoot
        CopiedScript   = $copiedScript
        StartCountFile = $startCountFile
        StartArgsFile  = $startArgsFile
        PsqlCountFile  = $psqlCountFile
        PsqlArgsFile   = $psqlArgsFile
        MavenCountFile = $mavenCountFile
        MavenArgsFile  = $mavenArgsFile
        MavenEnvFile   = $mavenEnvFile
        SmokeCountFile = $smokeCountFile
        SmokeArgsFile  = $smokeArgsFile
        OriginalPath   = $originalPath
    }
}

function Use-Sandbox {
    param(
        $Sandbox,
        [scriptblock]$Body
    )

    $originalDialect = $env:DATING_APP_DB_DIALECT
    $originalUrl = $env:DATING_APP_DB_URL
    $originalUsername = $env:DATING_APP_DB_USERNAME
    $originalPassword = $env:DATING_APP_DB_PASSWORD

    try {
        $env:PATH = "$($Sandbox.TempRoot);$($Sandbox.OriginalPath)"
        $env:DATING_APP_DB_DIALECT = 'SENTINEL_DIALECT'
        $env:DATING_APP_DB_URL = 'SENTINEL_URL'
        $env:DATING_APP_DB_USERNAME = 'SENTINEL_USER'
        $env:DATING_APP_DB_PASSWORD = 'SENTINEL_PASS'
        & $Body
    }
    finally {
        $env:PATH = $Sandbox.OriginalPath

        if ($null -eq $originalDialect) {
            Remove-Item Env:DATING_APP_DB_DIALECT -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_DIALECT = $originalDialect
        }

        if ($null -eq $originalUrl) {
            Remove-Item Env:DATING_APP_DB_URL -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_URL = $originalUrl
        }

        if ($null -eq $originalUsername) {
            Remove-Item Env:DATING_APP_DB_USERNAME -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_USERNAME = $originalUsername
        }

        if ($null -eq $originalPassword) {
            Remove-Item Env:DATING_APP_DB_PASSWORD -ErrorAction SilentlyContinue
        }
        else {
            $env:DATING_APP_DB_PASSWORD = $originalPassword
        }
    }
}

function Get-CountValue {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return 0
    }

    [int]((Get-Content -Path $Path -Raw).Trim())
}

function Get-LogLines {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return , ([string[]]@())
    }

    return , ([string[]]@((Get-Content -Path $Path -Raw) -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }))
}

function Get-KeptTempDirectory {
    param([string]$Message)

    $match = [regex]::Match($Message, 'Temporary SQL files were kept at (?<path>.+)\.$')
    if (-not $match.Success) {
        throw "Expected failure message to include the kept temporary SQL directory, but observed: $Message"
    }

    return $match.Groups['path'].Value
}

function Invoke-ResetScript {
    param(
        $Sandbox,
        [string]$Database = 'datingapp',
        [switch]$ProfileDataOnly
    )

    $result = [ordered]@{
        Threw    = $false
        Message  = ''
        Output   = ''
        ExitCode = 0
    }

    try {
        if ($ProfileDataOnly) {
            $result.Output = & $Sandbox.CopiedScript -ThrowOnFailure -BackupSchema 'reset_backup_test' -Database $Database -ProfileDataOnly 2>&1 | Out-String
        }
        else {
            $result.Output = & $Sandbox.CopiedScript -ThrowOnFailure -BackupSchema 'reset_backup_test' -Database $Database 2>&1 | Out-String
        }
    }
    catch {
        $result.Threw = $true
        $result.Message = $_.Exception.Message
    }

    $exitCodeVariable = Get-Variable -Name LASTEXITCODE -ErrorAction SilentlyContinue
    $result.ExitCode = if ($null -ne $exitCodeVariable) { [int]$exitCodeVariable.Value } else { 0 }
    [pscustomobject]$result
}

function Assert-ResetScriptHappyPath {
    $sandbox = New-ResetScriptSandbox
    try {
        Use-Sandbox $sandbox {
            $result = Invoke-ResetScript -Sandbox $sandbox

            if ($result.Threw) {
                throw "Expected happy path to succeed, but it threw: $($result.Message)"
            }

            if ($result.ExitCode -ne 0) {
                throw "Expected reset_local_postgres.ps1 to exit with code 0 on the happy path, but observed $($result.ExitCode)."
            }

            if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 1) {
                throw 'Expected one start-helper invocation on the happy path.'
            }

            if ((Get-CountValue -Path $sandbox.PsqlCountFile) -ne 3) {
                throw 'Expected three psql invocations on the happy path.'
            }

            if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 1) {
                throw 'Expected one Maven invocation on the happy path.'
            }

            if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 1) {
                throw 'Expected one smoke invocation on the happy path.'
            }

            $mavenArgs = Get-LogLines -Path $sandbox.MavenArgsFile
            if ($mavenArgs.Count -ne 1 -or $mavenArgs[0] -notmatch '-Dcheckstyle\.skip=true') {
                throw 'Expected Maven to include -Dcheckstyle.skip=true.'
            }

            if ($mavenArgs[0] -notmatch '-Dtest=PostgresqlSchemaBootstrapSmokeTest') {
                throw 'Expected Maven to target PostgresqlSchemaBootstrapSmokeTest.'
            }

            if ($mavenArgs[0] -notmatch '\btest\b') {
                throw 'Expected Maven to run the test goal.'
            }

            $mavenEnv = Get-LogLines -Path $sandbox.MavenEnvFile
            if ($mavenEnv -notcontains 'DATING_APP_DB_DIALECT=POSTGRESQL') {
                throw 'Expected Maven bootstrap to set DATING_APP_DB_DIALECT=POSTGRESQL.'
            }

            if ($mavenEnv -notcontains 'DATING_APP_DB_URL=jdbc:postgresql://localhost:55432/datingapp') {
                throw 'Expected Maven bootstrap to target the local PostgreSQL JDBC URL.'
            }

            if ($mavenEnv -notcontains 'DATING_APP_DB_USERNAME=datingapp') {
                throw 'Expected Maven bootstrap to set DATING_APP_DB_USERNAME=datingapp.'
            }

            if ($mavenEnv -notcontains 'DATING_APP_DB_PASSWORD=datingapp') {
                throw 'Expected Maven bootstrap to set DATING_APP_DB_PASSWORD=datingapp.'
            }

            $psqlArgs = Get-LogLines -Path $sandbox.PsqlArgsFile
            if ($psqlArgs.Count -ne 3) {
                throw 'Expected three captured psql command lines on the happy path.'
            }

            foreach ($line in $psqlArgs) {
                if ($line -notmatch '\s-w(\s|$)') {
                    throw 'Expected psql to use -w.'
                }

                if ($line -notmatch '\s-X(\s|$)') {
                    throw 'Expected psql to use -X.'
                }

                if ($line -notmatch '-v ON_ERROR_STOP=1') {
                    throw 'Expected psql to use -v ON_ERROR_STOP=1.'
                }

                if ($line -notmatch '\s-f\s') {
                    throw 'Expected psql to use a generated SQL file via -f.'
                }
            }

            $smokeArgs = Get-LogLines -Path $sandbox.SmokeArgsFile
            if ($smokeArgs.Count -ne 1 -or $smokeArgs[0] -notmatch 'ThrowOnFailure=True') {
                throw 'Expected the smoke validation call to include -ThrowOnFailure.'
            }

            if ($env:DATING_APP_DB_DIALECT -ne 'SENTINEL_DIALECT' -or
                $env:DATING_APP_DB_URL -ne 'SENTINEL_URL' -or
                $env:DATING_APP_DB_USERNAME -ne 'SENTINEL_USER' -or
                $env:DATING_APP_DB_PASSWORD -ne 'SENTINEL_PASS') {
                throw 'Expected bootstrap environment variables to be restored after the reset script completed.'
            }
        }
    }
    finally {
        Remove-Item -Path $sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Assert-ResetScriptShortCircuitsWhenStartFails {
    $sandbox = New-ResetScriptSandbox -StartExitCode 17
    try {
        Use-Sandbox $sandbox {
            $result = Invoke-ResetScript -Sandbox $sandbox

            if (-not $result.Threw) {
                throw 'Expected the reset script to throw when the start helper fails.'
            }

            if ($result.ExitCode -ne 17) {
                throw "Expected reset_local_postgres.ps1 to propagate exit code 17, but observed $($result.ExitCode)."
            }

            if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 1) {
                throw 'Expected one start-helper invocation when startup fails.'
            }

            if ((Get-CountValue -Path $sandbox.PsqlCountFile) -ne 0) {
                throw 'Expected psql to be skipped when the start helper fails.'
            }

            if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 0) {
                throw 'Expected Maven to be skipped when the start helper fails.'
            }

            if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 0) {
                throw 'Expected the smoke validation to be skipped when the start helper fails.'
            }
        }
    }
    finally {
        Remove-Item -Path $sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Assert-ResetScriptStopsAfterMavenFailure {
    $sandbox = New-ResetScriptSandbox -MavenExitCode 23
    try {
        Use-Sandbox $sandbox {
            $result = Invoke-ResetScript -Sandbox $sandbox

            if (-not $result.Threw) {
                throw 'Expected the reset script to throw when Maven bootstrap fails.'
            }

            if ($result.ExitCode -ne 23) {
                throw "Expected reset_local_postgres.ps1 to propagate exit code 23, but observed $($result.ExitCode)."
            }

            if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 1) {
                throw 'Expected one start-helper invocation when Maven fails.'
            }

            if ((Get-CountValue -Path $sandbox.PsqlCountFile) -ne 2) {
                throw 'Expected backup and reset psql calls before Maven fails.'
            }

            if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 1) {
                throw 'Expected one Maven invocation when Maven fails.'
            }

            if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 0) {
                throw 'Expected the smoke validation to be skipped when Maven fails.'
            }

            if ($env:DATING_APP_DB_DIALECT -ne 'SENTINEL_DIALECT' -or
                $env:DATING_APP_DB_URL -ne 'SENTINEL_URL' -or
                $env:DATING_APP_DB_USERNAME -ne 'SENTINEL_USER' -or
                $env:DATING_APP_DB_PASSWORD -ne 'SENTINEL_PASS') {
                throw 'Expected bootstrap environment variables to be restored after a Maven failure.'
            }
        }
    }
    finally {
        Remove-Item -Path $sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Assert-ResetScriptGeneratedImportSqlIncludesNormalizationAndTextFilters {
    $sandbox = New-ResetScriptSandbox -MavenExitCode 23
    $keptTempDirectory = $null
    try {
        Use-Sandbox $sandbox {
            $result = Invoke-ResetScript -Sandbox $sandbox

            if (-not $result.Threw) {
                throw 'Expected the reset script to throw when Maven bootstrap fails so the generated SQL files are preserved.'
            }

            $keptTempDirectory = Get-KeptTempDirectory -Message $result.Message
            $importSqlPath = Join-Path $keptTempDirectory 'import.sql'

            if (-not (Test-Path $importSqlPath)) {
                throw "Expected import.sql to be preserved at $importSqlPath."
            }

            $importSql = Get-Content -Path $importSqlPath -Raw

            if (-not $importSql.Contains('INSERT INTO public.messages')) {
                throw 'Expected import.sql to contain the messages import statement.'
            }

            if (-not $importSql.Contains("BTRIM(msg.`"content`") <> ''")) {
                throw 'Expected import.sql to filter blank message content before import.'
            }

            if (-not $importSql.Contains("BTRIM(src.`"content`") <> ''")) {
                throw 'Expected import.sql to filter blank profile note content before import.'
            }

            if (-not $importSql.Contains("CASE WHEN UPPER(BTRIM(src.`"value`")) = 'YES' THEN 'SOMEDAY' WHEN UPPER(BTRIM(src.`"value`")) = 'OPEN_TO_IT' THEN 'OPEN'")) {
                throw 'Expected import.sql to normalize legacy wants-kids values during import.'
            }
        }
    }
    finally {
        if ($keptTempDirectory -and (Test-Path $keptTempDirectory)) {
            Remove-Item -Path $keptTempDirectory -Recurse -Force -ErrorAction SilentlyContinue
        }
        Remove-Item -Path $sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Assert-ResetScriptRejectsUnsafeDatabaseName {
    $sandbox = New-ResetScriptSandbox
    try {
        Use-Sandbox $sandbox {
            $result = Invoke-ResetScript -Sandbox $sandbox -Database "bad-name';DROP DATABASE postgres;--"

            if (-not $result.Threw) {
                throw 'Expected reset_local_postgres.ps1 to reject an unsafe database name.'
            }

            if ($result.Message -notmatch 'Database name must match') {
                throw 'Expected the reset script to report the database-name validation failure.'
            }

            if ((Get-CountValue -Path $sandbox.StartCountFile) -ne 0) {
                throw 'Expected database-name validation to happen before invoking the start helper.'
            }

            if ((Get-CountValue -Path $sandbox.PsqlCountFile) -ne 0) {
                throw 'Expected database-name validation to happen before invoking psql.'
            }

            if ((Get-CountValue -Path $sandbox.MavenCountFile) -ne 0) {
                throw 'Expected database-name validation to happen before invoking Maven.'
            }

            if ((Get-CountValue -Path $sandbox.SmokeCountFile) -ne 0) {
                throw 'Expected database-name validation to happen before invoking the smoke validation.'
            }
        }
    }
    finally {
        Remove-Item -Path $sandbox.TempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Assert-ResetScriptHappyPath
Assert-ResetScriptShortCircuitsWhenStartFails
Assert-ResetScriptStopsAfterMavenFailure
Assert-ResetScriptGeneratedImportSqlIncludesNormalizationAndTextFilters
Assert-ResetScriptRejectsUnsafeDatabaseName

Write-Output 'ResetLocalPostgresScriptTest passed.'
