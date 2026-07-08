# goView 独立工程集成设计 spec

**版本：** v2.0
**日期：** 2026-07-08
**作者：** huangjian
**状态：** 待评审
**关联文档：**
- 大屏后端运行手册：`apps/forge-server/docs/SCREEN-MODULE.md`
- goView 源码：`/Users/huangjian/workspace/cursor/go-view-master`

---

## 1. 背景与目标

### 1.1 背景

goView 是一个独立 Vue3 + Naive UI 前端工程（端口 8001），拥有完整的拖拽编辑器/JSON 编辑器/预览器/50+ 组件/模板市场。之前尝试嵌入 forge-admin 作为子目录（goview/）遇到 SCSS/sass/路径/变量等大量构建冲突。

**正确方案**：goView 作为独立前端工程 `apps/forge-screen`，与 forge-admin 通过 iframe + URL query + HTTP API 通信，互不污染构建体系。

### 1.2 目标

1. 复制 goView 到 `apps/forge-screen`，独立启动（端口 8001）
2. goView axios 改 baseURL 为 forge-admin 后端（开发 localhost:8181/admin-api，生产 /admin-api）
3. goView chartEditStore 项目保存/读取改用 forge-admin API（替代 localStorage）
4. goView 组件数据源加 `'forge'` 类型（调 forge-admin data-source/execute）
5. forge-admin 前端通过 iframe 嵌入 goView 编辑器/预览器（URL query 传 id + token）
6. forge-admin 自研编辑器/预览器/卡片/装饰/模板全部删除
7. 大屏列表/数据源管理/运行时渲染保留在 forge-admin

### 1.3 非目标

- 不合并 goView 构建到 forge-admin（独立工程互不污染）
- 不迁移旧大屏数据
- 不引入 SSO/跨域复杂认证（开发阶段 URL query 传 token；生产阶段同域反向代理）

---

## 2. 架构

```
┌──────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                  │
│  ┌─ forge-admin (localhost:3003) ────────────────────────────┐   │
│  │  /screen/screen        大屏列表 (Element Plus)              │   │
│  │  /screen/data-source   数据源管理                           │   │
│  │  /screen/:code         运行时渲染                           │   │
│  │                                                            │   │
│  │  ┌─ iframe ───────────────────────────────────────────┐   │   │
│  │  │  goView (localhost:8001)                            │   │   │
│  │  │  /#/chart?id=5&token=xxx    拖拽编辑器              │   │   │
│  │  │  /#/edit?id=5&token=xxx     JSON 编辑器             │   │   │
│  │  │  /#/preview?id=5&token=xxx  预览器                  │   │   │
│  │  │  调 forge-admin API (localhost:8181/admin-api/...)  │   │   │
│  │  └────────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ forge-admin 后端 (localhost:8181) ───────────────────────┐   │
│  │  /admin-api/screen/*       14 API                          │   │
│  │  /admin-api/screen/data-source/*  6 API                    │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

**关键约束：**
1. goView 独立 `pnpm dev` 启动，不依赖 forge-admin 构建
2. goView 构建输出放在 `apps/forge-screen/dist`，生产由 nginx 代理
3. iframe 通信：URL query 传 `id` + `token`（生产阶段改为 postMessage 或同域 cookie）
4. goView 所有 localStorage 存/读改为调 forge-admin API
5. goView 项目列表页改为跳转到 forge-admin `/screen/screen`

---

## 3. goView 改造点

### 3.1 目录结构

```
apps/forge-screen/                  # 从 go-view-master 复制
├── src/
│   ├── main.ts                     # 不改（独立启动）
│   ├── api/
│   │   ├── axios.ts                # 改 baseURL → forge-admin 后端
│   │   ├── http.ts                 # 不改
│   │   └── forge/                  # 新增：forge-admin API 调用
│   │       ├── screen.ts           # getScreenDetail / updateScreen / ...
│   │       └── dataSource.ts       # executeDataSource
│   ├── store/modules/
│   │   └── chartEditStore/
│   │       └── chartEditStore.ts   # 改 localStorage → forge API
│   ├── router/modules/
│   │   └── project.router.ts       # 改 redirect → /screen/screen
│   ├── views/chart/ContentConfigurations/
│   │   └── components/ChartData/
│   │       └── ChartDataAjax/      # 加 'forge' 类型
│   └── hooks/
│       └── useChartDataFetch.hook.ts # 加 'forge' 分支
├── .env                             # 端口 8001，API baseURL
├── .env.development
├── .env.production
├── package.json
└── vite.config.ts                   # 加 proxy /admin-api → localhost:8181
```

### 3.2 改造点清单

| # | 文件 | 改造内容 | 工作量 |
|---|------|---------|--------|
| 1 | `.env` | `VITE_DEV_PORT=8001`, `VITE_API_BASE=http://localhost:8181/admin-api` | 0.1d |
| 2 | `vite.config.ts` | 加 proxy `/admin-api` → `http://localhost:8181` | 0.1d |
| 3 | `api/axios.ts` | baseURL 改为 `import.meta.env.VITE_API_BASE`；token 从 URL query 读 | 0.3d |
| 4 | `api/forge/screen.ts` | 新增：封装 getScreenDetail/updateScreen/publishScreen/deleteScreen/copyScreen | 0.3d |
| 5 | `api/forge/dataSource.ts` | 新增：封装 executeDataSource | 0.1d |
| 6 | `store/chartEditStore.ts` | setProjectData → updateScreen API；getProjectData → getScreenDetail API | 0.5d |
| 7 | `router/project.router.ts` | /project → redirect 到 forge-admin 列表 | 0.1d |
| 8 | `hooks/useChartDataFetch.hook.ts` | 加 `type === 'forge'` 分支，调 executeDataSource | 0.3d |
| 9 | ChartDataAjax 配置 | 数据源类型加 `'forge'` 选项 + forgeDataSourceId 字段 | 0.2d |
| 10 | `main.ts` 或 App.vue | 从 URL query 读 `id` → 自动调 loadProject | 0.3d |

### 3.3 axios.ts 改造（关键）

```ts
// 原
const axiosInstance = axios.create({
  baseURL: import.meta.env.DEV ? import.meta.env.VITE_DEV_PATH : import.meta.env.VITE_PRO_PATH,
  timeout: ResultEnum.TIMEOUT,
})

// 改为
const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,   // http://localhost:8181/admin-api
  timeout: ResultEnum.TIMEOUT,
})

// 请求拦截器加 token（从 URL query 读）
axiosInstance.interceptors.request.use((config) => {
  const params = new URLSearchParams(window.location.hash.split('?')[1] || '')
  const token = params.get('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

### 3.4 响应拦截器适配

goView 原响应拦截器检查 `code === ResultEnum.DATA_SUCCESS`。forge-admin API 返回格式：
```json
{ "code": 200, "message": "success", "data": { ... } }
```

改为检查 `code === 200`（与 forge-admin Result.success() 一致）。

### 3.5 chartEditStore 改造（关键）

goView 原 `setProjectData` 写 localStorage，改为调 `updateScreen` API：

```ts
// 新增方法
import { getScreenDetail, updateScreen, publishScreen } from '@/api/forge/screen'

// 从 URL query 获取当前大屏 id
function getScreenIdFromUrl(): number | null {
  const params = new URLSearchParams(window.location.hash.split('?')[1] || '')
  const id = params.get('id')
  return id ? Number(id) : null
}

// 保存项目
async function saveProject() {
  const id = getScreenIdFromUrl()
  if (!id) return
  const config = JSON.stringify({
    editCanvasConfig: toRaw(editCanvasConfig),
    componentList: toRaw(componentList),
    ... 
  })
  await updateScreen({ id, config })
}

// 加载项目
async function loadProject() {
  const id = getScreenIdFromUrl()
  if (!id) return
  const detail = await getScreenDetail(id)
  const cfg = JSON.parse(detail.config || detail.configDraft || '{}')
  // 恢复到 store
}
```

---

## 4. forge-admin 前端改造

### 4.1 改造清单

| # | 文件 | 改造内容 |
|---|------|---------|
| 1 | `views/screen/index/index.vue` | handleCreate 新窗口打开 goView；handleEdit 跳 iframe 页面 |
| 2 | `views/screen/editor/index.vue` | 改为 iframe 容器：`<iframe :src="goViewUrl" />` |
| 3 | `views/screen/preview/index.vue` | 同上 |
| 4 | `router/constants.ts` | 编辑器/预览路由保持不变（包装器加载 iframe） |
| 5 | 删除自研 11 Task 文件 | 同之前 spec 删除清单 |

### 4.2 iframe 编辑器容器

```vue
<!-- views/screen/editor/index.vue -->
<template>
  <iframe
    v-if="goViewUrl"
    :src="goViewUrl"
    style="width: 100vw; height: 100vh; border: none"
    allow="fullscreen"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const user = useUserStore()

const goViewUrl = computed(() => {
  const id = route.params.code
  const token = user.token  // JWT access token
  const base = import.meta.env.DEV
    ? 'http://localhost:8001'
    : '/screen-app'          // 生产 nginx 代理路径
  return `${base}/#/chart?id=${id}&token=${token}`
})
</script>
```

### 4.3 iframe 预览容器

```vue
<!-- views/screen/preview/index.vue -->
<!-- 同 editor，但 hash 改为 /#/preview?id=... -->
```

---

## 5. 运行时渲染（保留）

`/screen/:code` 渲染页**保留 forge-admin 自研**：
- 调 `getScreenByCode(code)` → JSON.parse config → 解析 goView JSON 格式
- 用自研 ScreenRuntime 组件渲染（goView 组件 type → Vue 组件映射）
- 保留 `useScreenScale` 等比缩放
- 保留 `useCardDataSource` 数据加载

**原因**：运行时渲染不需要 goView 编辑器（Naive UI + 拖拽逻辑），只需要渲染 goView 配置格式的 JSON。自研运行时渲染器足够。

---

## 6. 开发/生产环境

### 6.1 开发环境

```bash
# 终端 1：forge-admin 后端
cd apps/forge-server && mvn spring-boot:run -pl forge-server

# 终端 2：forge-admin 前端
cd apps/forge-web && pnpm dev  # localhost:3003

# 终端 3：goView 大屏前端
cd apps/forge-screen && pnpm dev  # localhost:8001
```

### 6.2 生产环境

Nginx 配置：
```nginx
# forge-admin 前端
location / {
    root /var/www/forge-admin;
    try_files $uri /index.html;
}

# goView 大屏前端
location /screen-app/ {
    alias /var/www/forge-screen/;
    try_files $uri /screen-app/index.html;
}

# forge-admin 后端 API
location /admin-api/ {
    proxy_pass http://127.0.0.1:8181/api/;
}
```

goView 生产环境 `.env.production`：
```
VITE_API_BASE = '/admin-api'
```

---

## 7. 删除清单（forge-admin 自研）

```
apps/forge-web/src/views/screen/
├── components/{HistoryToolbar,CardPanel,PropertyPanel,JsonSchemaForm,
│              CardErrorBoundary,ScreenRenderer,DataSourceBinder,
│              FieldMappingEditor,TemplateSelector}.vue + __tests__/
├── cards/               # 整个目录（8 卡片 + ScrollNumber + tests）
├── decorations/         # 整个目录（4 装饰）
└── templates/           # 整个目录（6 模板）

apps/forge-web/src/
├── stores/screenEditor.ts
└── composables/useScreenHistory.ts
```

**保留：**
- `views/screen/index/index.vue`（列表页，修改 iframe 跳转）
- `views/screen/editor/index.vue`（改为 iframe 容器）
- `views/screen/preview/index.vue`（改为 iframe 容器）
- `views/screen/render/index.vue`（运行时渲染，保留）
- `views/screen/data-source/`（数据源管理，保留）
- `composables/useScreenScale.ts`（保留）
- `composables/useCardDataSource.ts`（保留）
- `api/screen/index.ts`（保留）

---

## 8. 测试策略

### forge-admin 端

- 保留测试：`api/screen/__tests__/screen.test.ts`（14 用例）
- 保留测试：`composables/__tests__/useScreenScale.test.ts`（4 用例）
- 保留测试：`composables/__tests__/useCardDataSource.test.ts`（4 用例）
- 新增测试：`views/screen/editor/__tests__/iframe.test.ts`（3 用例）
- 新增测试：`views/screen/preview/__tests__/iframe.test.ts`（2 用例）

### goView 端

- 新增测试：`api/forge/__tests__/screen.test.ts`（6 用例）
- 新增测试：`api/forge/__tests__/dataSource.test.ts`（3 用例）

---

## 9. 风险

| 风险 | 缓解 |
|------|------|
| iframe 跨域 cookie/header | 开发阶段 URL query 传 token；生产同域不需要 |
| goView hash 路由与 query 参数冲突 | `/#/chart?id=5&token=xxx` → 从 `window.location.hash.split('?')[1]` 解析 |
| goView 生产构建路径 | `.env.production` 设 `VITE_API_BASE=/admin-api` |
| 运行时渲染需支持 goView config 格式 | goView config 是 JSON，自研 ScreenRuntime 按 componentList 遍历渲染 |

---

## 10. 验收清单

- [ ] `apps/forge-screen` 目录存在，`pnpm dev` 在 8001 端口启动
- [ ] goView axios 改调 forge-admin 后端 API（`localhost:8181/admin-api`）
- [ ] goView chartEditStore 用 API 替代 localStorage 存/读项目
- [ ] goView 组件支持 `'forge'` 数据源类型
- [ ] forge-admin 编辑器页用 iframe 加载 goView
- [ ] forge-admin 预览页用 iframe 加载 goView
- [ ] forge-admin 大屏列表页"新增大屏"/"编辑"正确跳转
- [ ] forge-admin 自研 11 Task 文件全部删除
- [ ] 运行时渲染 `/screen/:code` 正常工作
- [ ] 数据源管理 `/screen/data-source` 正常工作
