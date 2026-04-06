Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
Set-Location $repoRoot

$overallExitCode = 0

Write-Host 'Running Maven quality gate: mvn spotless:apply verify'

try {
    & mvn spotless:apply verify
    $overallExitCode = $LASTEXITCODE

    if ($overallExitCode -ne 0) {
        Write-Host "Maven quality gate failed with exit code $overallExitCode."
    }
    else {
        Write-Host 'Running PostgreSQL smoke verification: run_postgresql_smoke.ps1'
        try {
            & (Join-Path $repoRoot 'run_postgresql_smoke.ps1') -ThrowOnFailure
            $overallExitCode = 0
            Write-Host 'PostgreSQL smoke verification passed.'
        }
        catch {
            $overallExitCode = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
            Write-Host "PostgreSQL smoke verification failed with exit code $overallExitCode."
            Write-Host $_.Exception.Message
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
