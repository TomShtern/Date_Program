#!/usr/bin/env pwsh
# Run targeted tests for event handlers

Set-Location (Split-Path -Parent $PSScriptRoot)

Write-Host "=== Starting Tests ===" -ForegroundColor Green
Write-Host "Time: $(Get-Date)" -ForegroundColor Gray

# Run just the two handler test classes
Write-Host "`nRunning Notification Event Handler Tests..." -ForegroundColor Cyan
& mvn test -Dtest=NotificationEventHandlerTest -q 2>&1 | ForEach-Object { Write-Host $_ }

Write-Host "`nRunning Metrics Event Handler Tests..." -ForegroundColor Cyan
& mvn test -Dtest=MetricsEventHandlerTest -q 2>&1 | ForEach-Object { Write-Host $_ }

Write-Host "`n=== Tests Complete ===" -ForegroundColor Green
Write-Host "Time: $(Get-Date)" -ForegroundColor Gray
