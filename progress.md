# 项目进展交接

更新时间：2026-08-18（Asia/Shanghai）
当前分支：`main`  
基线提交：`b02ec72dc12f20a208fc693f628dfbdc8592d82b`

## 当前环境状态

- Docker Compose 环境正在运行：MySQL、Redis、EMQX、API、Worker 和 Simulator 均已启动；MySQL、Redis、EMQX、API 健康检查通过，API 的 liveness/readiness 均为 `UP`。
- API 对外端口为 `8080`，MQTT 为 `1883`，EMQX Dashboard 为 `18083`；前端 Vite 开发服务器通过独立终端运行在 `http://127.0.0.1:5173`，不由 Compose 管理。
- 未执行 `docker compose down -v`，MySQL、Redis 和 EMQX 命名数据卷均保留。
- 当前持久卷包含阶段 3 验收用户 `demo`、冰箱、虚拟设备、绑定传感器和 Debug 场景；不应在未确认时清理。
- 当前数据库已在保留原数据卷的情况下从 Flyway `V005` 原地升级到 `V009`；`V001-V005` 未修改、未重编号。
- 工作区包含阶段 2 遗留补齐与阶段 4 的未提交实现和测试，详见本文末尾“阶段 2 遗留补齐与阶段 4”记录。

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
- 首页、库存、保质期、采购、环境、通知偏好、语音草稿、菜谱、饮食和助手主流程均已迁移到真实 API；页面刷新后从后端重建状态。
- 登录、注册和初始化页已统一为浅冰蓝视觉，不再使用大面积深色背景。

### 6. 首页环境入口与响应式布局

- 首页左侧第四个槽位固定为环境入口，两种状态均进入 `/app/environment`。
- 无告警时显示普通冰箱门控件：“环境状态 / 温度正常”。
- 有告警时显示原琥珀色 `.home-warning`，保留异常数量和提示文案。
- 桌面端左右栏均固定四个视觉槽位并保持垂直对齐。
- 移动端正常态为 `2x2`；告警态为前三个入口加整行告警控件。
- 已修复移动端背景弧线使用 `vw` 和负定位导致布局视口扩展到约 707px 的问题；现在 `390x844` 下布局视口为 390px，无横向溢出。
- 首页状态条位于左上角，浅色显示“冰箱配置已同步 / 实时 API 数据 / 用户 / 退出”，不再遮挡右上角传感器状态和温度单位。

### 7. 契约与验证

- `backend/openapi.yaml` 是当前正式 API 契约，已覆盖已实现端点和用户名字段。
- 后端全量 Maven 测试：37 项通过，包括 MySQL/Redis/EMQX Testcontainers、Flyway 空库与 V002→V009 升级、认证、库存、遥测、语音、通知、菜谱、饮食、助手和管理员权限。
- `npm test`：25 项测试通过。
- `npm run build`：通过；仅有 Vite 单包超过 500 kB 的提示。
- `git diff --check`：通过，仅有 Windows LF/CRLF 提示。
- Compose 构建和启动通过，`/actuator/health` 返回 `UP`。
- 真实 API 验收通过：注册 -> 初始化 3 个分区 -> 刷新 Cookie -> 邮箱登录 -> 修改用户名 -> 大写用户名登录 -> 会话恢复。
- 已检查 `2000x1255`、`1440x1000`、`390x844`；登录/注册、首页正常态和告警态无横向溢出，环境入口布局正确。

## 关键决策

- 当前已交付阶段 0、1、2、3 和阶段 4 的本地可验收版本：身份与初始化、库存/采购/语音/通知、MQTT 环境、菜谱/饮食和规则助手均已持久化。
- 真实语音转写、对象存储、邮件、LLM 和 Embedding 仍未配置；开发环境分别使用 Fake、本地存储、跳过投递和规则/MySQL 回退，不得宣称外部服务已经投产。
- 用户数据采用单用户私有模型，所有查询必须以认证用户 ID 隔离。
- 用户名全局唯一且不因软删除释放，不提供用户名复用。
- 邮箱策略、JWT 内容、密码策略和刷新会话策略不因用户名功能改变。
- 登录限流账户键使用规范化后的登录标识；邮箱和用户名分别形成稳定键。
- 用户名修改不主动撤销登录会话。
- OpenAPI 为契约来源；实现新正式端点时必须同步契约和测试。
- 本地开发通过 Vite 同源代理使用非 Secure Cookie；生产环境必须启用 HTTPS 和 Secure Cookie。
- Compose 关闭默认保留数据卷；只有明确需要清空数据时才执行 `docker compose down -v`。
- MQTT/EMQX 已在阶段 3 接入；MinIO 与 Qdrant 为可选 Compose Profile，默认关闭，真实邮件和外部 AI 调用也默认关闭。

## 未完成的待办

### 后续业务与外部适配

- 配置并验收真实对象存储、语音转写、邮件、LLM 和 Embedding 服务；当前 Fake/disabled/fallback 行为继续作为开发降级路径。
- 扩展菜谱来源管理、批量导入运维界面和可重建 Qdrant 索引的生产监控。
- 设计家庭共享、多用户冰箱授权和 OAuth；这些不属于当前单用户阶段。

### 工程与生产化

- 增加真正的浏览器 E2E 测试套件。目前前端是 Node 单元测试加人工 Chrome 截图验收，尚未配置 Playwright 流程。
- 配置生产密钥管理、HTTPS、Secure Cookie、数据库备份、日志采集、指标和告警。
- 根据实际部署目标补充 CI/CD、镜像发布和生产环境变量校验。
- 可选优化前端包体积：当前主 JS 约 763 kB，中文字体文件较大，可做路由拆包和字体子集化。
- 可选清理本地持久卷中的 `qa_` / `ren_` 验收账号和 `QA Fridge` 数据。
- 在开始下一业务域前，建议为 `progress.md` 单独提交一次，避免交接文档停留在未跟踪状态。

## 新对话启动方式

先让新对话读取本文件和 `backend-construction-plan.md`，再启动环境：

```powershell
Set-Location C:\Users\LENOVO\Desktop\smart-fridge-requirements
docker compose --profile simulator up --build -d
docker compose ps

Set-Location frontend
npm run dev
```

健康检查：`http://localhost:8080/actuator/health`  
前端默认地址：`http://127.0.0.1:5173`

建议下一步按计划选择语音/通知增强或菜谱域，先明确数据库模型、API 契约和前端迁移边界后再实现。

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

### 当时暂不实现（现已在后续增量完成或继续延期）

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

## 阶段 3：MQTT 环境监测与保质期联动（2026-08-18）

### 已完成

- 新增 `V005__telemetry_environment_and_notifications.sql`，保持 `V001-V004` 不变；包含设备、传感器档案、遥测消息、按月 RANGE 分区的原始读数、小时聚合、分区环境状态、环境事件、批次异常暴露、Debug 场景、站内通知和 ShedLock。
- `sensor` 槽位已扩展设备/档案绑定、最后有效值、质量、时间和连续可疑次数；储存档案加入版本化偏离阈值、风险倍率与高风险分钟数；保质期评估加入环境影响快照。
- Outbox 已扩展尝试次数、可用/锁定/完成时间和失败原因；Worker 使用 `FOR UPDATE SKIP LOCKED` 批量领取，指数退避，五次失败后进入 `FAILED`，并可重新领取超时的 `PROCESSING` 事件。
- Compose 已加入 EMQX 5.8.8 和可选 `simulator` Profile；API/Worker/Simulator 等待 EMQX 与 API 健康状态，服务账号、模拟器账号、内部回调密钥和 Debug 操作员名单均通过环境变量配置。
- 实现 `PHYSICAL`/`VIRTUAL` 设备注册、一次性随机 MQTT 凭据、Argon2 哈希存储、凭据轮换、停用、PENDING_BIND 槽位绑定和解除绑定。
- EMQX HTTP 鉴权/ACL 回调受内部密钥保护；设备只能发布自己的 `smart-fridge/v1/{deviceId}/telemetry`，服务账号只能订阅通配遥测主题，模拟器只允许向有效虚拟设备主题发布。
- API 使用 QoS 1、非 retained 消息、持久会话和手动确认；数据库事务成功或业务拒绝审计落库后才确认，基础设施异常不确认并由 Broker 重投。
- 遥测支持温度 C/F、湿度 PERCENT、摄氏归一化、七天 messageId 去重、整消息 Schema/归属/物理边界校验、未来十分钟限制和相对最新有效值 48 小时历史窗口。
- 48 小时内乱序读数会保存但不覆盖当前值；BAD 只审计；变化速率超限以 SUSPECT 保存但不参与当前环境/保质期，连续三次创建传感器异常 Outbox 事件，恢复后关闭异常。
- Worker 每五分钟聚合 15 分钟窗口内各绑定传感器的最新有效值并求平均；实现 NORMAL、WARNING、STALE、NO_SENSOR，异常/恢复均需连续 15 分钟，只有待绑定槽位时不产生陈旧告警。
- 批次风险通过唯一的批次/事件暴露台账不可逆累计，公式为暴露分钟 × 食材风险系数 × 轻微/中等/严重倍率；实测评估来源为 `MEASURED_ENVIRONMENT`，预计到期时间按累计风险前移，高风险只返回 `CHECK_BEFORE_CONSUMING`。
- 环境事件和传感器异常按事件 ID 创建去重站内通知；事件关闭时只设置解决时间，不自动标记已读。通知列表和读/隐藏更新均按当前用户隔离。
- Worker 已替换原空壳进程，包含 Outbox 消费、环境聚合与风险重算、小时聚合、90 天原始数据/24 个月小时数据清理及未来分区维护任务，所有周期任务使用 ShedLock。
- 正式 API 已实现设备、分区槽位、读数、冰箱环境、通知和 dev/test Debug 场景端点；所有用户写接口要求 `Idempotency-Key`，Debug Controller 在生产 Profile 不注册且仅允许名单用户操作自己的已绑定虚拟设备。
- `backend/openapi.yaml` 已同步阶段 3 路径、DTO、幂等头、状态码和错误响应；内部 EMQX 回调未暴露在公共 OpenAPI。
- 前端已接入真实环境、趋势、设备/槽位和通知 API；首页与环境页使用真实在线/陈旧数量、温湿度、同步时间和活动事件；设置页展示真实待绑定/已绑定状态，刷新后重新加载环境、通知和环境调整后的保质期评估。

### 阶段 3 验证

- 后端全量 Maven 测试：30 项通过，0 失败、0 错误；包含既有 8 项阶段 1/2 Testcontainers 集成回归、空库 V001-V005 迁移和 V002→V005 升级。
- 新增阶段 3 单元覆盖：C/F 转换、未来/物理边界、跨设备传感器伪造、48 小时历史边界、去重、乱序、BAD、连续 SUSPECT/恢复、15 分钟开启/关闭、STALE/NO_SENSOR、风险倍率与不可逆累计、通知去重/解决和 Outbox 五次失败策略。
- 新增 EMQX/MySQL/Redis Testcontainers 集成：通过正式 API 完成注册、初始化、虚拟设备注册与槽位绑定，QoS 1 消息经 EMQX→API→MySQL 入库；重复发布相同 messageId 后读数和消息均保持一条。
- Compose 实链路验收通过：Simulator 经 EMQX HTTP 鉴权/ACL 发布，API 入库温度 `4°C / GOOD / EXTERNAL_DEBUG` 并更新设备最后在线时间。
- Worker `reading_rank` 窗口查询修复后已在整五分钟调度执行；环境 API 返回 CHILL `NORMAL / 4°C`、在线传感器 1、陈旧传感器 0，Worker 日志无后续 SQL 错误。
- 前端 `npm test`：24 项通过；`npm run build`：通过，仅保留 Vite 主包超过 500 kB 的提示。
- `git diff --check`：通过。

### 当时继续延期（语音、菜谱、饮食和助手已在后续增量完成）

- 语音录入、草稿确认和转写。
- 邮件投递与通知偏好；本阶段只实现站内环境提醒。
- 菜谱、饮食记录和 AI 生成/持久化。
- MinIO、Qdrant 及真实硬件厂商协议适配。
- 风险阈值和倍率目前是开发参考种子，正式上线前仍需食品安全领域人员校准。

## 阶段 2 遗留补齐与阶段 4（2026-08-18）

### 数据库与后端

- 新增 `V006__speech_and_notification_delivery.sql`，保持 `V001-V005` 不变；实现语音草稿、对象元数据、转写状态与失败原因、过期时间、通知偏好和通知投递台账。
- 语音上传只创建异步草稿；Worker 使用可替换的对象存储、转写和解析端口处理，开发环境启用 Fake 转写与本地持久卷，只有用户确认后才复用库存创建、幂等和流水事务。
- Worker 已增加语音处理/过期、临期/过期/低库存扫描，以及站内/邮件投递重试；邮件未配置时记录为跳过，不阻塞业务事务。通知支持四类事件、静默时段、用户隔离和去重。
- 新增 `V007__recipe_nutrition_and_preferences.sql`：菜谱来源、许可证、规范化菜谱/组件/步骤、知识块、MySQL FULLTEXT、收藏/事件、营养、饮食记录、统计和用户偏好；普通用户不能访问管理员导入 API。
- 新增 `V008__assistant_context_and_actions.sql`：助手会话、消息、最小化上下文快照、洞察和操作草案；外部 AI 默认关闭，规则路由与 MySQL 菜谱回退可用，写操作必须确认。
- 新增 `V009__recipe_import_versions.sql`：菜谱版本快照和导入任务锁定字段；Worker 支持导入任务领取、指纹去重、许可证追溯和可选 Qdrant 派生索引，Qdrant 不可用时自动回退 MySQL。
- 菜谱匹配先应用过敏/忌口过滤，再检查库存批次归属、有效性、数量和单位；调味品支持有界缩放。做菜确认在事务中扣减库存、记录流水/饮食/菜谱事件，重试由幂等快照保护。
- 助手上下文版本只由用户偏好和库存事实计算，不包含邮箱等敏感字段；确认草案时同时比较过期时间和当前数据版本，变化后返回 `409 CONTEXT_STALE`。
- 菜谱检索优先使用标题/摘要和知识块 FULLTEXT，未命中时回退食材组件名称；按“豆腐”等纯食材名称也可检索和生成候选。
- 公共 OpenAPI 已同步阶段 2 遗留与阶段 4 路径、DTO、状态码、管理员权限和幂等头；内部 MQTT 回调仍不进入公共契约。

### 前端

- `api.js` 已接入语音草稿、通知偏好、菜谱生成/收藏/做菜、营养估算、饮食记录/统计和助手会话/草案 API，所有需要幂等的写请求自动生成新键。
- `App.vue` 已移除菜谱、饮食和助手的运行时 Mock/Legacy 实现；页面加载时清空本地视图并从 API 重建，状态条改为“实时 API 数据”。
- 修复饮食历史键名损坏和烹饪完成写入错误分组导致的运行时异常；库存/采购历史不再提供仅删除本地视图的伪删除操作。
- 补货候选改为由后端低库存状态和已加载菜谱缺料实时派生；移除固定金额、固定菜谱、固定饮食记录和固定补货候选。

### 2026-08-18 本地验收

- 未执行 `docker compose down -v`；使用 `docker compose --profile simulator up -d --build api worker simulator` 仅重建应用容器。原 MySQL/Redis/EMQX 数据卷和 `demo` 数据保留，数据库成功执行 `V006-V009` 并达到 `V009`。
- `GET /actuator/health` 与 `/actuator/health/readiness` 均返回 `UP`；MySQL、Redis、EMQX、API 健康，Worker/Simulator 正常运行；API 迁移日志显示从 `V005` 连续升级并成功应用 4 个迁移。
- 真实 HTTP 链路通过：注册/登录 → 初始化冰箱 → 创建库存 → `CONSUME` 扣减 → 10 天内保质期查询 → 创建采购项并原子入库 → 语音草稿 `READY` 并确认 → 偏好/通知偏好 → 菜谱查询与生成 → 饮食记录 → 助手消息 → 环境查询。新验收用户只能读取自己的 3 个库存条目。
- 新注册虚拟设备并绑定温度槽位后，使用返回的精确 MQTT `clientId`、一次性凭据和主题发布 QoS 1、非 retained 的 `4°C / GOOD` 消息；EMQX → API → MySQL → 读数 API 链路通过，设备在线时间更新。重复发布同一 `messageId` 后仍只有 1 条读数。
- 验收中确认设备 MQTT 客户端必须使用注册响应给出的精确 `clientId`；MQTT 集成测试已同步修正为契约值。
- 后端全量 Maven 测试：37 项通过，0 失败、0 错误；前端 Node 测试：25 项通过；前端生产构建通过，仅有既有的主包超过 500 kB 提示。
- Vite 开发服务器已启动，首页返回 HTTP 200 且包含应用挂载点；通过 Vite 访问 `/actuator/health` 可正确代理到后端并返回 `UP`。
- Flyway 已验证空库到 `V009`、`V002→V009`、MQTT 集成空库到 `V009`，以及本地持久卷 `V005→V009`。

### 仍未完成或未启用

- 真实对象存储、语音转写、邮件服务、LLM 和 Embedding 凭据尚未配置；MinIO `storage` 与 Qdrant `vector` Profile 默认不启动。
- 真实外部 AI 的超时、计费、内容安全和生产级可观测性仍待接入；当前 `AI_EXTERNAL_CALLS_ENABLED=false`。
- 尚未加入 Playwright 浏览器 E2E；当前为后端集成测试、前端 Node 测试、生产构建和 Compose/API/MQTT 实链路验收。
- 生产 HTTPS、Secure Cookie、密钥管理、备份、监控和 CI/CD 仍属于阶段 5。
