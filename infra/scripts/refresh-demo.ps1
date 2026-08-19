[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $rootDir '.env.prod'
$composeFile = Join-Path $rootDir 'compose.prod.yaml'
$dockerDesktop = 'C:\Users\LENOVO\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
$docker = if (Test-Path -LiteralPath $dockerDesktop) { $dockerDesktop } else { (Get-Command docker -ErrorAction Stop).Source }

if (-not (Test-Path -LiteralPath $envFile)) { throw 'Missing .env.prod. Run the project setup before refreshing the demo.' }

Push-Location $rootDir
try {
    # Keep named volumes intact. The migration resets only probe and telemetry data for demo mode.
    & $docker compose --env-file $envFile -f $composeFile up -d mysql redis emqx minio qdrant
    if ($LASTEXITCODE -ne 0) { throw 'Failed to start demo dependencies.' }

    # Build first so the one-shot migration task runs the same artifact as API and Worker.
    & $docker compose --env-file $envFile -f $composeFile build api worker web
    if ($LASTEXITCODE -ne 0) { throw 'Failed to build the updated demo services.' }

    & $docker compose --env-file $envFile -f $composeFile --profile tools run --rm migrate
    if ($LASTEXITCODE -ne 0) { throw 'Database migration failed. Existing services were left unchanged.' }

    # Force recreation so a previously running container cannot keep serving the old UI or backend jar.
    & $docker compose --env-file $envFile -f $composeFile up -d --force-recreate api worker web
    if ($LASTEXITCODE -ne 0) { throw 'Failed to rebuild or start the demo services.' }

    & $docker compose --env-file $envFile -f $composeFile ps
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the updated demo service status.' }
} finally {
    Pop-Location
}

Write-Output '演示服务已更新。浏览器按 Ctrl+F5 后重新打开 https://localhost。'
