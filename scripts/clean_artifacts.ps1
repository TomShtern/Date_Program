$path = Get-Location
$pattern = "cUserstom7sAppDataLocalTempclaude-*"

Write-Host "🔍 Scanning for artifact files matching: $pattern" -ForegroundColor Cyan

$files = Get-ChildItem -Path $path -Filter $pattern -File

if ($files.Count -eq 0) {
    Write-Host "✅ No artifacts found. Directory is clean." -ForegroundColor Green
} else {
    Write-Host "⚠️ Found $($files.Count) artifact files. Cleaning up..." -ForegroundColor Yellow
    foreach ($file in $files) {
        try {
            Remove-Item $file.FullName -Force -ErrorAction Stop
            Write-Host "   🗑️ Deleted: $($file.Name)" -ForegroundColor Gray
        } catch {
            Write-Host "   ❌ Failed to delete: $($file.Name) - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    Write-Host "🎉 Cleanup complete." -ForegroundColor Green
}
