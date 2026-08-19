[CmdletBinding()]
param(
    [string]$DeviceId,
    [switch]$Compact,
    [switch]$Once,
    [ValidateRange(0, 86400)]
    [int]$DurationSeconds = 0
)

$ErrorActionPreference = 'Stop'
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$python = (Get-Command python.exe -ErrorAction SilentlyContinue)?.Source
if (-not $python) { $python = (Get-Command python -ErrorAction SilentlyContinue)?.Source }
if (-not $python) { throw '找不到 Python。请安装 Python 3.11+。' }

$oldPythonPath = $env:PYTHONPATH
try {
    & $python -c 'import paho.mqtt.client' 2>$null
    if ($LASTEXITCODE -ne 0) {
        $dependencyPath = @(
            (Join-Path $rootDir '.tmp\mqtt-watch'),
            (Join-Path $rootDir '.tmp\mqtt-load')
        ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
        if (-not $dependencyPath) {
            throw '缺少 paho-mqtt。请先执行：python -m pip install --target .tmp/mqtt-watch -r infra/load/requirements.txt'
        }
        $env:PYTHONPATH = if ($oldPythonPath) {
            $dependencyPath + [IO.Path]::PathSeparator + $oldPythonPath
        } else {
            $dependencyPath
        }
    }

    $arguments = @((Join-Path $PSScriptRoot 'watch-mqtt.py'))
    if ($DeviceId) { $arguments += @('--device-id', $DeviceId) }
    if ($Compact) { $arguments += '--compact' }
    if ($Once) { $arguments += '--once' }
    if ($DurationSeconds -gt 0) { $arguments += @('--duration-seconds', $DurationSeconds) }
    & $python @arguments
    if ($LASTEXITCODE -ne 0) { throw "MQTT 观测器退出码为 $LASTEXITCODE。" }
} finally {
    $env:PYTHONPATH = $oldPythonPath
}
