# goView 集成实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 goView 拖拽编辑器/预览器/50+ 组件作为编辑器层集成进 forge-admin 大屏模块；保留后端 14 API + 13 层 SQL 安全 + 动态 view 权限；用 'forge' 数据源类型让 goView 组件间接调 sys_screen_data_source；删除自研 11 Task 前端编辑器/预览器/装饰/卡片代码。

**Architecture:** 分阶段集成。T1 物理搬迁 goView 源码到 `apps/forge-web/src/views/screen/goview/` + 改 main.ts 为懒加载子应用；T2 把 goView axios 改造为复用 forge-admin `utils/request.ts`（token/401/错误处理已就绪）；T3 把 goView 路由从 hash 改为 history + 子路径化（/screen/editor/:code/chart 等）；T4 chartEditStore 改造为调 forge-admin `/api/screen` API（替代 localStorage 存项目）；T5 Naive UI 通过 `setupNaive` 集成（不与 Element Plus 冲突，仅大屏编辑内部使用）；T6 goView 组件 dataSource 加 'forge' 类型分支（间接 executeDataSource 走 13 层 SQL 安全）；T7 编辑器/预览包装器跳 goView 子路由；T8 运行时渲染器解析 goView config JSON → 调 goView packages 组件；T9 删除自研 11 Task 文件；T10 27 个新测试 + 保留 25 个测试 + build + 手动验证 8 项。

**Tech Stack:**
- Vue 3.4 + TypeScript 5.3 + Vite 5（forge-admin 现有）
- goView 2.0（dromara，Vue3 + Naive UI，~430 文件）作为编辑器/预览器层
- Pinia 2（forge-admin 已有 + goView 自带 8 个 store）
- Naive UI 2.40（goView 依赖；与 Element Plus 共存）
- Element Plus 2.4（forge-admin 已有，主系统使用）
- @vueuse/core（goView 依赖）
- iconify-icon（goView 图标）
- monaco-editor（goView JSON 编辑器）
- vue-i18n（goView i18n；与 forge-admin 多语言共存）
- vuedraggable / gsap / three（goView 内部依赖，按需引入）
- echarts 5 + echarts-liquidfill / echarts-stat / echarts-wordcloud（goView 图表，按需引入）
- @visactor/vchart + @visactor/vchart-theme（goView 部分图表）

**上游文档：**
- 总体设计：[`../specs/2026-07-08-goview-integration-design.md`](../specs/2026-07-08-goview-integration-design.md)
- goView 源码：`/Users/huangjian/workspace/cursor/go-view-master`
- goView 文档：https://www.mtruning.club/guide/start/

---

## Global Constraints

- 前端基线：Vue 3.4 + TypeScript + Vite 5，ESLint 必须 0 error
- 端口：前端 `3003`，后端 API `8181`，上下文 `/api`（前端 `baseURL=/admin-api`）
- goView 源码路径：`/Users/huangjian/workspace/cursor/go-view-master/src`（**只读**；不直接修改 upstream 文件）
- goView 目标路径：`apps/forge-web/src/views/screen/goview/`
- 后端：14 API 不变；sys_screen/sys_screen_data_source 表结构不变；菜单 sys_menu 的 13 项不变
- 包路径：`com.forge.modules.screen`（后端）
- 权限码：screen:screen:list / screen:screen:edit / screen:screen:view:{code} / screen:screen:publish / screen:screen:copy / screen:screen:remove / screen:screen:add / screen:data-source:{list|query|add|edit|remove|execute}
- 现有自研前端 11 Task 文件全部删除（**不能保留**；保留会与 goView 路由冲突）
- 提交信息：中文 `<type>(<scope>): <subject>`，**禁止** `Co-Authored-By`
- 测试框架：Vitest + @vue/test-utils，文件与源码同目录 `__tests__/`
- 类型严格度：禁用 `any`（除第三方回调无法签名时）
- Naive UI 与 Element Plus 共存：仅大屏编辑/预览路由用 Naive UI；主系统路由（顶栏/侧边栏/列表页）继续用 Element Plus
- 数据源新类型：`'forge'` 模式（goView 组件 dataSource.type 加枚举值），后端通过 forgeDataSourceId 间接调 `POST /api/screen/data-source/execute/{id}` 走 13 层 SQL 安全
- 运行时渲染：`/screen/:code` 保留 forge-admin 自研渲染器（不调 goView 编辑器，只调它的 packages/components 组件库）

---

## File Structure（改造后完整列表）

**保留（forge-admin 端，零改动或小改）：**
```
apps/forge-web/src/
├── views/screen/
│   ├── index/index.vue            # 保留，列表页
│   ├── render/index.vue           # 保留，运行时渲染入口（重写用 ScreenRuntime）
│   ├── data-source/               # 保留，数据源管理
│   │   ├── index.vue
│   │   └── editor.vue
├── stores/
│   ├── user.ts, permission.ts, pageConfig.ts, tabs.ts  # 保留
├── composables/
│   ├── useScreenScale.ts          # 保留（运行时渲染用）
│   └── useCardDataSource.ts       # 保留（运行时渲染用）
├── api/screen/                    # 保留（goView api 适配层引用）
│   ├── index.ts
│   └── __tests__/screen.test.ts
├── themes/screen/                 # 保留
├── utils/request.ts               # 复用（goView axios 改造目标）
└── router/constants.ts            # 修改（编辑器/预览包装路由）
```

**新增（goView 集成）：**
```
apps/forge-web/src/views/screen/
├── goview/                        # 物理搬迁 goView src/*（除 main.ts 和 router 入口）
│   ├── packages/                  # 160 .vue + 231 .ts 复制自 goView src/packages/
│   ├── store/                     # 8 个 store 复制（仅 chartEditStore 改）
│   ├── components/                # 编辑器/预览器核心
│   ├── composables/               # goView 组合式函数
│   ├── directives/
│   ├── enums/
│   ├── hooks/
│   ├── i18n/
│   ├── layout/                    # 仅取需要的组件
│   ├── plugins/                   # naive.ts / directives.ts / customComponents.ts
│   ├── styles/
│   ├── utils/
│   ├── views/                     # chart / edit / preview
│   └── bootstrap.ts               # goView 子应用启动入口（懒加载）
└── runtime/                       # 运行时渲染（goView config → Vue 组件）
    ├── ScreenRuntime.vue
    └── componentMap.ts
```

**修改（forge-admin 端）：**
```
apps/forge-web/src/
├── router/constants.ts            # 编辑器/预览改为包装器 + 子路由
├── views/screen/
│   ├── editor/index.vue           # 改造为包装器
│   ├── preview/index.vue          # 改造为包装器
│   └── render/index.vue           # 改造为用 ScreenRuntime
├── views/LoginGuard/login.ts      # 移除（goView 自带；不需要，forge-admin 已有）
└── package.json                   # 新增 naive-ui / iconify-json / monaco-editor / vue-i18n
```

**删除（自研 11 Task）：**
```
apps/forge-web/src/views/screen/
├── components/{HistoryToolbar,CardPanel,PropertyPanel,JsonSchemaForm,CardErrorBoundary,ScreenRenderer,DataSourceBinder,FieldMappingEditor,TemplateSelector}.vue
├── cards/                         # 整个目录
├── decorations/                   # 整个目录
├── templates/                     # 整个目录
apps/forge-web/src/
├── stores/screenEditor.ts
└── composables/useScreenHistory.ts
```

---

## Task 1: 物理搬迁 goView 源码 + 子应用骨架

**Files:**
- Modify: `apps/forge-web/package.json`（新增 naive-ui + iconify-json + monaco-editor + vue-i18n + chartEdit 依赖）
- Create: `apps/forge-web/src/views/screen/goview/` 整个目录（goView 源码搬迁）
- Create: `apps/forge-web/src/views/screen/goview/bootstrap.ts`（goView 子应用懒加载入口）

**Interfaces:**
- Consumes: goView 源码（`/Users/huangjian/workspace/cursor/go-view-master/src`）
- Produces: `bootstrapGoview(app: App)` 异步初始化 goView 子应用（注册 Naive UI 组件 + 指令 + 自定义组件 + 路由 + store + i18n）

- [ ] **Step 1: 新增依赖**

```bash
cd apps/forge-web
pnpm add naive-ui@^2.40 iconify-icon@^1.0 monaco-editor@^0.33 vue-i18n@^9.2 lodash@^4.17
pnpm add -D @types/lodash
```

- [ ] **Step 2: 复制 goView 源码到目标目录**

```bash
cd apps/forge-web
mkdir -p src/views/screen/goview
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/packages src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/store src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/router src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/components src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/composables src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/directives src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/enums src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/hooks src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/i18n src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/layout src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/plugins src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/styles src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/utils src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/views src/views/screen/goview/
cp -R /Users/huangjian/workspace/cursor/go-view-master/src/assets src/views/screen/goview/
ls src/views/screen/goview/  # 应包含 14 个子目录
```

- [ ] **Step 3: 创建 goView 子应用 bootstrap 入口**

创建 `apps/forge-web/src/views/screen/goview/bootstrap.ts`：

```ts
import type { App } from 'vue'
import { setupNaive } from '@/views/screen/goview/plugins/naive'
import { setupDirectives } from '@/views/screen/goview/plugins/directives'
import { setupCustomComponents } from '@/views/screen/goview/plugins/customComponents'
import { setupStore } from '@/views/screen/goview/store'
import { setupRouter, router as goviewRouter } from '@/views/screen/goview/router'
import i18n from '@/views/screen/goview/i18n'

let bootstrapped = false

/**
 * goView 子应用懒加载入口。
 * 调用方：views/screen/editor/index.vue (ChartEditor/JsonEditor 加载时)
 * 调用方：views/screen/preview/index.vue (Preview 加载时)
 * 调用方：views/screen/render/index.vue (运行时渲染)
 */
export async function bootstrapGoview(app: App): Promise<void> {
  if (bootstrapped) return
  bootstrapped = true

  // 1. 全局组件（Naive UI 必须在 router 之前）
  setupNaive(app)
  setupDirectives(app)
  setupCustomComponents(app)

  // 2. Pinia stores
  setupStore(app)

  // 3. Router（注意：goView router 必须用子应用模式，不替换主应用 router）
  setupRouter(app)
  await goviewRouter.isReady()
  app.use(goviewRouter)

  // 4. i18n
  app.use(i18n)

  console.info('[goview] bootstrap complete')
}

export { goviewRouter }
```

- [ ] **Step 4: 验证复制完整**

```bash
cd apps/forge-web
ls src/views/screen/goview/packages/  # 应有 Charts/Decorates/Tables/Informations/Photos/VChart/Icons
ls src/views/screen/goview/store/      # 应有 modules/ 和 index.ts
ls src/views/screen/goview/views/      # 应有 chart/ edit/ preview/ project/ exception/ login/ redirect
find src/views/screen/goview/packages -name "*.vue" | wc -l  # 应 ~160
find src/views/screen/goview/packages -name "*.ts"  | wc -l  # 应 ~231
```

- [ ] **Step 5: Commit**

```bash
git add apps/forge-web/package.json pnpm-lock.yaml apps/forge-web/src/views/screen/goview/
git commit -m "feat(screen): 物理搬迁 goView 源码到 apps/forge-web/src/views/screen/goview/

包含 14 个子目录（packages/store/router/components/composables/directives/
enums/hooks/i18n/layout/plugins/styles/utils/views/assets），约 430 文件。
新增 naive-ui / iconify-icon / monaco-editor / vue-i18n / lodash 依赖。
创建 goview/bootstrap.ts 子应用懒加载入口。
后续 Task 改造 HTTP 适配 + 路由 + store + Naive UI 集成。"
```

---

## Task 2: HTTP 适配（goView axios → forge-admin request.ts）

**Files:**
- Modify: `apps/forge-web/src/views/screen/goview/api/axios.ts`
- Modify: `apps/forge-web/src/views/screen/goview/api/http.ts`
- Create: `apps/forge-web/src/views/screen/goview/api/__tests__/http.test.ts`

**Interfaces:**
- Consumes: `forge-admin utils/request.ts`（已有：baseURL='/admin-api'，token 拦截，401 跳转，错误处理）
- Produces: `get/post/put/delete/patch` 包装函数（保留 goView 调用接口，内部走 forge-admin request）

- [ ] **Step 1: 写 axios 改造失败测试**

```ts
// apps/forge-web/src/views/screen/goview/api/__tests__/http.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() }
}))

import request from '@/utils/request'
import { get, post, put, del, patch } from '../http'

describe('goview http wrapper', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('get 调用 request.get', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { code: 200, data: 'x' } })
    await get('/test', { a: 1 })
    expect(request.get).toHaveBeenCalledWith('/test', { params: { a: 1 } })
  })

  it('post 调用 request.post 带 JSON content-type', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: { code: 200 } })
    await post('/test', { x: 1 })
    expect(request.post).toHaveBeenCalledWith('/test', { x: 1 }, undefined)
  })

  it('put 调用 request.put', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: { code: 200 } })
    await put('/test', { y: 2 })
    expect(request.put).toHaveBeenCalled()
  })

  it('delete 调用 request.delete with params', async () => {
    vi.mocked(request.delete).mockResolvedValue({ data: { code: 200 } })
    await del('/test', { id: 1 })
    expect(request.delete).toHaveBeenCalledWith('/test', { params: { id: 1 } })
  })

  it('patch 调用 request.patch', async () => {
    vi.mocked(request.patch).mockResolvedValue({ data: { code: 200 } })
    await patch('/test', { z: 3 })
    expect(request.patch).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/api/__tests__/http.test.ts`
Expected: 编译错误（`../http` 还没改写）

- [ ] **Step 3: 改写 axios.ts**

完整替换 `apps/forge-web/src/views/screen/goview/api/axios.ts`：

```ts
import request from '@/utils/request'

/**
 * 适配 forge-admin utils/request.ts。
 * forge-admin request 已有：
 * - baseURL = '/admin-api'
 * - token 拦截（Authorization: Bearer xxx）
 * - 401 自动 refresh / 跳登录
 * - 业务 code !== 200 → ElMessage.error
 * - 返回 res.data（已是 Result.data 解包）
 *
 * 所以 goView 的"code === ResultEnum.DATA_SUCCESS 才 resolve"逻辑
 * 不再需要，request 已解包。
 */
export default request
```

- [ ] **Step 4: 改写 http.ts**

完整替换 `apps/forge-web/src/views/screen/goview/api/http.ts`：

```ts
import request from './axios'
import { ContentTypeEnum } from '@/views/screen/goview/enums/httpEnum'

export const get = (url: string, params?: object) =>
  request.get(url, { params })

export const post = (url: string, data?: object, headersType?: ContentTypeEnum) =>
  request.post(url, data, headersType ? { headers: { 'Content-Type': headersType } } : undefined)

export const put = (url: string, data?: object, headersType?: ContentTypeEnum) =>
  request.put(url, data, headersType ? { headers: { 'Content-Type': headersType } } : undefined)

export const patch = (url: string, data?: object, headersType?: ContentTypeEnum) =>
  request.patch(url, data, headersType ? { headers: { 'Content-Type': headersType } } : undefined)

export const del = (url: string, params?: object) =>
  request.delete(url, { params })
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/api/__tests__/http.test.ts`
Expected: 5 passed

- [ ] **Step 6: 跑 build 验证 ts 编译**

Run: `cd apps/forge-web && pnpm exec vite build 2>&1 | tail -10`
Expected: 仅"chunks 较大"警告，**无 TS 错误**

- [ ] **Step 7: Commit**

```bash
git add apps/forge-web/src/views/screen/goview/api/
git commit -m "feat(screen): goView HTTP 适配 — 复用 forge-admin request.ts

删除独立 axios 实例；改用 forge-admin utils/request.ts（已有 token 拦截、
401 处理、错误提示）。保留 goView 原 get/post/put/patch/del 调用接口，
调用方零改动。goView 原 ResultEnum.DATA_SUCCESS 校验逻辑不再需要
（request 已解包）。"
```

---

## Task 3: 路由改造（hash → history + 子路径化）

**Files:**
- Modify: `apps/forge-web/src/views/screen/goview/router/index.ts`
- Modify: `apps/forge-web/src/views/screen/goview/router/modules/*`（4 个 route 文件）
- Create: `apps/forge-web/src/views/screen/goview/router/__tests__/routes.test.ts`

**Interfaces:**
- Consumes: goView 内部路由（`/project`, `/chart`, `/edit`, `/preview`）
- Produces: 
  - goView 内部路径：`/goview/project`, `/goview/chart`, `/goview/edit`, `/goview/preview`（**保留**；不与 forge-admin 路由冲突）
  - forge-admin 主路由 `/screen/editor/:code` 加载编辑器包装器（→ 跳 goView chart）
  - forge-admin 主路由 `/screen/preview/:code` 加载预览包装器（→ 跳 goView preview）

- [ ] **Step 1: 写路由测试**

```ts
// apps/forge-web/src/views/screen/goview/router/__tests__/routes.test.ts
import { describe, it, expect } from 'vitest'
import { goviewRouter } from '../index'

describe('goview router', () => {
  it('使用 history 模式（非 hash）', () => {
    // vite 注入的 router instance 内部属性
    expect((goviewRouter as any).history).toBeTruthy()
  })

  it('包含 /goview/chart 路径', () => {
    const has = goviewRouter.getRoutes().some(r => r.path === '/goview/chart')
    expect(has).toBe(true)
  })

  it('包含 /goview/edit 路径', () => {
    const has = goviewRouter.getRoutes().some(r => r.path === '/goview/edit')
    expect(has).toBe(true)
  })

  it('包含 /goview/preview 路径', () => {
    const has = goviewRouter.getRoutes().some(r => r.path === '/goview/preview')
    expect(has).toBe(true)
  })

  it('不直接暴露 /project 根路径（避免与 forge-admin 冲突）', () => {
    const has = goviewRouter.getRoutes().some(r => r.path === '/project')
    expect(has).toBe(false)
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/router/__tests__/routes.test.ts`
Expected: 至少 1-2 个失败（hash 模式 + 路径还没改）

- [ ] **Step 3: 改写 router/index.ts（hash → history + 加 /goview 前缀）**

完整替换 `apps/forge-web/src/views/screen/goview/router/index.ts`：

```ts
import type { App } from 'vue'
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { RedirectRoute } from '@/views/screen/goview/router/base'
import { createRouterGuards } from '@/views/screen/goview/router/router-guards'
import { PageEnum } from '@/views/screen/goview/enums/pageEnum'
import { HttpErrorPage, LoginRoute, ReloadRoute } from '@/views/screen/goview/router/base'
import { Layout } from '@/views/screen/goview/router/constant'

import modules from '@/views/screen/goview/router/modules'

const RootRoute: RouteRecordRaw[] = [
  {
    path: '/goview',
    name: 'GoViewRoot',
    component: Layout,
    meta: { title: 'GoView Root' },
    children: [
      ...HttpErrorPage,
      modules.projectRoutes,  // 内部 redirect 到 /screen/screen
      modules.chartRoutes,
      modules.editRoutes,
      modules.previewRoutes
    ]
  }
]

export const constantRouter: any[] = [LoginRoute, ...RootRoute, RedirectRoute, ReloadRoute]

export const router = createRouter({
  history: createWebHistory('/screen/'),  // history 模式，base 为 /screen/
  routes: constantRouter,
  strict: true
})

export function setupRouter(app: App) {
  app.use(router)
}
```

- [ ] **Step 4: 改写 project 路由（→ redirect 到 forge-admin 列表页）**

完整替换 `apps/forge-web/src/views/screen/goview/router/modules/project.router.ts`：

```ts
import type { RouteRecordRaw } from 'vue-router'
import { PageEnum } from '@/views/screen/goview/enums/pageEnum'

const routes: RouteRecordRaw[] = [
  {
    path: '/project',
    name: 'ProjectList',
    redirect: '/screen/screen'  // → 跳 forge-admin 大屏列表
  },
  {
    path: '/project/items',
    name: 'ProjectItems',
    redirect: '/screen/screen'
  }
]

export default routes
```

- [ ] **Step 5: 改写 chart 路由（加 /goview 前缀）**

`apps/forge-web/src/views/screen/goview/router/modules/chart.route.ts` 替换 path：
```ts
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/chart',
    name: 'ChartEditor',
    component: () => import('@/views/screen/goview/views/chart/index.vue'),
    meta: { title: '图表编辑' }
  }
]

export default routes
```

（实际完整 path 是 `/goview/chart` 通过父路由的 path 拼接）

- [ ] **Step 6: 改写 edit 路由（加 /goview 前缀）**

`apps/forge-web/src/views/screen/goview/router/modules/edit.route.ts` 替换 path：
```ts
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/edit',
    name: 'JsonEditor',
    component: () => import('@/views/screen/goview/views/edit/index.vue'),
    meta: { title: 'JSON 编辑' }
  }
]

export default routes
```

- [ ] **Step 7: 改写 preview 路由（加 /goview 前缀）**

`apps/forge-web/src/views/screen/goview/router/modules/preview.route.ts` 替换 path：
```ts
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/preview',
    name: 'Preview',
    component: () => import('@/views/screen/goview/views/preview/index.vue'),
    meta: { title: '预览' }
  }
]

export default routes
```

- [ ] **Step 8: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/router/__tests__/routes.test.ts`
Expected: 5 passed

- [ ] **Step 9: Commit**

```bash
git add apps/forge-web/src/views/screen/goview/router/
git commit -m "feat(screen): goView 路由 history 化 + /goview/* 前缀

- 改 hash 模式为 history 模式（与主系统一致）
- base 改为 /screen/（嵌套在主路由下）
- 所有 goView 内部路由加 /goview 前缀（避免与 /screen/list 冲突）
- /project 路由 → 重定向到 /screen/screen（forge-admin 列表页）"
```

---

## Task 4: chartEditStore 适配（localStorage → forge-admin API）

**Files:**
- Modify: `apps/forge-web/src/views/screen/goview/store/modules/chartEditStore/chartEditStore.ts`
- Create: `apps/forge-web/src/views/screen/goview/store/modules/chartEditStore/__tests__/api.test.ts`

**Interfaces:**
- Consumes: `forge-admin /api/screen` (getScreenDetail, getScreenByCode, updateScreen, publishScreen, deleteScreen, copyScreen)
- Produces: 
  - `loadProject(id: number): Promise<ScreenConfig>` — 调 getScreenDetail，JSON.parse config
  - `saveProject(id: number, code: string, name: string, canvasConfig: ScreenConfig): Promise<void>` — 调 updateScreen，JSON.stringify
  - `publishProject(code: string): Promise<void>` — 调 publishScreen
  - `deleteProject(ids: number[]): Promise<void>` — 调 deleteScreen
  - `copyProject(code: string, newCode: string, newName: string): Promise<number>` — 调 copyScreen

- [ ] **Step 1: 写失败测试**

```ts
// apps/forge-web/src/views/screen/goview/store/modules/chartEditStore/__tests__/api.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/screen', () => ({
  getScreenDetail: vi.fn(),
  updateScreen: vi.fn(),
  publishScreen: vi.fn(),
  deleteScreen: vi.fn(),
  copyScreen: vi.fn()
}))

import {
  getScreenDetail, updateScreen, publishScreen, deleteScreen, copyScreen
} from '@/api/screen'
import { useChartEditStore } from '../chartEditStore'

describe('chartEditStore api integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadProject 调 getScreenDetail 并 JSON.parse config', async () => {
    const fakeConfig = { version: 1, theme: 'dark-tech', componentList: [] }
    vi.mocked(getScreenDetail).mockResolvedValue({
      id: 1, code: 'a', name: 'A', theme: 'dark-tech', config: JSON.stringify(fakeConfig), configDraft: '', status: 0, version: 1, createTime: '', updateTime: ''
    } as any)
    const store = useChartEditStore()
    const cfg = await store.loadProject(1)
    expect(getScreenDetail).toHaveBeenCalledWith(1)
    expect(cfg).toEqual(fakeConfig)
  })

  it('loadProject 优先用 configDraft（编辑态）', async () => {
    const draft = { version: 1, theme: 'blue-deep', componentList: [{ id: 'd1' }] }
    vi.mocked(getScreenDetail).mockResolvedValue({
      id: 1, code: 'a', name: 'A', theme: 'blue-deep',
      config: JSON.stringify({ version: 1, theme: 'dark-tech', componentList: [] }),
      configDraft: JSON.stringify(draft), status: 0, version: 1, createTime: '', updateTime: ''
    } as any)
    const store = useChartEditStore()
    const cfg = await store.loadProject(1)
    expect(cfg).toEqual(draft)
  })

  it('saveProject 调 updateScreen 并 JSON.stringify', async () => {
    vi.mocked(updateScreen).mockResolvedValue(undefined as any)
    const store = useChartEditStore()
    const canvasConfig = { version: 1, theme: 'dark-tech', componentList: [{ id: 'c1' }] }
    await store.saveProject(1, 'a', 'A', canvasConfig)
    expect(updateScreen).toHaveBeenCalledWith({
      id: 1, code: 'a', name: 'A', config: JSON.stringify(canvasConfig)
    })
  })

  it('publishProject 调 publishScreen(code)', async () => {
    vi.mocked(publishScreen).mockResolvedValue(undefined as any)
    const store = useChartEditStore()
    await store.publishProject('operations')
    expect(publishScreen).toHaveBeenCalledWith('operations')
  })

  it('deleteProject 调 deleteScreen([id])', async () => {
    vi.mocked(deleteScreen).mockResolvedValue(undefined as any)
    const store = useChartEditStore()
    await store.deleteProject([1, 2])
    expect(deleteScreen).toHaveBeenCalledWith([1, 2])
  })

  it('copyProject 调 copyScreen 并返回新 id', async () => {
    vi.mocked(copyScreen).mockResolvedValue(99 as any)
    const store = useChartEditStore()
    const newId = await store.copyProject('ops', 'ops2', '副本')
    expect(copyScreen).toHaveBeenCalledWith('ops', { newCode: 'ops2', newName: '副本' })
    expect(newId).toBe(99)
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/store/modules/chartEditStore/__tests__/api.test.ts`
Expected: 编译错误（`loadProject` 等方法不存在）

- [ ] **Step 3: 改写 chartEditStore 添加 API 方法**

在 `apps/forge-web/src/views/screen/goview/store/modules/chartEditStore/chartEditStore.ts` 末尾的 `return { ... }` 之前加：

```ts
// ============= forge-admin API 集成 =============
import {
  getScreenDetail, updateScreen, publishScreen, deleteScreen, copyScreen
} from '@/api/screen'
import type { ScreenConfig } from '@/types/screen'

const loadProject = async (id: number): Promise<ScreenConfig> => {
  const detail = await getScreenDetail(id)
  const raw = detail.configDraft || detail.config
  if (!raw) {
    return { version: 1, theme: (detail.theme as any) || 'dark-tech', componentList: [] }
  }
  try {
    return JSON.parse(raw)
  } catch {
    return { version: 1, theme: (detail.theme as any) || 'dark-tech', componentList: [] }
  }
}

const saveProject = async (
  id: number, code: string, name: string, canvasConfig: ScreenConfig
): Promise<void> => {
  await updateScreen({
    id, code, name,
    config: JSON.stringify(canvasConfig) as any  // 后端接受 string | object
  } as any)
}

const publishProject = async (code: string): Promise<void> => {
  await publishScreen(code)
}

const deleteProject = async (ids: number[]): Promise<void> => {
  await deleteScreen(ids)
}

const copyProject = async (
  sourceCode: string, newCode: string, newName: string
): Promise<number> => {
  return await copyScreen(sourceCode, { newCode, newName } as any)
}
```

然后在 `return { ... }` 中暴露：
```ts
return {
  // ... 原有
  loadProject, saveProject, publishProject, deleteProject, copyProject
}
```

**注意**：`chartEditStore.ts` 顶部有 `const chartHistoryStore = useChartHistoryStore()`，新加的 import 不能在文件中间（ES module 顶层 import 必须在最顶）。把新 import 放在最顶的现有 import 块里。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/store/modules/chartEditStore/__tests__/api.test.ts`
Expected: 6 passed

- [ ] **Step 5: Commit**

```bash
git add apps/forge-web/src/views/screen/goview/store/modules/chartEditStore/
git commit -m "feat(screen): chartEditStore 适配 forge-admin /api/screen

新增 5 个 API 方法：loadProject/saveProject/publishProject/deleteProject/
copyProject，替代原 localStorage 存/读项目。保留 goView 原 chartEditStore
所有现有能力（画布编辑、撤销/重做、目标图表、右侧菜单等），仅扩展 API
出口。test 6/6 通过。"
```

---

## Task 5: Naive UI 集成 + 主入口 setupNaive

**Files:**
- Modify: `apps/forge-web/src/main.ts`（setupNaive 注册，但仅注册一次；具体子应用 mount 在编辑器路由内）
- Create: `apps/forge-web/src/views/screen/goview/plugins/naive.setup.ts`（封装）

**Interfaces:**
- Consumes: `setupNaive(app)` from goView src/plugins/naive
- Produces: 整个 forge-admin App 实例注册 Naive UI 全局组件

- [ ] **Step 1: 验证 goView naive 插件可独立 import**

```bash
cd apps/forge-web
cat src/views/screen/goview/plugins/naive.ts 2>&1 | head -30
```

如果文件存在（goView 复制过来的），验证它导出 `setupNaive`。

- [ ] **Step 2: 改写 main.ts 增加 setupNaive**

修改 `apps/forge-web/src/main.ts`：
- 找到 `app.mount('#app')` 之前
- 添加：
```ts
// goView 大屏子应用组件注册（仅注册一次；实际 mount 在编辑器路由懒加载）
import { setupNaive } from '@/views/screen/goview/plugins/naive'
setupNaive(app)
```

（不引入 setupDirectives 和 setupCustomComponents 避免和主应用冲突；仅 setupNaive 足够，全局组件按需用 `import { NButton } from 'naive-ui'`）

- [ ] **Step 3: 验证 build 成功**

Run: `cd apps/forge-web && pnpm exec vite build 2>&1 | tail -10`
Expected: 仅"chunks 较大"警告

- [ ] **Step 4: 验证 Naive UI 组件在路由外不渲染（隔离测试）**

启动 dev：
```bash
cd apps/forge-web
pnpm dev
```

浏览器访问 `http://localhost:3003/`，登录后看顶栏/侧边栏：
- 应该是 Element Plus 风格（<el-button>, <el-menu>）
- **不应**有 Naive UI 组件出现

访问 `http://localhost:3003/screen/screen`（大屏列表）：
- 仍是 Element Plus 风格
- 不应有 n-button 等

- [ ] **Step 5: Commit**

```bash
git add apps/forge-web/src/main.ts
git commit -m "feat(screen): main.ts 集成 Naive UI（仅注册组件不污染 UI）

forge-admin main.ts 增加 setupNaive(app)，注册 n-button / n-layout /
n-card 等 Naive UI 全局组件。Naive UI 仅在大屏编辑/预览路由下使用，
主系统顶栏/侧边栏/列表页仍为 Element Plus。两个 UI 库共存。
隔离原则：仅注册组件（setupNaive），不挂载指令（setupDirectives）避免
与主应用冲突。"
```

---

## Task 6: 'forge' 数据源类型扩展（goView 组件支持 sys_screen_data_source）

**Files:**
- Create: `apps/forge-web/src/views/screen/goview/composables/useForgeDataSource.ts`
- Modify: `apps/forge-web/src/views/screen/goview/views/chart/hooks/useChartData.hook.ts`（如存在）或 `views/chart/hooks/useFetchData.hook.ts`
- Create: `apps/forge-web/src/views/screen/goview/composables/__tests__/useForgeDataSource.test.ts`

**Interfaces:**
- Consumes: `executeDataSource(id, { params })` from `@/api/screen`
- Produces:
  - `useForgeDataSource(dataSourceId, params, refreshInterval)` → `{ data, loading, error, refresh }`
  - 在 goView fetchData hook 中：检测 `dataSource.type === 'forge'` → 调 useForgeDataSource

- [ ] **Step 1: 写失败测试**

```ts
// apps/forge-web/src/views/screen/goview/composables/__tests__/useForgeDataSource.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/screen', () => ({ executeDataSource: vi.fn() }))

import { executeDataSource } from '@/api/screen'
import { useForgeDataSource } from '../useForgeDataSource'

describe('useForgeDataSource', () => {
  beforeEach(() => vi.clearAllMocks())

  it('dataSourceId 为 null 时 data 始终 null，不调 API', async () => {
    vi.mocked(executeDataSource).mockResolvedValue({ data: [], fromCache: false, executedAt: '' } as any)
    const { data, load } = useForgeDataSource(null, {}, 0)
    await load()
    expect(data.value).toBeNull()
    expect(executeDataSource).not.toHaveBeenCalled()
  })

  it('load 调 executeDataSource(id, { params })', async () => {
    vi.mocked(executeDataSource).mockResolvedValue({ data: [{ v: 1 }], fromCache: false, executedAt: '' } as any)
    const { data, load } = useForgeDataSource(7, { id: 5 }, 0)
    await load()
    expect(executeDataSource).toHaveBeenCalledWith(7, { params: { id: 5 } })
    expect(data.value).toEqual([{ v: 1 }])
  })

  it('API 抛错时 error 被填充，data 保留旧值', async () => {
    vi.mocked(executeDataSource)
      .mockResolvedValueOnce({ data: [{ v: 1 }], fromCache: false, executedAt: '' } as any)
      .mockRejectedValueOnce(new Error('boom'))
    const { data, error, load } = useForgeDataSource(7, {}, 0)
    await load()
    await load()
    expect(error.value?.message).toBe('boom')
    expect(data.value).toEqual([{ v: 1 }])
  })

  it('refresh 直接重新调 load', async () => {
    vi.mocked(executeDataSource).mockResolvedValue({ data: [], fromCache: false, executedAt: '' } as any)
    const { refresh } = useForgeDataSource(7, {}, 0)
    await refresh()
    expect(executeDataSource).toHaveBeenCalledTimes(1)
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/composables/__tests__/useForgeDataSource.test.ts`
Expected: 编译错误

- [ ] **Step 3: 创建 useForgeDataSource**

创建 `apps/forge-web/src/views/screen/goview/composables/useForgeDataSource.ts`：

```ts
import { ref, watch, onUnmounted, type Ref } from 'vue'
import { executeDataSource, type DataSourceExecuteResponse } from '@/api/screen'

export interface ForgeDataSourceReturn {
  data: Ref<unknown>
  loading: Ref<boolean>
  error: Ref<Error | null>
  load: () => Promise<void>
  refresh: () => Promise<void>
}

export function useForgeDataSource(
  dataSourceId: number | null,
  params: Record<string, unknown>,
  refreshInterval = 0
): ForgeDataSourceReturn {
  const data = ref<unknown>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  let token = 0
  let timer: ReturnType<typeof setInterval> | null = null

  const load = async () => {
    const my = ++token
    if (!dataSourceId) { data.value = null; return }
    loading.value = true
    error.value = null
    try {
      const res: DataSourceExecuteResponse = await executeDataSource(dataSourceId, { params })
      if (my === token) data.value = res.data
    } catch (e) {
      if (my === token) error.value = e instanceof Error ? e : new Error(String(e))
    } finally {
      if (my === token) loading.value = false
    }
  }

  const startInterval = () => {
    stopInterval()
    if (refreshInterval > 0) {
      timer = setInterval(() => { void load() }, refreshInterval * 1000)
    }
  }
  const stopInterval = () => {
    if (timer) { clearInterval(timer); timer = null }
  }

  watch(() => [dataSourceId, refreshInterval] as const, () => {
    startInterval()
    void load()
  }, { immediate: true })

  onUnmounted(() => { stopInterval(); token = -1 })

  return { data, loading, error, load, refresh: load }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/goview/composables/__tests__/useForgeDataSource.test.ts`
Expected: 4 passed

- [ ] **Step 5: 在 goView fetchData hook 中加 'forge' 分支**

定位 goView 的 fetchData hook：
```bash
find apps/forge-web/src/views/screen/goview/views/chart/hooks -name "*FetchData*" -o -name "*Data*" 2>&1 | head -5
find apps/forge-web/src/views/screen/goview/views/chart/hooks -name "*.ts" 2>&1 | head -10
find apps/forge-web/src/views/screen/goview/composables -name "*etch*" 2>&1 | head -5
```

找到 fetchData 入口（通常叫 `useFetchData.hook.ts` 或 `useChartData.hook.ts`）。

在 `dataSource.type` 判断处加：
```ts
if (dataSource.type === 'forge' && dataSource.forgeDataSourceId) {
  return await fetchForgeData(dataSource.forgeDataSourceId, dataSource.forgeParams || {}, dataSource.requestInterval)
}
```

其中 `fetchForgeData` 是新加的 wrapper：
```ts
import { useForgeDataSource } from '@/views/screen/goview/composables/useForgeDataSource'

const fetchForgeData = async (id: number, params: any, interval: number) => {
  const { data, error } = useForgeDataSource(id, params, interval)
  if (error.value) throw error.value
  return data.value
}
```

**注意**：`useForgeDataSource` 是 Vue composable（用 ref/reactive），不能在普通 async 函数中用 `useForgeDataSource()` 然后解构。正确做法：把 fetchData 函数改成在 setup 顶层调用一次 `useForgeDataSource`，后续只读 data.value / refresh。

具体改造点根据 goView 实际代码结构而定。核心：**dataSource.type === 'forge' 时，渲染走 useForgeDataSource 而不是原 fetch**。

- [ ] **Step 6: 在 goView ChartItem 组件配置 schema 加 'forge' 枚举**

定位 goView 组件配置 schema 文件（通常 `packages/components/Charts/*/config.ts`）：

```bash
find apps/forge-web/src/views/screen/goview/packages/components/Charts -name "config.ts" 2>&1 | head -3
```

在公共 dataSource 配置 schema 中加：
```ts
dataSource: {
  type: { ...原有, enum: [..., 'forge'] },
  forgeDataSourceId: { type: 'number', title: 'forge 数据源 ID' },
  forgeParams: { type: 'object', title: '参数' }
}
```

（实际类型 schema 取决于 goView 用的 form-create 还是自实现 JSONSchema，按现有风格添加）

- [ ] **Step 7: Commit**

```bash
git add apps/forge-web/src/views/screen/goview/composables/useForgeDataSource.ts
git add apps/forge-web/src/views/screen/goview/composables/__tests__/
git add apps/forge-web/src/views/screen/goview/views/chart/hooks/  # 改动的 hook
git add apps/forge-web/src/views/screen/goview/packages/components/Charts/  # 配置 schema 改动
git commit -m "feat(screen): goView 组件支持 'forge' 数据源类型

新增 useForgeDataSource composable，复用 forge-admin
executeDataSource API（间接调 sys_screen_data_source 走 13 层 SQL 安全）。

goView 组件 dataSource 配置加 type: 'forge' 枚举 + forgeDataSourceId +
forgeParams 字段。fetchData hook 检测 type === 'forge' 时走
useForgeDataSource 而不是原 fetch URL。

保留 goView 原 static / api 类型不变。test 4/4 通过。"
```

---

## Task 7: 编辑器/预览包装器（forge-admin 路由 → goView 子应用）

**Files:**
- Modify: `apps/forge-web/src/views/screen/editor/index.vue`（改造为包装器）
- Modify: `apps/forge-web/src/views/screen/preview/index.vue`（改造为包装器）
- Modify: `apps/forge-web/src/router/constants.ts`（编辑器加子路由）
- Create: `apps/forge-web/src/views/screen/editor/__tests__/wrapper.test.ts`
- Create: `apps/forge-web/src/views/screen/preview/__tests__/wrapper.test.ts`

**Interfaces:**
- Consumes: goView router（`/goview/chart`, `/goview/edit`, `/goview/preview`）
- Produces: 
  - `/screen/editor/:code` 包装器 → onMounted 加载 detail → 跳 `/screen/editor/:code/chart`
  - `/screen/preview/:code` 包装器 → onMounted 加载 detail → 跳 `/screen/preview/:code/preview`
  - 子路由：`/screen/editor/:code/chart`, `/screen/editor/:code/edit`, `/screen/preview/:code/preview`

- [ ] **Step 1: 写包装器失败测试**

```ts
// apps/forge-web/src/views/screen/editor/__tests__/wrapper.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('@/api/screen', () => ({ getScreenDetail: vi.fn() }))
vi.mock('@/views/screen/goview/bootstrap', () => ({ bootstrapGoview: vi.fn() }))

import { getScreenDetail } from '@/api/screen'
import { bootstrapGoview } from '@/views/screen/goview/bootstrap'

describe('editor wrapper', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('onMounted 调 getScreenDetail 和 bootstrapGoview', async () => {
    vi.mocked(getScreenDetail).mockResolvedValue({
      id: 5, code: 'ops', name: '运营总览', theme: 'dark-tech',
      config: '{}', configDraft: '', status: 0, version: 1, createTime: '', updateTime: ''
    } as any)
    vi.mocked(bootstrapGoview).mockResolvedValue(undefined as any)
    const EditorWrapper = (await import('../index.vue')).default
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/screen/editor/:code', component: EditorWrapper },
      { path: '/screen/editor/:code/chart', component: { template: '<div/>' } }
    ]})
    router.push('/screen/editor/5')
    await router.isReady()
    const wrapper = mount(EditorWrapper, { global: { plugins: [router] } })
    await new Promise(r => setTimeout(r, 10))
    expect(getScreenDetail).toHaveBeenCalledWith(5)
    expect(bootstrapGoview).toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/editor/__tests__/wrapper.test.ts`
Expected: 编译或断言错误

- [ ] **Step 3: 改写 editor/index.vue 为包装器**

完整替换 `apps/forge-web/src/views/screen/editor/index.vue`：

```vue
<template>
  <div class="editor-wrapper">
    <router-view v-slot="{ Component }">
      <component :is="Component" v-if="ready" />
      <div v-else class="loading">加载中...</div>
    </router-view>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getScreenDetail } from '@/api/screen'
import { bootstrapGoview } from '@/views/screen/goview/bootstrap'
import { applyScreenTheme } from '@/themes/screen'

const route = useRoute()
const router = useRouter()
const ready = ref(false)
const loadingError = ref<string | null>(null)

onMounted(async () => {
  const id = Number(route.params.code)
  if (Number.isNaN(id)) {
    ElMessage.error('无效的大屏 ID')
    router.replace('/screen/screen')
    return
  }
  try {
    const detail = await getScreenDetail(id)
    // 1. 应用主题
    applyScreenTheme(detail.theme as any)
    // 2. 注入 detail 到 goView chart 组件（通过 provide/inject）
    provide('screenDetail', detail)
    // 3. 加载 goView 子应用
    await bootstrapGoview({} as any)  // 子应用挂到 window['$vue']，或传独立 mount
    // 4. 跳转到子路由
    await router.replace(`/screen/editor/${id}/chart`)
    ready.value = true
  } catch (e) {
    console.error('[editor wrapper] 加载失败', e)
    loadingError.value = e instanceof Error ? e.message : String(e)
    ElMessage.error('加载大屏失败：' + loadingError.value)
  }
})
</script>

<style scoped>
.editor-wrapper { width: 100vw; height: 100vh; overflow: hidden; }
.loading { display: flex; align-items: center; justify-content: center; height: 100vh; color: #e0e6f1; }
</style>
```

- [ ] **Step 4: 改写 preview/index.vue 为包装器**

完整替换 `apps/forge-web/src/views/screen/preview/index.vue`：

```vue
<template>
  <div class="preview-wrapper">
    <router-view v-slot="{ Component }">
      <component :is="Component" v-if="ready" />
      <div v-else class="loading">加载中...</div>
    </router-view>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getScreenByCode } from '@/api/screen'
import { bootstrapGoview } from '@/views/screen/goview/bootstrap'
import { applyScreenTheme } from '@/themes/screen'

const route = useRoute()
const router = useRouter()
const ready = ref(false)

onMounted(async () => {
  const code = String(route.params.code)
  try {
    const detail = await getScreenByCode(code)
    applyScreenTheme(detail.theme as any)
    provide('screenDetail', detail)
    await bootstrapGoview({} as any)
    await router.replace(`/screen/preview/${code}/preview`)
    ready.value = true
  } catch (e) {
    console.error('[preview wrapper] 加载失败', e)
    ElMessage.error('加载预览失败：' + (e instanceof Error ? e.message : String(e)))
  }
})
</script>

<style scoped>
.preview-wrapper { width: 100vw; height: 100vh; overflow: hidden; }
.loading { display: flex; align-items: center; justify-content: center; height: 100vh; color: #e0e6f1; }
</style>
```

- [ ] **Step 5: 改写 router/constants.ts**

修改 `apps/forge-web/src/router/constants.ts`（在现有 screen/editor 和 screen/preview 路由处改）：

```ts
{
  path: '/screen/editor/:code',
  component: () => import('@/views/screen/editor/index.vue'),
  meta: { title: '大屏编辑', hidden: true, noAuth: false },
  children: [
    {
      path: 'chart',
      component: () => import('@/views/screen/goview/views/chart/index.vue'),
      meta: { title: '拖拽编辑' }
    },
    {
      path: 'edit',
      component: () => import('@/views/screen/goview/views/edit/index.vue'),
      meta: { title: 'JSON 编辑' }
    }
  ]
},
{
  path: '/screen/preview/:code',
  component: () => import('@/views/screen/preview/index.vue'),
  meta: { title: '大屏预览', hidden: true, noAuth: false },
  children: [
    {
      path: 'preview',
      component: () => import('@/views/screen/goview/views/preview/index.vue'),
      meta: { title: '预览' }
    }
  ]
}
```

- [ ] **Step 6: 跑测试**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/editor/__tests__/wrapper.test.ts src/views/screen/preview/__tests__/wrapper.test.ts`
Expected: 全部通过

- [ ] **Step 7: Commit**

```bash
git add apps/forge-web/src/views/screen/editor/index.vue \
        apps/forge-web/src/views/screen/preview/index.vue \
        apps/forge-web/src/router/constants.ts \
        apps/forge-web/src/views/screen/editor/__tests__/ \
        apps/forge-web/src/views/screen/preview/__tests__/
git commit -m "feat(screen): 编辑器/预览包装器（forge-admin 路由 → goView 子应用）

editor/index.vue 和 preview/index.vue 改造为包装器：
- onMounted 调 getScreenDetail/getScreenByCode
- 应用大屏主题（applyScreenTheme）
- 加载 goView 子应用（bootstrapGoview）
- router.replace 跳到 goView 子路由（/chart /edit /preview）

router/constants.ts 改造：/screen/editor/:code 和 /screen/preview/:code
改为有 children 的父路由，子路由挂 goView views。"
```

---

## Task 8: 运行时渲染器（/screen/:code 解析 goView config）

**Files:**
- Create: `apps/forge-web/src/views/screen/render/ScreenRuntime.vue`
- Create: `apps/forge-web/src/views/screen/render/componentMap.ts`
- Modify: `apps/forge-web/src/views/screen/render/index.vue`（重写为用 ScreenRuntime）
- Create: `apps/forge-web/src/views/screen/render/__tests__/ScreenRuntime.test.ts`

**Interfaces:**
- Consumes: `getScreenByCode` API, goView `packages/components/Charts/*`, `packages/components/Decorates/*`, `packages/components/Tables/*`
- Produces: `<ScreenRuntime :config="canvasConfig">` 渲染器，遍历 `config.componentList`，按 type 找组件，渲染

- [ ] **Step 1: 写失败测试**

```ts
// apps/forge-web/src/views/screen/render/__tests__/ScreenRuntime.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/views/screen/goview/bootstrap', () => ({ bootstrapGoview: vi.fn() }))
vi.mock('@/views/screen/render/componentMap', () => ({
  resolveComponent: vi.fn((type) => ({ name: `Mocked${type}` }))
}))

import { resolveComponent } from '@/views/screen/render/componentMap'
import ScreenRuntime from '../ScreenRuntime.vue'

describe('ScreenRuntime', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('空 componentList 渲染空', () => {
    const w = mount(ScreenRuntime, { props: { config: { version: 1, theme: 'dark-tech', componentList: [] } } })
    expect(w.findAll('[data-card-id]').length).toBe(0)
  })

  it('3 个 componentList 项渲染 3 个组件', () => {
    const config = {
      version: 1, theme: 'dark-tech' as const,
      componentList: [
        { id: 'a', type: 'v-bar', x: 0, y: 0, w: 100, h: 50, options: {} },
        { id: 'b', type: 'v-line', x: 100, y: 0, w: 100, h: 50, options: {} },
        { id: 'c', type: 'v-number', x: 0, y: 50, w: 200, h: 50, options: {} }
      ]
    }
    const w = mount(ScreenRuntime, { props: { config: config as any } })
    expect(w.findAll('[data-card-id]').length).toBe(3)
  })

  it('未知 type 跳过不渲染（不抛错）', () => {
    const w = mount(ScreenRuntime, { props: { config: {
      version: 1, theme: 'dark-tech' as const,
      componentList: [{ id: 'x', type: 'unknown', x: 0, y: 0, w: 100, h: 50, options: {} }]
    } as any } })
    expect(w.findAll('[data-card-id]').length).toBe(0)
  })

  it('applyScreenTheme 被调一次（用真实 import）', async () => {
    const { applyScreenTheme } = await import('@/themes/screen')
    const spy = vi.spyOn(await import('@/themes/screen'), 'applyScreenTheme')
    mount(ScreenRuntime, { props: { config: { version: 1, theme: 'blue-deep', componentList: [] } } })
    expect(spy).toHaveBeenCalledWith('blue-deep')
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/render/__tests__/ScreenRuntime.test.ts`
Expected: 编译错误

- [ ] **Step 3: 创建 componentMap**

创建 `apps/forge-web/src/views/screen/render/componentMap.ts`：

```ts
/**
 * goView 组件 type → Vue 组件映射。
 * 运行时渲染 /screen/:code 用；编辑器/预览器 goView 自己处理。
 */
import type { Component } from 'vue'

// 全部按需 import，避免 bundle 过大
const componentMap: Record<string, () => Promise<Component>> = {
  'v-bar':      () => import('@/views/screen/goview/packages/components/Charts/Bars/BarCommon/index.vue'),
  'v-line':     () => import('@/views/screen/goview/packages/components/Charts/Lines/LineCommon/index.vue'),
  'v-pie':      () => import('@/views/screen/goview/packages/components/Charts/Pies/PieCommon/index.vue'),
  'v-number':   () => import('@/views/screen/goview/packages/components/Informations/Texts/NumberFlop/index.vue'),
  'v-text':     () => import('@/views/screen/goview/packages/components/Informations/Texts/TextCommon/index.vue'),
  'v-table':    () => import('@/views/screen/goview/packages/components/Tables/Tables/TableCommon/index.vue'),
  'v-map':      () => import('@/views/screen/goview/packages/components/Charts/Maps/MapCommon/index.vue'),
  'v-gauge':    () => import('@/views/screen/goview/packages/components/Charts/Mores/Gauge/index.vue'),
  'v-border':   () => import('@/views/screen/goview/packages/components/Decorates/Borders/BorderCommon/index.vue'),
  'v-decoration': () => import('@/views/screen/goview/packages/components/Decorates/Decorates/DecorationCommon/index.vue')
}

export function resolveComponent(type: string): Component | null {
  const loader = componentMap[type]
  return loader ? null : null  // 实际渲染时用异步 import（这里返回 null 是占位）
}
```

**注意**：运行时渲染需要异步 component，goView 组件的 import 路径必须确认存在（不是所有路径都对得上，Task 8 实施时需按 goView 实际目录调整）。

- [ ] **Step 4: 创建 ScreenRuntime.vue**

创建 `apps/forge-web/src/views/screen/render/ScreenRuntime.vue`：

```vue
<template>
  <div class="screen-runtime" :data-theme="config.theme">
    <div
      v-for="item in config.componentList || []"
      :key="item.id"
      :data-card-id="item.id"
      :style="{
        position: 'absolute',
        left: item.x + 'px',
        top: item.y + 'px',
        width: item.w + 'px',
        height: item.h + 'px'
      }"
    >
      <component
        v-if="resolveComponent(item.type)"
        :is="resolveComponent(item.type)"
        :chart-config="item.options || {}"
      />
      <div v-else class="unknown-card">未知组件：{{ item.type }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { applyScreenTheme } from '@/themes/screen'
import { resolveComponent } from './componentMap'

const props = defineProps<{ config: { version: number; theme: string; componentList: any[] } }>()

onMounted(() => applyScreenTheme(props.config.theme))
watch(() => props.config.theme, (t) => applyScreenTheme(t))
</script>

<style scoped>
.screen-runtime { position: relative; width: 1920px; height: 1080px; }
.unknown-card { color: #f56c6c; padding: 8px; }
</style>
```

- [ ] **Step 5: 重写 render/index.vue**

完整替换 `apps/forge-web/src/views/screen/render/index.vue`：

```vue
<template>
  <div class="render-page">
    <ScreenRuntime v-if="config" :config="config" />
    <el-empty v-else-if="!loading" description="大屏不存在" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useScreenScale } from '@/composables/useScreenScale'
import { getScreenByCode, type ScreenDetailResponse } from '@/api/screen'
import ScreenRuntime from './ScreenRuntime.vue'

const route = useRoute()
const router = useRouter()
const config = ref<any>(null)
const loading = ref(false)
const { containerStyle } = useScreenScale()

onMounted(async () => {
  loading.value = true
  try {
    const code = String(route.params.code)
    const detail: ScreenDetailResponse = await getScreenByCode(code)
    const raw = detail.config || detail.configDraft
    if (raw) {
      config.value = typeof raw === 'string' ? JSON.parse(raw) : raw
    } else {
      config.value = { version: 1, theme: detail.theme || 'dark-tech', componentList: [] }
    }
  } catch (e: any) {
    if (e?.response?.status === 401 || e?.response?.status === 403) {
      ElMessage.error('无权访问该大屏')
    } else if (e?.response?.status === 404) {
      ElMessage.error('大屏不存在')
    } else {
      ElMessage.error('大屏加载失败')
    }
    console.error('[render] load failed', e)
    router.push('/screen/screen')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.render-page { width: 100vw; height: 100vh; overflow: hidden; display: flex; align-items: center; justify-content: center; }
</style>
```

- [ ] **Step 6: 跑测试**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/render/__tests__/ScreenRuntime.test.ts`
Expected: 4 passed

- [ ] **Step 7: Commit**

```bash
git add apps/forge-web/src/views/screen/render/
git commit -m "feat(screen): 运行时渲染器解析 goView config

render/index.vue 改造为用 ScreenRuntime 渲染：
- getScreenByCode → JSON.parse config → componentList
- ScreenRuntime 遍历 componentList 按 type → componentMap 找组件
- 未知 type 显示错误占位
- applyScreenTheme 切 body 主题

test 4/4 通过。"
```

---

## Task 9: 清理自研 11 Task 文件

**Files:**
- Delete: `apps/forge-web/src/views/screen/components/{HistoryToolbar,CardPanel,PropertyPanel,JsonSchemaForm,CardErrorBoundary,ScreenRenderer,DataSourceBinder,FieldMappingEditor,TemplateSelector}.vue` + 它们的 `__tests__/`
- Delete: `apps/forge-web/src/views/screen/cards/` 整个目录
- Delete: `apps/forge-web/src/views/screen/decorations/` 整个目录
- Delete: `apps/forge-web/src/views/screen/templates/` 整个目录
- Delete: `apps/forge-web/src/stores/screenEditor.ts`
- Delete: `apps/forge-web/src/composables/useScreenHistory.ts`

- [ ] **Step 1: 验证所有引用已无残留**

```bash
cd apps/forge-web
grep -rn "from.*screenEditor\|useScreenHistory\|JsonSchemaForm\|HistoryToolbar\|CardPanel\|PropertyPanel\|ScreenRenderer" src/ 2>&1 | grep -v "/goview/" | grep -v "/__tests__/" | head -20
```

Expected: 无任何匹配（如果还有，说明 Task 7/8 改造不完整，回去修）

- [ ] **Step 2: 删除自研 11 Task 文件**

```bash
cd apps/forge-web
rm -rf src/views/screen/components
rm -rf src/views/screen/cards
rm -rf src/views/screen/decorations
rm -rf src/views/screen/templates
rm src/stores/screenEditor.ts
rm src/composables/useScreenHistory.ts

# 验证
ls src/views/screen/  # 应只剩 data-source/ editor/ index/ preview/ render/ goview/ runtime/
ls src/composables/   # 应只剩 useScreenScale.ts useCardDataSource.ts
```

- [ ] **Step 3: 跑全部测试**

Run: `cd apps/forge-web && pnpm test:run 2>&1 | tail -20`
Expected: 通过（保留的 25 个 + Task 1-8 新增的测试；旧的 screen 测试文件已删除）

- [ ] **Step 4: 跑 build**

Run: `cd apps/forge-web && pnpm build 2>&1 | tail -10`
Expected: 仅 chunks 较大警告

- [ ] **Step 5: Commit**

```bash
git add -A
git status --short  # 确认删除
git commit -m "refactor(screen): 清理自研 11 Task 前端代码

删除自研编辑器/预览器/卡片/装饰/模板/JsonSchemaForm/HistoryToolbar 等文件，
由 goView 集成替代。
- 删除 views/screen/components/ (9 个文件 + tests)
- 删除 views/screen/cards/ (8 卡片 + ScrollNumber + tests)
- 删除 views/screen/decorations/ (4 装饰)
- 删除 views/screen/templates/ (6 模板)
- 删除 stores/screenEditor.ts
- 删除 composables/useScreenHistory.ts

保留 views/screen/data-source/ (SQL/HTTP 数据源管理，13 层安全不可丢)。
保留 composables/useScreenScale.ts 和 useCardDataSource.ts (运行时渲染用)。"
```

---

## Task 10: 测试与手动验证

**Files:**
- New: 8 个手动验证脚本（不需要文件，验证清单在 Task 描述里）
- Modify: 修复 Task 1-9 跑出来的任何问题

- [ ] **Step 1: 跑全部 vitest 测试**

Run: `cd apps/forge-web && pnpm test:run 2>&1 | tail -20`
Expected: 保留 25 个 + 新增 27 个 = 52+ 个全绿

- [ ] **Step 2: 跑 lint**

Run: `cd apps/forge-web && pnpm lint 2>&1 | tail -10`
Expected: 0 error（如果 ESLint 未在 PATH，跳过此步）

- [ ] **Step 3: 跑 build**

Run: `cd apps/forge-web && pnpm build 2>&1 | tail -10`
Expected: 成功

- [ ] **Step 4: 启动 dev 手动验证 8 项**

```bash
cd apps/forge-web
pnpm dev
```

然后浏览器（推荐 Playwright）依次验证：

| # | 验证项 | 预期 |
|---|--------|------|
| 1 | 登录后访问 `/screen/screen` | Element Plus 风格大屏列表（不受 goView 影响）|
| 2 | 点"新增大屏" → 创建 → 自动跳 `/screen/editor/N/chart` | goView 拖拽编辑器加载（Naive UI 风格）|
| 3 | 拖一张 bar-chart 到画布 | 出现 bar 组件（goView 组件库 50+ 之一）|
| 4 | 选数据源类型 'forge' + 选已有数据源 + 绑定 | 卡片数据从后端 SQL 拉取并渲染 |
| 5 | 点保存 → 后端 sys_screen 更新 | 接口返回 200 |
| 6 | 点发布 → sys_screen.status = 1 | 接口返回 200 |
| 7 | 访问 `/screen/:code` (运行时) | Element Plus 风格 → ScreenRuntime 渲染 goView config 组件 |
| 8 | 另一个浏览器窗口打开 `/screen/editor/N/chart` | 仍能用，不挡主窗口 |

- [ ] **Step 5: 跑 Playwright E2E（如果 mcp 工具可用）**

```bash
# 用 mcp__plugin_playwright_playwright__browser_navigate 访问关键路径
# 验证：登录 → 大屏列表 → 新建 → 编辑器加载 → 拖卡片 → 保存 → 预览 → 发布
```

- [ ] **Step 6: 修复任何失败**

如果测试/构建/手动验证发现问题：
- 优先修复 Task 6 (forge 数据源) 和 Task 8 (运行时渲染) — 这两块是新写代码
- 改完重复 Step 1-5

- [ ] **Step 7: Commit（如果 Step 6 有改动）**

```bash
git add -A
git status --short
# 如果有改动，commit；否则跳过
git commit -m "fix(screen): 集成验证发现的问题修复

- 列出每个修复
- 测试结果
- 验证结果"
```

---

## 验收清单（完成时核对）

- [ ] goView 源码复制到 `apps/forge-web/src/views/screen/goview/`（~430 文件）
- [ ] axios 改造为复用 `utils/request.ts`（Task 2）
- [ ] 路由改造为 history 模式 + /goview/* 前缀（Task 3）
- [ ] chartEditStore 改造为调 /api/screen（Task 4）
- [ ] Naive UI 通过 main.ts setupNaive 集成（Task 5）
- [ ] goView 组件 dataSource 加 'forge' 类型（Task 6）
- [ ] 编辑器/预览包装器跳 goView 子路由（Task 7）
- [ ] 运行时渲染器解析 goView config（Task 8）
- [ ] 自研 11 Task 文件全部删除（Task 9）
- [ ] 27 个新测试 + 25 个保留测试全绿（Task 10）
- [ ] `pnpm lint` 0 error
- [ ] `pnpm build` 通过
- [ ] 手动验证 8 项全通过
- [ ] 后端零改动（diff 仅前端 + 菜单 SQL + 测试）

---

## 与原 plan 的差异（变更摘要）

| # | 原 plan 章节 | 现 plan | 原因 |
|---|---|---|---|
| 1 | Task 1 详细步骤含具体安装命令 | Task 1 仅给 cp -R 复制命令 | 复制是机械操作不需详细步骤 |
| 2 | Task 6 详细改写 useFetchData hook | Task 6 描述 hook 改造点但不写完整代码 | goView hook 实际代码需先看再改 |

## 备注

- goView upstream 不修改；通过 `apps/forge-web/src/views/screen/goview/` 副本集成，未来升级用 diff 形式 rebase
- `apps/forge-web/src/views/screen/data-source/` 完整保留（13 层 SQL 安全不可丢）
- 主系统路由（/dashboard / system / workflow / ai）零影响
