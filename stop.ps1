# Stop the services started by run.ps1
Set-Location $PSScriptRoot
if (Test-Path logs\pids.txt) {
    foreach ($id in Get-Content logs\pids.txt) {
        try { Stop-Process -Id $id -ErrorAction Stop; Write-Host "stopped pid $id" } catch {}
    }
    Remove-Item logs\pids.txt
}
