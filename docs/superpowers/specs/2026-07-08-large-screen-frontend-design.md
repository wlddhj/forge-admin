# 大屏展示系统 - 前端实现设计

**版本：** v1.0
**日期：** 2026-07-08
**作者：** huangjian
**状态：** 待评审
**关联文档：**
- 后端设计：[`2026-07-04-large-screen-design.md`](2026-07-04-large-screen-design.md)
- 后端运行手册：`apps/forge-server/docs/SCREEN-MODULE.md`
- 实施计划（任务级）：[`../plans/2026-07-06-large-screen-frontend.md`](../plans/2026-07-06-large-screen-frontend.md)

---

## 1. 背景与目标

### 1.1 背景

大屏模块（`forge-module-screen`）后端已完整交付：
- 14 个 API 端点（8 大屏 + 6 数据源）
- 7+7 静态权限码 + 动态 `screen:screen:view:{code}` 权限
- 13 层 SQL 安全护栏、HTTP SSRF 防护、熔断 + 缓存、配置即 JSON
- 运行手册 `SCREEN-MODULE.md` 完备

但**前端完全缺失**：`apps/forge-web/src/views/screen/` 目录不存在，业务方无法：
- 浏览大屏列表
- 创建 / 编辑 / 发布 / 复制大屏
- 浏览大屏（领导投屏场景）
- 管理数据源（HTTP / SQL）

### 1.2 目标

按既有规划文档 `2026-07-06-large-screen-frontend.md` 的 11 个 Task 一次性完成前端实现，**唯一调整**：把 `@form-create/element-ui` 替换为自实现 `JsonSchemaForm`（避免 Element Plus 3 兼容性风险，体积更小）。

交付物：
- 11 Task 顺序完成 + 每个 Task 单独 commit
- 大屏列表 / 渲染 / 编辑器 / 数据源管理 4 个核心流程端到端可用
- 8 个核心卡片 + 4 个装饰组件
- 3 套主题 + 6 个预设模板
- Vitest ≥ 40 个单测 + Playwright 视觉回归基线
- ESLint 0 error + `pnpm build` 通过

### 1.3 非目标

- 不实现"无代码 SQL 查询构建器"（沿用后端 SQL 白名单模式）
- 不实现大屏导出 PDF / 图片
- 不实现多租户隔离
- 不接入移动端
- 不做协同编辑（单人编辑 + 撤销/重做）

### 1.4 用户故事

| 角色 | 故事 |
|------|------|
| 管理员 | 作为系统管理员，我能创建 / 编辑 / 发布 / 复制大屏，调整卡片布局和样式 |
| 业务方 | 作为业务人员，我能在编辑器左侧拖卡片到画布，在右侧用表单配置卡片属性 |
| 业务方 | 作为业务人员，我能管理数据源（HTTP / SQL），把数据源绑定到卡片上 |
| 普通角色 | 作为被授权的查看者，我能打开 `/screen/{code}` 查看大屏 |
| 领导 | 作为观看者，我能在投屏上看到 1920×1080 等比缩放的大屏 |

---

## 2. 整体架构

### 2.1 分层架构

```
┌────────────────────────────────────────────────────────────────────┐
│  路由层                                                            │
│    /screen/list        - 列表（动态菜单注入）                      │
│    /screen/editor/:code - 编辑器（CONSTANT_ROUTES 静态）           │
│    /screen/preview/:code - 预览（CONSTANT_ROUTES 静态）            │
│    /screen/:code        - 渲染页（动态菜单注入，按 code 授权）     │
├────────────────────────────────────────────────────────────────────┤
│  Pinia stores                                                      │
│    screen.ts          - 运行时状态（active config/loading）        │
│    screenEditor.ts    - 编辑器草稿 + 撤销/重做快照栈              │
├────────────────────────────────────────────────────────────────────┤
│  视图层  views/screen/{index, render, preview, editor, data-source}│
├────────────────────────────────────────────────────────────────────┤
│  核心组件  ScreenRenderer / CardErrorBoundary / JsonSchemaForm      │
│            CardPanel / PropertyPanel / DataSourceBinder            │
│            FieldMappingEditor / HistoryToolbar / TemplateSelector │
├────────────────────────────────────────────────────────────────────┤
│  composables  useScreenScale / useCardDataSource / useScreenHistory │
├────────────────────────────────────────────────────────────────────┤
│  cards registry  cardRegistry + 8 builtin cards + 4 decorations    │
├────────────────────────────────────────────────────────────────────┤
│  themes/screen  3 SCSS bundles + applyScreenTheme(name)            │
├────────────────────────────────────────────────────────────────────┤
│  API 层  /api/screen  →  screenApi + dataSourceApi                 │
└────────────────────────────────────────────────────────────────────┘
```

### 2.2 关键不变量

1. **配置即 JSON**：`ScreenResponse.config` 是字符串，**首次加载时** `JSON.parse` 一次，缓存在 Pinia，永不重复解析
2. **registry 单例**：`cardRegistry` 模块级单例，编辑器与渲染页共享
3. **画布固定 1920×1080**：所有定位用绝对像素，`<ScreenRenderer>` 外层 `<Transform :scale>` 等比缩放
4. **catch 永不静默**：所有 catch 必须 `console.error` + `ElMessage.error`
5. **ECharts 按需引入**：禁止 `import * as echarts from 'echarts'`，体积 < 350KB gzip

---

## 3. 组件清单

### 3.1 文件清单（11 Task 全部产出）

| 模块 | 路径 |
|------|------|
| **API** | `apps/forge-web/src/api/screen/index.ts` + `__tests__/screen.test.ts` |
| **types** | `apps/forge-web/src/types/screen.ts` |
| **constants** | `apps/forge-web/src/constants/screen.ts` |
| **composables** | `apps/forge-web/src/composables/useScreenScale.ts` |
| | `apps/forge-web/src/composables/useCardDataSource.ts` |
| | `apps/forge-web/src/composables/useScreenHistory.ts` |
| **stores** | `apps/forge-web/src/stores/screen.ts` |
| | `apps/forge-web/src/stores/screenEditor.ts` |
| **themes** | `apps/forge-web/src/themes/screen/_shared.scss` |
| | `apps/forge-web/src/themes/screen/_dark-tech.scss` |
| | `apps/forge-web/src/themes/screen/_blue-deep.scss` |
| | `apps/forge-web/src/themes/screen/_black-gold.scss` |
| | `apps/forge-web/src/themes/screen/index.ts` |
| **核心组件** | `apps/forge-web/src/views/screen/components/ScreenRenderer.vue` |
| | `apps/forge-web/src/views/screen/components/CardErrorBoundary.vue` |
| | `apps/forge-web/src/views/screen/components/CardPanel.vue` |
| | `apps/forge-web/src/views/screen/components/PropertyPanel.vue` |
| | `apps/forge-web/src/views/screen/components/DataSourceBinder.vue` |
| | `apps/forge-web/src/views/screen/components/FieldMappingEditor.vue` |
| | `apps/forge-web/src/views/screen/components/HistoryToolbar.vue` |
| | `apps/forge-web/src/views/screen/components/TemplateSelector.vue` |
| | `apps/forge-web/src/views/screen/components/JsonSchemaForm.vue` |
| **8 核心卡片** | `apps/forge-web/src/views/screen/cards/types.ts` |
| | `apps/forge-web/src/views/screen/cards/registry.ts` |
| | `apps/forge-web/src/views/screen/cards/digital-number/index.vue` |
| | `apps/forge-web/src/views/screen/cards/digital-number/ScrollNumber.vue` |
| | `apps/forge-web/src/views/screen/cards/line-chart/index.vue` |
| | `apps/forge-web/src/views/screen/cards/bar-chart/index.vue` |
| | `apps/forge-web/src/views/screen/cards/pie-chart/index.vue` |
| | `apps/forge-web/src/views/screen/cards/scroll-table/index.vue` |
| | `apps/forge-web/src/views/screen/cards/text-board/index.vue` |
| | `apps/forge-web/src/views/screen/cards/map-chart/index.vue` |
| | `apps/forge-web/src/views/screen/cards/gauge/index.vue` |
| **4 装饰** | `apps/forge-web/src/views/screen/decorations/TechBorder.vue` |
| | `apps/forge-web/src/views/screen/decorations/DecorationCorner.vue` |
| | `apps/forge-web/src/views/screen/decorations/TechTitle.vue` |
| | `apps/forge-web/src/views/screen/decorations/RadarBackground.vue` |
| **视图** | `apps/forge-web/src/views/screen/index/index.vue` |
| | `apps/forge-web/src/views/screen/render/index.vue` |
| | `apps/forge-web/src/views/screen/preview/index.vue` |
| | `apps/forge-web/src/views/screen/editor/index.vue` |
| | `apps/forge-web/src/views/screen/data-source/index.vue` |
| | `apps/forge-web/src/views/screen/data-source/editor.vue` |
| **模板** | `apps/forge-web/src/views/screen/templates/index.ts` |
| **路由** | `apps/forge-web/src/router/constants.ts`（追加 3 条 CONSTANT_ROUTES） |
| **样式入口** | `apps/forge-web/src/styles/screen.scss`（仅 @import themes/screen） |
| **E2E** | `apps/forge-web/e2e/screen/render.spec.ts` |
| | `apps/forge-web/e2e/screen/editor.spec.ts` |
| **依赖** | `apps/forge-web/package.json` 新增 `echarts` / `grid-layout-plus` / `@vueuse/core` |

### 3.2 8 个内置卡片规格

| type | 组件 | 关键 props (options) | dataShape |
|------|------|---------------------|-----------|
| `digital-number` | DigitalNumberCard | `{ prefix, suffix, decimals, duration, color, fontSize }` | `{ value: number }` |
| `line-chart` | LineChartCard | `{ xField, yField, series, smooth, area, color }` | `{ rows: [{ x: string, [yField]: number }] }` |
| `bar-chart` | BarChartCard | `{ xField, yField, stack, horizontal, color }` | `{ rows: [{ x: string, [yField]: number }] }` |
| `pie-chart` | PieChartCard | `{ nameField, valueField, radius, roseType }` | `{ rows: [{ [nameField]: string, [valueField]: number }] }` |
| `scroll-table` | ScrollTableCard | `{ columns: [{ field, title, width }], rowHeight, scrollSpeed }` | `{ rows: any[] }` |
| `text-board` | TextBoardCard | `{ value, unit, label, color, fontSize, align }` | `{ value: string | number }` |
| `map-chart` | MapChartCard | `{ nameField, valueField, colorRange }` | `{ rows: [{ name, value }] }`（简化版中国地图） |
| `gauge` | GaugeCard | `{ min, max, value, unit, color }` | `{ value: number }` |

### 3.3 6 个预设模板

| 模板 code | 用途 | 包含卡片 |
|----------|------|---------|
| `operations` | 运营总览 | 4 digital-number + 2 line-chart + 1 bar-chart + 1 pie-chart + 1 scroll-table |
| `sales` | 销售分析 | 4 digital-number + 2 bar-chart + 1 line-chart + 1 pie-chart + 1 scroll-table |
| `iot` | 物联网监控 | 4 digital-number + 2 line-chart + 1 gauge + 1 map-chart + 1 scroll-table |
| `hr` | 人力资源 | 3 digital-number + 2 pie-chart + 1 bar-chart + 1 scroll-table + 1 text-board |
| `finance` | 财务驾驶舱 | 4 digital-number + 2 line-chart + 1 pie-chart + 1 bar-chart + 1 scroll-table |
| `blank` | 空白 | 1 text-board 占位 |

---

## 4. 关键设计决策

### 4.1 JsonSchemaForm 自实现（替代 @form-create）

**为什么替换**：@form-create/element-ui 对 Element Plus 3 兼容性未验证；自实现 4 种 widget 足够覆盖卡片配置场景，体积 ~5KB。

**Props 契约**：

```ts
defineProps<{
  schema: JSONSchema7
  modelValue: unknown
  uiSchema?: Record<string, {
    widget: 'input' | 'input-number' | 'select' | 'switch'
    options?: { label: string; value: unknown }[]
  }>
}>()
defineEmits<{ (e: 'update:modelValue', v: unknown): void }>()
```

**Widget 路由**：

| schema.type | format | enum | uiSchema 覆盖 | 渲染为 |
|------------|--------|------|--------------|--------|
| string | - | - | - | `<el-input>` |
| string | textarea | - | - | `<el-input type="textarea">` |
| string | - | 存在 | - | `<el-select>`（options = enum） |
| string | - | - | `{ widget: 'select', options: [...] }` | `<el-select>` |
| number/integer | - | - | - | `<el-input-number>` |
| boolean | - | - | - | `<el-switch>` |
| 未知 | - | - | - | 降级 `<el-input>` 字符串（绝不抛错） |

**卡片 metadata 集成**：每张卡片在 `cardRegistry.register({ meta: { configSchema, ... } })` 时声明 schema 与 uiSchema。

```ts
// cards/line-chart/index.vue 注册示例
const LineChartCard: ScreenCardComponent = {
  type: 'line-chart',
  component: LineChart,
  meta: {
    title: '折线图',
    icon: 'TrendCharts',
    defaultProps: { smooth: true, area: true, color: '#1e88e5' },
    configSchema: {
      type: 'object',
      properties: {
        xField: { type: 'string', title: 'X 字段' },
        yField: { type: 'string', title: 'Y 字段' },
        smooth: { type: 'boolean', title: '平滑曲线' },
        area: { type: 'boolean', title: '面积填充' },
        color: { type: 'string', title: '颜色' }
      },
      required: ['xField', 'yField']
    },
    uiSchema: {
      color: { widget: 'select', options: [
        { label: '科技蓝', value: '#1e88e5' },
        { label: '青色', value: '#26c6da' },
        { label: '橙色', value: '#ffa726' }
      ]}
    },
    dataShape: {
      fields: [
        { name: 'x', type: 'string' },
        { name: 'y', type: 'number' }
      ],
      sample: { x: '1月', y: 100 }
    },
    minWidth: 4,
    minHeight: 3
  }
}
```

### 4.2 撤销/重做（结构化克隆，不用 immer）

**理由**：`structuredClone` 浏览器原生（Chrome 98+、Safari 15.4+、Firefox 94+），零依赖；引入 immer 多 ~7KB gzip。

**Store 设计**：

```ts
// stores/screenEditor.ts
export const useScreenEditorStore = defineStore('screenEditor', () => {
  const draft = ref<ScreenConfig>({ version: 1, theme: 'dark-tech', cards: [] })
  const undoStack = ref<ScreenConfig[]>([])
  const redoStack = ref<ScreenConfig[]>([])
  const MAX_HISTORY = 50

  function commit(next: ScreenConfig) {
    undoStack.value.push(structuredClone(draft.value))
    if (undoStack.value.length > MAX_HISTORY) undoStack.value.shift()
    redoStack.value = []
    draft.value = structuredClone(next)
  }

  function undo() {
    const prev = undoStack.value.pop()
    if (!prev) return
    redoStack.value.push(structuredClone(draft.value))
    draft.value = prev
  }

  function redo() {
    const next = redoStack.value.pop()
    if (!next) return
    undoStack.value.push(structuredClone(draft.value))
    draft.value = next
  }

  return { draft, undoStack, redoStack, commit, undo, redo }
})
```

**触发 commit 的位置**：
- 卡片拖动结束（grid-layout-plus 的 `@layout-updated`）
- 卡片属性修改（PropertyPanel v-model）
- 卡片删除
- 新增卡片（从 CardPanel 拖入）
- 主题切换

**useScreenHistory 封装**：

```ts
// composables/useScreenHistory.ts
export function useHistory() {
  const store = useScreenEditorStore()
  return {
    canUndo: computed(() => store.undoStack.length > 0),
    canRedo: computed(() => store.redoStack.length > 0),
    undo: () => store.undo(),
    redo: () => store.redo(),
    commit: (next: ScreenConfig) => store.commit(next)
  }
}
```

### 4.3 卡片注册中心契约

```ts
// views/screen/cards/registry.ts
export interface Registry<T> {
  register(entry: T): void
  get(type: string): T | undefined
  list(): T[]
  has(type: string): boolean
}

export function createRegistry<T extends { type: string }>(): Registry<T> {
  const map = new Map<string, T>()
  return {
    register(entry) {
      if (map.has(entry.type)) throw new Error(`Card type already registered: ${entry.type}`)
      map.set(entry.type, entry)
    },
    get(type) { return map.get(type) },
    list() { return Array.from(map.values()) },
    has(type) { return map.has(type) }
  }
}

export const cardRegistry: Registry<ScreenCardComponent> = createRegistry()
export function registerBuiltinCards() {
  cardRegistry.register(DigitalNumberCard)
  cardRegistry.register(LineChartCard)
  cardRegistry.register(BarChartCard)
  cardRegistry.register(PieChartCard)
  cardRegistry.register(ScrollTableCard)
  cardRegistry.register(TextBoardCard)
  cardRegistry.register(MapChartCard)
  cardRegistry.register(GaugeCard)
}
```

**调用约定**：
- 渲染页入口 `views/screen/render/index.vue`：`onMounted` 调用 `registerBuiltinCards()` 后再渲染
- 编辑器入口 `views/screen/editor/index.vue`：同上
- **不**在 `main.ts` 调用：保持懒加载，避免影响首屏

### 4.4 ECharts 按需引入

每张图表卡片顶部：

```ts
// cards/line-chart/index.vue
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
```

体积估算：line/bar/pie 共享 core+renderers~120KB + 各自 chart~30KB，总 < 350KB gzip。

### 4.5 主题切换

```ts
// themes/screen/index.ts
const THEME_CLASS_NAMES: Record<ScreenTheme, string> = {
  'dark-tech': 'screen-theme-dark-tech',
  'blue-deep': 'screen-theme-blue-deep',
  'black-gold': 'screen-theme-black-gold'
}

export function applyScreenTheme(name: ScreenTheme) {
  const root = document.body
  Object.values(THEME_CLASS_NAMES).forEach(c => root.classList.remove(c))
  root.classList.add(THEME_CLASS_NAMES[name])
}
```

**SCSS 结构**：
- `_shared.scss` 定义 mixin 和变量映射
- `_dark-tech.scss`、`_blue-deep.scss`、`_black-gold.scss` 各定义一组 CSS 变量
- `styles/screen.scss` 在 `main.ts` 全局 `@import`

```scss
// _dark-tech.scss
body.screen-theme-dark-tech {
  --screen-bg: #0a1929;
  --screen-bg-elevated: #0d2540;
  --screen-accent: #1e88e5;
  --screen-accent-2: #26c6da;
  --screen-text-primary: #e3f2fd;
  --screen-text-secondary: #8a96a8;
  --screen-border: rgba(30, 136, 229, 0.3);
}
```

### 4.6 路由 + 动态 view 权限

| 路径 | 来源 | 权限 |
|------|------|------|
| `/screen/list` | 后端 sys_menu 动态注入 | `screen:screen:list` |
| `/screen/editor/:code` | `CONSTANT_ROUTES` 静态 | `screen:screen:edit`（前端判断按钮可见性） |
| `/screen/preview/:code` | `CONSTANT_ROUTES` 静态 | `screen:screen:edit`（编辑者才能预览） |
| `/screen/:code` | 后端 sys_menu.routePath 动态注入 | `screen:screen:view:{code}`（后端 @PreAuthorize 校验） |

**CONSTANT_ROUTES 新增**（在 `router/constants.ts`）：

```ts
{
  path: '/screen/editor/:code',
  component: () => import('@/views/screen/editor/index.vue'),
  meta: { title: '大屏编辑器', hidden: true, requiresAuth: true }
},
{
  path: '/screen/preview/:code',
  component: () => import('@/views/screen/preview/index.vue'),
  meta: { title: '大屏预览', hidden: true, requiresAuth: true }
}
```

**渲染页错误处理**：
- 后端 401/403 → `ElMessage.error('无权访问该大屏')` + `router.push('/screen/list')`
- 后端 404 → `ElMessage.error('大屏不存在或已删除')` + `router.push('/screen/list')`
- 500 → `ElMessage.error('大屏加载失败')` + 显示空状态

### 4.7 错误降级（4 层兜底）

| Level | 触发 | 兜底 |
|-------|------|------|
| 1 | 单卡片 render 抛错 | `CardErrorBoundary` 显示「数据加载失败，5s 后重试」+ 手动重试按钮 |
| 2 | 数据源 execute 失败 | `useCardDataSource` 标记 error，卡片显示「暂无数据」 |
| 3 | 大屏整体加载失败 | 渲染页 catch → 友好空状态 + 跳回列表 |
| 4 | API 401/403/500 | Axios 拦截器（`utils/request.ts`，已有） |

**强制规范**：所有 catch 必须 `console.error(err)` + `ElMessage.error(用户可读消息)`，禁止 `catch {}`。

---

## 5. 数据流

### 5.1 渲染页数据流

```
/screen/:code
  ↓ useRoute().params.code
  ↓ getScreenByCode(code) → HTTP GET /api/screen/code/operations
  ↓ 后端 @PreAuthorize('hasAuthority(screen:screen:view:operations)')
  ↓
ScreenResponse { config: '<JSON string>', configDraft: '...', theme: 'dark-tech' }
  ↓ screenStore.setActive(response) → JSON.parse(config) 一次
  ↓ applyScreenTheme(response.theme) → body.className
  ↓
<ScreenRenderer :config="screenStore.active">
  ↓ for each card in config.cards:
    <CardErrorBoundary :on-retry="() => cardRefresh(card)">
      <LineChart :options="card.options" :data-source-id="card.dataSourceId" />
        ↓ useCardDataSource(card.dataSourceId, params)
        ↓ executeDataSource API (POST /api/screen/data-source/execute/{id})
        ↓ echarts.setOption(processedData)
```

### 5.2 编辑器数据流

```
/screen/editor/:code
  ↓ getScreenDetail(id) → 读 configDraft（后端：未发布时 configDraft 反映最新编辑；已发布时读 config）
  ↓ screenEditorStore.load(draft)
  ↓
左侧 <CardPanel> + 中央 <GridLayout :layout="screenEditor.draft.cards"> + 右侧 <PropertyPanel :card="selected">
  用户操作：
    拖卡片到画布 → screenEditor.commit({ ...draft, cards: [...draft.cards, newCard] })
    拖动调整位置 → grid-layout @layout-updated → screenEditor.commit(...)
    PropertyPanel 修改 → JsonSchemaForm v-model → screenEditor.commit(...)
    删除卡片 → screenEditor.commit(...)
  顶部 <HistoryToolbar>：
    撤销 → store.undo()
    重做 → store.redo()
    保存 → updateScreen({ id, ..., configDraft: JSON.stringify(draft) })
    预览 → router.push('/screen/preview/' + code)
    发布 → publishScreen(code) + 提示「请到菜单管理为 view:{code} 权限分配角色」
```

---

## 6. 测试策略

### 6.1 Vitest 单元测试

**目标**：≥ 40 个 `it()` 用例。

| Task | 测试文件 | 用例数（最低） |
|------|---------|---------------|
| 1 | `views/screen/components/__tests__/CardErrorBoundary.test.ts` | 3 |
| 1 | `views/screen/cards/__tests__/registry.test.ts` | 6 |
| 2 | `api/screen/__tests__/screen.test.ts` | 14 |
| 3 | `composables/__tests__/useScreenScale.test.ts` | 2 |
| 3 | `composables/__tests__/useCardDataSource.test.ts` | 3 |
| 3 | `composables/__tests__/useScreenHistory.test.ts` | 4 |
| 4 | `themes/screen/__tests__/applyScreenTheme.test.ts` | 3 |
| 5/6 | 每个卡片组件至少 1 个 happy path | 8 |
| 9 | `views/screen/templates/__tests__/templates.test.ts` | 2 |
| 10 | `views/screen/components/__tests__/PropertyPanel.test.ts` | 2 |
| 10 | `views/screen/components/__tests__/JsonSchemaForm.test.ts` | 4 |
| 合计 | | **51** |

**测试规范**：
- 文件位置：`__tests__/*.test.ts` 与源码同目录
- 命名：被测文件去后缀 + `.test.ts`
- 第三方库 mock：request / echarts / grid-layout-plus
- 覆盖率目标：composables / stores / utilities 100%，components 80%+

### 6.2 Playwright E2E + 视觉回归（Task 11）

**测试场景**：

```ts
// e2e/screen/render.spec.ts
test('渲染页：登录 → 进入列表 → 打开 operations 大屏 → 截图对比', async ({ page }) => {
  await loginAsAdmin(page)
  await page.goto('/screen/list')
  await page.click('text=运营总览')
  await page.waitForSelector('.echarts')           // 等待 ECharts 渲染
  await page.waitForTimeout(1000)                   // 动画稳定
  await expect(page).toHaveScreenshot('operations-dark-tech.png', { fullPage: true })
})

// e2e/screen/editor.spec.ts
test('编辑器：从 CardPanel 拖 line-chart 到画布 → 修改 title → 保存 → 截图', async ({ page }) => {
  await loginAsAdmin(page)
  await page.goto('/screen/editor/operations')
  await page.waitForSelector('.card-panel-item:has-text("折线图")')
  // 模拟拖拽（grid-layout-plus 支持 programmatic add）
  await page.click('button:has-text("+折线图")')
  await page.waitForSelector('.property-panel')
  await page.fill('input[aria-label="标题"]', '测试折线图')
  await page.click('button:has-text("保存")')
  await expect(page).toHaveScreenshot('editor-with-linechart.png', { fullPage: true })
})
```

**视觉基线**：
- 3 主题 × 1 模板 = 3 张渲染页基线
- 1 编辑器空状态基线
- 1 编辑器含 1 卡片基线
- **合计 5 张基线**，保存到 `e2e/screen/__screenshots__/`

### 6.3 验收门（每个 Task 完成时）

```bash
cd apps/forge-web
pnpm lint                       # ESLint 0 error
pnpm test:run                   # Vitest 全绿
pnpm build                      # tsc + vite build 通过
```

---

## 7. 实施计划（11 Task 顺序）

| # | Task 名 | 估时 | 关键产物 |
|---|---------|------|---------|
| 1 | 基础抽象 | 0.5d | types/constants/registry/CardErrorBoundary + 9 单测 |
| 2 | API 模块 | 0.5d | screenApi + dataSourceApi + 14 单测 |
| 3 | composables | 1d | useScreenScale / useCardDataSource / useScreenHistory + 9 单测 |
| 4 | 主题系统 | 0.5d | 3 SCSS 主题 + applyScreenTheme + 3 单测 |
| 5 | 6 核心卡片 | 2d | digital-number / line / bar / pie / scroll-table / text-board + 6 单测 |
| 6 | 装饰 + map/gauge | 1.5d | 4 装饰 + 2 图表 + 2 单测 |
| 7 | 大屏列表页 | 1.5d | CRUD + 复制 + 发布 + 新建选模板 |
| 8 | 数据源管理 | 1.5d | 列表 + 编辑（HTTP/SQL） + 字段映射预览 |
| 9 | ScreenRenderer + 渲染/预览/模板 | 2d | 渲染页 + 预览页 + 6 预设模板 + 3 单测 |
| 10 | 拖拽编辑器 | 3d | grid-layout-plus + 撤销/重做 + PropertyPanel + FieldMapping + TemplateSelector + 6 单测 |
| 11 | 路由 + Playwright | 1.5d | CONSTANT_ROUTES + 2 E2E + 5 视觉基线 |
| **合计** | | **15 工作日** | |

**执行方式**：使用 `superpowers:subagent-driven-development` skill（推荐）或 `superpowers:executing-plans` skill，每个 Task 单独 commit。

---

## 8. 风险与已知限制

| 风险 | 触发 | 缓解 |
|------|------|------|
| grid-layout-plus 与 Vue 3.4 兼容 | Task 10 | Task 1-9 不依赖；如有问题降级为 vue-draggable-next + 自实现 grid 定位 |
| ECharts 按需引入漏掉组件 | Task 5/6 | CI 加 bundle size 检查（vite-plugin-bundle-analyzer） |
| 大屏 JSON 体积大（>100KB）编辑卡顿 | Task 10 | 撤销栈上限 50；如果还卡，把 structuredClone 改为节流版本（仅 500ms 内的多次 commit 合并） |
| 动态 view 权限未自动建菜单 | 后端已知 | 编辑器发布成功后给操作员提示「请到菜单管理为 view:{code} 添加按钮权限并分配角色」 |
| 渲染页刷新频次过高内存增长 | Task 9 | useCardDataSource 用 AbortController + 组件 onUnmounted 取消所有 in-flight 请求 |
| 6 模板 8 卡片 3 主题 累计 8000+ 行代码 | 整体 | 任务 11 加 ESLint + bundle 体积检查 |

---

## 9. 验收清单（完成时核对）

- [ ] 11 个 Task 全部 commit，commit message 中文，类型前缀正确（feat/fix/docs/test/refactor）
- [ ] `apps/forge-web/src/views/screen/` 目录存在，4 个核心流程页面可访问
- [ ] 大屏列表：能看 / 新增 / 编辑 / 删除 / 复制 / 发布
- [ ] 大屏渲染页：领导投屏能正常显示（1920×1080 等比缩放）
- [ ] 大屏编辑器：拖卡片 / 调位置 / 配置属性 / 撤销重做 / 保存 / 预览
- [ ] 数据源管理：CRUD + execute 测试
- [ ] 3 主题可切换
- [ ] 6 模板可一键应用
- [ ] Vitest ≥ 40 个用例全绿
- [ ] Playwright 视觉基线存在
- [ ] `pnpm lint` 0 error
- [ ] `pnpm build` 通过
- [ ] 后端 `SCREEN-MODULE.md` 中"动态 view 权限自动建菜单"限制保留文档说明

---

## 10. 后续可选（不在本次范围）

- 大屏导出 PDF / 图片
- 大屏多租户隔离
- 大屏协同编辑（多人同时编辑 + 锁）
- 大屏订阅 / 告警
- 大屏移动端适配
- 卡片市场（卡片包发布与共享）
