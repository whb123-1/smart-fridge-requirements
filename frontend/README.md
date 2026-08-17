# 鲜知 · 智能冰箱管家

一个使用 Vue 3、Vue Router 与 Vite 构建的高保真前端原型。账户、会话和首次冰箱初始化已接入真实后端；库存、菜谱、饮食记录、购物清单和 AI 数据仍暂存于页面状态中。

## 运行

```bash
npm install
npm run dev
```

然后访问终端显示的本地地址（通常为 `http://localhost:5173`）。项目脚本已使用 Vite runner 配置加载器，以兼容受限的 Windows 工作区。

生产构建：

```bash
npm run build
```

## 后端接入

所有远端操作统一收口于 `src/services/api.js`。正式接口使用 `/api/v1` 响应信封；API 层负责内存访问令牌、401 后的单次刷新和 `data` 解析。开发环境由 Vite 代理后端，刷新令牌仅通过 HttpOnly Cookie 传递。
