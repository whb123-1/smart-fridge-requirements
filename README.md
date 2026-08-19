# 鲜知智慧冰箱

鲜知是一个单用户私有数据模型的智慧冰箱应用。仓库已包含 Vue 3 前端、Spring Boot API/Worker、MySQL、Redis、EMQX、可选 MinIO/Qdrant/OpenAI 兼容适配器、管理员后台，以及生产部署、监控、备份和 CI/CD 配置。

## 已实现能力

- 账号注册、JWT/刷新会话、强制改密、禁用、角色、软删除、90 天恢复与匿名化。
- 独立 `admin` 管理员初始化；管理员可查询、筛选、启停、强退、重置密码、升降角色、删除和恢复用户。
- 冰箱初始化、库存、保质期、采购、MQTT 遥测、通知、语音草稿、菜谱、饮食分析和助手。
- S3 兼容对象存储、OpenAI 兼容语音/LLM/Embedding 与 Qdrant；供应商不可用时按配置降级。
- HTTPS、MQTT-over-WSS、Prometheus/Grafana/Loki/Alertmanager、每日备份和每周恢复演练。

项目不实现家庭共享、OAuth、邮件或 Web Push；通知只使用站内渠道。

AI 外部调用默认关闭。需要启用 DeepSeek 聊天、OpenAI 语音转写和 Embedding 时，请在本机运行 `.\infra\scripts\set-ai-provider-secrets.ps1 -EnableProduction`，按提示输入两把 API Key；密钥只保存到 `secrets/`，不会写入配置文件或日志。

## 已验证交付基线

2026-08-19 的最终门禁结果：后端 63/63、前端 25/25、Playwright 5 passed/3 skipped；V001→V013 空库及 V002→V013 原地升级通过；API/Web/Backup 三张镜像的 OS 与应用二进制均为 0 High/Critical。50 VU 下读取 p95 34.74 ms、写入 p95 36.21 ms、错误率 0%；100 个 MQTT 设备 QoS 1 的 Broker 确认与四层落库计数均为 100。

本机生产 Profile 已通过内部 CA 的 HTTPS/WSS、管理员全生命周期、Worker、S3、备份和隔离恢复演练。真实公网发布仍必须提供实际域名、ACME 邮箱和生产 Secret；OpenAI/Embedding 等可选供应商只有启用并完成真实调用验收后，才算该适配器生产正常。Windows Docker Desktop 不替代 Linux 主机的 cAdvisor/宿主磁盘指标验收。详见 [项目进展](progress.md) 和 [生产运行手册](docs/production-runbook.md)。

## 开发环境

要求 Docker Desktop、Java 21+、Node.js 22+。仓库根目录执行：

```powershell
Copy-Item .env.example .env
docker compose --profile simulator up --build -d
docker compose ps
```

API：`http://localhost:8080`；readiness：`http://localhost:8080/actuator/health/readiness`。

前端开发服务器：

```powershell
Set-Location frontend
npm ci
npm run dev
```

## 质量门禁

```powershell
Set-Location backend
.\mvnw.cmd verify

Set-Location ..\frontend
npm ci
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

后端集成测试会通过 Testcontainers 启动 MySQL、Redis、EMQX、MinIO 和 Qdrant。正式 API 契约位于 `backend/openapi.yaml`。

## 生产部署

生产环境不得直接复用 `.env.prod.example` 中的示例值。首次部署前：

```bash
cp .env.prod.example .env.prod
sh ./infra/scripts/init-secrets.sh
docker compose --env-file .env.prod -f compose.prod.yaml -f compose.monitoring.yaml -f compose.backup.yaml config --quiet
bash ./infra/scripts/deploy-prod.sh
```

首次管理员初始化、Secret 管理、升级、回滚、备份恢复和故障排查的完整步骤见 [生产运行手册](docs/production-runbook.md)。只有生产 Profile 的 HTTPS、readiness、管理员、MQTT WSS、Worker、备份恢复和所有已启用适配器均通过后，才可将环境标记为生产正常。

完整的系统架构、黑盒用例、测试记录模板，以及 MQTT/MySQL 实时观测方法见 [黑盒测试与运行观测手册](docs/black-box-test-guide.md)。

> 不要在包含真实数据的环境执行 `docker compose down -v`。Flyway 迁移只允许追加，不修改既有 V001–V013。
