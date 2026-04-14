Set-Location "C:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program"
mvn --% -Dcheckstyle.skip=true -Dtest=ImageCacheTest test
Write-Output "__EXITCODE__=$LASTEXITCODE"
