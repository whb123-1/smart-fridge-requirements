[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$EnableProduction
)

$ErrorActionPreference = 'Stop'
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$secretDir = Join-Path $rootDir 'secrets'
$targets = @{
    DeepSeek = Join-Path $secretDir 'deepseek_api_key'
    OpenAI = Join-Path $secretDir 'openai_api_key'
}

if (-not (Test-Path -LiteralPath $secretDir -PathType Container)) {
    throw 'secrets 目录不存在。请先执行 infra/scripts/init-secrets.sh。'
}

foreach ($entry in $targets.GetEnumerator()) {
    if ((Test-Path -LiteralPath $entry.Value) -and (Get-Item -LiteralPath $entry.Value).Length -gt 0 -and -not $Force) {
        throw "$($entry.Key) Secret 已存在。轮换时请显式添加 -Force。"
    }
}

function Read-ConfirmedSecret([string]$Provider) {
    while ($true) {
        $first = Read-Host "$Provider API Key" -AsSecureString
        $second = Read-Host "再次输入 $Provider API Key" -AsSecureString
        $firstPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($first)
        $secondPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($second)
        try {
            $firstValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($firstPointer)
            $secondValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secondPointer)
            if ($firstValue.Length -lt 20) {
                Write-Warning "$Provider API Key 长度不足，请重新输入。"
                continue
            }
            if ($firstValue -cne $secondValue) {
                Write-Warning "$Provider 两次输入不一致，请重新输入。"
                continue
            }
            return $firstValue
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($firstPointer)
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secondPointer)
        }
    }
}

$deepSeekSecret = Read-ConfirmedSecret 'DeepSeek'
$openAiSecret = Read-ConfirmedSecret 'OpenAI'
$encoding = [Text.UTF8Encoding]::new($false)

try {
    [IO.File]::WriteAllText($targets.DeepSeek, $deepSeekSecret, $encoding)
    [IO.File]::WriteAllText($targets.OpenAI, $openAiSecret, $encoding)
} finally {
    $deepSeekSecret = $null
    $openAiSecret = $null
}

if ($EnableProduction) {
    $envPath = Join-Path $rootDir '.env.prod'
    if (-not (Test-Path -LiteralPath $envPath)) { throw '.env.prod 不存在，无法启用生产 AI 配置。' }
    $required = [ordered]@{
        SPEECH_PROVIDER='openai'
        SPEECH_BASE_URL='https://api.openai.com/v1'
        SPEECH_MODEL='whisper-1'
        STORAGE_PROVIDER='s3'
        STORAGE_ENDPOINT='http://minio:9000'
        STORAGE_REGION='us-east-1'
        STORAGE_BUCKET='xianzhi-speech'
        STORAGE_PATH_STYLE='true'
        AI_EXTERNAL_CALLS_ENABLED='true'
        AI_BASE_URL='https://api.deepseek.com/v1'
        AI_MODEL_NAME='deepseek-chat'
        AI_VECTOR_ENABLED='true'
        EMBEDDING_PROVIDER='openai'
        EMBEDDING_BASE_URL='https://api.openai.com/v1'
        EMBEDDING_MODEL='text-embedding-3-small'
        EMBEDDING_DIMENSIONS='1536'
    }
    $lines = [System.Collections.Generic.List[string]](Get-Content -LiteralPath $envPath)
    foreach ($name in $required.Keys) {
        $index = -1
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match "^\s*$([regex]::Escape($name))=") { $index = $i; break }
        }
        $line = "$name=$($required[$name])"
        if ($index -ge 0) { $lines[$index] = $line } else { [void]$lines.Add($line) }
    }
    [IO.File]::WriteAllLines($envPath, $lines, $encoding)
    Write-Output 'AI_PRODUCTION_FLAGS_ENABLED'
}

Write-Output 'AI_PROVIDER_SECRETS_WRITTEN'
Write-Output '已写入 deepseek_api_key 和 openai_api_key；未输出密钥内容。'
