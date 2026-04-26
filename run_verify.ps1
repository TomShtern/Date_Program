Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
Set-Location $repoRoot

$overallExitCode = 0
$verifyStopwatch = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host 'Starting local PostgreSQL: start_local_postgres.ps1'

try {
    & (Join-Path $repoRoot 'start_local_postgres.ps1')
    $overallExitCode = $LASTEXITCODE

    if ($overallExitCode -ne 0) {
        Write-Host "[STARTUP] Local PostgreSQL startup failed with exit code $overallExitCode."
        Write-Host "  Hint: Run .\check_postgresql_runtime_env.ps1 to diagnose the environment."
    }
    else {
        Write-Host 'Running Maven quality gate: mvn spotless:apply verify'
        & mvn @('spotless:apply', 'verify')
        $overallExitCode = $LASTEXITCODE
        $verifyStopwatch.Stop()

        if ($overallExitCode -ne 0) {
            Write-Host "[MAVEN] Maven quality gate failed with exit code $overallExitCode after $($verifyStopwatch.Elapsed)."
            Write-Host "  Hint: Fix formatting/checkstyle/test failures above, then re-run .\run_verify.ps1."
        }
        else {
            Write-Host 'Running PostgreSQL smoke verification: run_postgresql_smoke.ps1'
            $smokeStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                & (Join-Path $repoRoot 'run_postgresql_smoke.ps1') -ThrowOnFailure
                $overallExitCode = 0
                $smokeStopwatch.Stop()
                Write-Host "PostgreSQL smoke verification passed in $($smokeStopwatch.Elapsed)."
            }
            catch {
                $overallExitCode = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
                $smokeStopwatch.Stop()
                Write-Host "[MAVEN-SMOKE] PostgreSQL smoke verification failed with exit code $overallExitCode after $($smokeStopwatch.Elapsed)."
                Write-Host "  Hint: The server is still running. Check PostgresqlRuntimeSmokeTest output for details."
                Write-Host $_.Exception.Message
            }
        }
    }
}
finally {
    Write-Host 'Stopping local PostgreSQL: stop_local_postgres.ps1'

    try {
        & (Join-Path $repoRoot 'stop_local_postgres.ps1')

        if ($LASTEXITCODE -ne 0) {
            Write-Host "Local PostgreSQL stop reported exit code $LASTEXITCODE."

            if ($overallExitCode -eq 0) {
                $overallExitCode = $LASTEXITCODE
            }
        }
    }
    catch {
        Write-Host "Local PostgreSQL stop threw an exception: $($_.Exception.Message)"

        if ($overallExitCode -eq 0) {
            $overallExitCode = 1
        }
    }
}

exit $overallExitCode
