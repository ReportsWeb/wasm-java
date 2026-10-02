$ErrorActionPreference = 'Stop'
docker compose -f (Join-Path $PSScriptRoot 'compose.yaml') up -d --build --wait --wait-timeout 180
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Start-Process 'http://127.0.0.1:19212/'
