param(
    [string]$BaseUrl = "https://localhost",
    [string]$TemporaryPasswordFile = "secrets/bootstrap_admin_password",
    [string]$PermanentPasswordFile = "secrets/smoke_admin_password"
)

$ErrorActionPreference = "Stop"
$temporaryPath = [System.IO.Path]::GetFullPath((Join-Path $PWD $TemporaryPasswordFile))
$permanentPath = [System.IO.Path]::GetFullPath((Join-Path $PWD $PermanentPasswordFile))
$temporaryPassword = [System.IO.File]::ReadAllText($temporaryPath).Trim()

$randomBytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$randomPart = [Convert]::ToBase64String($randomBytes).Replace("+", "X").Replace("/", "Y").TrimEnd("=")
$permanentPassword = "Aa1!$randomPart"
$headers = @{ Origin = $BaseUrl }

$login = Invoke-RestMethod -SkipCertificateCheck -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
    -Headers $headers -ContentType "application/json" `
    -Body (@{ identifier = "admin"; password = $temporaryPassword } | ConvertTo-Json -Compress)
if ($login.data.user.role -ne "ADMIN" -or -not $login.data.user.passwordChangeRequired) {
    throw "Bootstrap administrator state is invalid"
}

$headers.Authorization = "Bearer $($login.data.accessToken)"
Invoke-RestMethod -SkipCertificateCheck -Method Patch -Uri "$BaseUrl/api/v1/me/password" `
    -Headers $headers -ContentType "application/json" `
    -Body (@{ currentPassword = $temporaryPassword; newPassword = $permanentPassword } | ConvertTo-Json -Compress) | Out-Null

$headers.Remove("Authorization")
$verified = Invoke-RestMethod -SkipCertificateCheck -Method Post -Uri "$BaseUrl/api/v1/auth/login" `
    -Headers $headers -ContentType "application/json" `
    -Body (@{ identifier = "admin"; password = $permanentPassword } | ConvertTo-Json -Compress)
if ($verified.data.user.role -ne "ADMIN" -or $verified.data.user.passwordChangeRequired) {
    throw "Administrator password change was not persisted"
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($permanentPath, $permanentPassword, $utf8)
Remove-Item -LiteralPath $temporaryPath -Force
Write-Output "ADMIN_HTTPS_PASSWORD_CHANGE_OK"
Write-Output "BOOTSTRAP_PASSWORD_REMOVED"
