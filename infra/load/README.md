# 负载验收

核心 API 使用 k6 的 50 VU 门禁：

```bash
docker run --rm --network host -i \
  -e BASE_URL=https://fridge.example.com \
  -e LOAD_HOST_HEADER=fridge.example.com \
  -e LOAD_IDENTIFIER=load_user \
  -e LOAD_PASSWORD_FILE=/run/secrets/load_password \
  -v "$PWD/secrets/load_password:/run/secrets/load_password:ro" \
  -v "$PWD/infra/load/core-api.js:/work/core-api.js:ro" \
  grafana/k6:0.54.0 run /work/core-api.js
```

目标：读取 p95 < 500 ms、写入 p95 < 800 ms、错误率 < 1%。测试写请求使用预先落库的同一幂等快照，不改变用户业务状态。

MQTT 验收脚本会创建 3 个临时用户，通过正式 API 初始化 100 个传感器槽位、签发 100 组独立设备凭据并逐一绑定；随后经生产 WSS 和正式 ACL 保持 100 个连接，以 QoS 1 并发发布唯一 `messageId`。脚本在 Broker PUBACK 之外，还会从 MySQL 核对 `telemetry_message`、`ACCEPTED` 与 `sensor_reading` 数量。测试结束会软删除临时用户，并确认既有连接不能继续入库、原设备凭据不能重新认证。

```powershell
python -m pip install --target .tmp/mqtt-load -r infra/load/requirements.txt
$env:PYTHONPATH = (Resolve-Path .tmp/mqtt-load).Path
python infra/load/mqtt-qos1.py
Remove-Item Env:PYTHONPATH
```

脚本只从 `.env.prod` 读取域名和 TLS 模式，从 `secrets/smoke_admin_password` 读取管理员密码；密码、令牌和设备凭据不会写入输出或磁盘。默认且唯一的生产门禁规模为 100 台设备。

## 最近一次结果

2026-08-19 在参考资源上执行生产 Profile 门禁：50 VU 的核心 API 读取 p95 34.74 ms、写入 p95 36.21 ms、错误率 0%；100 个并发设备的 QoS 1 PUBACK、`telemetry_message`、`ACCEPTED` 和 `sensor_reading` 计数均为 100。结果满足阈值且未发现静默丢失。换主机、镜像、数据库参数或网络拓扑后必须重新执行，不能把该结果直接外推到新环境。
