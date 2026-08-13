# 蓝白像素冰箱内部风格改造计划

## 背景

用户希望将 3D 冰箱点击各区域后显示的「冰箱内部」和「分区管理」界面，改成更精致、统一的蓝白像素冰箱内部风格。当前 `FridgePanel.vue` 已具备初步蓝白像素样式，但 3D 视图中的分区标签、高亮框以及页面辅助元素（图例、提示、工具栏）尚未完全统一，且右侧面板的「冰箱内部」Tab 仍缺少“冰箱内壁、隔板、灯光”等冷藏室视觉元素。

## 目标

1. 让「冰箱内部」看起来像像素风格的冷藏室内部（带侧墙、顶灯、立体隔板）。
2. 让 3D 视图中的分区标签、告警标记、高亮框统一成蓝白像素风格。
3. 统一页面级辅助元素（图例、提示、悬浮工具）为蓝白硬边像素卡片。
4. 保持明亮配色，不使用黑色背景；与 Element Plus 组件共存。
5. 尽量使用纯 CSS，不新增图片资源。

## 方案

### 1. 新增主题变量文件

创建 `src/styles/pixel-theme.css`，集中定义蓝白像素调色板和可复用工具类：

- `--pixel-bg: #f4f9ff`
- `--pixel-wall: #e8f4fc`
- `--pixel-wall-dark: #d4e5f5`
- `--pixel-blue: #6f9fd0`
- `--pixel-blue-dark: #3a6ea5`
- `--pixel-blue-deep: #2c6bb5`
- `--pixel-blue-light: #9cc8f2`
- `--pixel-accent: #4c8fe0`
- `--pixel-text: #3a556f`
- `--pixel-text-secondary: #5a7ba0`
- `--pixel-white: #ffffff`
- `--pixel-shadow: rgba(74, 111, 160, 0.25)`

工具类：`.pixel-box`（硬边蓝框白底卡片）、`.pixel-text`（等宽字体）。

在 `src/main.ts` 中于 `style.css` 之后引入。

### 2. 改造 `src/components/FridgePanel.vue`

- **「冰箱内部」容器 `.fridge-interior`**：
  - 背景改为带左右侧墙暗部、顶部白光条、后壁横向冷光线的像素冷藏室。
  - 使用 `box-shadow` 模拟内凹高光和外部硬投影。

- **隔板 `.shelf-bar`**：
  - 改为带高光、底影的立体玻璃隔板效果。

- **信息条 `.inner-info`**：
  - 用 `.pixel-box` 包裹为硬边小卡片。

- **食材卡片 `.food-tile`**：
  - 保持硬边，阴影和悬停效果改用 CSS 变量统一。

- **「分区管理」描述列表 `.zone-desc`**：
  - 给单元格增加硬边框，label 单元格加右侧蓝边，强化像素表格感。

- **弹窗 `.pixel-dialog`**：
  - 将色值替换为 CSS 变量，与右侧面板保持一致。

### 3. 改造 `src/components/Fridge3D.vue`

- **分区标签 Sprite `makeLabel`**：
  - 改为硬边矩形像素标签：先画投影 → 背景 → 外边框 → 内边框 → 文字。
  - 字体使用 `bold 56px "Courier New", monospace`。
  - `CanvasTexture` 设置 `magFilter = minFilter = THREE.NearestFilter` 使边缘锐利。
  - 未配置分区使用灰蓝弱化显示。

- **告警 Sprite `makeAlertSprite`**：
  - 改为硬边矩形 + 投影，保留语义色但统一风格。
  - 同样设置 `NearestFilter`。

- **高亮线框 `updateAllVisuals`**：
  - 普通态：蓝色线框。
  - 悬停态：浅蓝填充 + 白/蓝线框。
  - 选中态：半透明亮蓝填充 + 白色/亮蓝线框（保持蓝白主题，同时提供明显选中反馈）。
  - 告警态：保留过期红/临期黄/低库存橙/异常紫，但边框与 Sprite 风格统一。

- **底部提示 `.fridge3d-tip` / 加载提示 `.fridge3d-loading`**：
  - `.fridge3d-tip` 改为白底蓝框硬边像素小卡片，去掉深色半透明胶囊背景。

### 4. 改造 `src/views/Fridge3DView.vue`

- **悬浮工具 `.floating-tools`**：
  - 容器加 `.pixel-box` 样式，按钮去圆角。

- **图例 `.legend`**：
  - 改成白底蓝框硬边卡片，`.dot` 改为小方块，文字用像素字体。
  - `.dot.selected` 颜色与 3D 选中态保持一致（亮蓝底或蓝底白边）。

- **中央提示 `.center-hint`**：
  - 改成白底蓝框硬边卡片，统一投影。

## 关键文件

- `D:\冰箱\vite-project\src\styles\pixel-theme.css`（新增）
- `D:\冰箱\vite-project\src\main.ts`（引入主题文件）
- `D:\冰箱\vite-project\src\components\FridgePanel.vue`（面板冰箱内部、分区管理样式）
- `D:\冰箱\vite-project\src\components\Fridge3D.vue`（3D 标签、告警、高亮、提示样式）
- `D:\冰箱\vite-project\src\views\Fridge3DView.vue`（页面辅助元素样式）

## 验证方式

1. 打开 3D 冰箱页面，点击四个分区，确认右侧面板「冰箱内部」出现蓝白像素冷藏室效果（顶灯、侧墙、立体隔板）。
2. 确认 3D 分区标签为硬边矩形、等宽字体、蓝白配色，未配置分区弱化显示。
3. 悬停/选中分区，确认高亮框为蓝白风格；告警分区颜色可读。
4. 检查页面图例、提示、悬浮工具均为硬边蓝白像素卡片，无深色半透明背景。
5. 打开「分区管理」Tab 及新增/编辑/添加食材弹窗，确认保持统一像素风格。
6. 回归检查其他页面未受 `pixel-theme.css` 全局变量影响。
