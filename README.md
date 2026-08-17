# 鲜知智慧冰箱

仓库包含 Vue 3 前端原型和阶段 0-1 的 Spring Boot 后端。当前真实持久化范围是账户、会话、用户资料、首次冰箱初始化、分区及逻辑传感器槽位；库存、菜谱、采购、饮食和 AI 仍为前端演示数据。

## 环境要求

- Docker Desktop（含 Docker Compose）
- Java 21；项目自带 Maven Wrapper，不要求全局 Maven
- Node.js 20+

## 启动后端

在仓库根目录创建本地配置并启动 Compose：

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

API 位于 `http://localhost:8080`，健康检查位于 `http://localhost:8080/actuator/health`。MySQL 使用 `xianzhi` schema，Flyway 会从空库创建阶段 0-1 表结构。

## 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

开发服务器会把 `/api` 和 `/actuator` 代理到 `127.0.0.1:8080`，因此 HttpOnly 刷新 Cookie 可以保持同源。访问令牌只保存在浏览器内存中，刷新页面时通过 Cookie 恢复会话。

首次使用流程为：注册或登录 -> 配置 3-6 个分区 -> 配置每类 0-4 个传感器槽位 -> 确认并进入冰箱首页。

## 验证

```powershell
Set-Location backend
.\mvnw.cmd test
Set-Location ..\frontend
npm test
npm run build
```

后端 Testcontainers 测试需要 Docker；检测不到 Docker 时会跳过容器集成测试，普通单元测试和架构测试仍会执行。HTTP 契约位于 `backend/openapi.yaml`。
