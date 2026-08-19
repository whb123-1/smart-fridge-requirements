[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $rootDir '.env.prod'
$values = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $name, $value = $line -split '=', 2
    $values[$name.Trim()] = $value.Trim().Trim('"').Trim("'")
}

$domain = $values['APP_DOMAIN']
if ([string]::IsNullOrWhiteSpace($domain)) { throw 'APP_DOMAIN is required in .env.prod' }
$baseUrl = "https://$domain"
$skipTls = $values['SMOKE_INSECURE_TLS'] -eq 'true' -or $values['CADDY_TLS_MODE'] -eq 'internal'
$webArgs = @{ SkipHttpErrorCheck = $true; TimeoutSec = 30 }
if ($skipTls) { $webArgs['SkipCertificateCheck'] = $true }
function Get-ResponseText([object]$response) {
    if ($response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($response.Content) }
    return [string]$response.Content
}

$health = Invoke-WebRequest @webArgs -Uri "$baseUrl/healthz"
if ($health.StatusCode -ne 200 -or ((Get-ResponseText $health) | ConvertFrom-Json).status -ne 'UP') { throw 'HTTPS readiness failed' }
foreach ($header in @('Strict-Transport-Security', 'X-Content-Type-Options', 'X-Frame-Options', 'Referrer-Policy')) {
    if ($null -eq $health.Headers[$header]) { throw "Missing security header: $header" }
}

$httpHeaders = (& curl.exe --max-redirs 0 --max-time 10 -sS -D - -o NUL "http://$domain/" 2>$null) -join "`n"
if ($httpHeaders -notmatch 'HTTP/[0-9.]+ 30[1278]' -or $httpHeaders -notmatch '(?im)^Location:\s*https://') { throw 'HTTP does not redirect to HTTPS' }

$passwordFile = Join-Path $rootDir 'secrets\smoke_admin_password'
if (-not (Test-Path -LiteralPath $passwordFile) -or (Get-Item -LiteralPath $passwordFile).Length -eq 0) { throw 'Missing secrets/smoke_admin_password' }
$loginBody = @{ identifier = 'admin'; password = (Get-Content -Raw -LiteralPath $passwordFile).Trim() } | ConvertTo-Json -Compress
$login = Invoke-WebRequest @webArgs -Method Post -ContentType 'application/json' -Body $loginBody -Uri "$baseUrl/api/v1/auth/login"
$session = (Get-ResponseText $login) | ConvertFrom-Json
if ($login.StatusCode -ne 200 -or $session.data.user.role -ne 'ADMIN' -or $session.data.user.passwordChangeRequired) { throw 'Administrator login failed' }
$cookie = ($login.Headers['Set-Cookie'] -join ';')
if ($cookie -notmatch '(?i)Secure' -or $cookie -notmatch '(?i)HttpOnly' -or $cookie -notmatch '(?i)SameSite=(Strict|Lax)') { throw 'Refresh cookie flags are incomplete' }
$authHeaders = @{ Authorization = "Bearer $($session.data.accessToken)" }

$users = Invoke-WebRequest @webArgs -Headers $authHeaders -Uri "$baseUrl/api/v1/admin/users?page=0&size=1"
if ($users.StatusCode -ne 200 -or ((Get-ResponseText $users) | ConvertFrom-Json).code -ne 'OK') { throw 'Administrator user query failed' }

$crossOrigin = Invoke-WebRequest @webArgs -Method Patch -Headers ($authHeaders + @{ Origin = 'https://cross-origin.invalid'; 'Sec-Fetch-Site' = 'cross-site' }) -ContentType 'application/json' -Body '{}' -Uri "$baseUrl/api/v1/me/password"
if ($crossOrigin.StatusCode -ne 403 -or ((Get-ResponseText $crossOrigin) | ConvertFrom-Json).code -ne 'CROSS_ORIGIN_REQUEST_REJECTED') { throw 'Cross-origin write protection failed' }

foreach ($path in @('/v3/api-docs', '/swagger-ui/index.html')) {
    $disabled = Invoke-WebRequest @webArgs -Uri "$baseUrl$path"
    if ($disabled.StatusCode -ne 404) { throw "Production endpoint is not disabled: $path" }
}
$debug = Invoke-WebRequest @webArgs -Headers $authHeaders -Uri "$baseUrl/api/v1/debug/telemetry/scenarios"
if ($debug.StatusCode -ne 404) { throw 'Debug telemetry endpoint is not disabled' }

$metrics = docker compose --env-file .env.prod -f compose.prod.yaml exec -T worker curl --fail --silent http://localhost:8080/actuator/prometheus
$metricsText = $metrics -join "`n"
if ($LASTEXITCODE -ne 0 -or $metricsText -notmatch '(?m)^xianzhi_worker_heartbeat_age_seconds\s+([0-9.]+)$') { throw 'Worker heartbeat metric is missing' }
if ([double]$Matches[1] -ge 120) { throw 'Worker heartbeat is stale' }

if ($metricsText -notmatch '(?m)^xianzhi_virtual_simulator_connected\s+1(?:\.0+)?$') { throw 'Virtual probe simulator is not connected' }

Write-Output '生产冒烟通过：HTTPS、安全头与 Cookie、管理员 API、同源保护、虚拟探头发布器、Worker、Swagger/Debug 禁用均正常。'
