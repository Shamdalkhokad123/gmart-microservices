# Starts the GMart microservices, each in its own PowerShell window.
#
#   .\start-all.ps1            # local dev with Docker MySQL / Redis / Kafka (C:\Users\ksham\dev-services)
#   .\start-all.ps1 -Offline   # local dev without Docker: in-memory H2 databases, no Redis/Kafka
#
# Both use the "dev" profile: public development secrets plus demo data
# (admin / 12345678, john_doe / password123). Never use it in production.
#
# Online payments need the Razorpay key secret in this window first:
#   $env:RAZORPAY_KEY_SECRET = "..."
param([switch]$Offline)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

Write-Host "Building..." -ForegroundColor Cyan
Push-Location $root
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Build failed" }
Pop-Location

if (-not $env:RAZORPAY_KEY_SECRET) {
    Write-Warning "RAZORPAY_KEY_SECRET is not set: 'Pay online' will be unavailable (cash on delivery still works)."
}

$profileArg = if ($Offline) { "--spring.profiles.active=h2" } else { "--spring.profiles.active=dev" }
$services = @(
    @{ Name = "user-service";    Port = 8081 },
    @{ Name = "catalog-service"; Port = 8082 },
    @{ Name = "order-service";   Port = 8083 },
    @{ Name = "payment-service"; Port = 8084 },
    @{ Name = "store-service";   Port = 8085 },
    @{ Name = "api-gateway";     Port = 8080 }
)

foreach ($s in $services) {
    $jar = Join-Path $root "$($s.Name)\target\$($s.Name)-1.0.0-SNAPSHOT.jar"
    $title = "$($s.Name) :$($s.Port)"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = '$title'; java -jar '$jar' $profileArg"
}

Write-Host ""
Write-Host "Starting (give it a minute):" -ForegroundColor Green
$services | ForEach-Object { Write-Host ("  {0,-16} http://localhost:{1}/api" -f $_.Name, $_.Port) }
Write-Host "Frontend talks to the gateway: http://localhost:8080/api"
