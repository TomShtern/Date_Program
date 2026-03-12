Set-Location "C:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program"
$out = mvn spotless:apply 2>&1 | Out-String
Write-Host "=== SPOTLESS ==="
$out | Select-String "BUILD (SUCCESS|FAILURE)"

$out2 = mvn verify 2>&1 | Out-String
Write-Host "=== VERIFY BUILD STATUS ==="
$out2 | Select-String "BUILD (SUCCESS|FAILURE)" | Select-Object -Last 1
Write-Host "=== TESTS ==="
$out2 | Select-String "Tests run:" | Select-Object -Last 1
Write-Host "=== ERRORS ==="
$out2 | Select-String "ERROR|FAILURE" | Select-Object -Last 5
