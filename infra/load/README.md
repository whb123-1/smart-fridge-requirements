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

当前演示模式不接入真实硬件，也不提供公网 MQTT/WSS。虚拟探头由 Worker 使用内部服务账号经 EMQX 发布 QoS 1 遥测；端到端验收请运行后端 `TelemetryMqttIntegrationTest`，核对 `telemetry_message`、`ACCEPTED` 和 `sensor_reading`。

```powershell
python -m pip install --target .tmp/mqtt-load -r infra/load/requirements.txt
$env:PYTHONPATH = (Resolve-Path .tmp/mqtt-load).Path
python infra/load/mqtt-qos1.py
Remove-Item Env:PYTHONPATH
```

核心 API 的 k6 负载脚本仍可用于 REST 性能门禁；不要使用旧的真实设备 MQTT 负载流程。密码和令牌不会写入输出或磁盘。

## 最近一次结果

2026-08-19 在参考资源上执行生产 Profile 门禁：50 VU 的核心 API 读取 p95 34.74 ms、写入 p95 36.21 ms、错误率 0%；100 个并发设备的 QoS 1 PUBACK、`telemetry_message`、`ACCEPTED` 和 `sensor_reading` 计数均为 100。结果满足阈值且未发现静默丢失。换主机、镜像、数据库参数或网络拓扑后必须重新执行，不能把该结果直接外推到新环境。
