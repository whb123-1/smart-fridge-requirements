# 项目进展交接

更新时间：2026-08-17（Asia/Shanghai）  
当前分支：`main`  
基线提交：`fcca65aa35eafd02c2d2b47a7868162ba217649e`（`后端第一进程`）

## 当前环境状态

- 项目环境已关闭：`docker compose down` 已执行，`5173` 和 `8080` 均无监听。
- Docker Desktop已退出。
- 未使用 `docker compose down -v`，MySQL 和 Redis 命名数据卷保留。
- Vite 开发服务器已停止。
- 本地持久卷中包含验收时创建的 `qa_` / `ren_` 前缀测试账号及 `QA Fridge` 数据，后续可按需清理。
- 写入本文件前工作区为干净状态；`progress.md` 是本次交接新增文件，尚未提交。

## 已完成的事项

### 1. 阶段 0-1 后端底座

- 已建立 `backend/` Java 21 / Spring Boot 3 Maven 工程和 Maven Wrapper。
- 后端按 `shared`、`identity`、`fridge` 组织，已接入 Spring Web、Security、Validation、JPA、Flyway、Redis、Actuator、springdoc、JWT、Argon2 和 Testcontainers。
- 已提供 Dockerfile，以及根目录 `compose.yaml`、`.env.example`；Compose 包含 MySQL 8.4、Redis 7、API 和无端口 Worker。
- API 等待 MySQL/Redis 健康后启动，Worker 等待 API 健康后启动。
- 已实现统一 `{ code, message, data, traceId }` 响应信封、请求 ID、统一错误映射、审计、日志脱敏、登录限流和幂等记录。
- 已实现用户隔离，冰箱查询按当前认证用户过滤。

### 2. 数据库与迁移

- `V001__identity_and_platform.sql`：用户、刷新会话、审计、幂等记录等身份与平台表。
- `V002__fridge_onboarding.sql`：冰箱、分区和 `PENDING_BIND` 传感器槽位。
- `V003__usernames.sql`：全局唯一用户名及旧账号确定性回填。
- 主键使用 UUID/BINARY(16)，业务时间使用 UTC `datetime(3)`。
- 用户名存储为小写 ASCII，规则为 `^[a-z0-9_]{3,32}$`，唯一范围包含软删除账号。
- 旧账号优先使用符合规则且不冲突的 `display_name` 小写值，否则使用完整 UUID 十六进制值。

### 3. 认证、会话与用户资料

- 已实现邮箱密码注册、登录、刷新、注销和启动时会话恢复。
- 访问 JWT 使用 HS256，默认 15 分钟；刷新会话默认 30 天并执行令牌轮换。
- 刷新令牌仅保存哈希；检测到旧刷新令牌重放时撤销整个会话族。
- 密码使用 Argon2id；刷新令牌通过 `HttpOnly`、`SameSite=Lax` Cookie 返回。
- 登录支持邮箱或用户名，用户名输入统一转小写；邮箱/用户名不存在与密码错误均返回 `401 INVALID_CREDENTIALS`。
- `LoginRequest` 使用 `{ identifier, password }`，并用 `@JsonAlias("email")` 临时兼容旧客户端。
- 注册必须提交独立的 `username` 和 `displayName`。
- 设置页允许修改用户名，重复用户名返回 `409 USERNAME_ALREADY_REGISTERED`；修改为自身原用户名允许成功。
- 用户名修改不撤销现有会话，因为 JWT 仅使用用户 ID 标识账户。
- 已实现：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`
  - `GET/PATCH /api/v1/me`
  - `PATCH /api/v1/me/password`

### 4. 首次冰箱初始化

- 已实现 `GET /api/v1/onboarding`、`POST /api/v1/onboarding/initialize` 和只读冰箱摘要查询。
- 初始化支持 3-6 个分区，分区名唯一，每个分区温度/湿度传感器数量分别为 0-4。
- 初始化请求必须携带 `Idempotency-Key`；相同键返回原响应快照，不同请求在完成后返回 `409 ONBOARDING_ALREADY_COMPLETED`。
- 初始化事务、默认分区目标值、零传感器、重复名称和跨用户隔离均已有测试覆盖。

### 5. 前端接入

- 已加入 Vue Router、内存会话状态和认证/初始化路由守卫。
- 访问令牌只保存在内存，不写入 `localStorage`；刷新页面时通过刷新 Cookie 恢复。
- `src/services/api.js` 已支持 Bearer Token、响应信封解包、一次 401 后串行刷新并重试。
- Vite 将 `/api` 和 `/actuator` 代理到 `127.0.0.1:8080`，用于同源 Cookie。
- 已完成登录、注册、三步初始化和注销流程。
- 登录页使用“邮箱或用户名”，注册页包含称呼、用户名、邮箱和密码。
- 设置页用户名可编辑、邮箱只读，保存成功后同步 `session.user`。
- 已迁移首页真实冰箱/分区摘要；库存、菜谱、采购、饮食和助手等仍使用演示数据并带演示状态标识。
- 登录、注册和初始化页已统一为浅冰蓝视觉，不再使用大面积深色背景。

### 6. 首页环境入口与响应式布局

- 首页左侧第四个槽位固定为环境入口，两种状态均进入 `/app/environment`。
- 无告警时显示普通冰箱门控件：“环境状态 / 温度正常”。
- 有告警时显示原琥珀色 `.home-warning`，保留异常数量和提示文案。
- 桌面端左右栏均固定四个视觉槽位并保持垂直对齐。
- 移动端正常态为 `2x2`；告警态为前三个入口加整行告警控件。
- 已修复移动端背景弧线使用 `vw` 和负定位导致布局视口扩展到约 707px 的问题；现在 `390x844` 下布局视口为 390px，无横向溢出。
- 首页状态条位于左上角，浅色显示“冰箱配置已同步 / 业务数据演示 / 用户 / 退出”，不再遮挡右上角传感器状态和温度单位。

### 7. 契约与验证

- `backend/openapi.yaml` 是当前正式 API 契约，已覆盖已实现端点和用户名字段。
- `backend\mvnw.cmd test`：11 项测试通过，包括 MySQL/Redis Testcontainers、Flyway V003、旧账号回填、认证 Cookie、用户名登录/修改和用户隔离。
- `npm test`：20 项测试通过。
- `npm run build`：通过；仅有 Vite 单包超过 500 kB 的提示。
- `git diff --check`：通过，仅有 Windows LF/CRLF 提示。
- Compose 构建和启动通过，`/actuator/health` 返回 `UP`。
- 真实 API 验收通过：注册 -> 初始化 3 个分区 -> 刷新 Cookie -> 邮箱登录 -> 修改用户名 -> 大写用户名登录 -> 会话恢复。
- 已检查 `2000x1255`、`1440x1000`、`390x844`；登录/注册、首页正常态和告警态无横向溢出，环境入口布局正确。

## 关键决策

- 当前只交付阶段 0-1：账户、会话、资料、首次冰箱初始化和只读冰箱摘要持久化。
- 库存、采购、菜谱、饮食、助手、遥测和 AI 暂不持久化，不能将现有演示数据视为后端数据。
- 用户数据采用单用户私有模型，所有查询必须以认证用户 ID 隔离。
- 用户名全局唯一且不因软删除释放，不提供用户名复用。
- 邮箱策略、JWT 内容、密码策略和刷新会话策略不因用户名功能改变。
- 登录限流账户键使用规范化后的登录标识；邮箱和用户名分别形成稳定键。
- 用户名修改不主动撤销登录会话。
- OpenAPI 为契约来源；实现新正式端点时必须同步契约和测试。
- 本地开发通过 Vite 同源代理使用非 Secure Cookie；生产环境必须启用 HTTPS 和 Secure Cookie。
- Compose 关闭默认保留数据卷；只有明确需要清空数据时才执行 `docker compose down -v`。
- MQTT、MinIO、Qdrant 和 AI 基础设施延后到对应业务阶段，不提前加入当前 Compose。

## 未完成的待办

### 下一阶段业务

- 将库存、保质期、采购、菜谱、饮食记录和助手逐域迁移到真实后端与数据库。
- 为上述领域替换当前 mock 数据和旧 API 别名，并补充用户隔离、幂等、事务与集成测试。
- 按业务阶段接入 MQTT 遥测、MinIO、Qdrant 和 AI 能力。
- 设计家庭共享、多用户冰箱授权和 OAuth；这些不属于当前单用户阶段。

### 工程与生产化

- 增加真正的浏览器 E2E 测试套件。目前前端是 Node 单元测试加人工 Chrome 截图验收，尚未配置 Playwright 流程。
- 配置生产密钥管理、HTTPS、Secure Cookie、数据库备份、日志采集、指标和告警。
- 根据实际部署目标补充 CI/CD、镜像发布和生产环境变量校验。
- 可选优化前端包体积：当前主 JS 约 757 kB，中文字体文件较大，可做路由拆包和字体子集化。
- 可选清理本地持久卷中的 `qa_` / `ren_` 验收账号和 `QA Fridge` 数据。
- 在开始下一业务域前，建议为 `progress.md` 单独提交一次，避免交接文档停留在未跟踪状态。

## 新对话启动方式

先让新对话读取本文件和 `backend-construction-plan.md`，再启动环境：

```powershell
Set-Location C:\Users\LENOVO\Desktop\smart-fridge-requirements
docker compose up --build -d
docker compose ps

Set-Location frontend
npm run dev
```

健康检查：`http://localhost:8080/actuator/health`  
前端默认地址：`http://127.0.0.1:5173`

建议下一步先选择一个业务域（推荐库存），明确数据库模型、API 契约和前端迁移边界后再实现。

## 阶段 2：库存核心闭环（2026-08-18）

### 已完成

- 新增 `V004__catalog_inventory_and_expiry.sql`，保持 `V001-V003` 不变；包含食材目录、别名、重量估算、储存档案、库存条目/批次/流水、保质期评估、采购清单/采购项、Outbox，以及用户隔离和状态/分区/评估索引。
- 内置常见中文食材、分类、单位、参考保质期、储存档案和常见重量估算；未命中的自定义食材仅在用户提供日期依据时评估，否则返回 `UNKNOWN`。
- 实现库存条目软删除、批次创建与部分更新、`ADJUST` 目标数量、`CONSUME/DISCARD/EXPIRED` 正向扣减、单位不转换和非负库存约束。
- 实现保质期优先级：包装到期时间 -> 用户保质期 -> 目录/储存档案参考值 -> `UNKNOWN`；无 MQTT 实测时使用 `REFERENCE_TARGET`/`REFERENCE_DEFAULT`，保留风险累计字段。
- 幂等指纹包含用户、HTTP 方法、路径和请求体；保存请求方法、路径、状态码和响应快照；重复请求重放，复用键提交不同请求返回 `IDEMPOTENCY_KEY_REUSED`。
- 实现库存、批次、流水、临期、评估、目录联想、重量估算、采购清单、采购项和原子采购入库 API。采购入库在同一事务中创建批次、`IN` 流水、Outbox，并将采购项置为 `STORED`。
- 批次写操作和采购入库锁定行；软删除保留批次、流水和评估历史，仍有活动数量时返回 `INVENTORY_ITEM_HAS_ACTIVE_BATCHES`。
- 前端库存、保质期和采购主流程改用真实 API；写请求自动携带 `Idempotency-Key`，使用后端 UUID，刷新页面重新加载库存、临期和采购数据。菜谱、饮食、AI 补货建议仍保留演示实现并明确不属于本阶段持久化范围。

### 暂不实现

- 语音草稿/转写和确认入库。
- 邮件或站内通知投递、通知偏好和定时 Worker 扫描。
- MQTT 环境实测、风险累计重算和异常驱动的保质期缩短。
- 菜谱、饮食记录、AI 生成与 AI 持久化。

### 阶段 2 验证

- `backend\mvnw.cmd test`：全量后端测试通过；Flyway 从空库和阶段 1 数据升级到 V004 通过。
- 阶段 2 集成覆盖：用户隔离、幂等重放/冲突、保质期优先级与 `UNKNOWN`、目录重量估算、并发扣减、单位约束、软删除历史保留、采购入库原子回滚和重复重放。
- `npm.cmd --prefix frontend test`：22 项通过，包含鉴权和幂等请求头测试。
- `npm.cmd --prefix frontend run build`：通过；仅保留 Vite 主包超过 500 kB 的既有提示。
- `git diff --check`：通过，仅有 Windows 换行符提示。
