# Build and start all the services (logs in logs\). Stop with .\stop.ps1
Set-Location $PSScriptRoot

mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force logs | Out-Null

$services = @(
    @{ Name = 'gateway'; Jar = 'gateway\target\gateway.jar' },
    @{ Name = 'lamp';    Jar = 'thing-lamp\target\thing-lamp.jar' }
    # TODO: thermostat, motion sensor
)
$ids = foreach ($s in $services) {
    $p = Start-Process java -ArgumentList '-jar', $s.Jar -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput "logs\$($s.Name).log" -RedirectStandardError "logs\$($s.Name).err.log"
    Write-Host "started $($s.Name) (pid $($p.Id))"
    $p.Id
}
$ids | Set-Content logs\pids.txt
Write-Host 'dashboard: http://localhost:8080/  (token: operator-secret) - stop with .\stop.ps1'
