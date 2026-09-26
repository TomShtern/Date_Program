Set-Location (Split-Path -Parent $PSScriptRoot)
mvn --% -Dcheckstyle.skip=true -Dtest=ImageCacheTest test
Write-Output "__EXITCODE__=$LASTEXITCODE"

# `exit` terminates the caller's PowerShell session. Only exit when invoked as
# a child process (pwsh -File), where the process exit code is the only way to
# report failure. Interactive callers read the propagated $LASTEXITCODE.
if ([string]::IsNullOrEmpty($MyInvocation.Line)) {
    exit $LASTEXITCODE
}
$global:LASTEXITCODE = $LASTEXITCODE
