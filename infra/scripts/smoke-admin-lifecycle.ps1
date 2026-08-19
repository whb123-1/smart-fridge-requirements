[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$values = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $rootDir '.env.prod')) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $name, $value = $line -split '=', 2
    $values[$name.Trim()] = $value.Trim().Trim('"').Trim("'")
}
$baseUrl = "https://$($values['APP_DOMAIN'])"
$skipTls = $values['SMOKE_INSECURE_TLS'] -eq 'true' -or $values['CADDY_TLS_MODE'] -eq 'internal'
$webArgs = @{ SkipHttpErrorCheck = $true; TimeoutSec = 30 }
if ($skipTls) { $webArgs['SkipCertificateCheck'] = $true }

function Get-Text([object]$response) {
    if ($response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($response.Content) }
    return [string]$response.Content
}
function Invoke-Json([string]$method, [string]$path, [object]$body = $null, [hashtable]$headers = @{}) {
    $arguments = $webArgs.Clone()
    $arguments['Method'] = $method
    $arguments['Uri'] = "$baseUrl$path"
    $arguments['Headers'] = $headers
    if ($null -ne $body) {
        $arguments['ContentType'] = 'application/json'
        $arguments['Body'] = $body | ConvertTo-Json -Compress
    }
    return Invoke-WebRequest @arguments
}
function Assert-Status([object]$response, [int[]]$expected, [string]$operation) {
    if ($response.StatusCode -notin $expected) { throw "$operation returned HTTP $($response.StatusCode)" }
}
function New-IdempotencyHeaders([string]$token) {
    return @{ Authorization = "Bearer $token"; 'Idempotency-Key' = [Guid]::NewGuid().ToString() }
}
function Invoke-LoginWithRetry([hashtable]$body, [string]$operation) {
    $response = Invoke-Json POST '/api/v1/auth/login' $body
    if ($response.StatusCode -eq 429) {
        $retryAfter = [int](($response.Headers['Retry-After'] | Select-Object -First 1) ?? 0)
        if ($retryAfter -lt 1 -or $retryAfter -gt 65) {
            throw "$operation was rate limited without a safe Retry-After value"
        }
        Start-Sleep -Seconds ($retryAfter + 1)
        $response = Invoke-Json POST '/api/v1/auth/login' $body
    }
    Assert-Status $response @(200) $operation
    return $response
}

$adminPassword = (Get-Content -Raw -LiteralPath (Join-Path $rootDir 'secrets\smoke_admin_password')).Trim()
$adminLogin = Invoke-LoginWithRetry @{ identifier = 'admin'; password = $adminPassword } 'admin login'
$adminToken = ((Get-Text $adminLogin) | ConvertFrom-Json).data.accessToken
$username = 'smoke_' + (Get-Date -Format 'yyyyMMddHHmmss')
$email = "$username@smoke.invalid"
$initialPassword = 'S!' + [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$changedPassword = 'N!' + [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$userId = $null
$finallyDeleted = $false

try {
    $register = Invoke-Json POST '/api/v1/auth/register' @{ username = $username; email = $email; password = $initialPassword; displayName = 'Production Smoke User' }
    Assert-Status $register @(201) 'user registration'
    $registeredSession = (Get-Text $register) | ConvertFrom-Json
    $userId = $registeredSession.data.user.id
    $userToken = $registeredSession.data.accessToken

    $search = Invoke-Json GET "/api/v1/admin/users?query=$username&page=0&size=20" $null @{ Authorization = "Bearer $adminToken" }
    Assert-Status $search @(200) 'admin user search'
    if (((Get-Text $search) | ConvertFrom-Json).data.items.id -notcontains $userId) { throw 'registered user was not found by admin search' }

    $disable = Invoke-Json PATCH "/api/v1/admin/users/$userId/status" @{ status = 'DISABLED' } (New-IdempotencyHeaders $adminToken)
    Assert-Status $disable @(200) 'disable user'
    $invalidated = Invoke-Json GET '/api/v1/me' $null @{ Authorization = "Bearer $userToken" }
    Assert-Status $invalidated @(401) 'disabled user immediate token invalidation'

    $enable = Invoke-Json PATCH "/api/v1/admin/users/$userId/status" @{ status = 'ACTIVE' } (New-IdempotencyHeaders $adminToken)
    Assert-Status $enable @(200) 'enable user'
    $userLogin = Invoke-LoginWithRetry @{ identifier = $username; password = $initialPassword } 'reactivated user login'
    $userToken = ((Get-Text $userLogin) | ConvertFrom-Json).data.accessToken

    $revoke = Invoke-Json POST "/api/v1/admin/users/$userId/sessions/revoke" $null (New-IdempotencyHeaders $adminToken)
    Assert-Status $revoke @(200) 'revoke sessions'
    $revoked = Invoke-Json GET '/api/v1/me' $null @{ Authorization = "Bearer $userToken" }
    Assert-Status $revoked @(401) 'revoked access token invalidation'

    $reset = Invoke-Json POST "/api/v1/admin/users/$userId/password-reset" $null (New-IdempotencyHeaders $adminToken)
    Assert-Status $reset @(200) 'password reset'
    $temporaryPassword = ((Get-Text $reset) | ConvertFrom-Json).data.temporaryPassword
    if ($temporaryPassword.Length -ne 24) { throw 'temporary password does not have 24 characters' }
    $temporaryLogin = Invoke-LoginWithRetry @{ identifier = $username; password = $temporaryPassword } 'temporary password login'
    $temporarySession = (Get-Text $temporaryLogin) | ConvertFrom-Json
    if (-not $temporarySession.data.user.passwordChangeRequired) { throw 'temporary login did not require password change' }
    $blocked = Invoke-Json GET '/api/v1/fridges' $null @{ Authorization = "Bearer $($temporarySession.data.accessToken)" }
    Assert-Status $blocked @(403) 'password-change gate'
    if (((Get-Text $blocked) | ConvertFrom-Json).code -ne 'PASSWORD_CHANGE_REQUIRED') { throw 'password-change gate returned the wrong code' }
    $change = Invoke-Json PATCH '/api/v1/me/password' @{ currentPassword = $temporaryPassword; newPassword = $changedPassword } @{ Authorization = "Bearer $($temporarySession.data.accessToken)" }
    Assert-Status $change @(200) 'required password change'

    $promote = Invoke-Json PATCH "/api/v1/admin/users/$userId/role" @{ role = 'ADMIN' } (New-IdempotencyHeaders $adminToken)
    Assert-Status $promote @(200) 'promote user'
    $promotedLogin = Invoke-LoginWithRetry @{ identifier = $username; password = $changedPassword } 'promoted administrator login'
    $promotedToken = ((Get-Text $promotedLogin) | ConvertFrom-Json).data.accessToken
    $adminAccess = Invoke-Json GET '/api/v1/admin/users?page=0&size=1' $null @{ Authorization = "Bearer $promotedToken" }
    Assert-Status $adminAccess @(200) 'promoted administrator authorization'
    $demote = Invoke-Json PATCH "/api/v1/admin/users/$userId/role" @{ role = 'USER' } (New-IdempotencyHeaders $adminToken)
    Assert-Status $demote @(200) 'demote user'
    $demotedAccess = Invoke-Json GET '/api/v1/admin/users?page=0&size=1' $null @{ Authorization = "Bearer $promotedToken" }
    Assert-Status $demotedAccess @(401, 403) 'demotion immediate invalidation'

    $delete = Invoke-Json DELETE "/api/v1/admin/users/$userId" $null (New-IdempotencyHeaders $adminToken)
    Assert-Status $delete @(200) 'soft delete user'
    $restore = Invoke-Json POST "/api/v1/admin/users/$userId/restore" $null (New-IdempotencyHeaders $adminToken)
    Assert-Status $restore @(200) 'restore user'
    $restoredLogin = Invoke-LoginWithRetry @{ identifier = $username; password = $changedPassword } 'restored user login'

    $audit = Invoke-Json GET "/api/v1/admin/users/$userId/audit-logs?page=0&size=100" $null @{ Authorization = "Bearer $adminToken" }
    Assert-Status $audit @(200) 'admin audit query'
    if (((Get-Text $audit) | ConvertFrom-Json).data.total -lt 8) { throw 'administrator lifecycle audit is incomplete' }

    $finalDelete = Invoke-Json DELETE "/api/v1/admin/users/$userId" $null (New-IdempotencyHeaders $adminToken)
    Assert-Status $finalDelete @(200) 'final smoke-user soft delete'
    $finallyDeleted = $true
} finally {
    if ($null -ne $userId -and -not $finallyDeleted) {
        $null = Invoke-Json DELETE "/api/v1/admin/users/$userId" $null (New-IdempotencyHeaders $adminToken)
    }
}

Write-Output '管理员生产生命周期冒烟通过：搜索、启停、即时失效、强退、临时密码、强制改密、升降权、软删除、恢复与审计均正常。'
