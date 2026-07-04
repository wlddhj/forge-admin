# 多套 UI 主题切换系统 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 forge-web 实现 4 套可自主切换的 UI 预设套餐（默认/极客紫/商务器/酷暗黑），每套含配色+布局+风格三维度，每套支持明/暗双版本。

**Architecture:** 三层抽象（palette+layout+style）→ 4 个预设套餐。CSS 变量分两阶（业务变量 → EP/vxe 变量），切换通过 `data-palette`/`data-layout`/`data-style` 属性 + Vue `<component :is>` 完成，无刷新。localStorage 持久化（沿用 `forge_admin-page-config` key）。

**Tech Stack:** Vue 3.4 + TypeScript + Element Plus 2.4 + vxe-table 4.9 + Pinia 2.1 + Vite 5 + Sass（modern API）+ vitest 1.6 + happy-dom

## Global Constraints

- **Node**: 22.9.0；**pnpm**: 8.15.4
- **工作目录**: 所有 `pnpm` / `vitest` 命令在 `apps/forge-web/` 下执行
- **localStorage key**: `forge_admin-page-config`（沿用，禁止改名）
- **CSS 变量分层**: 第一阶 `--app-*`（业务），第二阶 `--el-*` / `--vxe-ui-*`（框架）
- **数据属性**: `data-palette`、`data-layout`、`data-style` 设置在 `document.documentElement`
- **提交规范**: `<type>(<scope>): <subject>`，中文。**禁止** `Co-Authored-By`
- **不引入新依赖**: 复用现有 element-plus、vxe-pc-ui、sass、vitest、@vue/test-utils、@pinia/testing、happy-dom
- **不破坏现有**: `light`/`dark` 切换保留、`vxe-table` 主题跟随、WebSocket 通知不丢、keep-alive 缓存保留
- **测试约定**: `src/<module>/__tests__/<name>.test.ts`，vitest globals（无需 import describe/it/expect）
- **性能预算**: 切换同步耗时 < 16ms；新增 CSS（gzip）< 8KB；新增 JS（gzip）< 5KB

---

## 文件结构总览

**新建：**
```
src/
├── themes/
│   ├── index.ts                          # Task 1: 套餐注册表 + 类型
│   ├── tokens.scss                       # Task 2: 第一阶业务变量默认值
│   ├── palettes/
│   │   ├── blue.scss                     # Task 3
│   │   ├── purple.scss                   # Task 3
│   │   ├── green.scss                    # Task 3
│   │   └── crimson.scss                  # Task 3
│   ├── styles/
│   │   ├── _flat.scss                    # Task 4
│   │   ├── _glass.scss                   # Task 4
│   │   ├── _card.scss                    # Task 4
│   │   └── _compact.scss                 # Task 4
│   └── __tests__/
│       └── themes.test.ts                # Task 1
├── layouts/
│   ├── LayoutSidebar.vue                 # Task 7: 抽自 BasicLayout
│   └── LayoutTop.vue                     # Task 9: 新增
└── stores/
    └── __tests__/
        └── pageConfig.test.ts            # Task 5
```

**修改：**
```
src/
├── styles/index.scss                     # Task 2/3/4: 引入 themes 样式
├── stores/pageConfig.ts                  # Task 5: 增加 preset 字段
├── main.ts                               # Task 6: 初始化 applyPreset
├── layouts/BasicLayout.vue               # Task 8: 改为分发器；Task 10: 清硬编码
└── components/SettingsPanel.vue          # Task 11: 加套餐切换 UI
```

---

## Task 1: 套餐注册表与类型定义

**Files:**
- Create: `apps/forge-web/src/themes/index.ts`
- Test: `apps/forge-web/src/themes/__tests__/themes.test.ts`

**Interfaces:**
- Consumes: 无（项目内首个 themes 文件）
- Produces:
  - `Palette` 类型: `'blue' | 'purple' | 'green' | 'crimson'`
  - `LayoutKind` 类型: `'sidebar' | 'top'`
  - `StyleKind` 类型: `'flat' | 'glass' | 'card' | 'compact'`
  - `Preset` 接口: `{ id: string; name: string; palette: Palette; layout: LayoutKind; style: StyleKind }`
  - `PRESETS` 常量: `Preset[]`，长度 4
  - `getPreset(id: string): Preset` 函数，未知 id 回落 `PRESETS[0]`

- [ ] **Step 1: 写失败测试**

创建 `apps/forge-web/src/themes/__tests__/themes.test.ts`：

```ts
import { PRESETS, getPreset } from '@/themes'
import type { Palette, LayoutKind, StyleKind, Preset } from '@/themes'

describe('套餐注册表', () => {
  it('注册了 4 个套餐', () => {
    expect(PRESETS).toHaveLength(4)
  })

  it('套餐 id 唯一', () => {
    const ids = PRESETS.map(p => p.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('default 套餐三维度组合正确', () => {
    expect(getPreset('default')).toMatchObject({
      palette: 'blue',
      layout: 'sidebar',
      style: 'flat'
    })
  })

  it('geek 套餐三维度组合正确', () => {
    expect(getPreset('geek')).toMatchObject({
      palette: 'purple',
      layout: 'top',
      style: 'glass'
    })
  })

  it('business 套餐三维度组合正确', () => {
    expect(getPreset('business')).toMatchObject({
      palette: 'green',
      layout: 'sidebar',
      style: 'card'
    })
  })

  it('dark-pro 套餐三维度组合正确', () => {
    expect(getPreset('dark-pro')).toMatchObject({
      palette: 'crimson',
      layout: 'sidebar',
      style: 'compact'
    })
  })

  it('未知 id 回落到 default', () => {
    expect(getPreset('unknown').id).toBe('default')
    expect(getPreset('').id).toBe('default')
  })

  it('类型导出可用', () => {
    const p: Palette = 'blue'
    const l: LayoutKind = 'sidebar'
    const s: StyleKind = 'flat'
    const preset: Preset = { id: 'x', name: 'X', palette: p, layout: l, style: s }
    expect(preset).toBeDefined()
  })
})
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd apps/forge-web && pnpm test:run -- themes 2>&1 | tail -20
```

预期：FAIL，提示 `Cannot find module '@/themes'`

- [ ] **Step 3: 写最小实现**

创建 `apps/forge-web/src/themes/index.ts`：

```ts
export type Palette = 'blue' | 'purple' | 'green' | 'crimson'
export type LayoutKind = 'sidebar' | 'top'
export type StyleKind = 'flat' | 'glass' | 'card' | 'compact'

export interface Preset {
  id: string
  name: string
  palette: Palette
  layout: LayoutKind
  style: StyleKind
}

export const PRESETS: Preset[] = [
  { id: 'default',  name: '默认',     palette: 'blue',    layout: 'sidebar', style: 'flat' },
  { id: 'geek',     name: '极客紫',   palette: 'purple',  layout: 'top',     style: 'glass' },
  { id: 'business', name: '商务器',   palette: 'green',   layout: 'sidebar', style: 'card' },
  { id: 'dark-pro', name: '酷暗黑',   palette: 'crimson', layout: 'sidebar', style: 'compact' }
]

export const getPreset = (id: string): Preset =>
  PRESETS.find(p => p.id === id) ?? PRESETS[0]
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd apps/forge-web && pnpm test:run -- themes 2>&1 | tail -20
```

预期：PASS，8 个测试全通过

- [ ] **Step 5: 提交**

```bash
git add apps/forge-web/src/themes/index.ts apps/forge-web/src/themes/__tests__/themes.test.ts
git commit -m "feat(theme): 新增套餐注册表与类型定义

定义 Palette/Layout/Style 三维度类型与 4 个预设套餐，
提供 getPreset 函数支持未知 id 回落到默认套餐。"
```

---

## Task 2: 第一阶业务变量 tokens.scss

**Files:**
- Create: `apps/forge-web/src/themes/tokens.scss`
- Modify: `apps/forge-web/src/styles/index.scss`

**Interfaces:**
- Consumes: 无（CSS 变量基础层）
- Produces: CSS 自定义属性（`--app-color-primary`、`--app-sidebar-bg`、`--app-radius-base` 等），供 palettes/styles/组件 SCSS 引用

- [ ] **Step 1: 创建 tokens.scss**

创建 `apps/forge-web/src/themes/tokens.scss`：

```scss
// 第一阶业务变量：默认值
// 具体值由 [data-palette] / [data-style] 覆盖
:root {
  // ─── 调色板相关（由 palette 覆盖） ───
  --app-color-primary: #409EFF;
  --app-color-success: #67c23a;
  --app-color-warning: #e6a23c;
  --app-color-danger:  #f56c6c;

  --app-sidebar-bg:     #304156;
  --app-sidebar-text:   #bfcbd9;
  --app-sidebar-active: var(--app-color-primary);

  --app-header-bg:      #ffffff;

  // ─── 风格相关（由 style 覆盖） ───
  --app-radius-sm:   2px;
  --app-radius-base: 4px;
  --app-radius-lg:   8px;

  --app-shadow-card:    0 1px 4px rgba(0, 21, 41, 0.08);
  --app-shadow-popover: 0 2px 12px rgba(0, 0, 0, 0.12);

  --app-gap-page:    10px;
  --app-gap-card:    10px;
  --app-density-pad: 10px;
}

// dark 通用覆盖（不依赖 palette，调中性色）
[data-theme='dark'] {
  --app-header-bg:     var(--el-bg-color);
  --app-shadow-card:   0 1px 4px rgba(0, 0, 0, 0.3);
  --app-shadow-popover:0 2px 12px rgba(0, 0, 0, 0.5);
}
```

- [ ] **Step 2: 引入到 index.scss**

修改 `apps/forge-web/src/styles/index.scss`，在 `@use './var.css';` 之后加一行：

```scss
@use './var.css';
@use '../themes/tokens.scss';  // ← 新增

// 引入响应式样式
@use './responsive.scss' as *;

// 引入表格页面样式
@use './table-page.scss' as *;

// ... 其余不变
```

- [ ] **Step 3: 验证 dev 启动不报错**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

预期：看到 `ready in XXXms`，无 SCSS 错误

- [ ] **Step 4: 视觉验证**

```bash
cd apps/forge-web && pnpm dev
```

打开 `http://localhost:3003`，登录后**视觉应与改动前完全一致**（默认值与原 var.css 同）。验证后停掉 dev。

- [ ] **Step 5: 提交**

```bash
git add apps/forge-web/src/themes/tokens.scss apps/forge-web/src/styles/index.scss
git commit -m "feat(theme): 新增第一阶业务变量 tokens.scss

定义 --app-* 语义变量默认值（与现有 var.css 同值），
为后续调色板/风格覆盖提供基础。"
```

---

## Task 3: 4 个调色板 SCSS

**Files:**
- Create: `apps/forge-web/src/themes/palettes/blue.scss`
- Create: `apps/forge-web/src/themes/palettes/purple.scss`
- Create: `apps/forge-web/src/themes/palettes/green.scss`
- Create: `apps/forge-web/src/themes/palettes/crimson.scss`
- Modify: `apps/forge-web/src/styles/index.scss`

**Interfaces:**
- Consumes: Task 2 的 `--app-*` 变量（在 `:root` 已定义）
- Produces: `[data-palette='xxx']` 选择器，覆盖 `--app-color-*`、`--app-sidebar-*` 并桥接到 `--el-*` / `--vxe-ui-*`

- [ ] **Step 1: 创建 blue.scss**

创建 `apps/forge-web/src/themes/palettes/blue.scss`：

```scss
[data-palette='blue'] {
  --app-color-primary: #409EFF;
  --app-color-success: #67c23a;
  --app-color-warning: #e6a23c;
  --app-color-danger:  #f56c6c;

  // 桥接到 Element Plus
  --el-color-primary:        var(--app-color-primary);
  --el-color-success:        var(--app-color-success);
  --el-color-warning:        var(--app-color-warning);
  --el-color-danger:         var(--app-color-danger);
  --el-color-primary-light-3:#79bbff;
  --el-color-primary-light-5:#a0cfff;
  --el-color-primary-light-7:#c6e2ff;
  --el-color-primary-light-9:#ecf5ff;
  --el-color-primary-dark-2: #337ecc;

  // 桥接到 vxe-table
  --vxe-ui-primary-color: var(--app-color-primary);

  // 侧栏（仅在 sidebar 布局下生效）
  [data-layout='sidebar'] & {
    --app-sidebar-bg:     #304156;
    --app-sidebar-text:   #bfcbd9;
    --app-sidebar-active: var(--app-color-primary);
  }
}

[data-palette='blue'][data-theme='dark'] {
  --app-color-primary:         #3a8ee6;
  --el-color-primary-light-3:  #1d6dbf;
}
```

- [ ] **Step 2: 创建 purple.scss**

创建 `apps/forge-web/src/themes/palettes/purple.scss`：

```scss
[data-palette='purple'] {
  --app-color-primary: #722ed1;
  --app-color-success: #52c41a;
  --app-color-warning: #faad14;
  --app-color-danger:  #eb2f96;

  --el-color-primary:        var(--app-color-primary);
  --el-color-success:        var(--app-color-success);
  --el-color-warning:        var(--app-color-warning);
  --el-color-danger:         var(--app-color-danger);
  --el-color-primary-light-3:#b37feb;
  --el-color-primary-light-5:#d3adf7;
  --el-color-primary-light-7:#efdbff;
  --el-color-primary-light-9:#f9f0ff;
  --el-color-primary-dark-2: #531dab;

  --vxe-ui-primary-color: var(--app-color-primary);

  [data-layout='sidebar'] & {
    --app-sidebar-bg:     #1f0f3d;
    --app-sidebar-text:   #c8b8e8;
    --app-sidebar-active: var(--app-color-primary);
  }
}

[data-palette='purple'][data-theme='dark'] {
  --app-color-primary:         #9254de;
  --el-color-primary-light-3:  #692db3;
}
```

- [ ] **Step 3: 创建 green.scss**

创建 `apps/forge-web/src/themes/palettes/green.scss`：

```scss
[data-palette='green'] {
  --app-color-primary: #52c41a;
  --app-color-success: #389e0d;
  --app-color-warning: #faad14;
  --app-color-danger:  #ff4d4f;

  --el-color-primary:        var(--app-color-primary);
  --el-color-success:        var(--app-color-success);
  --el-color-warning:        var(--app-color-warning);
  --el-color-danger:         var(--app-color-danger);
  --el-color-primary-light-3:#95de64;
  --el-color-primary-light-5:#b7eb8f;
  --el-color-primary-light-7:#d9f7be;
  --el-color-primary-light-9:#f6ffed;
  --el-color-primary-dark-2: #389e0d;

  --vxe-ui-primary-color: var(--app-color-primary);

  [data-layout='sidebar'] & {
    --app-sidebar-bg:     #0d2818;
    --app-sidebar-text:   #b8e6c1;
    --app-sidebar-active: var(--app-color-primary);
  }
}

[data-palette='green'][data-theme='dark'] {
  --app-color-primary:         #389e0d;
  --el-color-primary-light-3:  #1f5e08;
}
```

- [ ] **Step 4: 创建 crimson.scss**

创建 `apps/forge-web/src/themes/palettes/crimson.scss`：

```scss
[data-palette='crimson'] {
  --app-color-primary: #f5222d;
  --app-color-success: #52c41a;
  --app-color-warning: #faad14;
  --app-color-danger:  #a8071a;

  --el-color-primary:        var(--app-color-primary);
  --el-color-success:        var(--app-color-success);
  --el-color-warning:        var(--app-color-warning);
  --el-color-danger:         var(--app-color-danger);
  --el-color-primary-light-3:#ff7875;
  --el-color-primary-light-5:#ffccc7;
  --el-color-primary-light-7:#ffa39e;
  --el-color-primary-light-9:#fff1f0;
  --el-color-primary-dark-2: #cf1322;

  --vxe-ui-primary-color: var(--app-color-primary);

  [data-layout='sidebar'] & {
    --app-sidebar-bg:     #1a0808;
    --app-sidebar-text:   #e6b8b8;
    --app-sidebar-active: var(--app-color-primary);
  }
}

[data-palette='crimson'][data-theme='dark'] {
  --app-color-primary:         #cf1322;
  --el-color-primary-light-3:  #820a14;
}
```

- [ ] **Step 5: 引入到 index.scss**

修改 `apps/forge-web/src/styles/index.scss`，在 tokens.scss 之后加 4 行：

```scss
@use './var.css';
@use '../themes/tokens.scss';
@use '../themes/palettes/blue.scss';     // ← 新增
@use '../themes/palettes/purple.scss';   // ← 新增
@use '../themes/palettes/green.scss';    // ← 新增
@use '../themes/palettes/crimson.scss';  // ← 新增
// ... 后续不变
```

- [ ] **Step 6: dev 启动验证**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

预期：`ready in XXXms`，无 SCSS 错误

- [ ] **Step 7: 视觉验证（手动切换 data-palette）**

```bash
cd apps/forge-web && pnpm dev
```

打开 `http://localhost:3003`，登录后在浏览器 console 执行：

```js
document.documentElement.setAttribute('data-palette', 'purple')
document.documentElement.setAttribute('data-palette', 'green')
document.documentElement.setAttribute('data-palette', 'crimson')
document.documentElement.setAttribute('data-palette', 'blue')
```

每次执行后主色应明显变化（蓝→紫→绿→红→蓝）。验证后停掉 dev。

- [ ] **Step 8: 提交**

```bash
git add apps/forge-web/src/themes/palettes/ apps/forge-web/src/styles/index.scss
git commit -m "feat(theme): 新增 4 个调色板 SCSS

定义 blue/purple/green/crimson 四套色板，
桥接到 Element Plus 与 vxe-table 变量，
支持 light/dark 双版本色值切换。"
```

---

## Task 4: 4 个风格 mixin

**Files:**
- Create: `apps/forge-web/src/themes/styles/_flat.scss`
- Create: `apps/forge-web/src/themes/styles/_glass.scss`
- Create: `apps/forge-web/src/themes/styles/_card.scss`
- Create: `apps/forge-web/src/themes/styles/_compact.scss`
- Modify: `apps/forge-web/src/styles/index.scss`

**Interfaces:**
- Consumes: Task 2 的 `--app-*` 风格变量
- Produces: `[data-style='xxx']` 选择器，覆盖圆角/阴影/间距/密度

- [ ] **Step 1: 创建 _flat.scss**

创建 `apps/forge-web/src/themes/styles/_flat.scss`：

```scss
// 扁平风：默认值，最小装饰
[data-style='flat'] {
  --app-radius-base: 4px;
  --app-radius-lg:   8px;
  --app-radius-sm:   2px;

  --app-shadow-card:    0 1px 4px rgba(0, 21, 41, 0.08);
  --app-shadow-popover: 0 2px 12px rgba(0, 0, 0, 0.12);

  --app-gap-page:    10px;
  --app-gap-card:    10px;
  --app-density-pad: 10px;
}
```

- [ ] **Step 2: 创建 _glass.scss**

创建 `apps/forge-web/src/themes/styles/_glass.scss`：

```scss
// 玻璃拟态：大圆角、模糊背景、柔和阴影
@mixin glass-surface {
  // 基础：不支持 backdrop-filter 时退化到半透明实色
  background: rgba(255, 255, 255, 0.92);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-card);

  // 渐进增强
  @supports (backdrop-filter: blur(12px)) {
    background: rgba(255, 255, 255, 0.65);
    backdrop-filter: blur(12px) saturate(180%);
  }

  [data-theme='dark'] & {
    background: rgba(30, 30, 40, 0.92);

    @supports (backdrop-filter: blur(12px)) {
      background: rgba(30, 30, 40, 0.65);
    }
  }
}

[data-style='glass'] {
  --app-radius-base: 12px;
  --app-radius-lg:   16px;
  --app-radius-sm:   8px;

  --app-shadow-card:    0 4px 16px rgba(0, 0, 0, 0.08);
  --app-shadow-popover: 0 8px 24px rgba(0, 0, 0, 0.12);

  --app-gap-page:    16px;
  --app-gap-card:    16px;
  --app-density-pad: 12px;

  // 应用到关键容器
  .el-card { @include glass-surface; }
}
```

- [ ] **Step 3: 创建 _card.scss**

创建 `apps/forge-web/src/themes/styles/_card.scss`：

```scss
// 卡片化：中圆角、明显阴影、宽松间距
[data-style='card'] {
  --app-radius-base: 8px;
  --app-radius-lg:   12px;
  --app-radius-sm:   4px;

  --app-shadow-card:    0 2px 8px rgba(0, 0, 0, 0.1);
  --app-shadow-popover: 0 4px 16px rgba(0, 0, 0, 0.15);

  --app-gap-page:    12px;
  --app-gap-card:    14px;
  --app-density-pad: 12px;

  // 强化卡片视觉
  .el-card {
    border-radius: var(--app-radius-lg);
    box-shadow: var(--app-shadow-card);
  }

  .app-container {
    padding: var(--app-gap-page);
  }
}
```

- [ ] **Step 4: 创建 _compact.scss**

创建 `apps/forge-web/src/themes/styles/_compact.scss`：

```scss
// 紧凑：小圆角、极弱阴影、密集间距
[data-style='compact'] {
  --app-radius-base: 2px;
  --app-radius-lg:   4px;
  --app-radius-sm:   1px;

  --app-shadow-card:    0 1px 2px rgba(0, 0, 0, 0.06);
  --app-shadow-popover: 0 2px 8px rgba(0, 0, 0, 0.1);

  --app-gap-page:    6px;
  --app-gap-card:    8px;
  --app-density-pad: 6px;

  // 表格/表单更紧凑
  .el-card {
    --el-card-padding: 8px;
    border-radius: var(--app-radius-base);
  }

  .el-dialog {
    --el-dialog-padding-primary: 8px;
  }

  .el-form-item {
    margin-bottom: 12px;
  }

  .vxe-table {
    --vxe-ui-table-cell-padding-left: 4px;
    --vxe-ui-table-cell-padding-right: 4px;
  }
}
```

- [ ] **Step 5: 引入到 index.scss**

修改 `apps/forge-web/src/styles/index.scss`：

```scss
@use './var.css';
@use '../themes/tokens.scss';
@use '../themes/palettes/blue.scss';
@use '../themes/palettes/purple.scss';
@use '../themes/palettes/green.scss';
@use '../themes/palettes/crimson.scss';
@use '../themes/styles/flat';      // 下划线前缀可省略
@use '../themes/styles/glass';
@use '../themes/styles/card';
@use '../themes/styles/compact';
// ... 后续不变
```

注意：文件名用 `_flat.scss` 但 `@use` 时写 `'../themes/styles/flat'`（Sass 约定，下划线可省略）。

- [ ] **Step 6: dev 启动验证**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

预期：`ready in XXXms`

- [ ] **Step 7: 视觉验证（手动切换 data-style）**

```bash
cd apps/forge-web && pnpm dev
```

浏览器 console 执行：

```js
document.documentElement.setAttribute('data-style', 'glass')
document.documentElement.setAttribute('data-style', 'card')
document.documentElement.setAttribute('data-style', 'compact')
document.documentElement.setAttribute('data-style', 'flat')
```

每次执行后圆角/间距/卡片阴影应明显变化。验证后停掉 dev。

- [ ] **Step 8: 提交**

```bash
git add apps/forge-web/src/themes/styles/ apps/forge-web/src/styles/index.scss
git commit -m "feat(theme): 新增 4 个风格 mixin

定义 flat/glass/card/compact 四种风格，
覆盖圆角、阴影、间距、密度变量，
glass 通过 @supports 检测 backdrop-filter 实现优雅降级。"
```

---

## Task 5: 扩展 pageConfig store

**Files:**
- Modify: `apps/forge-web/src/stores/pageConfig.ts`
- Test: `apps/forge-web/src/stores/__tests__/pageConfig.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `getPreset` 函数
- Produces:
  - `PageConfig.preset: string`（默认 `'default'`）
  - `applyPreset(presetId: string): void` 方法
  - `changePreset(presetId: string): void` 方法
  - `resetConfig()` 同时重置 preset 为 `'default'`

- [ ] **Step 1: 写失败测试**

创建 `apps/forge-web/src/stores/__tests__/pageConfig.test.ts`：

```ts
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect } from 'vitest'
import { usePageConfigStore } from '@/stores/pageConfig'

describe('pageConfig store - 套餐切换', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.className = ''
    document.documentElement.removeAttribute('data-palette')
    document.documentElement.removeAttribute('data-layout')
    document.documentElement.removeAttribute('data-style')
    document.documentElement.removeAttribute('data-theme')
  })

  it('默认 preset 为 default', () => {
    const store = usePageConfigStore()
    expect(store.config.preset).toBe('default')
  })

  it('applyPreset 设置三个 data 属性', () => {
    const store = usePageConfigStore()
    store.applyPreset('geek')
    const html = document.documentElement
    expect(html.getAttribute('data-palette')).toBe('purple')
    expect(html.getAttribute('data-layout')).toBe('top')
    expect(html.getAttribute('data-style')).toBe('glass')
  })

  it('applyPreset 不影响 theme（明暗独立）', () => {
    const store = usePageConfigStore()
    store.applyTheme('dark')
    store.applyPreset('business')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('changePreset 同时更新 config 和 data 属性', () => {
    const store = usePageConfigStore()
    store.changePreset('dark-pro')
    expect(store.config.preset).toBe('dark-pro')
    expect(document.documentElement.getAttribute('data-palette')).toBe('crimson')
  })

  it('未知 presetId 回落到 default', () => {
    const store = usePageConfigStore()
    store.applyPreset('unknown-id')
    expect(store.config.preset).toBe('default')
    expect(document.documentElement.getAttribute('data-palette')).toBe('blue')
  })

  it('localStorage 缺失 preset 字段时回落 default', () => {
    localStorage.setItem('forge_admin-page-config', JSON.stringify({ theme: 'dark' }))
    const store = usePageConfigStore()
    store.loadConfig()
    expect(store.config.preset).toBe('default')
  })

  it('localStorage 损坏时不抛错', () => {
    localStorage.setItem('forge_admin-page-config', '{not json')
    expect(() => {
      const store = usePageConfigStore()
      store.loadConfig()
    }).not.toThrow()
  })

  it('resetConfig 把 preset 重置为 default', () => {
    const store = usePageConfigStore()
    store.applyPreset('geek')
    store.resetConfig()
    expect(store.config.preset).toBe('default')
  })
})
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
cd apps/forge-web && pnpm test:run -- pageConfig 2>&1 | tail -30
```

预期：FAIL，提示 `config.preset is undefined` 或 `store.applyPreset is not a function`

- [ ] **Step 3: 修改 pageConfig.ts**

修改 `apps/forge-web/src/stores/pageConfig.ts`：

**改动 1**：在文件顶部 import：

```ts
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { CACHE_KEY, useCache } from "@/hooks/web/useCache.ts";
import { VxeUI } from "vxe-pc-ui";
import { getPreset } from '@/themes'  // ← 新增
const { wsCache } = useCache()
export type ThemeType = 'light' | 'dark'
```

**改动 2**：在 `PageConfig` 接口加 `preset` 字段：

```ts
export interface PageConfig {
  showTabs: boolean
  maxTabsCount: number
  autoHideTabsOnMobile: boolean

  theme: ThemeType
  preset: string              // ← 新增

  sidebarCollapsed: boolean
  showBreadcrumb: boolean
  showPageTransition: boolean
  keepAlive: boolean
}
```

**改动 3**：在 `defaultConfig` 加默认值：

```ts
const defaultConfig: PageConfig = {
  showTabs: true,
  maxTabsCount: 20,
  autoHideTabsOnMobile: true,
  theme: 'light',
  preset: 'default',          // ← 新增
  sidebarCollapsed: false,
  showBreadcrumb: true,
  showPageTransition: true,
  keepAlive: true
}
```

**改动 4**：在 `loadConfig` 内增加 preset 合法性校验：

```ts
const loadConfig = () => {
  try {
    const saved = localStorage.getItem(LOCAL_STORAGE_KEY)
    if (saved) {
      const parsed = JSON.parse(saved)
      config.value = { ...defaultConfig, ...parsed }
      // 校验 preset 合法性：未知 id 回落 default
      if (parsed?.preset && getPreset(parsed.preset).id !== parsed.preset) {
        config.value.preset = 'default'
      }
    } else {
      if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
        config.value.theme = 'dark'
      }
    }
  } catch (error) {
    console.error('加载页面配置失败:', error)
    config.value = { ...defaultConfig }
  }
}
```

**改动 5**：在 `applyTheme` 之后新增 `applyPreset` 和 `changePreset` 方法：

```ts
// 应用主题（现有，保持不变）
const applyTheme = (theme: ThemeType) => {
  document.documentElement.setAttribute('data-theme', theme)
  if (theme === 'dark') {
    document.documentElement.classList.add('dark')
    document.documentElement.classList.remove('light')
  } else {
    document.documentElement.classList.add('light')
    document.documentElement.classList.remove('dark')
  }
  wsCache.set(CACHE_KEY.IS_DARK, 'dark' === theme)
  VxeUI.setTheme(theme)
}

// 应用套餐（新增）
const applyPreset = (presetId: string) => {
  const preset = getPreset(presetId)
  config.value.preset = preset.id  // 规范化（未知 id 回落后）
  document.documentElement.setAttribute('data-palette', preset.palette)
  document.documentElement.setAttribute('data-layout', preset.layout)
  document.documentElement.setAttribute('data-style', preset.style)
}

// 切换套餐（新增，对外入口）
const changePreset = (presetId: string) => {
  applyPreset(presetId)
}
```

**改动 6**：在初始化时调用 `applyPreset`：

```ts
// 初始化时加载配置并应用主题
loadConfig()
applyTheme(config.value.theme)
applyPreset(config.value.preset)  // ← 新增
```

**改动 7**：在 store 返回值中导出新方法：

```ts
return {
  config,
  settingsVisible,
  updateConfig,
  updateMultipleConfig,
  resetConfig,
  openSettings,
  closeSettings,
  loadConfig,
  saveConfig,
  applyTheme,
  applyPreset,    // ← 新增
  changePreset,   // ← 新增
  toggleTheme
}
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd apps/forge-web && pnpm test:run -- pageConfig 2>&1 | tail -20
```

预期：PASS，8 个测试全通过

- [ ] **Step 5: 运行所有测试确保无回归**

```bash
cd apps/forge-web && pnpm test:run 2>&1 | tail -10
```

预期：全部 PASS

- [ ] **Step 6: 提交**

```bash
git add apps/forge-web/src/stores/pageConfig.ts apps/forge-web/src/stores/__tests__/pageConfig.test.ts
git commit -m "feat(theme): pageConfig 增加 preset 字段与切换方法

新增 applyPreset/changePreset 方法，设置 data-palette/layout/style 属性；
loadConfig 校验 preset 合法性，未知值回落 default；
不影响现有 theme（明暗）切换。"
```

---

## Task 6: main.ts 初始化时应用 preset

**Files:**
- Modify: `apps/forge-web/src/main.ts`

**Interfaces:**
- Consumes: Task 5 的 `usePageConfigStore().applyPreset`
- Produces: 应用启动时自动应用 localStorage 中的 preset（已在 store 初始化时完成，main.ts 仅需保证 store 被实例化）

**说明**：由于 `usePageConfigStore` 在 `app.use(createPinia())` 之后任何首次调用都会触发初始化（包括 BasicLayout 加载时），且初始化中已经调用 `applyPreset`，**main.ts 不需要显式调用**。但需要在路由守卫或 App.vue 中确保 store 被首次实例化。

- [ ] **Step 1: 检查现状**

```bash
grep -n "usePageConfigStore\|pageConfig" apps/forge-web/src/main.ts apps/forge-web/src/App.vue apps/forge-web/src/router/index.ts 2>/dev/null
```

如果都没有，需要在 main.ts 显式调用一次以保证启动时应用 preset。

- [ ] **Step 2: 修改 main.ts（仅在 Step 1 显示无引用时执行）**

修改 `apps/forge-web/src/main.ts`，在 `app.mount('#app')` 之前加：

```ts
// ... 现有 import
import { usePageConfigStore } from '@/stores/pageConfig'

// ... 现有代码
const { wsCache } = useCache()
const app = createApp(App)

// ... 现有注册代码
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
setupVxe(app)
setupFormCreate(app)

const setDefaultTheme = () => {
  let isDarkTheme = wsCache.get(CACHE_KEY.IS_DARK)
  if (isDarkTheme === null) {
    isDarkTheme = isDark()
  }
  VxeUI.setTheme(isDarkTheme ? 'dark' : 'light')
}
setDefaultTheme()

// 应用页面配置（preset + theme）—— 必须在 pinia 注册之后
const pageConfigStore = usePageConfigStore()
pageConfigStore.applyPreset(pageConfigStore.config.preset)
pageConfigStore.applyTheme(pageConfigStore.config.theme)

app.mount('#app')
```

**注意**：如果 Step 1 显示 `App.vue` 或 `router/index.ts` 已经实例化了 `usePageConfigStore`，则此 Task **跳过 Step 2**，直接进入 Step 3 验证。

- [ ] **Step 3: 验证启动**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

预期：无错误，`ready in XXXms`

- [ ] **Step 4: 验证 DOM 属性已设置**

```bash
cd apps/forge-web && pnpm dev
```

打开浏览器，登录后检查 `<html>` 元素，应有：
- `data-palette="blue"`（默认）
- `data-layout="sidebar"`
- `data-style="flat"`
- `data-theme="light"` 或 `"dark"`

Console 验证：

```js
document.documentElement.dataset
// 应输出 { palette: 'blue', layout: 'sidebar', style: 'flat', theme: 'light' }
```

- [ ] **Step 5: 提交**

```bash
git add apps/forge-web/src/main.ts
git commit -m "feat(theme): main.ts 启动时应用 preset 与 theme

确保 data-palette/layout/style/theme 属性在应用挂载前已设置，
避免主题切换闪烁。"
```

---

## Task 7: 抽出 LayoutSidebar.vue

**Files:**
- Create: `apps/forge-web/src/layouts/LayoutSidebar.vue`
- Reference: `apps/forge-web/src/layouts/BasicLayout.vue`（保持原状直到 Task 8）

**Interfaces:**
- Consumes: Task 5 的 `usePageConfigStore`、现有 composables/components
- Produces: `LayoutSidebar` 默认导出 Vue 组件，渲染侧栏+顶部+主内容区

**说明**：本 Task 只是把 BasicLayout 现有内容**复制**到 LayoutSidebar.vue，**BasicLayout 暂不动**（Task 8 改为分发器）。Task 10 才清理硬编码颜色。

- [ ] **Step 1: 创建 LayoutSidebar.vue（复制 BasicLayout 内容）**

创建 `apps/forge-web/src/layouts/LayoutSidebar.vue`，将 `apps/forge-web/src/layouts/BasicLayout.vue` 的全部内容（template + script + style）**逐字复制**过来。

复制的目的是保证功能 100% 一致，便于回滚验证。

- [ ] **Step 2: 验证编译**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

预期：`ready in XXXms`，无 TS / Vue 编译错误

- [ ] **Step 3: lint 验证**

```bash
cd apps/forge-web && pnpm lint 2>&1 | tail -10
```

预期：无错误（warning 可接受）

- [ ] **Step 4: 提交**

```bash
git add apps/forge-web/src/layouts/LayoutSidebar.vue
git commit -m "refactor(theme): 抽出 LayoutSidebar 组件

从 BasicLayout 复制内容到 LayoutSidebar，
为后续 BasicLayout 改为分发器做准备。
本提交仅为代码搬迁，视觉与功能不变。"
```

---

## Task 8: BasicLayout 改为分发器

**Files:**
- Modify: `apps/forge-web/src/layouts/BasicLayout.vue`（完全重写）

**Interfaces:**
- Consumes: Task 1 的 `getPreset`、Task 7 的 `LayoutSidebar`
- Produces: BasicLayout 改为按 preset 渲染对应布局组件的分发器

- [ ] **Step 1: 重写 BasicLayout.vue**

完全覆盖 `apps/forge-web/src/layouts/BasicLayout.vue` 内容为：

```vue
<template>
  <component :is="currentLayout" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import { getPreset } from '@/themes'
import LayoutSidebar from '@/layouts/LayoutSidebar.vue'

const { config } = usePageConfigStore()
const { isMobile } = useResponsive()

const currentLayout = computed(() => {
  // 移动端强制侧栏布局（无论套餐预设）
  if (isMobile.value) return LayoutSidebar
  // 桌面端按套餐预设选择（LayoutTop 在 Task 9 加入）
  return getPreset(config.preset).layout === 'top' ? LayoutSidebar : LayoutSidebar
})
</script>
```

**注意**：Task 8 阶段 LayoutTop 尚未实现，三元表达式两边都返回 `LayoutSidebar`，保证不破坏功能。Task 9 会引入 `LayoutTop` 并替换。

- [ ] **Step 2: 验证编译与功能**

```bash
cd apps/forge-web && pnpm dev
```

打开 `http://localhost:3003`，登录后视觉应与改动前**完全一致**。

Console 验证：

```js
usePageConfigStore()  // 不应报错
document.documentElement.dataset.layout  // 'sidebar'
```

- [ ] **Step 3: lint 验证**

```bash
cd apps/forge-web && pnpm lint 2>&1 | tail -10
```

- [ ] **Step 4: 提交**

```bash
git add apps/forge-web/src/layouts/BasicLayout.vue
git commit -m "refactor(theme): BasicLayout 改为布局分发器

按 preset.layout 与 isMobile 选择渲染 LayoutSidebar 或 LayoutTop，
为后续引入多布局做准备。当前 LayoutTop 未实现，临时全走 Sidebar。"
```

---

## Task 9: 新增 LayoutTop.vue

**Files:**
- Create: `apps/forge-web/src/layouts/LayoutTop.vue`
- Modify: `apps/forge-web/src/layouts/BasicLayout.vue`（替换三元表达式的 LayoutSidebar 为 LayoutTop）

**Interfaces:**
- Consumes: 现有 `useUserStore`、`usePermissionStore`、`useTabsStore`、`usePageConfigStore`、`useResponsive`、`useWebSocket`、`router`、`IconPreview`、`TabsView`、`SettingsPanel`
- Produces: `LayoutTop` Vue 组件，渲染顶部横向导航布局

- [ ] **Step 1: 创建 LayoutTop.vue**

创建 `apps/forge-web/src/layouts/LayoutTop.vue`：

```vue
<template>
  <el-container class="layout-container layout-top" :class="{ 'is-mobile': isMobile }">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="logo">
          <img src="/logo.svg" alt="logo" />
          <span>{{ appTitle }}</span>
        </div>
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          :ellipsis="false"
          background-color="transparent"
          text-color="var(--el-text-color-primary)"
          active-text-color="var(--app-color-primary)"
          router
        >
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>
          <template v-for="menu in topMenuList" :key="menu.id || menu.path">
            <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.routePath || menu.path">
              <template #title>
                <IconPreview v-if="menu.icon" :icon="menu.icon" :size="18" />
                <span>{{ menu.menuName || menu.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="child in menu.children.filter((c: any) => c.menuType !== 2)"
                :key="child.id"
                :index="getChildPath(menu.routePath || menu.path, child.routePath)"
              >
                <IconPreview v-if="child.icon" :icon="child.icon" :size="18" />
                <span>{{ child.menuName }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.routePath || menu.path">
              <IconPreview v-if="menu.icon" :icon="menu.icon" :size="18" />
              <span>{{ menu.menuName || menu.meta?.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
      <div class="header-right">
        <el-popover
          :visible="notificationVisible"
          placement="bottom-end"
          :width="360"
          trigger="click"
          @update:visible="(val: boolean) => notificationVisible = val"
        >
          <template #reference>
            <el-badge :value="wsUnreadCount" :hidden="wsUnreadCount === 0" :max="99">
              <el-icon class="header-icon"><Bell /></el-icon>
            </el-badge>
          </template>
          <div class="notification-panel">
            <div class="notification-header">
              <span class="notification-title">通知</span>
              <div class="notification-actions">
                <el-button v-if="wsNotifications.length > 0" type="primary" link size="small" @click="wsMarkAllRead">全部已读</el-button>
                <el-button type="primary" link size="small" @click="goToNoticePage">查看全部</el-button>
              </div>
            </div>
            <el-scrollbar max-height="320px">
              <div v-if="wsNotifications.length > 0" class="notification-list">
                <div v-for="item in wsNotifications" :key="item.timestamp" class="notification-item">
                  <div class="notification-item-title">{{ item.title }}</div>
                  <div class="notification-item-content">{{ item.content }}</div>
                  <div class="notification-item-time">{{ formatNotificationTime(item.timestamp) }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无通知" :image-size="60" />
            </el-scrollbar>
          </div>
        </el-popover>

        <el-tooltip :content="pageConfigStore.config.theme === 'light' ? '切换暗黑模式' : '切换明亮模式'" placement="bottom">
          <el-icon class="header-icon" @click="pageConfigStore.toggleTheme()">
            <Sunny v-if="pageConfigStore.config.theme === 'light'" />
            <Moon v-else />
          </el-icon>
        </el-tooltip>

        <el-tooltip content="页面设置" placement="bottom">
          <el-icon class="header-icon" @click="pageConfigStore.openSettings()">
            <Setting />
          </el-icon>
        </el-tooltip>

        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-avatar :size="isMobile ? 28 : 32" :src="userStore.userInfo?.avatar">
              {{ userStore.userInfo?.nickname?.charAt(0) }}
            </el-avatar>
            <span v-if="!isMobile" class="username">{{ userStore.userInfo?.nickname }}</span>
            <el-icon v-if="!isMobile"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <TabsView v-if="shouldShowTabs" />

    <el-main class="layout-content">
      <router-view v-slot="{ Component }">
        <keep-alive v-if="pageConfigStore.config.keepAlive" :include="tabsStore.cachedViews">
          <component :is="Component" :key="$route.path" />
        </keep-alive>
        <component v-else :is="Component" />
      </router-view>
    </el-main>

    <SettingsPanel />
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import { useWebSocket } from '@/composables/useWebSocket'
import { resetRouter } from '@/router'
import { HomeFilled, Sunny, Moon, Setting, Bell, ArrowDown } from '@element-plus/icons-vue'
import TabsView from '@/components/TabsView.vue'
import SettingsPanel from '@/components/SettingsPanel.vue'
import IconPreview from '@/components/IconPreview.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()
const pageConfigStore = usePageConfigStore()
const { isMobile } = useResponsive()
const { connect: wsConnect, disconnect: wsDisconnect, unreadCount: wsUnreadCount, notifications: wsNotifications, markAllRead: wsMarkAllRead } = useWebSocket()
const notificationVisible = ref(false)

const appTitle = import.meta.env.VITE_APP_TITLE

const activeMenu = computed(() => route.path)

const shouldShowTabs = computed(() => {
  if (isMobile.value && pageConfigStore.config.autoHideTabsOnMobile) return false
  return pageConfigStore.config.showTabs
})

// 顶栏布局：只显示一级菜单（有子菜单的作为折叠入口）
const topMenuList = computed(() => {
  const menus = userStore.menus
  if (menus && menus.length > 0) {
    return menus
      .filter((item: any) => item.visible !== 0 && item.menuType !== 2)
      .map((item: any) => {
        if (item.children && item.children.length > 0) {
          const filteredChildren = item.children.filter((c: any) => c.menuType !== 2 && c.visible !== 0)
          return { ...item, children: filteredChildren.length > 0 ? filteredChildren : undefined }
        }
        return item
      })
  }
  return permissionStore.routes
    .find((r: any) => r.path === '/')?.children
    ?.filter((item: any) => !item.meta?.hidden) || []
})

const getChildPath = (parentPath: string, childPath: string) => {
  if (childPath.startsWith('/')) return childPath
  return `${parentPath}/${childPath}`
}

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await userStore.logoutAction()
    } catch (e) {
      console.error('退出失败', e)
    } finally {
      permissionStore.resetRoutes()
      tabsStore.clearAllTabs()
      resetRouter()
      router.push('/login')
    }
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

const formatNotificationTime = (timestamp: number) => {
  const now = Date.now()
  const diff = now - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

const goToNoticePage = () => {
  notificationVisible.value = false
  router.push('/system/notice')
}

watch(
  () => route.path,
  (path) => {
    if (path && route.meta?.title && shouldShowTabs.value) {
      if (tabsStore.tabs.length >= pageConfigStore.config.maxTabsCount) {
        const closableTab = tabsStore.tabs.find(t => t.closable)
        if (closableTab) tabsStore.removeTab(closableTab.path)
      }
      tabsStore.addTab({
        path,
        title: route.meta.title as string,
        icon: route.meta.icon as string,
        closable: path !== '/dashboard',
        routeName: route.name as string
      })
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (userStore.token) wsConnect()
})

onUnmounted(() => {
  wsDisconnect()
})

watch(() => userStore.token, (newToken) => {
  if (newToken) wsConnect()
  else wsDisconnect()
})
</script>

<style scoped lang="scss">
@use '@/styles/responsive.scss' as *;

.layout-top {
  height: 100vh;
  flex-direction: column;
}

.layout-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--app-header-bg);
  box-shadow: var(--app-shadow-card);
  padding: 0 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .header-left {
    display: flex;
    align-items: center;
    gap: 24px;
    flex: 1;
    min-width: 0;

    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 18px;
      font-weight: bold;
      color: var(--el-text-color-primary);
      flex-shrink: 0;

      img {
        width: 32px;
        height: 32px;
      }
    }

    :deep(.el-menu) {
      flex: 1;
      min-width: 0;
      border-bottom: none;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-shrink: 0;

    .header-icon {
      font-size: 20px;
      cursor: pointer;
      color: var(--el-text-color-regular);

      &:hover {
        color: var(--app-color-primary);
      }
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;

      .username {
        color: var(--el-text-color-primary);
      }
    }
  }
}

.layout-content {
  background: var(--el-bg-color-page);
  padding: 10px;
  overflow: auto;
  flex: 1;
}

// 通知面板（与 LayoutSidebar 一致）
.notification-panel {
  .notification-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    margin-bottom: 8px;

    .notification-title {
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .notification-actions {
      display: flex;
      gap: 8px;
    }
  }

  .notification-list {
    .notification-item {
      padding: 10px 0;
      border-bottom: 1px solid var(--el-border-color-lighter);
      cursor: pointer;
      transition: background 0.3s;

      &:last-child {
        border-bottom: none;
      }

      &:hover {
        background: var(--el-bg-color-page);
      }

      .notification-item-title {
        font-size: 14px;
        font-weight: 500;
        color: var(--el-text-color-primary);
        margin-bottom: 4px;
      }

      .notification-item-content {
        font-size: 13px;
        color: var(--el-text-color-regular);
        line-height: 1.5;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .notification-item-time {
        font-size: 12px;
        color: var(--el-text-color-placeholder);
        margin-top: 4px;
      }
    }
  }
}

// 移动端（顶栏在移动端被 BasicLayout 强制切换为 sidebar，所以这里仅作 fallback）
.is-mobile {
  .layout-header {
    padding: 0 12px;
    height: 50px;
  }
}
</style>
```

- [ ] **Step 2: 修改 BasicLayout.vue 的 currentLayout**

修改 `apps/forge-web/src/layouts/BasicLayout.vue`，引入 LayoutTop 并替换三元表达式：

```vue
<template>
  <component :is="currentLayout" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import { getPreset } from '@/themes'
import LayoutSidebar from '@/layouts/LayoutSidebar.vue'
import LayoutTop from '@/layouts/LayoutTop.vue'

const { config } = usePageConfigStore()
const { isMobile } = useResponsive()

const currentLayout = computed(() => {
  if (isMobile.value) return LayoutSidebar
  return getPreset(config.preset).layout === 'top' ? LayoutTop : LayoutSidebar
})
</script>
```

- [ ] **Step 3: 验证编译**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

预期：`ready in XXXms`

- [ ] **Step 4: 视觉验证（geek 套餐切换到顶栏）**

```bash
cd apps/forge-web && pnpm dev
```

浏览器 console 执行：

```js
const { usePageConfigStore } = await import('/src/stores/pageConfig.ts')
// 简单方式：直接改属性
document.documentElement.setAttribute('data-palette', 'purple')

// 或者通过设置面板后续 Task 11 暴露的入口；这里临时手动改 localStorage
localStorage.setItem('forge_admin-page-config', JSON.stringify({ theme: 'light', preset: 'geek', showTabs: true }))
location.reload()
```

刷新后应看到：顶部横向导航布局，主色为紫色。

切回 default：

```js
localStorage.setItem('forge_admin-page-config', JSON.stringify({ theme: 'light', preset: 'default', showTabs: true }))
location.reload()
```

恢复侧栏布局。

- [ ] **Step 5: lint 验证**

```bash
cd apps/forge-web && pnpm lint 2>&1 | tail -10
```

- [ ] **Step 6: 提交**

```bash
git add apps/forge-web/src/layouts/LayoutTop.vue apps/forge-web/src/layouts/BasicLayout.vue
git commit -m "feat(theme): 新增 LayoutTop 顶部导航布局

支持 geek（极客紫）套餐使用顶部横向导航，
BasicLayout 分发器接入 LayoutTop，按 preset.layout 选择渲染。"
```

---

## Task 10: 清理 LayoutSidebar 硬编码颜色

**Files:**
- Modify: `apps/forge-web/src/layouts/LayoutSidebar.vue`

**Interfaces:**
- Consumes: Task 2/3 的 `--app-sidebar-bg` / `--app-sidebar-text` / `--app-color-primary` 变量
- Produces: LayoutSidebar 中所有硬编码颜色替换为 CSS 变量

- [ ] **Step 1: 替换 template 中的硬编码颜色**

修改 `apps/forge-web/src/layouts/LayoutSidebar.vue`，找到 `<el-menu>` 元素（约第 10-18 行）：

**修改前：**
```vue
<el-menu
  :default-active="activeMenu"
  :collapse="isCollapse"
  :unique-opened="true"
  background-color="#304156"
  text-color="#bfcbd9"
  active-text-color="#409EFF"
  router
>
```

**修改后：**
```vue
<el-menu
  :default-active="activeMenu"
  :collapse="isCollapse"
  :unique-opened="true"
  background-color="var(--app-sidebar-bg)"
  text-color="var(--app-sidebar-text)"
  active-text-color="var(--app-color-primary)"
  router
>
```

- [ ] **Step 2: 替换 style 中的硬编码颜色**

在 `<style scoped lang="scss">` 内：

**修改前（`.layout-aside` 块）：**
```scss
.layout-aside {
  background-color: #304156;
  transition: width 0.3s;
  // ...
```

**修改后：**
```scss
.layout-aside {
  background-color: var(--app-sidebar-bg);
  transition: width 0.3s;
  // ...
```

- [ ] **Step 3: 验证 dev 启动**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

- [ ] **Step 4: 视觉验证（侧栏色随套餐变化）**

```bash
cd apps/forge-web && pnpm dev
```

确保当前是 `default` 套餐，侧栏色应为 `#304156`（蓝套餐默认侧栏）。

Console 切换调色板：

```js
document.documentElement.setAttribute('data-palette', 'purple')
// 侧栏应变成 #1f0f3d（深紫）

document.documentElement.setAttribute('data-palette', 'green')
// 侧栏应变成 #0d2818（深绿）

document.documentElement.setAttribute('data-palette', 'crimson')
// 侧栏应变成 #1a0808（深红黑）

document.documentElement.setAttribute('data-palette', 'blue')
// 恢复 #304156
```

**注意**：由于侧栏色变量在 `[data-layout='sidebar'] &` 嵌套下定义，必须保证 `<html>` 上同时有 `data-layout='sidebar'`（默认值）才能看到效果。

- [ ] **Step 5: lint 与单元测试**

```bash
cd apps/forge-web && pnpm lint 2>&1 | tail -5
cd apps/forge-web && pnpm test:run 2>&1 | tail -5
```

- [ ] **Step 6: 提交**

```bash
git add apps/forge-web/src/layouts/LayoutSidebar.vue
git commit -m "refactor(theme): LayoutSidebar 替换硬编码颜色为 CSS 变量

将 #304156、#bfcbd9、#409EFF 替换为 var(--app-sidebar-bg) 等，
使侧栏色随套餐调色板自动变化。"
```

---

## Task 11: SettingsPanel 增加套餐切换 UI

**Files:**
- Modify: `apps/forge-web/src/components/SettingsPanel.vue`
- Test: 可选（视觉为主，逻辑已被 Task 5 的单元测试覆盖）

**Interfaces:**
- Consumes: Task 1 的 `PRESETS`、Task 5 的 `changePreset`
- Produces: SettingsPanel 顶部新增"主题套餐"区块

- [ ] **Step 1: 在 SettingsPanel.vue template 顶部插入套餐区块**

修改 `apps/forge-web/src/components/SettingsPanel.vue`，在 `<div class="settings-panel">` 内最前面（在"主题设置" section 之前）插入：

```vue
<!-- 主题套餐 -->
<div class="setting-section">
  <h3 class="section-title">主题套餐</h3>
  <p class="section-desc">选择整体视觉风格包，包含配色、布局、风格三维度</p>

  <div class="preset-grid">
    <div
      v-for="preset in PRESETS"
      :key="preset.id"
      class="preset-card"
      :class="{ active: localConfig.preset === preset.id }"
      @click="handlePresetChange(preset.id)"
    >
      <div class="preset-thumb" :data-palette="preset.palette" :data-style="preset.style">
        <span class="preset-thumb-sidebar" v-if="preset.layout === 'sidebar'"></span>
        <span class="preset-thumb-topbar" v-else></span>
        <span class="preset-thumb-dot"></span>
      </div>
      <span class="preset-name">{{ preset.name }}</span>
    </div>
  </div>
</div>

<el-divider />
```

- [ ] **Step 2: 修改 script，引入 PRESETS 与 handlePresetChange**

在 `<script setup>` 顶部 import 区加：

```ts
import { PRESETS } from '@/themes'
```

在 `handleConfigChange` 之后加：

```ts
// 选择套餐
const handlePresetChange = (presetId: string) => {
  localConfig.value.preset = presetId
  pageConfigStore.changePreset(presetId)
}
```

在 `handleReset` 内补充 preset 重置（resetConfig 已重置，但 localConfig 需同步 + 应用到 DOM）：

```ts
const handleReset = () => {
  pageConfigStore.resetConfig()
  localConfig.value = { ...pageConfigStore.config }
  pageConfigStore.applyTheme(localConfig.value.theme)
  pageConfigStore.applyPreset(localConfig.value.preset)  // ← 新增
  ElMessage.success('已恢复默认设置')
}
```

- [ ] **Step 3: 在 style 内增加套餐区块样式**

在 `<style scoped lang="scss">` 内 `.settings-panel` 块内最前面加：

```scss
.section-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px 0;
  line-height: 1.5;
}

.preset-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 8px;
}

.preset-card {
  cursor: pointer;
  padding: 8px;
  border: 2px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  transition: all 0.2s;

  &:hover {
    border-color: var(--app-color-primary);
  }

  &.active {
    border-color: var(--app-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .preset-thumb {
    height: 48px;
    border-radius: 4px;
    margin-bottom: 6px;
    position: relative;
    overflow: hidden;
    background: var(--el-fill-color-lighter);

    // 默认主色映射（每张缩略图反映该套餐主色）
    &[data-palette='blue']    { background: linear-gradient(135deg, #ecf5ff, #409EFF); }
    &[data-palette='purple']  { background: linear-gradient(135deg, #f9f0ff, #722ed1); }
    &[data-palette='green']   { background: linear-gradient(135deg, #f6ffed, #52c41a); }
    &[data-palette='crimson'] { background: linear-gradient(135deg, #fff1f0, #f5222d); }

    // 风格映射：圆角差异
    &[data-style='flat']      { border-radius: 4px; }
    &[data-style='glass']     { border-radius: 12px; }
    &[data-style='card']      { border-radius: 8px; }
    &[data-style='compact']   { border-radius: 2px; }

    .preset-thumb-sidebar,
    .preset-thumb-topbar {
      position: absolute;
      background: rgba(255, 255, 255, 0.6);
    }

    .preset-thumb-sidebar {
      left: 4px;
      top: 4px;
      bottom: 4px;
      width: 12px;
      border-radius: 2px;
    }

    .preset-thumb-topbar {
      left: 4px;
      right: 4px;
      top: 4px;
      height: 10px;
      border-radius: 2px;
    }

    .preset-thumb-dot {
      position: absolute;
      right: 6px;
      bottom: 6px;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.9);
    }
  }

  .preset-name {
    display: block;
    text-align: center;
    font-size: 13px;
    color: var(--el-text-color-primary);
  }
}
```

- [ ] **Step 4: 验证 dev 启动**

```bash
cd apps/forge-web && pnpm dev > /tmp/vite-dev.log 2>&1 &
sleep 4 && grep -E "(error|Error|ready)" /tmp/vite-dev.log | head -10
kill %1 2>/dev/null
```

- [ ] **Step 5: 视觉验证**

```bash
cd apps/forge-web && pnpm dev
```

1. 打开页面，点击右上角"页面设置"按钮
2. 抽屉打开后，顶部应看到"主题套餐"区块，4 张缩略图（蓝/紫/绿/红渐变）
3. 默认套餐（default）应高亮
4. 点击"极客紫"，应**立即**看到：
   - 顶部布局（LayoutTop 生效）
   - 主色变紫
   - 卡片圆角变大
5. 点击"商务器"，应回到侧栏布局、绿色主色、卡片化样式
6. 点击"酷暗黑"，应回到侧栏布局、红色主色、紧凑样式
7. 点击"默认"，回到初始状态
8. 关闭抽屉，刷新页面，套餐应保留

- [ ] **Step 6: 单元测试无回归**

```bash
cd apps/forge-web && pnpm test:run 2>&1 | tail -10
```

- [ ] **Step 7: lint 与 build 验证**

```bash
cd apps/forge-web && pnpm lint 2>&1 | tail -5
cd apps/forge-web && pnpm build 2>&1 | tail -10
```

预期：build 通过，产物大小涨幅 < 50KB（参考 Task 12 性能预算）

- [ ] **Step 8: 提交**

```bash
git add apps/forge-web/src/components/SettingsPanel.vue
git commit -m "feat(theme): SettingsPanel 新增主题套餐切换 UI

顶部加入 4 个套餐卡片（含 SVG 缩略图），
点击即时切换 palette/layout/style 三维度，
套餐选择持久化到 localStorage。"
```

---

## Task 12: 最终集成验证与回归测试

**Files:**
- 无新建/修改（验证任务）

**Interfaces:**
- Consumes: 全部前述 Task 的产出
- Produces: 完整的可用功能 + 通过的验收清单

- [ ] **Step 1: 完整单元测试**

```bash
cd apps/forge-web && pnpm test:run 2>&1 | tail -20
```

预期：themes.test.ts 8 个、pageConfig.test.ts 8 个、其他现有测试全部 PASS

- [ ] **Step 2: lint + 类型检查 + build**

```bash
cd apps/forge-web && pnpm lint 2>&1 | tail -5
cd apps/forge-web && pnpm build 2>&1 | tail -10
```

预期：build 通过，无 TS 错误

- [ ] **Step 3: 产物大小检查**

```bash
ls -lh apps/forge-web/dist/assets/index-*.css 2>/dev/null
```

记录 CSS 产物大小（gzip 后估算应 < 50KB 增量）。

- [ ] **Step 4: 视觉走查清单（8 种组合）**

```bash
cd apps/forge-web && pnpm dev
```

依次切换并验证：

| 套餐 | 明/暗 | 验证点 |
|---|---|---|
| default | light | 蓝主色、侧栏 #304156、扁平圆角 |
| default | dark | 暗背景、主色略深、对比度 ≥ 4.5:1 |
| geek | light | 紫主色、顶栏布局、玻璃拟态圆角 |
| geek | dark | 暗紫、玻璃背景模糊生效 |
| business | light | 绿主色、侧栏 #0d2818、卡片化 |
| business | dark | 暗绿、卡片阴影明显 |
| dark-pro | light | 红主色、侧栏 #1a0808、紧凑密度 |
| dark-pro | dark | 暗红、表格行高小 |

每个组合下检查：
- [ ] 登录页（独立路由 `/login`）
- [ ] Dashboard 首页
- [ ] 用户列表（vxe-table 视觉跟随）
- [ ] 任意表单页（输入框、按钮主色）
- [ ] 任意详情页（卡片样式）
- [ ] 打开一个对话框（主色按钮、圆角）
- [ ] 打开一个抽屉
- [ ] 标签页切换、面包屑
- [ ] WebSocket 通知（接收一条消息，弹通知）

- [ ] **Step 5: 移动端响应式验证**

浏览器 DevTools 切换到手机视图（iPhone 12 Pro 390x844）：

1. 当前套餐为 `geek`（顶栏）
2. 切到移动端视图后，应**强制使用 LayoutSidebar**（顶栏在小屏不可用）
3. 汉堡菜单可用
4. 标签页自动隐藏（如启用 autoHideTabsOnMobile）

切回桌面视图，应自动恢复 `geek` 的顶栏布局。

- [ ] **Step 6: localStorage 兼容性验证**

模拟老用户：

```js
// 在 dev 页面 console
localStorage.setItem('forge_admin-page-config', JSON.stringify({ theme: 'dark', showTabs: true }))
location.reload()
```

刷新后应：
- theme = dark（保留）
- preset = default（回落）
- 不报错

模拟损坏数据：

```js
localStorage.setItem('forge_admin-page-config', '{not json')
location.reload()
```

刷新后应：
- 回落到 defaultConfig
- console 有错误日志但不阻塞应用

- [ ] **Step 7: 性能验证**

打开 DevTools Performance 面板，录制以下操作：
1. 点击"页面设置"
2. 点击"极客紫"
3. 关闭设置面板

录制结果分析：
- 切换同步耗时（SettingsPanel click 事件 → DOM 属性变化）应 < 16ms
- 无长任务（> 50ms）
- 无强制重排（红色 Layout 条）

- [ ] **Step 8: 最终提交（如有微小调整）**

如果在 Step 4-7 发现小 bug，修复后提交：

```bash
git add -A
git commit -m "fix(theme): 集成验证修复"
```

如全部通过，无需提交，本 Task 仅验证。

- [ ] **Step 9: 标记完成**

执行 `git log --oneline -15` 确认 11 个 Task 的提交全部存在，无遗漏。

```bash
git log --oneline | head -15
```

预期看到 Task 1-11 的提交序列。

---

## 自查清单

完成所有 Task 后，对照 spec 检查：

**Spec 第 2 节（架构）：**
- [x] 三层抽象（palette/layout/style）→ Task 1 实现
- [x] 4 个预设套餐 → Task 1 注册
- [x] CSS 变量分两阶 → Task 2/3 实现
- [x] 文件结构 → Task 1-9 创建
- [x] 无刷新切换 → Task 5/8/11 实现

**Spec 第 3 节（组件）：**
- [x] 套餐注册表 → Task 1
- [x] pageConfig 扩展 → Task 5
- [x] 布局分发器 → Task 8
- [x] 移动端强制 sidebar → Task 8
- [x] SettingsPanel 扩展 → Task 11

**Spec 第 4 节（数据流）：**
- [x] 第一阶业务变量 → Task 2
- [x] 调色板桥接 → Task 3
- [x] 风格 mixin → Task 4
- [x] 切换数据流 → Task 5 + Task 11

**Spec 第 5 节（错误处理）：**
- [x] 配置加载失败 → Task 5 try/catch
- [x] 未知 preset → Task 1 getPreset fallback + Task 5 校验
- [x] backdrop-filter 不支持 → Task 4 @supports
- [x] localStorage 写失败 → 现有 saveConfig 已有 try/catch
- [x] 旧版本数据兼容 → Task 5 defaultConfig 合并

**Spec 第 6 节（测试）：**
- [x] 单元测试 → Task 1 + Task 5
- [x] 视觉走查 → Task 12 Step 4
- [x] 验收标准 → Task 12 Step 4-7
- [x] 性能预算 → Task 12 Step 7

**Spec 第 7 节（实现优先级）：**
- [x] P0 全部完成 → Task 1/2/3/5/10/11
- [x] P1 全部完成 → Task 4/7/8/9
- [ ] P2（截图回归、E2E、缩略图）→ 不在本次范围

---

## 执行风险提示

1. **Task 7（抽出 LayoutSidebar）**：完全复制 BasicLayout 内容，**不要试图同时优化**。任何优化都放到 Task 10 之后单独 PR。
2. **Task 9（LayoutTop）**：通知面板的样式与 LayoutSidebar 重复，**先重复一遍**。后期如要抽出 shared 组件，单独重构。
3. **Task 10（清硬编码）**：仅替换颜色为变量，**不调整任何间距/圆角**。
4. **Task 11（SettingsPanel）**：UI 改动较大，**先在 dev 模式充分测试**再提交。
5. **每个 Task 完成后必须运行 `pnpm test:run` 与 `pnpm dev` 启动验证**，确保无回归。
