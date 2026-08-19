# 鲜知生产运行手册

本文适用于 Linux Docker Compose 主机。推荐基线为 4 vCPU、8 GB 内存、100 GB SSD。生产只暴露 TCP 80/443 和 UDP 443；数据库、中间件、Actuator 和管理端口不得映射到公网。

当前已验证镜像基线为 Spring Boot 3.5.14 / Ubuntu 22.04、Caddy 2.11.4 / Go 1.26.6 / Alpine 3.24、Restic 0.19.1 / Go 1.26.6 / Oracle Linux 9.8。2026-08-19 使用 Trivy 0.65.0 扫描三张镜像的 OS 与 app.jar/Caddy/Restic，High/Critical 均为 0。每次重建仍必须使用当日漏洞库重新扫描，不能复用这一历史结论。

## 1. 首次部署

1. 准备 DNS，将 `APP_DOMAIN` 指向服务器；放行 80/443，设置 `ACME_EMAIL`，并确认系统时间同步。
2. 从 `.env.prod.example` 创建权限为 `0600` 的 `.env.prod`。镜像必须使用 `sha-<commit>` 不可变标签。
3. 执行 `sh ./infra/scripts/init-secrets.sh`。检查 `secrets/` 权限为 `0700`、文件为 `0644`；父目录禁止其他宿主用户遍历，文件模式用于让 Compose 单独只读挂载后可被非 root 容器读取。不得把内容复制到环境文件或 CI 日志。
4. 如复用既有 MySQL 数据卷，`secrets/mysql_root_password` 必须与数据卷初始化时的 root 密码一致。绝不通过删除卷解决密码不一致。
5. 校验配置：

   ```bash
   docker compose --env-file .env.prod \
     -f compose.prod.yaml -f compose.monitoring.yaml -f compose.backup.yaml config --quiet
   ```

### 1.1 启用 DeepSeek + OpenAI（可选付费能力）

默认生产环境保持外部 AI 关闭。确认供应商账户、额度和网络策略后，在仓库根目录执行以下 PowerShell 脚本；脚本会两次掩码读取每把 Key，不会打印或写入命令历史：

```powershell
.\infra\scripts\set-ai-provider-secrets.ps1 -EnableProduction
```

该脚本写入 `secrets/deepseek_api_key`、`secrets/openai_api_key`，并设置 `deepseek-chat`、`whisper-1`、`text-embedding-3-small`、MinIO 和 1536 维向量配置。若只轮换 Key、不改变开关，省略 `-EnableProduction` 并添加 `-Force`。

启用前必须先确认 `secrets/qdrant_api_key`、MinIO 访问 Secret 和 `.env.prod` 中的 `STORAGE_*` 配置存在；不要把 Key 写进 `.env.prod`。

6. 启动内部依赖、创建最小权限账号并执行独立迁移：

   ```bash
   docker compose --env-file .env.prod -f compose.prod.yaml up -d mysql redis emqx minio qdrant
   docker compose --env-file .env.prod -f compose.prod.yaml exec -T mysql /docker-entrypoint-initdb.d/20-users.sh
   docker compose --env-file .env.prod -f compose.prod.yaml --profile tools run --rm migrate
   ```

7. 仅首次执行管理员初始化，并把终端中显示一次的临时密码立即放入密码管理器：

   ```bash
   docker compose --env-file .env.prod -f compose.prod.yaml --profile tools run --rm admin-bootstrap
   ```

   登录用户名固定为 `admin`，邮箱来自 `ADMIN_BOOTSTRAP_EMAIL`。临时密码为 24 位、24 小时过期；首次登录必须修改。已有管理员或用户名冲突时任务不会覆盖账号。

8. 启动应用、监控和备份：

   ```bash
   docker compose --env-file .env.prod --profile linux-host \
     -f compose.prod.yaml -f compose.monitoring.yaml -f compose.backup.yaml up -d
   ```

   `linux-host` 启用需要 Linux 宿主机 cgroup/`/sys` 挂载的 cAdvisor。Docker Desktop 本机证书演练应省略该 profile，并追加 `-f compose.monitoring.desktop.yaml`；该覆盖只移除 Node Exporter 的 Linux `rslave` 根挂载，Prometheus、应用指标、日志和备份 textfile 指标仍会启动。容器级 cAdvisor 与宿主磁盘指标只在实际 Linux 生产主机验收。

9. 将管理员正式密码写入不受版本控制的 `secrets/smoke_admin_password`（该文件不作为容器 Secret，保持 `0600`），Linux 执行 `bash ./infra/scripts/smoke-prod.sh`，Windows 本机证书演练执行 `pwsh ./infra/scripts/smoke-prod.ps1`。检查 60 秒观察窗口内 API/Worker 无 ERROR。
10. 发布前在隔离演练环境执行 `pwsh ./infra/scripts/smoke-admin-lifecycle.ps1`。脚本创建唯一临时用户，验证完整管理员生命周期后将其软删除；不要在无变更窗口或未经批准的真实业务租户上运行。

本机证书验收时，将 `APP_DOMAIN=localhost`、`CADDY_TLS_MODE=internal`、`SMOKE_INSECURE_TLS=true`；公网使用 `CADDY_TLS_MODE=$ACME_EMAIL` 以启用 ACME。

## 2. 升级

1. 确认 CI 的测试、OpenAPI、覆盖率、依赖/镜像/密钥扫描、SBOM 和签名全部通过。
2. 在 `.env.prod` 中设置新的不可变 API/Web/Backup 镜像。
3. 执行 `bash ./infra/scripts/deploy-prod.sh`。脚本顺序为：记录旧镜像、启动依赖、备份、迁移、拉取、滚动重建、冒烟、观察日志。
4. Flyway 必须是只追加迁移；发布前在空库和上一生产版本快照上各验证一次。

## 3. 回滚

部署脚本失败会自动恢复上一个镜像。手工回滚时只回滚应用镜像，不回滚数据库文件：

```bash
export API_IMAGE=ghcr.io/ORG/xianzhi-api:sha-PREVIOUS
export WEB_IMAGE=ghcr.io/ORG/xianzhi-web:sha-PREVIOUS
export BACKUP_IMAGE=ghcr.io/ORG/xianzhi-backup:sha-PREVIOUS
docker compose --env-file .env.prod \
  -f compose.prod.yaml -f compose.monitoring.yaml -f compose.backup.yaml up -d api worker web backup
bash ./infra/scripts/smoke-prod.sh
```

若新迁移与旧镜像不兼容，保持新镜像停机并从发布前备份恢复到新 schema 进行取证；不要直接修改 `flyway_schema_history`。

## 4. 备份与恢复

- Backup 容器使用与数据库同系列的 MySQL 客户端，每天执行 `mysqldump --single-transaction`，本地保留 7 天；只有导出、非空校验和 gzip 完整性检查全部成功后才原子发布备份文件。
- 配置 `RESTIC_REPOSITORY` 后，远端加密保留 30 个日备份和 12 个月备份。
- 每周日在隔离的 `xianzhi_restore_drill` schema 自动恢复，成功时间写入 Node Exporter textfile 指标。

立即备份：

```bash
docker compose --env-file .env.prod \
  -f compose.prod.yaml -f compose.monitoring.yaml -f compose.backup.yaml \
  run --rm --entrypoint /opt/xianzhi/backup.sh backup
```

恢复演练：

```bash
docker compose --env-file .env.prod \
  -f compose.prod.yaml -f compose.monitoring.yaml -f compose.backup.yaml \
  run --rm --entrypoint /opt/xianzhi/restore-drill.sh backup
```

灾难恢复必须先停止 API/Worker、备份当前损坏状态、恢复到新 schema 验证表数/Flyway/关键账号，再在维护窗口切换。严禁把演练 schema 当作生产 schema。

## 5. Secret 轮换

- JWT：轮换会使全部访问令牌失效；更新 `jwt_signing_key` 后重建 API/Worker，并要求用户重新登录。
- MySQL：先以 root 修改对应运行/迁移账号密码，再原子替换 Secret 文件并重建容器。迁移账号只在迁移任务中使用。
- Redis/MQTT/Qdrant/MinIO：先配置服务端新值，再更新客户端 Secret，逐项执行冒烟。
- 身份墓碑 HMAC：历史墓碑需要旧键才能比对；轮换前应实现双键过渡，不能直接覆盖。
- Restic：仓库密码不能就地轮换；创建新仓库并完成全量备份和恢复演练后再退役旧仓库。

所有 Secret 变更必须记录操作人、时间、目标和验证结果，不记录 Secret 内容。

## 6. 管理员事故处理

- 管理员不可停用、降权或删除自己；最后一个有效管理员受数据库悲观锁保护。
- 用户停用、删除、降权、强退或密码重置会增加 `session_version` 并撤销刷新会话，Access Token 立即失效。
- 删除账号 90 天内可恢复；匿名化后返回 `USER_ANONYMIZED`，不可恢复。
- 若所有管理员因外部数据库操作被破坏，先停止 API，保留审计证据，经双人批准修复一个账号；不要重复运行 bootstrap 覆盖现有用户。

## 7. 故障排查

```bash
docker compose --env-file .env.prod -f compose.prod.yaml ps
docker compose --env-file .env.prod -f compose.prod.yaml logs --since 15m api worker emqx
curl --fail --silent https://$APP_DOMAIN/healthz
```

- `Unsafe production configuration`：修正弱密钥、HTTP URL、Fake、调试用户名或 provider 冲突；不要绕过校验。
- API readiness 失败：依次检查 MySQL 最小权限账号、Redis 密码、Flyway 版本和磁盘空间。
- MQTT WSS 失败：检查 Caddy `/mqtt`、EMQX 8083 内部监听、HTTP 鉴权回调和服务账号。
- Worker 心跳超过 120 秒：检查 ShedLock、数据库连接、线程阻塞和 `xianzhi_worker_heartbeat_age_seconds`。
- 外部 AI 降级：查看 `xianzhi.provider.calls`、熔断和 Schema 校验日志；MySQL/规则降级保持可用，但需恢复供应商。
- 索引失败：修复 Embedding/Qdrant 后创建新版本重建，禁止直接覆盖线上集合。
- 备份告警：检查目标磁盘、Restic 凭据和最近恢复演练指标；在修复前停止高风险发布。

## 8. 发布验收清单

- `mvn verify`、前端单测/构建、Playwright、Compose config、扫描和签名通过。
- V001→V013 空库迁移及上一版本原地升级通过。
- HTTPS/HSTS、Cookie、安全头、同源写保护、Swagger/Debug/Simulator 关闭。
- 管理员登录、分页筛选、启停、强退、临时密码、删除恢复、审计通过。
- 内部 EMQX QoS 1 虚拟探头、Worker、Outbox、已启用 MinIO/OpenAI/Qdrant 适配器通过。
- 备份和隔离恢复演练通过；Prometheus、Grafana、Loki、Alertmanager 可用。
- 50 并发核心 API 读取 p95 < 500 ms、写入 p95 < 800 ms、错误率 < 1%；100 个设备 QoS 1 无静默丢失。
- 观察窗口无已知 P1/P2 缺陷和新的 ERROR 日志。

### 8.1 本机生产 Profile 验收记录（2026-08-19）

- 内部 CA 的 HTTPS、HSTS/安全头、Secure/HttpOnly/SameSite Cookie、同源写保护、管理员 API、虚拟探头 Worker 以及 Swagger/Debug 关闭均通过。
- 管理员搜索、启停、即时失效、强退、临时密码、强制改密、升降权、软删除、恢复和审计均通过；测试密码未写入日志或文档。
- 发布前备份、V013 独立迁移、发布后备份和 `xianzhi_restore_drill` 隔离恢复演练通过；S3 AES256 流式往返通过。
- 50 VU：读取 p95 34.74 ms、写入 p95 36.21 ms、错误率 0%。100 设备 MQTT：PUBACK、遥测消息、ACCEPTED 和传感器读数各 100。
- OpenAI/Embedding 外部调用未启用；Qdrant 仅完成本地 Testcontainers/降级测试。公网 ACME 与 Linux cAdvisor/宿主磁盘指标未由 Windows 本机验收覆盖。
