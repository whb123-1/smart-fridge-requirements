# 智慧冰箱与智能菜谱系统 — 技术方案

## 1. 技术栈

| 层 | 技术 | 说明 |
| --- | --- | --- |
| 前端 | Vue 3 + TypeScript + Vite | 已有 `vite-project` 工程，直接在其上扩展 |
| 前端 UI | Element Plus + Vue Router + Pinia + Axios | 组件库、路由、状态管理、请求封装 |
| 后端 | Spring Boot 3.5.x | Java 21 编译目标（本机 JDK 25 可运行） |
| 持久层 | MyBatis-Plus | 通用 CRUD + 分页 |
| 数据库 | MySQL 8.0 | utf8mb4 |
| 认证 | JWT + BCrypt | 无状态登录，`Authorization: Bearer <token>` |
| 构建 | Maven Wrapper + npm | 无需全局安装 Maven |

## 2. 项目结构

```
D:\冰箱
├── docs/                  # 架构文档、数据库脚本
│   ├── architecture.md
│   └── schema.sql
├── backend/               # Spring Boot 后端
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/main/java/com/smartfridge/
│       ├── SmartFridgeApplication.java
│       ├── common/        # 统一返回、异常、分页
│       ├── security/      # JWT 工具、登录拦截器、当前用户
│       └── module/
│           ├── user/      # 账号与饮食偏好
│           ├── zone/      # 冰箱分区与温湿度记录
│           ├── food/      # 库存、消耗、保质期
│           ├── recipe/    # 菜谱与推荐
│           ├── diet/      # 饮食记录与评估
│           ├── shopping/  # 购物清单
│           ├── reminder/  # 提醒
│           └── stats/     # 消耗统计
└── vite-project/          # Vue 3 前端
    └── src/
        ├── api/           # 接口封装
        ├── router/        # 路由与登录守卫
        ├── stores/        # Pinia 用户状态
        ├── layouts/       # 登录/主布局
        └── views/         # 页面
```

## 3. 模块与需求对应

| 需求章节 | 后端模块 | 核心接口 |
| --- | --- | --- |
| 二、账号与偏好 | user | 注册、登录、退出、我的信息、偏好增改 |
| 三、冰箱基础属性 | zone | 分区 CRUD、温湿度上报、记录查询、异常提醒 |
| 四、库存管理 | food | 食材 CRUD、分类筛选、计量单位推荐、消耗/过期/丢弃记录 |
| 五、保质期提醒 | food + reminder | 建议食用期限计算（结合分区温湿度）、临期/过期/低库存提醒 |
| 六、智能菜谱 | recipe | 库存匹配推荐（可制作/缺少量/替代）、忌口过滤、指定生成、用量调整、收藏与历史 |
| 七、热量饮食 | diet | 每日记录、当日汇总、均衡评估建议 |
| 八、购物与统计 | shopping + stats | 自动生成购物清单、标记已购、周/月消耗统计 |

## 4. 关键设计说明

### 4.1 认证
- 注册时密码使用 BCrypt 加密存储；登录成功返回 JWT（有效期 7 天）。
- 后端通过拦截器校验 `Authorization` 头，解析出当前用户 ID 放入上下文。
- 所有业务接口按当前用户隔离数据（`user_id` 字段）。

### 4.2 保质期计算
- 基础参考期限来自 `food_category.shelf_life_days`（按分类、未开封）。
- 开封后按分类的「开封后期限」折算；存放分区有温湿度时，结合
  `zone_record` 计算折算系数（低温冷藏正常 → 系数 1.0；温度偏高或湿度异常 → 按偏离程度打折）。
- 温度异常持续期间不恢复已损失的期限；无法可靠评估时提示「请检查食材状态」。
- 计算后的日期标记来源：包装标注 / 系统估算 / 按参考温湿度估算。

### 4.3 菜谱推荐
- 规则引擎：计算库存对菜谱原料的覆盖率。
  - 全部必需原料有库存 → 可直接制作；
  - 缺 1~2 项且存在替代品 → 可用替代制作；
  - 其余 → 缺少食材。
- 推荐前先过滤掉包含用户忌口 / 过敏食材的菜谱。
- 支持按烹饪时间、口味、热量、饮食目标筛选。
- 用量调整：主料按比例缩放，调味品在上下限范围内微调，不做同比例放大。

### 4.4 数据隔离与删除
- 所有业务表带 `user_id`，MyBatis-Plus 逻辑删除统一用 `deleted` 字段。

## 5. 运行方式

见根目录 `README.md`。
