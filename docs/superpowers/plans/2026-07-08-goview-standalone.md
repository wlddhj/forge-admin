# goView 独立工程集成实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 goView 作为独立前端工程 `apps/forge-screen` 部署（端口 8001），通过 iframe + URL query 与 forge-admin 通信；goView axios 改调 forge-admin API；chartEditStore 用 API 替代 localStorage；组件数据源加 'forge' 类型；forge-admin 自研编辑器/预览器/卡片/装饰全部删除。

**Architecture:** forge-admin 前端 iframe 嵌入 goView（`<iframe src="localhost:8001/#/chart?id=5&token=xxx">`）；goView 独立 pnpm dev 启动，axios baseURL = forge-admin 后端（`localhost:8181/admin-api`）；iframe 通信靠 URL query 传递 id + JWT token；生产环境 goView build 产物部署在同域 `/screen-app/` 下，token 通过同域 cookie 自动携带。

**Tech Stack:**
- goView：Vue 3.5 + Naive UI + Pinia + ECharts + VChart + vue-i18n（端口 8001）
- forge-admin 前端：Vue 3.4 + Element Plus（端口 3003），iframe 容器
- forge-admin 后端：Spring Boot 3.2.0（端口 8181），14 API 不变

**上游文档：**
- 总体设计：[`../specs/2026-07-08-goview-standalone-design.md`](../specs/2026-07-08-goview-standalone-design.md)
- goView 源码：`/Users/huangjian/workspace/cursor/go-view-master`

---

## Global Constraints

- goView 端口：**8001**（开发环境）
- forge-admin 前端端口：**3003**
- forge-admin 后端端口：**8181**，上下文 `/api`
- forge-admin 前端 baseURL：`/admin-api`（vite proxy → localhost:8181）
- goView axios baseURL：`http://localhost:8181/admin-api`（开发）/ `/admin-api`（生产）
- 权限码不变：`screen:screen:{list|view:{code}|edit|publish|copy|remove|add}` + `screen:data-source:{list|query|add|edit|remove|execute}`
- 菜单 13 项不变
- 后端 14 API 零改动
- iframe 通信：URL query 传 `id` + `token`（开发）；生产同域 cookie
- goView 路由模式：hash 路由（`/#/chart`），**不改** history 模式
- 提交信息：中文 `<type>(<scope>): <subject>`，**禁止** `Co-Authored-By`

---

## Task 1: 复制 goView → apps/forge-screen + 开发环境配置

**Files:**
- Create: `apps/forge-screen/` 整个目录（复制 goView 源码）
- Modify: `apps/forge-screen/.env`
- Modify: `apps/forge-screen/vite.config.ts`

- [ ] **Step 1: 复制 goView 源码**

```bash
cd /Users/huangjian/workspace/cursor/forge-admin
cp -R /Users/huangjian/workspace/cursor/go-view-master apps/forge-screen
# 删除 goView 的 .git（避免嵌套 git repo）
rm -rf apps/forge-screen/.git
ls apps/forge-screen/  # 应有 src/ package.json vite.config.ts 等
```

- [ ] **Step 2: 安装依赖**

```bash
cd apps/forge-screen
pnpm install
```

- [ ] **Step 3: 修改 .env — 端口 + API baseURL**

```env
# apps/forge-screen/.env
VITE_DEV_PORT = '8001'
VITE_DEV_PATH = '/'
VITE_PRO_PATH = '/screen-app/'
VITE_API_BASE = 'http://localhost:8181/admin-api'
```

- [ ] **Step 4: 修改 vite.config.ts — 加 proxy**

在 `apps/forge-screen/vite.config.ts` 的 `server` 块中加入 proxy：

```ts
server: {
  port: 8001,
  open: true,
  proxy: {
    '/admin-api': {
      target: 'http://localhost:8181',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/admin-api/, '/api')
    }
  }
},
```

- [ ] **Step 5: 验证独立启动**

```bash
cd apps/forge-screen
pnpm dev
# 浏览器打开 http://localhost:8001
# 应看到 goView 项目列表页（Naive UI 风格）
```

- [ ] **Step 6: Commit**

```bash
git add apps/forge-screen/
git commit -m "feat(screen): 复制 goView 到 apps/forge-screen（独立前端工程，端口 8001）

- 修改 .env：端口 8001，API baseURL = localhost:8181/admin-api
- 修改 vite.config.ts：加 /admin-api proxy 到 localhost:8181
- goView 作为独立工程 pnpm dev 启动，不与 forge-admin 构建耦合"
```

---

## Task 2: goView axios 改造（调 forge-admin API）

**Files:**
- Modify: `apps/forge-screen/src/api/axios.ts`
- Modify: `apps/forge-screen/src/enums/httpEnum.ts`（如 ResultEnum 值不同）

- [ ] **Step 1: 查看 forge-admin 响应格式**

forge-admin `Result.success()` 返回 `{ code: 200, message: "success", data: ... }`。
goView 原检查 `ResultEnum.DATA_SUCCESS`（值为 0）。
需要把 ResultEnum.DATA_SUCCESS 改为 200。

- [ ] **Step 2: 修改 axios.ts**

```ts
// apps/forge-screen/src/api/axios.ts
import axios, { AxiosResponse, InternalAxiosRequestConfig, AxiosError } from 'axios'

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/admin-api',
  timeout: 15000,
})

// 请求拦截器：从 URL query 读 token
axiosInstance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从 hash 路由的 query 参数中读 token（/#/chart?id=5&token=xxx）
    try {
      const hash = window.location.hash
      const queryStr = hash.split('?')[1] || ''
      const params = new URLSearchParams(queryStr)
      const token = params.get('token')
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`
      }
    } catch { /* URL 解析失败，跳过头 */ }
    return config
  },
  (error: AxiosError) => Promise.reject(error)
)

// 响应拦截器：适配 forge-admin Result 格式
axiosInstance.interceptors.response.use(
  (res: AxiosResponse) => {
    const data = res.data
    // forge-admin 格式：{ code: 200, message: 'success', data: ... }
    if (data && data.code === 200) {
      return data.data  // 自动解包，goView 组件拿到的是 data.data
    }
    // 非 200 的响应
    if (data && data.code) {
      console.error('[goview api] error', data.code, data.message)
      return Promise.reject(new Error(data.message || `API error: ${data.code}`))
    }
    return data
  },
  (err: AxiosResponse) => Promise.reject(err)
)

export default axiosInstance
```

- [ ] **Step 3: 修改 httpEnum.ts（如需要）**

```bash
grep -n "DATA_SUCCESS\|ResultEnum" apps/forge-screen/src/enums/httpEnum.ts
# 确认 ResultEnum.DATA_SUCCESS 的值，如果会影响，不需要改
# 因为新的 axios.ts 不再引用 ResultEnum
```

- [ ] **Step 4: 验证 goView 项目列表页仍能加载**

```bash
cd apps/forge-screen && pnpm dev
# 浏览器访问 http://localhost:8001
# 项目列表页从 localStorage 加载（还没改 API），应能正常显示
```

- [ ] **Step 5: Commit**

```bash
git add apps/forge-screen/src/api/axios.ts
git commit -m "fix(screen): goView axios 适配 forge-admin API 格式

- baseURL 改为 VITE_API_BASE（localhost:8181/admin-api）
- 请求拦截器从 URL query 读 token 加到 Authorization 头
- 响应拦截器改为检查 code === 200（forge-admin Result.success() 格式）
- 自动解包 data.data（goView 组件不需要再 .data.data）"
```

---

## Task 3: goView store 改造（localStorage → forge-admin API）

**Files:**
- Create: `apps/forge-screen/src/api/forge/screen.ts`（forge-admin API 封装）
- Modify: `apps/forge-screen/src/store/modules/chartEditStore/chartEditStore.ts`

- [ ] **Step 1: 创建 forge-admin API 封装**

```ts
// apps/forge-screen/src/api/forge/screen.ts
import request from '@/api/axios'
import type { AxiosResponse } from 'axios'

export interface ScreenDetail {
  id: number
  code: string
  name: string
  description?: string
  config: string        // JSON 字符串
  configDraft: string   // JSON 字符串
  theme: string
  status: number        // 0=草稿 1=已发布
  version: number
}

export interface ScreenUpdateRequest {
  id: number
  code?: string
  name?: string
  theme?: string
  config?: string       // JSON 字符串（goView 画布 config）
  description?: string
}

export const getScreenDetail = (id: number): Promise<ScreenDetail> =>
  request.get(`/screen/${id}`)

export const getScreenByCode = (code: string): Promise<ScreenDetail> =>
  request.get(`/screen/code/${code}`)

export const updateScreen = (data: ScreenUpdateRequest): Promise<void> =>
  request.put('/screen', data)

export const publishScreen = (code: string): Promise<void> =>
  request.put(`/screen/publish/${code}`)

export const deleteScreen = (ids: number[]): Promise<void> =>
  request.delete('/screen', { data: ids })

export const copyScreen = (code: string, data: { newCode: string; newName: string }): Promise<number> =>
  request.post(`/screen/copy/${code}`, data)
```

- [ ] **Step 2: 在 chartEditStore 加 API 方法**

在 `chartEditStore.ts` 顶部加 import：
```ts
import { getScreenDetail, updateScreen } from '@/api/forge/screen'
```

在 store `actions` 中加：
```ts
actions: {
  // 从 forge-admin API 加载项目
  async loadProjectById(id: number) {
    try {
      const detail = await getScreenDetail(id)
      const raw = detail.configDraft || detail.config
      if (raw) {
        const cfg = JSON.parse(raw)
        // 恢复到 store 的 editCanvasConfig 和 componentList
        if (cfg.editCanvasConfig) {
          Object.assign(this.editCanvasConfig, cfg.editCanvasConfig)
        }
        if (cfg.componentList) {
          this.componentList = cfg.componentList
        }
      }
      return detail
    } catch (e) {
      console.error('[chartEditStore] loadProjectById failed', e)
      throw e
    }
  },

  // 保存项目到 forge-admin API
  async saveProjectToApi(id: number, code: string, name: string) {
    const config = JSON.stringify({
      editCanvasConfig: toRaw(this.editCanvasConfig),
      componentList: toRaw(this.componentList)
    })
    await updateScreen({ id, code, name, config })
  }
}
```

- [ ] **Step 3: 在 chart 页面入口调用 loadProjectById**

找到 goView 的 `/chart` 路由入口组件，在 `onMounted` 中加：

```ts
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'

const chartEditStore = useChartEditStore()

onMounted(async () => {
  const id = getScreenIdFromUrl()  // 从 URL query 解析
  if (id) {
    await chartEditStore.loadProjectById(id)
  }
})

function getScreenIdFromUrl(): number | null {
  const params = new URLSearchParams(window.location.hash.split('?')[1] || '')
  const id = params.get('id')
  return id ? Number(id) : null
}
```

- [ ] **Step 4: 验证通过 forge-admin 后端加载大屏**

```bash
# 确保后端运行
cd apps/forge-server && mvn spring-boot:run -pl forge-server

# 启动 goView
cd apps/forge-screen && pnpm dev

# 浏览器访问 http://localhost:8001/#/chart?id=1&token=<valid_jwt_token>
# 如果能加载大屏数据，成功
```

- [ ] **Step 5: Commit**

```bash
git add apps/forge-screen/src/api/forge/ apps/forge-screen/src/store/modules/chartEditStore/ apps/forge-screen/src/views/chart/
git commit -m "feat(screen): goView store 改造 — localStorage → forge-admin API

- 新增 api/forge/screen.ts（封装 getScreenDetail/updateScreen/publishScreen）
- chartEditStore 加 loadProjectById / saveProjectToApi 方法
- chart 页面入口 onMounted 从 URL query 读 id → 调 loadProjectById
- 原 localStorage 存/读逻辑保留但不使用（后续 Task 可清理）"
```

---

## Task 4: forge-admin 前端 iframe 集成

**Files:**
- Modify: `apps/forge-web/src/views/screen/editor/index.vue`
- Modify: `apps/forge-web/src/views/screen/preview/index.vue`
- Modify: `apps/forge-web/src/views/screen/index/index.vue`
- Modify: `apps/forge-web/src/router/constants.ts`

- [ ] **Step 1: 改造 editor/index.vue 为 iframe 容器**

```vue
<!-- apps/forge-web/src/views/screen/editor/index.vue -->
<template>
  <iframe
    v-if="goViewUrl"
    :src="goViewUrl"
    class="screen-iframe"
    allow="fullscreen"
    @error="handleError"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const userStore = useUserStore()

const goViewUrl = computed(() => {
  const id = route.params.code
  const token = userStore.token
  const base = import.meta.env.DEV
    ? 'http://localhost:8001'
    : '/screen-app'
  return `${base}/#/chart?id=${id}&token=${token}`
})

const handleError = () => {
  ElMessage.error('goView 编辑器加载失败')
}
</script>

<style scoped>
.screen-iframe { width: 100vw; height: 100vh; border: none; }
</style>
```

- [ ] **Step 2: 改造 preview/index.vue 为 iframe 容器**

同上，但 hash 路径设为 `/#/preview`：

```ts
return `${base}/#/preview?id=${id}&token=${token}`
```

- [ ] **Step 3: 改造列表页 index/index.vue**

手风琴效仿：
- `handleCreate` → `window.open('/screen/editor/' + newId, '_blank')`（不变，iframe 在新窗口）
- `handleEdit` → `window.open('/screen/editor/' + row.id, '_blank')`（不变）

- [ ] **Step 4: router/constants.ts 保持不变**

当前路由：
```ts
{ path: '/screen/editor/:code', component: () => import('@/views/screen/editor/index.vue'), ... }
{ path: '/screen/preview/:code', component: () => import('@/views/screen/preview/index.vue'), ... }
```

编辑器/预览器路由不需要子路由，因为 iframe 不在 vue-router 管理范围内。

- [ ] **Step 5: 验证**

```bash
# 启动三个服务
# Terminal 1: forge-admin 后端
cd apps/forge-server && mvn spring-boot:run -pl forge-server

# Terminal 2: goView
cd apps/forge-screen && pnpm dev

# Terminal 3: forge-admin 前端
cd apps/forge-web && pnpm dev

# 浏览器访问 http://localhost:3003/screen/screen
# 点"新增大屏" → 新窗口打开 → iframe 嵌入 goView
```

- [ ] **Step 6: Commit**

```bash
git add apps/forge-web/src/views/screen/editor/index.vue \
        apps/forge-web/src/views/screen/preview/index.vue
git commit -m "feat(screen): 编辑器/预览页改为 iframe 容器嵌入 goView

- editor/index.vue: <iframe src='localhost:8001/#/chart?id=N&token=xxx'>
- preview/index.vue: <iframe src='localhost:8001/#/preview?id=N&token=xxx'>
- token 从 userStore.token 取（JWT access token）
- 生产环境 base 改为 /screen-app/（同域 nginx 代理）"
```

---

## Task 5: goView 项目列表页 → 跳 forge-admin

**Files:**
- Modify: `apps/forge-screen/src/router/modules/project.router.ts`
- Modify: `apps/forge-screen/src/views/project/` 相关文件

- [ ] **Step 1: 修改 project 路由为 redirect**

```ts
// apps/forge-screen/src/router/modules/project.router.ts
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/project',
    redirect: () => {
      // 跳转到 forge-admin 大屏列表
      const base = import.meta.env.DEV
        ? 'http://localhost:3003'
        : '/'
      window.location.href = `${base}/screen/screen`
    }
  }
]

export default routes
```

- [ ] **Step 2: 修改项目列表页面模板**

在项目列表入口页去引：
```ts
// 删除或重定向 localStorage 的项目列表逻辑
// 用户从 goView 入口进入 → 自动跳 forge-admin 列表
```

- [ ] **Step 3: Commit**

```bash
git add apps/forge-screen/src/router/modules/project.router.ts
git commit -m "feat(screen): goView 项目列表页 → redirect forge-admin /screen/screen"
```

---

## Task 6: goView 组件 'forge' 数据源类型

**Files:**
- Create: `apps/forge-screen/src/api/forge/dataSource.ts`
- Modify: `apps/forge-screen/src/hooks/useChartDataFetch.hook.ts`
- Modify: `apps/forge-screen/src/views/chart/ContentConfigurations/components/ChartData/components/ChartDataAjax/index.vue`

- [ ] **Step 1: 创建 forge 数据源 API 封装**

```ts
// apps/forge-screen/src/api/forge/dataSource.ts
import request from '@/api/axios'

export interface DataSourceExecuteRequest {
  params?: Record<string, unknown>
}

export interface DataSourceExecuteResponse {
  data: unknown
  fromCache: boolean
  executedAt: string
}

export const executeDataSource = (id: number, data: DataSourceExecuteRequest): Promise<DataSourceExecuteResponse> =>
  request.post(`/screen/data-source/execute/${id}`, data)
```

- [ ] **Step 2: 在 fetchData hook 中加 'forge' 分支**

```ts
// 在 useChartDataFetch.hook.ts 的数据获取逻辑中
import { executeDataSource } from '@/api/forge/dataSource'

// 检测 dataSource.type === 'forge'
if (dataSource?.type === 'forge' && dataSource?.forgeDataSourceId) {
  try {
    const res = await executeDataSource(dataSource.forgeDataSourceId, {
      params: dataSource.forgeParams || {}
    })
    return res.data  // 已由 axios 拦截器自动解包
  } catch (e) {
    console.error('[forge data source] fetch failed', e)
    return []
  }
}
```

- [ ] **Step 3: 在 ChartDataAjax 配置中加 'forge' 选项**

在数据源配置 schema 中加：
```ts
// 数据源类型枚举
dataSourceType: {
  type: 'select',
  options: [
    { label: '静态数据', value: 'static' },
    { label: 'HTTP 接口', value: 'api' },
    { label: 'forge 数据源', value: 'forge' }  // 新增
  ]
}

// forge 数据源 ID（当 type === 'forge' 时显示）
forgeDataSourceId: {
  type: 'number',
  title: 'forge 数据源',
  show: (config) => config.dataSource?.type === 'forge'
}
```

- [ ] **Step 4: 验证数据源执行**

```bash
# 确保后端有至少一个数据源记录
mysql -u root -ppassword -e "SELECT id, name, type FROM forge_admin.sys_screen_data_source"

# 浏览器 F12 打开 Network 面板
# 在 goView 编辑器中选择 'forge' 类型数据源，填入 ID
# 验证 POST /admin-api/screen/data-source/execute/{id} 返回 200 + data 数组
```

- [ ] **Step 5: Commit**

```bash
git add apps/forge-screen/src/api/forge/dataSource.ts \
        apps/forge-screen/src/hooks/ \
        apps/forge-screen/src/views/chart/ContentConfigurations/
git commit -m "feat(screen): goView 组件支持 'forge' 数据源类型

- 新增 api/forge/dataSource.ts（封装 executeDataSource）
- useChartDataFetch.hook.ts 加 type === 'forge' 分支
- ChartDataAjax 配置加 'forge' 选项 + forgeDataSourceId 字段
- 数据源执行走 forge-admin 13 层 SQL 安全 + 熔断 + 缓存"
```

---

## Task 7: goView '保存草稿' / '发布' → 调 forge-admin API

**Files:**
- Modify: `apps/forge-screen/src/views/chart/ContentEdit/components/EditTools/` （或对应的保存/发布按钮组件）

- [ ] **Step 1: 找到 goView 保存/发布按钮**

```bash
grep -rn "保存\|save\|saveProject\|发布\|publish" apps/forge-screen/src/views/chart/ContentEdit/ 2>/dev/null | head -10
```

- [ ] **Step 2: 改造按钮 handler**

```ts
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { publishScreen } from '@/api/forge/screen'

const chartEditStore = useChartEditStore()

// 保存草稿
const handleSave = async () => {
  const id = getScreenIdFromUrl()
  if (!id) return
  await chartEditStore.saveProjectToApi(id, /* code */, /* name */)
  window['$message'].success('保存成功')
}

// 发布
const handlePublish = async () => {
  const code = getScreenCode()  // 从 store 或 detail 拿
  await publishScreen(code)
  window['$message'].success('发布成功')
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/forge-screen/src/views/chart/ContentEdit/
git commit -m "feat(screen): goView 保存草稿/发布按钮 → fforge-admin API

- handleSave 调 chartEditStore.saveProjectToApi → updateScreen
- handlePublish 调 publishScreen(code) → publishScreen API"
```

---

## Task 8: forge-admin 删除自研 11 Task 文件

**Files:**
- Delete: 18 个自研文件（components/cards/decorations/templates/screenEditor/useScreenHistory）

与之前 spec 删除清单一致。

- [ ] **Step 1: 验证引用已无残留**

```bash
cd apps/forge-web
grep -rn "JsonSchemaForm\|HistoryToolbar\|CardPanel\|PropertyPanel\|ScreenRenderer\|screenEditor\|useScreenHistory" src/ 2>/dev/null | grep -v "node_modules" | grep -v "__tests__" | head -10
# 应无任何匹配
```

- [ ] **Step 2: 删除**

```bash
rm -rf src/views/screen/components
rm -rf src/views/screen/cards
rm -rf src/views/screen/decorations
rm -rf src/views/screen/templates
rm -f src/stores/screenEditor.ts
rm -f src/composables/useScreenHistory.ts
```

- [ ] **Step 3: 跑测试 + build 验证**

```bash
pnpm test:run 2>&1 | tail -10
pnpm exec vite build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(screen): 删除自研 11 Task 前端代码

删除自研编辑器/预览器/卡片/装饰/模板/JsonSchemaForm/HistoryToolbar 等，
由 goView 独立工程替代。
- views/screen/components/ (9 个文件)
- views/screen/cards/ (全部)
- views/screen/decorations/ (全部)
- views/screen/templates/ (全部)
- stores/screenEditor.ts
- composables/useScreenHistory.ts"
```

---

## Task 9: 集成测试与手动验证

- [ ] **Step 1: goView 独立启动**

```bash
cd apps/forge-screen && pnpm dev
# 浏览器 http://localhost:8001 → 跳转 forge-admin 列表页
```

- [ ] **Step 2: forge-admin 后端**

```bash
cd apps/forge-server && mvn spring-boot:run -pl forge-server
```

- [ ] **Step 3: forge-admin 前端**

```bash
cd apps/forge-web && pnpm dev
# 浏览器 http://localhost:3003/login → 登录 → /screen/screen
```

- [ ] **Step 4: 手动验证 8 项**

| # | 验证项 | 预期 |
|---|--------|------|
| 1 | 访问 goView 8001 | 自动跳转 forge-admin 列表 |
| 2 | forge-admin 新建大屏 | 创建 → 新窗口打开 iframe（goView chart 编辑器） |
| 3 | iframe 中拖放组件 | goView 50+ 组件可选，拖到画布正常 |
| 4 | 选 forge 数据源 | 下拉有 'forge' 选项，填入 ID 后调 API 成功 |
| 5 | 保存草稿 | 后端 sys_screen.config_draft 更新 |
| 6 | 发布 | 后端 sys_screen.config 更新，status=1 |
| 7 | forge-admin 渲染页 | /screen/:code 正常渲染 goView JSON |
| 8 | forge-admin 数据源管理 | 列表/新增/编辑/测试正常 |

- [ ] **Step 5: 跑 vitest 测试**

```bash
cd apps/forge-web && pnpm test:run 2>&1 | tail -10
# 保留 22 + 新增 5 = 27 个全绿
```

- [ ] **Step 6: 跑 build**

```bash
cd apps/forge-web && pnpm build 2>&1 | tail -5  # 成功
cd apps/forge-screen && pnpm build 2>&1 | tail -5 # 成功
```

---

## Task 10: 生产环境配置

**Files:**
- Modify: `apps/forge-screen/.env.production`
- Create: `nginx.conf` 片段（文档，不提交）

- [ ] **Step 1: goView 生产环境配置**

```env
# apps/forge-screen/.env.production
VITE_PRO_PATH = '/screen-app/'
VITE_API_BASE = '/admin-api'
```

- [ ] **Step 2: forge-admin vite proxy 加 goView 代理**

在 `apps/forge-web/vite.config.ts` 中加：

```ts
server: {
  proxy: {
    '/screen-app': {
      target: 'http://localhost:8001',
      changeOrigin: true
    }
  }
}
```

这样 dev 环境 forge-admin 页面中的 iframe 加载 goView 时可以跨 localhost。

- [ ] **Step 3: Nginx 配置（文档）**

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

# API 代理
location /admin-api/ {
    proxy_pass http://127.0.0.1:8181/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/forge-screen/.env.production apps/forge-web/vite.config.ts
git commit -m "chore(screen): goView 生产环境配置 + forge-admin 代理

- .env.production: goView build 路径 /screen-app/，API baseURL /admin-api
- vite.config.ts: 加 /screen-app proxy → localhost:8001（dev iframe 跨域）
- 附 nginx 生产部署配置"
```

---

## 验收清单（完成时核对）

- [ ] `apps/forge-screen` 独立启动（`pnpm dev`，端口 8001）
- [ ] goView axios 调 forge-admin API（code 200 格式）
- [ ] goView chartEditStore 用 API 替代 localStorage
- [ ] goView 组件支持 'forge' 数据源
- [ ] goView 保存草稿/发布调 API
- [ ] goView 项目列表 redirect forge-admin
- [ ] forge-admin 编辑器/预览用 iframe 嵌入 goView
- [ ] forge-admin 自研 11 Task 文件全部删除
- [ ] forge-admin 列表页/数据源管理/运行时渲染正常
- [ ] `pnpm test:run`（forge-web）全绿
- [ ] `pnpm build`（forge-web + forge-screen）全绿
- [ ] 后端零改动
