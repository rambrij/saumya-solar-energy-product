$ErrorActionPreference = "Stop"
Write-Host "WARNING: deleting local development containers and MySQL volume" -ForegroundColor Yellow
docker compose down -v --remove-orphans
docker compose build --no-cache backend frontend
docker compose up
