[CmdletBinding()]
param(
    [ValidateRange(1, 3600)]
    [int]$IntervalSeconds = 2,
    [ValidateRange(1, 100)]
    [int]$Limit = 10,
    [switch]$Once
)

$ErrorActionPreference = 'Stop'
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
if ($dockerCommand) {
    $docker = $dockerCommand.Source
} else {
    $docker = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
}
if (-not (Test-Path -LiteralPath $docker)) {
    throw '找不到 Docker CLI。请启动 Docker Desktop，或把 docker.exe 加入 PATH。'
}

$composeArgs = @('compose', '--env-file', '.env.prod', '-f', 'compose.prod.yaml')
$containerId = (& $docker @composeArgs 'ps' '-q' 'mysql').Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    throw 'MySQL 生产容器未运行。'
}

$query = @"
SELECT '最新 MQTT 消息' AS section;
SELECT DATE_FORMAT(received_at,'%Y-%m-%d %H:%i:%s.%f') AS received_utc,
       BIN_TO_UUID(device_id) AS device_id, message_id, status,
       COALESCE(rejection_code,'') AS rejection_code, source
FROM telemetry_message ORDER BY received_at DESC LIMIT $Limit;

SELECT '最新传感器读数' AS section;
SELECT DATE_FORMAT(received_at,'%Y-%m-%d %H:%i:%s.%f') AS received_utc,
       BIN_TO_UUID(device_id) AS device_id, BIN_TO_UUID(sensor_id) AS sensor_id,
       metric, value, unit, quality, source
FROM sensor_reading ORDER BY received_at DESC LIMIT $Limit;

SELECT '当前分区环境状态' AS section;
SELECT z.name AS zone_name, s.metric, s.current_value, s.current_unit,
       s.current_quality, DATE_FORMAT(s.last_received_at,'%Y-%m-%d %H:%i:%s.%f') AS last_received_utc,
       DATE_FORMAT(s.updated_at,'%Y-%m-%d %H:%i:%s.%f') AS updated_utc
FROM zone_environment_state s
JOIN fridge_zone z ON z.id=s.zone_id
ORDER BY s.updated_at DESC LIMIT $Limit;

SELECT '最近环境事件' AS section;
SELECT BIN_TO_UUID(zone_id) AS zone_id, metric, reason, severity, status,
       DATE_FORMAT(started_at,'%Y-%m-%d %H:%i:%s.%f') AS started_utc,
       DATE_FORMAT(ended_at,'%Y-%m-%d %H:%i:%s.%f') AS ended_utc
FROM environment_incident ORDER BY updated_at DESC LIMIT $Limit;

SELECT '最新库存流水（不显示食材名称或幂等键）' AS section;
SELECT DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i:%s.%f') AS created_utc,
       BIN_TO_UUID(t.id) AS transaction_id, BIN_TO_UUID(t.batch_id) AS batch_id,
       BIN_TO_UUID(i.id) AS item_id, i.category, t.type,
       t.before_quantity, t.after_quantity, t.quantity_delta, t.unit,
       COALESCE(t.source_type,'') AS source_type
FROM inventory_transaction t
JOIN inventory_batch b ON b.id=t.batch_id
JOIN inventory_item i ON i.id=b.item_id
ORDER BY t.created_at DESC LIMIT $Limit;

SELECT '最近 Outbox 状态（不显示 payload 或错误详情）' AS section;
SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') AS created_utc,
       BIN_TO_UUID(id) AS event_id, aggregate_type, event_type, status, attempts,
       DATE_FORMAT(available_at,'%Y-%m-%d %H:%i:%s.%f') AS available_utc,
       DATE_FORMAT(processed_at,'%Y-%m-%d %H:%i:%s.%f') AS processed_utc
FROM outbox_event ORDER BY created_at DESC LIMIT $Limit;

SELECT '核心表计数' AS section;
SELECT 'telemetry_message' AS table_name, COUNT(*) AS row_count FROM telemetry_message
UNION ALL SELECT 'sensor_reading', COUNT(*) FROM sensor_reading
UNION ALL SELECT 'inventory_item_active', COUNT(*) FROM inventory_item WHERE deleted_at IS NULL
UNION ALL SELECT 'inventory_batch_active', COUNT(*) FROM inventory_batch WHERE status='ACTIVE'
UNION ALL SELECT 'inventory_transaction', COUNT(*) FROM inventory_transaction
UNION ALL SELECT 'outbox_pending', COUNT(*) FROM outbox_event WHERE status='PENDING'
UNION ALL SELECT 'environment_incident_open', COUNT(*) FROM environment_incident WHERE status <> 'CLOSED';

SELECT '组件心跳' AS section;
SELECT component, instance_id,
       DATE_FORMAT(last_seen_at,'%Y-%m-%d %H:%i:%s.%f') AS last_seen_utc,
       TIMESTAMPDIFF(SECOND,last_seen_at,UTC_TIMESTAMP(3)) AS age_seconds
FROM operational_heartbeat ORDER BY last_seen_at DESC;
"@

Write-Host '只读轮询 MySQL 遥测、库存、Outbox 与心跳（Ctrl+C 停止；使用 xianzhi_app 最小权限账号）' -ForegroundColor Cyan
do {
    Write-Host "`n===== $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') / interval=${IntervalSeconds}s =====" -ForegroundColor Yellow
    $query | & $docker exec -i $containerId /bin/sh -lc 'MYSQL_PWD="$(cat /run/secrets/mysql_app_password)" exec mysql --protocol=tcp --host=127.0.0.1 --user=xianzhi_app --database=xianzhi --table --default-character-set=utf8mb4'
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL 只读查询失败，退出码 $LASTEXITCODE。"
    }
    if (-not $Once) { Start-Sleep -Seconds $IntervalSeconds }
} while (-not $Once)
