Set-Location $PSScriptRoot
mvn --% -Dcheckstyle.skip=true -Dtest=ImageCacheTest test
Write-Output "__EXITCODE__=$LASTEXITCODE"
exit $LASTEXITCODE
