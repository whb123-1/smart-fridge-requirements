# 鲜知 · 智能冰箱管家

一个使用 Vue 3 + Vite 构建的高保真前端原型。包含冰箱环境监测、库存、菜谱、饮食记录、购物清单、统计与偏好设置，数据暂存于页面状态中。

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

所有远端操作统一收口于 `src/services/api.js`。后端接入时可保留页面数据结构，将各方法替换为真实 API 请求，并在鉴权拦截器中附带 token。
