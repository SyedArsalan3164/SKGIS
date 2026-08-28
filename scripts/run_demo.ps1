# SKGIS One-Command PowerShell Demo Launcher
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Semantic Knowledge Graph Intelligence System (SKGIS)   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Resolve Docker Compose command
$dockerCmd = $null
if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
    $dockerCmd = "docker-compose"
} elseif (Test-Path "C:\Program Files\Docker\Docker\resources\bin\docker-compose.exe") {
    $dockerCmd = "& 'C:\Program Files\Docker\Docker\resources\bin\docker-compose.exe'"
}

Write-Host "[1/5] Checking Docker Compose & Starting Neo4j 5.x Container..." -ForegroundColor Yellow
if ($dockerCmd) {
    Invoke-Expression "$dockerCmd up -d"
    Write-Host "Waiting 15 seconds for Neo4j database initialization..." -ForegroundColor Gray
    Start-Sleep -Seconds 15
} else {
    Write-Host "Docker Compose not found. Please ensure Docker Desktop is open and running." -ForegroundColor Red
}

Write-Host "[2/5] Ensuring Sample Data Exists..." -ForegroundColor Yellow
python scripts/generate_sample_data.py

# 2. Resolve Maven command
$mvnCmd = $null
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnCmd = "mvn"
} elseif (Test-Path ".\.tools\maven\apache-maven-3.9.6\bin\mvn.cmd") {
    $mvnCmd = ".\.tools\maven\apache-maven-3.9.6\bin\mvn.cmd"
}

Write-Host "[3/5] Checking Maven Build System..." -ForegroundColor Yellow
if ($mvnCmd) {
    Invoke-Expression "$mvnCmd clean package -DskipTests"
    Write-Host "[4/5] Launching SKGIS Service..." -ForegroundColor Yellow
    Start-Process $mvnCmd -ArgumentList "spring-boot:run"
    Start-Sleep -Seconds 10

    Write-Host "Triggering Batch Ingestion API..." -ForegroundColor Green
    Invoke-RestMethod -Uri "http://localhost:8080/api/ingest/run" -Method Post

    Write-Host "Triggering Risk Detection Pipeline..." -ForegroundColor Green
    Invoke-RestMethod -Uri "http://localhost:8080/api/risk/detect" -Method Post
} else {
    Write-Host "Maven command not found." -ForegroundColor Red
}

Write-Host "[5/5] Opening vis.js Graph Intelligence Explorer..." -ForegroundColor Yellow
Start-Process "frontend/index.html"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "SKGIS Setup Complete!" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
