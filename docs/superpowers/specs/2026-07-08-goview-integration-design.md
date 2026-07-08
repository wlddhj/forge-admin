# goView 集成设计 spec

**版本：** v1.0
**日期：** 2026-07-08
**作者：** huangjian
**状态：** 待评审
**关联文档：**
- 大屏后端设计：[`2026-07-04-large-screen-design.md`](2026-07-04-large-screen-design.md)
- 大屏后端运行手册：`apps/forge-server/docs/SCREEN-MODULE.md`
- 大屏前端自研 spec：[`2026-07-08-large-screen-frontend-design.md`](2026-07-08-large-screen-frontend-design.md)
- goView 源码：`/Users/huangjian/workspace/cursor/go-view-master`
- goView 文档：https://www.mtruning.club/guide/start/

---

## 1. 背景与目标

### 1.1 背景

forge-admin 大屏模块后端完整（14 个 API + 13 层 SQL 安全 + 动态 view 权限 + 熔断缓存），但**前端自研 11 Task** 反复出体验 bug（编辑器主题切换、TDZ、保存草稿 disabled、JsonSchemaForm 字段编辑、卡片预览数据、新窗口打开等 7+ 问题），开发速度跟不上业务节奏。

业界成熟方案 **goView** (dromara 开源，Vue3 + Naive UI) 提供：
- 50+ 图表/装饰/表格组件
- 完整拖拽编辑器（含撤销/重做、复制粘贴、对齐、标尺）
- JSON 编辑器（Monaco）
- 预览渲染器
- 模板市场

**goView 缺点**：纯前端、项目存 localStorage、Naive UI 与 forge-admin 现有 Element Plus 风格冲突、数据源简单 fetch URL。

### 1.2 目标

把 goView 作为**编辑器/预览器层**集成进 forge-admin 大屏模块：
- **保留**：forge-admin 后端 14 API + 13 层 SQL 安全 + 动态 view 权限 + sys_screen_data_source 抽象
- **替换**：自研的 11 Task 前端编辑器/预览器/装饰组件（约 70 文件 → ~20 文件集成 goView 源码）
- **扩展**：goView 组件加 `'forge'` 数据源类型，间接调 sys_screen_data_source
- **共存**：Naive UI 与 Element Plus 共存（用户进入大屏编辑才看到 Naive UI）

### 1.3 非目标

- 不实现自研大屏 → goView config 迁移工具（旧大屏丢弃）
- 不引入数字孪生/3D 复杂场景（goView 基础功能足够，复杂 3D 后续按需扩展）
- 不改 forge-admin 已有菜单/权限/路由风格
- 不做协同编辑

### 1.4 用户故事

| 角色 | 故事 |
|------|------|
| 管理员 | 我能在 forge-admin 大屏模块用 goView 拖拽编辑器，50+ 组件可选 |
| 管理员 | 我能把 forge-admin 的数据源（SQL 白名单 + HTTP 代理）绑定到 goView 组件 |
| 普通角色 | 我能用 forge-admin 列表/详情/删除/复制/发布大屏 |
| 领导 | 我能在 `/screen/:code` 看大屏（保留自研渲染页）|

---

## 2. 整体架构

### 2.1 目录结构（改造后）

```
apps/forge-web/src/
├── views/screen/
│   ├── index/index.vue                # 列表页（保留自研）
│   ├── render/index.vue               # 运行时渲染页（保留自研，解析 goView config）
│   ├── data-source/                   # 数据源管理（保留自研）
│   │   ├── index.vue
│   │   └── editor.vue
│   ├── goview/                        # goView 改造集成（新增）
│   │   ├── packages/                  # goView src/packages/ 复制
│   │   │   ├── Charts/                # 50+ 图表
│   │   │   ├── Decorates/             # 边框/装饰/3D
│   │   │   ├── Tables/                # 滚动表格
│   │   │   ├── Informations/          # 文字/输入
│   │   │   ├── Photos/                # 图片
│   │   │   ├── VChart/                # 字节 VChart
│   │   │   ├── Icons/
│   │   │   ├── public/                # 公共类型/工具
│   │   │   └── index.d.ts             # 类型
│   │   ├── store/                     # goView store 改造
│   │   │   └── modules/
│   │   │       ├── chartEditStore/    # 画布编辑（改造为调 /api/screen）
│   │   │       ├── chartHistoryStore/ # 撤销/重做（保留 localStorage）
│   │   │       ├── chartLayoutStore/  # 编辑器布局（保留 localStorage）
│   │   │       └── settingStore/      # 设置（保留 localStorage）
│   │   ├── router/
│   │   │   └── modules/               # goView 内部路由
│   │   │       ├── projectRoutes.ts   # → 改跳 /screen/screen
│   │   │       ├── chartRoutes.ts     # → 改跳 /screen/editor/:code/chart
│   │   │       ├── editRoutes.ts      # → 改跳 /screen/editor/:code/json
│   │   │       └── previewRoutes.ts   # → 改跳 /screen/preview/:code
│   │   ├── components/                # goView 编辑器/预览器/包装器
│   │   │   ├── ChartEditor.vue        # /chart 页（拖拽编辑器）
│   │   │   ├── JsonEditor.vue         # /edit 页（Monaco）
│   │   │   ├── Preview.vue            # /preview 页（只读渲染）
│   │   │   └── ...
│   │   ├── api/                       # goView 适配层
│   │   │   ├── screen.ts              # 包装 getScreenList / getScreenDetail / updateScreen
│   │   │   ├── dataSource.ts          # 包装 executeDataSource
│   │   │   └── http.ts                # 复用 forge-admin request.ts
│   │   ├── plugins/                   # goView 启动器
│   │   │   ├── naive.ts               # setupNaive 注册全局组件
│   │   │   ├── directives.ts
│   │   │   └── customComponents.ts
│   │   ├── composables/
│   │   │   └── useForgeDataSource.ts  # 'forge' 类型数据获取
│   │   ├── utils/
│   │   ├── styles/
│   │   └── main.ts                    # goView 子应用入口（懒加载）
│   ├── editor/                        # 编辑器包装器（保留文件名）
│   │   └── index.vue                  # onMounted → router.replace(/chart)
│   ├── preview/                       # 预览包装器（保留文件名）
│   │   └── index.vue                  # onMounted → router.replace(/preview)
│   └── render/                        # 运行时渲染（保留）
├── stores/
│   ├── user.ts                        # 保留
│   ├── permission.ts                  # 保留
│   ├── pageConfig.ts                  # 保留
│   └── tabs.ts                        # 保留
├── composables/
│   ├── useScreenScale.ts              # 保留（运行时渲染页用）
│   └── useCardDataSource.ts           # 保留（运行时渲染页用）
├── api/screen/                        # 保留（goView 适配层引用）
│   ├── index.ts
│   └── __tests__/screen.test.ts        # 保留
├── themes/
│   ├── ui/                            # 保留（forge-admin 主题）
│   └── screen/                        # 保留（运行时渲染页用）
├── styles/
│   └── screen.scss                    # 保留
└── router/constants.ts                # 编辑器/预览器包装路由
```

**删除：**
- `apps/forge-web/src/views/screen/components/{HistoryToolbar,CardPanel,PropertyPanel,JsonSchemaForm,CardErrorBoundary,ScreenRenderer,DataSourceBinder,FieldMappingEditor,TemplateSelector}.vue`
- `apps/forge-web/src/views/screen/cards/` 整个目录（8 卡片 + 4 装饰子组件 + ScrollNumber + tests）
- `apps/forge-web/src/views/screen/decorations/` 整个目录
- `apps/forge-web/src/views/screen/templates/` 整个目录
- `apps/forge-web/src/stores/screenEditor.ts`
- `apps/forge-web/src/composables/useScreenHistory.ts`

### 2.2 关键不变量

1. **后端不变**：14 API + 13 层 SQL 安全 + 动态 view 权限 + 熔断缓存全部保留
2. **菜单不变**：sys_menu 的 13 项 + sys_role_menu 13 项不变
3. **运行时渲染保留**：`/screen/:code` 用 forge-admin 自研渲染（解析 goView config → 调 goView packages/components 渲染组件）
4. **Token 共享**：goView fetch 复用 forge-admin request.ts（已有 token 拦截 + 401 跳转）
5. **Naive UI 仅大屏编辑内部用**：主系统顶栏/侧边栏/列表页仍是 Element Plus

---

## 3. 集成点

### 3.1 HTTP 层（最关键）

**goView 当前**（`api/axios.ts`）：
```ts
const axiosInstance = axios.create({
  baseURL: import.meta.env.DEV ? import.meta.env.VITE_DEV_PATH : import.meta.env.VITE_PRO_PATH,
  timeout: ResultEnum.TIMEOUT,
})
// 响应拦截器检查 code === ResultEnum.DATA_SUCCESS
```

**改造**：
- 改为 `import { request } from '@/utils/request'` 复用 forge-admin 实例
- 删除独立 axios 拦截器（forge-admin request.ts 已有 token + 401 + 错误处理 + Result.success/wrapper）
- `http.ts` 的 get/post/put/delete 包装调 `request.get/post/put/delete` 即可

### 3.2 路由层

**goView 当前**（`router/index.ts`）：`createWebHashHistory('')` 

**改造**：
- 改为 `createWebHistory()` 跟主系统一致
- goView 内部路由（`/project`, `/chart`, `/edit`, `/preview`）改为子路径（如 `/screen/editor/:code/chart`）
- `/project` 路由：删除（列表页用 forge-admin 自研的 `/screen/screen`）
- `/chart` 路由：移入 `/screen/editor/:code` 子路由
- `/edit` 路由：移入 `/screen/editor/:code` 子路由
- `/preview` 路由：移入 `/screen/preview/:code` 子路由

### 3.3 Store 层

**chartEditStore 改造**（关键）：
- 移除 localStorage 持久化
- `setProjectData` 改为调 `updateScreen({ id, code, name, theme, config: JSON.stringify(canvasConfig) })`
- `getProjectData` 改为调 `getScreenDetail(id)` 然后 `JSON.parse(detail.config || detail.configDraft)`
- `removeProjectData` 改为调 `deleteScreen([id])`

**chartHistoryStore**：保留 localStorage（仅前端撤销栈）
**chartLayoutStore**：保留 localStorage（仅 UI 偏好）
**settingStore**：保留 localStorage；theme 应用时通知 forge-admin pageConfig（不破坏 pageConfig store）

### 3.4 主题

**双主题体系**：
- **forge-admin 主系统主题**：pageConfig store（Element Plus 风格），保留不变
- **goView 大屏主题**：goView 自带 themes（dark/light/custom），独立

**应用时机**：
- 用户在 goView 编辑器选择大屏主题 → 仅影响 body data-theme 属性（不影响主系统）
- `/screen/:code` 渲染页应用 goView 主题（与编辑时一致）
- 主系统页面用 forge-admin 主题

### 3.5 UI 库隔离

**主入口 `main.ts`（forge-admin）** 增加：
```ts
import { setupNaive } from '@/views/screen/goview/plugins/naive'
// 现有 Element Plus + 自动导入保留
// 大屏路由加载时再 setupNaive（懒加载）
```

**goView 子应用** `goview/main.ts` 独立 setup：
```ts
export async function bootstrapGoview() {
  setupNaive(app)  // 注册 n-button / n-layout 等
  setupDirectives(app)
  setupCustomComponents(app)
}
```

---

## 4. 数据流

### 4.1 编辑流程

```
1. 用户在 /screen/screen 列表页点"编辑"
2. window.open('/screen/editor/{id}', '_blank')
3. editor/index.vue 包装器 onMounted
   - getScreenDetail(id) → detail (name, theme, config)
   - applyScreenTheme(detail.theme)
   - router.replace('/screen/editor/{id}/chart')
4. /screen/editor/{id}/chart 加载 goView 拖拽编辑器
5. 用户拖拽/编辑/选数据源
   - 编辑画布 → chartEditStore → undoStack (localStorage)
   - 选 forge 数据源 → useForgeDataSource(id, params) → executeDataSource API
6. 用户点"保存" → chartEditStore.setProjectData → updateScreen API → 后端写 sys_screen
7. 用户点"发布" → publishScreen API → 后端 sys_screen.status = 1
```

### 4.2 渲染流程（运行时）

```
1. 用户访问 /screen/operations (sys_menu.routePath)
2. render/index.vue 加载
   - getScreenByCode('operations') → detail.config (JSON 字符串)
   - JSON.parse → canvasConfig
   - applyScreenTheme(canvasConfig.theme)
   - useScreenScale() → 等比缩放
3. <ScreenRuntime :config="canvasConfig">
   - 遍历 canvasConfig.componentList
   - 按 type 找 goView packages/components/Charts/{Type} 组件
   - 渲染组件（卡片 / 装饰 / 图表）
4. useForgeDataSource 在组件 mount 时调 executeDataSource 拿数据
5. ECharts 渲染图表
```

### 4.3 forge 数据源数据流（关键）

```
组件 mount
  ↓
goView 原 fetchData hook 检测 dataSource.type === 'forge'
  ↓
useForgeDataSource(forgeDataSourceId, forgeParams)
  ↓
POST /api/screen/data-source/execute/{id}
  ↓
后端 SysScreenDataSourceService.execute(id, request)
  ↓
1. DataSourceCacheService 查 Redis 缓存
2. DataSourceCircuitBreaker 检查熔断
3. SqlDataSourceExecutor / HttpDataSourceExecutor 实际执行
   - SQL: 13 层 SqlSafetyGuard 校验 + 白名单 + LIMIT + 参数化
   - HTTP: SSRF 防护 + 限流
4. 写回缓存 + 返回 DataSourceExecuteResponse { data, fromCache, executedAt }
  ↓
组件 setOption / 渲染数据
```

---

## 5. 改造点清单

### 5.1 必改文件（goView 源码复制 + 改造）

| 路径 | 改造点 |
|---|---|
| `api/axios.ts` | 替换为 `import request from '@/utils/request'` |
| `api/http.ts` | 包装 `request.get/post/put/delete` |
| `router/index.ts` | `createWebHashHistory` → `createWebHistory` |
| `router/modules/*` | 路径前缀调整（→ `/screen/editor/:code/...`） |
| `store/modules/chartEditStore.ts` | localStorage → `getScreenDetail`/`updateScreen` API |
| `store/modules/settingStore.ts` | 主题应用通知 pageConfig store |
| `main.ts` | 改为 `bootstrapGoview()` 函数（不自动执行）|
| `views/project/index.vue` | 删除或 redirect 到 `/screen/screen` |
| `packages/components/Charts/*/config.ts` | 加 `type: 'forge'` 配置项 + `forgeDataSourceId` 字段 |
| `packages/components/Charts/*/index.vue` | fetchData hook 加 forge 分支 |
| `composables/useFetchData.ts` | 加 forge 类型支持 |

### 5.2 新增文件（forge-admin 端）

| 路径 | 用途 |
|---|---|
| `views/screen/goview/api/screen.ts` | 包装 `getScreenList` / `getScreenDetail` / `updateScreen` / `deleteScreen` / `publishScreen` |
| `views/screen/goview/api/dataSource.ts` | 包装 `executeDataSource` |
| `views/screen/goview/composables/useForgeDataSource.ts` | forge 类型数据源 composable |
| `views/screen/goview/plugins/naive.ts` | 复制自 goView（注册 Naive UI 全局组件）|
| `views/screen/goview/router/mount.ts` | goView 子应用 mount 函数（供编辑器包装器调用）|
| `views/screen/goview/runtime/ScreenRuntime.vue` | 运行时渲染（解析 goView config → goView packages 组件）|
| `views/screen/editor/index.vue` | 编辑器包装器（保留文件名，内容改写）|
| `views/screen/preview/index.vue` | 预览包装器 |

### 5.3 删除文件

- `views/screen/components/{HistoryToolbar,CardPanel,PropertyPanel,JsonSchemaForm,CardErrorBoundary,ScreenRenderer,DataSourceBinder,FieldMappingEditor,TemplateSelector}.vue`
- `views/screen/cards/` 整个目录
- `views/screen/decorations/` 整个目录
- `views/screen/templates/` 整个目录
- `stores/screenEditor.ts`
- `composables/useScreenHistory.ts`

### 5.4 修改文件（forge-admin 现有）

- `router/constants.ts`：编辑器/预览路由改为包装器
- `views/screen/render/index.vue`：改为用 `ScreenRuntime.vue` 渲染
- `package.json`：新增 `naive-ui` dep

### 5.5 路由改动

**router/constants.ts**：
```ts
// 编辑器（包装器）
{
  path: '/screen/editor/:code',
  component: () => import('@/views/screen/editor/index.vue'),
  meta: { title: '大屏编辑', hidden: true, noAuth: false },
  children: [
    { path: 'chart', component: () => import('@/views/screen/goview/components/ChartEditor.vue') },
    { path: 'json',  component: () => import('@/views/screen/goview/components/JsonEditor.vue') },
    { path: '',      redirect: 'chart' }
  ]
}

// 预览（包装器）
{
  path: '/screen/preview/:code',
  component: () => import('@/views/screen/preview/index.vue'),
  meta: { title: '大屏预览', hidden: true, noAuth: false }
}
```

---

## 6. 测试策略

### 6.1 保留测试

- `apps/forge-web/src/api/screen/__tests__/screen.test.ts`（14 用例，验证 API 客户端）
- `apps/forge-web/src/composables/__tests__/useScreenScale.test.ts`（4 用例）
- `apps/forge-web/src/composables/__tests__/useCardDataSource.test.ts`（4 用例）
- `apps/forge-web/src/themes/__tests__/*`（保留）

### 6.2 新增测试

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `views/screen/goview/api/__tests__/screen.test.ts` | 8 | screen API 包装（getScreenList/getScreenDetail/updateScreen/...）|
| `views/screen/goview/api/__tests__/dataSource.test.ts` | 4 | executeDataSource 包装 |
| `views/screen/goview/composables/__tests__/useForgeDataSource.test.ts` | 4 | 'forge' 类型分支 |
| `views/screen/goview/components/__tests__/EditorWrapper.test.ts` | 3 | 编辑器包装器跳转逻辑 |
| `views/screen/goview/components/__tests__/PreviewWrapper.test.ts` | 2 | 预览包装器 |
| `views/screen/goview/runtime/__tests__/ScreenRuntime.test.ts` | 4 | goView config → Vue 组件映射 |
| **小计** | **27** | |

### 6.3 总测试数目标

- 保留：~25 个
- 新增：27 个
- **总目标：≥ 52 个用例**

### 6.4 验证门

```bash
cd apps/forge-web
pnpm lint         # 0 error
pnpm test:run     # 全绿
pnpm build        # tsc + vite build 通过
```

### 6.5 手动验证清单

- [ ] 登录后访问 `/screen/screen` 看到大屏列表
- [ ] 点"新增大屏" → 创建 → 跳转到 `/screen/editor/N/chart`（goView 拖拽编辑器）
- [ ] 在 goView 编辑器拖一个 line-chart 组件到画布
- [ ] 配置数据源选"forge"，下拉选择已有数据源，绑定
- [ ] 点保存 → 后端 sys_screen 更新
- [ ] 点发布 → 后端 sys_screen.status = 1
- [ ] 访问 `/screen/operations` 运行时渲染 → 看到大屏正常显示
- [ ] 在另一个浏览器窗口打开 `/screen/editor/N/chart`（新窗口不挡列表）

---

## 7. 风险与限制

| 风险 | 触发 | 缓解 |
|---|---|---|
| Naive UI 体积大 | goView 编辑器加载慢 | 编辑器路由 dynamic import，懒加载 |
| goView 升级困难 | 未来 goView upstream 升级 | 改造点集中在 5 个文件，diff 形式记录在 spec |
| 运行时渲染映射复杂 | goView 50+ 组件 → Vue 组件映射 | 写一个简单的 componentMap（type → component import）|
| token 同步 | goView 子应用 fetch 失败 | 复用 forge-admin request.ts（已有 token 拦截）|
| 主系统/大屏 UI 风格不一致 | Naive UI vs Element Plus | 用户进入大屏才看到 Naive UI；顶栏/侧边栏不变 |
| dataSource 配置 schema 不兼容 | goView 原 schema vs 加 'forge' 字段 | 扩展 goView packages/components/Charts/*/config.ts 加 type='forge' 选项 |
| localStorage 行为改变 | chartEditStore 不再写 localStorage | 撤销/重做栈仍用 localStorage（不影响用户）|
| iframe/全局副作用冲突 | goView main.ts 设 `window['$vue']` | 隔离到子应用，不污染主应用 |

---

## 8. 验收清单（完成时核对）

- [ ] goView 源码复制到 `apps/forge-web/src/views/screen/goview/`
- [ ] axios 改造为复用 `utils/request.ts`
- [ ] 路由改造为 history 模式 + 子路径
- [ ] chartEditStore 改造为调 forge-admin API
- [ ] Naive UI 集成（main.ts + 子应用 lazy load）
- [ ] 50+ goView 组件加 `type: 'forge'` 数据源类型支持
- [ ] 编辑器包装器 (`views/screen/editor/index.vue`) 跳 `/chart`
- [ ] 预览包装器 (`views/screen/preview/index.vue`) 跳 goView preview
- [ ] 运行时渲染器 (`views/screen/render/index.vue`) 解析 goView config
- [ ] 自研 11 Task 文件全部删除
- [ ] 27 个新测试 + 25 个保留测试全绿
- [ ] `pnpm lint` 0 error
- [ ] `pnpm build` 通过
- [ ] 手动验证 8 项全通过
- [ ] 后端零改动（diff = 仅前端 + 菜单 SQL + 测试）

---

## 9. 后续可选（不在本次范围）

- goView upstream 定期 rebase
- 数字孪生 / 3D 大屏扩展（goView 已支持 three.js）
- 协同编辑（多用户同时编辑）
- 大屏模板市场（goView 自带 mtTemplate）
- 移动端适配
