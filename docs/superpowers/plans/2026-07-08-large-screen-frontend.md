# 大屏展示系统 - 前端实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**上游设计文档：**
- 总体设计：[`../specs/2026-07-08-large-screen-frontend-design.md`](../specs/2026-07-08-large-screen-frontend-design.md)
- 详细 step-by-step（代码层）：[`2026-07-06-large-screen-frontend.md`](2026-07-06-large-screen-frontend.md)（4458 行，11 Task 完整 TDD 步骤）
- 后端运行手册：`apps/forge-server/docs/SCREEN-MODULE.md`

**Goal:** 实现 forge-web 端的大屏展示系统前端：基于配置的渲染页、配置驱动的拖拽编辑器、卡片组件注册中心、8 个核心卡片 + 4 个装饰组件、3 套大屏主题、6 个预设模板、数据源管理 UI、撤销/重做、错误降级。

**Architecture:** 配置驱动 + 组件注册中心模式。大屏 `config`（JSON）由后端存储，前端通过 `cardRegistry` 解码 `type → 组件` 渲染；`ScreenRenderer` 统一调度每张卡片的数据加载（缓存/节流/重试）；渲染页使用 `useScreenScale` 做 1920×1080 等比缩放；编辑器基于 `grid-layout-plus`（Vue 3 拖拽库）+ 自实现 `JsonSchemaForm` 配置面板；撤销/重做由 Pinia + `structuredClone` 快照栈实现；`CardErrorBoundary` 兜底每张卡片，不影响其他卡片渲染。

**Tech Stack:**
- Vue 3.4 + TypeScript 5.3 + Vite 5
- Pinia 2（store + 撤销/重做快照）
- Element Plus 2.4 + vxe-table 4.9
- echarts 5（按需引入，`echarts/core` + 显式 `use([...])`）
- grid-layout-plus 1.x（Vue 3 拖拽；vue-grid-layout 不支持 Vue 3）
- 自实现 `JsonSchemaForm`（4 widget：input / input-number / select / switch；支持 `uiSchema` 覆盖；**不引入 @form-create**）
- @vueuse/core（useDebounceFn / useIntervalFn）
- nanoid 5（卡片 ID）
- dayjs 1.11（日期格式化）
- Vitest 1.6 + @vue/test-utils 2 + happy-dom 20（单测）
- msw 2.x（API mock）
- Playwright（E2E + 视觉回归；通过 `mcp__plugin_playwright_playwright__*`）

## Global Constraints

- 前端基线：Vue 3.4 + TypeScript + Vite 5，ESLint 必须 0 error
- 端口：前端 `3003`，后端 API `8181`，上下文 `/api`（前端 `baseURL=/admin-api`）
- 包前缀：`com.forge.modules.screen`（后端）；前端模块路径 `apps/forge-web/src/views/screen/`
- 权限字符串：
  - 大屏列表 `screen:screen:list`
  - 大屏编辑 `screen:screen:edit`（编辑器入口）
  - 大屏查看 `screen:screen:view:{code}`（运行时由后端动态校验，**前端只做路由存在性检查**）
  - 大屏发布 `screen:screen:publish`、复制 `screen:screen:copy`、删除 `screen:screen:remove`、新增 `screen:screen:add`、修改 `screen:screen:edit`
  - 数据源 `screen:data-source:{list|query|add|edit|remove|execute}`
- 设计稿画布：**固定 1920×1080**，所有定位用绝对像素；运行时由 `useScreenScale` 等比缩放到视口
- 卡片网格：24 列宽（grid-layout-plus 的 `col-num`），最小卡片 4×3，最大 24×24
- 后端返回的 `config` / `configDraft` 是 JSON 字符串，**前端必须 `JSON.parse` 二次包装**为对象（后端实体字段是 String）
- 提交信息：中文 `<type>(<scope>): <subject>`，**禁止** `Co-Authored-By`
- 测试框架：Vitest + @vue/test-utils，文件与源码同目录 `__tests__/`，命名 `*.test.ts` 或 `*.test.tsx`
- 状态：撤销/重做用 Pinia + **`structuredClone`**（浏览器原生；不引入 immer；保持 deps 精简）
- 类型严格度：禁用 `any`（除第三方回调无法签名时），所有公开 API 必须导出 TypeScript 类型
- 国际化：暂不接入 i18n；文案中文写死
- ECharts 引入：必须按需，禁止 `import * as echarts from 'echarts'`；体积控制在 ~300KB gzip
- 路由模式：列表页走菜单动态路由；`/screen/editor/:code` 走静态路由（`CONSTANT_ROUTES`）；`/screen/:code` 渲染页由每张大屏在 `sys_menu` 注册独立 `routePath`（如 `/screen/operations`），后端按 code 校验 `hasAuthority('screen:screen:view:' + #code)`
- 错误处理：所有 catch 必须 `console.error` 并显示给用户（`ElMessage.error`）；禁止静默吞错

---

## 与原始 plan 的差异（必须应用）

| # | 位置 | 原 plan | 新 plan（采用此版本） | 原因 |
|---|------|---------|---------------------|------|
| 1 | Tech Stack（line 15） | `@form-create/element-ui 3.x` | **删除** | 改用自实现 JsonSchemaForm，规避 Element Plus 3 兼容性 |
| 2 | Task 10 JsonSchemaForm Step 3（line 3523） | 仅 4 类型（text/number/boolean/enum），不支持 uiSchema 覆盖 | **扩展**：增加 `uiSchema` props + select widget（`string` + `enum` 或 uiSchema.widget='select'）+ widget 路由表（详见下文 §"JsonSchemaForm v2 完整代码"） | 新设计 §4.1 要求 |
| 3 | Task 10 stores/screenEditor.ts Step 2（line 3452） | `JSON.parse(JSON.stringify(config.value))` 快照 | **替换**为 `structuredClone(config.value)` | 新设计 §4.2 要求；浏览器原生更高效 |
| 4 | 备注（line 4440） | "JSONSchema 表单覆盖度"备注提到 `@form-create/element-ui` 已在 deps | **改写**：删 `@form-create` 引用；说明"使用自实现 JsonSchemaForm，4 widget + uiSchema 覆盖" | 配套 #1 |

**应用方法**：执行 Task 1-9 时直接使用原 plan；执行 Task 10 时 **Step 2/3 用下文 §"JsonSchemaForm v2 完整代码" + §"screenEditor store v2 完整代码" 替换**。

---

## JsonSchemaForm v2 完整代码

**完整覆盖** 4 widget（input / input-number / select / switch）+ `uiSchema` 覆盖 + 未知类型降级为 input 字符串。

**Props 契约**（来自新设计 §4.1）：

```ts
defineProps<{
  schema: JSONSchema7
  modelValue: Record<string, unknown>
  uiSchema?: Record<string, {
    widget: 'input' | 'input-number' | 'select' | 'switch'
    options?: { label: string; value: unknown }[]
  }>
}>()
defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()
```

**Widget 路由规则**：

| schema.type | format | enum | uiSchema | 渲染为 |
|------------|--------|------|---------|--------|
| string | - | - | - | `<el-input>` |
| string | textarea | - | - | `<el-input type="textarea">` |
| string | - | 存在 | - | `<el-select>`（options = enum） |
| string | - | - | `{ widget: 'select' }` | `<el-select>` |
| number/integer | - | - | - | `<el-input-number>` |
| boolean | - | - | - | `<el-switch>` |
| 未知 | - | - | - | 降级 `<el-input>` + 字符串（绝不抛错） |

**完整代码**（替换原 plan Task 10 Step 3 的 JsonSchemaForm.vue）：

```vue
<!-- apps/forge-web/src/views/screen/components/JsonSchemaForm.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import type { JSONSchema7 } from 'json-schema'

type Widget = 'input' | 'input-number' | 'select' | 'switch'
type UiSchema = Record<string, { widget: Widget; options?: { label: string; value: unknown }[] }>

const props = defineProps<{
  schema: JSONSchema7
  modelValue: Record<string, unknown>
  uiSchema?: UiSchema
}>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const properties = computed(() => {
  const p = props.schema.properties ?? {}
  return Object.entries(p).map(([key, def]) => ({ key, def: def as JSONSchema7 }))
})

const update = (key: string, val: unknown) => {
  emit('update:modelValue', { ...props.modelValue, [key]: val })
}

const resolveWidget = (key: string, def: JSONSchema7): Widget => {
  const override = props.uiSchema?.[key]?.widget
  if (override) return override
  if (def.type === 'boolean') return 'switch'
  if (def.type === 'number' || def.type === 'integer') return 'input-number'
  if (def.enum || Array.isArray(def.enum)) return 'select'
  return 'input'
}

const selectOptions = (key: string, def: JSONSchema7): { label: string; value: unknown }[] => {
  const override = props.uiSchema?.[key]?.options
  if (override) return override
  return (def.enum ?? []).map((v) => ({ label: String(v), value: v }))
}

const stringValue = (key: string, def: JSONSchema7): string => {
  const v = props.modelValue[key]
  if (v === undefined || v === null) return typeof def.default === 'string' ? def.default : ''
  return String(v)
}
</script>

<template>
  <el-form label-width="100px" size="small">
    <el-form-item v-for="p in properties" :key="p.key" :label="p.def.title || p.key">
      <el-input
        v-if="resolveWidget(p.key, p.def) === 'input' && p.def.format !== 'textarea'"
        :model-value="stringValue(p.key, p.def)"
        @update:model-value="v => update(p.key, v)"
      />
      <el-input
        v-else-if="resolveWidget(p.key, p.def) === 'input'"
        type="textarea"
        :rows="3"
        :model-value="stringValue(p.key, p.def)"
        @update:model-value="v => update(p.key, p.key, v)"
      />
      <el-input-number
        v-else-if="resolveWidget(p.key, p.def) === 'input-number'"
        :model-value="Number(props.modelValue[p.key] ?? p.def.default ?? 0)"
        :min="p.def.minimum" :max="p.def.maximum" :step="p.def.type === 'integer' ? 1 : 0.1"
        @update:model-value="v => update(p.key, v)"
      />
      <el-switch
        v-else-if="resolveWidget(p.key, p.def) === 'switch'"
        :model-value="Boolean(props.modelValue[p.key] ?? p.def.default ?? false)"
        @update:model-value="v => update(p.key, v)"
      />
      <el-select
        v-else-if="resolveWidget(p.key, p.def) === 'select'"
        :model-value="props.modelValue[p.key] ?? p.def.default"
        @update:model-value="v => update(p.key, v)"
      >
        <el-option
          v-for="o in selectOptions(p.key, p.def)"
          :key="String(o.value)"
          :label="o.label"
          :value="o.value"
        />
      </el-select>
    </el-form-item>
  </el-form>
</template>
```

**测试代码**（替换原 plan Task 10 Step 4 的 JsonSchemaForm.test.ts）：

```ts
// apps/forge-web/src/views/screen/components/__tests__/JsonSchemaForm.test.ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import JsonSchemaForm from '../JsonSchemaForm.vue'
import type { JSONSchema7 } from 'json-schema'

const stringSchema: JSONSchema7 = {
  type: 'object',
  properties: { title: { type: 'string', title: '标题' } }
}

const numberSchema: JSONSchema7 = {
  type: 'object',
  properties: { count: { type: 'integer', title: '数量', minimum: 0, maximum: 100 } }
}

const booleanSchema: JSONSchema7 = {
  type: 'object',
  properties: { enabled: { type: 'boolean', title: '启用' } }
}

const enumSchema: JSONSchema7 = {
  type: 'object',
  properties: { color: { type: 'string', enum: ['red', 'blue', 'green'] } }
}

const uiSchema = {
  theme: {
    widget: 'select' as const,
    options: [
      { label: '暗色科技', value: 'dark-tech' },
      { label: '深空蓝', value: 'blue-deep' }
    ]
  }
}

describe('JsonSchemaForm', () => {
  it('string 字段渲染 el-input', () => {
    const w = mount(JsonSchemaForm, { props: { schema: stringSchema, modelValue: { title: 'Hello' } } })
    expect(w.find('input[type="text"]').exists()).toBe(true)
  })

  it('number 字段渲染 el-input-number', () => {
    const w = mount(JsonSchemaForm, { props: { schema: numberSchema, modelValue: { count: 5 } } })
    expect(w.find('.el-input-number').exists()).toBe(true)
  })

  it('boolean 字段渲染 el-switch', () => {
    const w = mount(JsonSchemaForm, { props: { schema: booleanSchema, modelValue: { enabled: true } } })
    expect(w.find('.el-switch').exists()).toBe(true)
  })

  it('enum 字段自动渲染 el-select', () => {
    const w = mount(JsonSchemaForm, { props: { schema: enumSchema, modelValue: { color: 'red' } } })
    expect(w.find('.el-select').exists()).toBe(true)
  })

  it('uiSchema.widget=select 强制渲染 select', () => {
    const schema: JSONSchema7 = {
      type: 'object',
      properties: { theme: { type: 'string', title: '主题' } }
    }
    const w = mount(JsonSchemaForm, {
      props: { schema, modelValue: { theme: 'dark-tech' }, uiSchema }
    })
    expect(w.find('.el-select').exists()).toBe(true)
  })

  it('未知类型降级为 el-input（不抛错）', () => {
    const schema: JSONSchema7 = {
      type: 'object',
      properties: { weird: { type: 'array' as any, title: '数组' } }
    }
    expect(() => {
      mount(JsonSchemaForm, { props: { schema, modelValue: { weird: 'fallback' } } })
    }).not.toThrow()
  })
})
```

---

## screenEditor store v2 完整代码

**用 `structuredClone` 替换 `JSON.parse(JSON.stringify(...))`**（替换原 plan Task 10 Step 2 的 stores/screenEditor.ts）：

```ts
// apps/forge-web/src/stores/screenEditor.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { nanoid } from 'nanoid'
import type { ScreenConfig, ScreenCard } from '@/types/screen'

const MAX_HISTORY = 50

export const useScreenEditorStore = defineStore('screenEditor', () => {
  const config = ref<ScreenConfig>({ version: 1, theme: 'dark-tech', cards: [] })
  const activeCardId = ref<string | null>(null)
  const undoStack = ref<ScreenConfig[]>([])
  const redoStack = ref<ScreenConfig[]>([])
  const isDirty = ref(false)
  const screenId = ref<number | null>(null)
  const screenCode = ref<string>('')

  const activeCard = computed<ScreenCard | null>(() =>
    config.value.cards.find(c => c.id === activeCardId.value) ?? null
  )

  const snapshot = (): ScreenConfig => structuredClone(config.value)

  const applyChange = (mutator: (draft: ScreenConfig) => void) => {
    undoStack.value.push(snapshot())
    if (undoStack.value.length > MAX_HISTORY) undoStack.value.shift()
    const next = snapshot()
    mutator(next)
    config.value = next
    redoStack.value = []
    isDirty.value = true
  }

  const undo = () => {
    if (undoStack.value.length === 0) return
    redoStack.value.push(snapshot())
    config.value = undoStack.value.pop()!
    isDirty.value = true
  }

  const redo = () => {
    if (redoStack.value.length === 0) return
    undoStack.value.push(snapshot())
    config.value = redoStack.value.pop()!
    isDirty.value = true
  }

  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  const addCard = (type: string, position: { x: number; y: number }) => {
    const newCard: ScreenCard = {
      id: nanoid(8),
      type, x: position.x, y: position.y, w: 6, h: 4,
      dataSourceId: null, refresh: 0, options: {}
    }
    applyChange(d => { d.cards.push(newCard) })
    activeCardId.value = newCard.id
  }

  const removeCard = (id: string) => {
    applyChange(d => { d.cards = d.cards.filter(c => c.id !== id) })
    if (activeCardId.value === id) activeCardId.value = null
  }

  const updateCard = (id: string, patch: Partial<ScreenCard>) => {
    applyChange(d => {
      const c = d.cards.find(x => x.id === id)
      if (c) Object.assign(c, patch)
    })
  }

  const markClean = () => { isDirty.value = false }

  const reset = () => {
    config.value = { version: 1, theme: 'dark-tech', cards: [] }
    activeCardId.value = null
    undoStack.value = []
    redoStack.value = []
    isDirty.value = false
  }

  return {
    config, activeCardId, activeCard, isDirty, screenId, screenCode,
    canUndo, canRedo,
    applyChange, undo, redo,
    addCard, removeCard, updateCard,
    markClean, reset
  }
})
```

---

## Task 1：基础抽象

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 1（line 136-517）](2026-07-06-large-screen-frontend.md#task-1-基础抽象types--cardregistry--carderrorboundary)

**Files:**
- Create: `apps/forge-web/src/types/screen.ts`
- Create: `apps/forge-web/src/constants/screen.ts`
- Create: `apps/forge-web/src/views/screen/cards/types.ts`
- Create: `apps/forge-web/src/views/screen/cards/registry.ts`
- Create: `apps/forge-web/src/views/screen/components/CardErrorBoundary.vue`
- Create: `apps/forge-web/src/views/screen/components/__tests__/CardErrorBoundary.test.ts`
- Create: `apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts`

**Interfaces:**
- Consumes: 无外部依赖
- Produces:
  - `types/screen.ts`：`ScreenConfig` / `ScreenCard` / `CardDataShape` / `FieldDef` / `ScreenCardComponent<TConfig, TData>` / `ScreenTheme` / `ScreenStatus`
  - `constants/screen.ts`：`SCREEN_BASE_WIDTH=1920` / `SCREEN_BASE_HEIGHT=1080` / `SCREEN_GRID_COLUMNS=24` / `SCREEN_MAX_CARD_ROWS=24` / `SCREEN_REFRESH_OPTIONS` / `SCREEN_DEFAULT_THEME='dark-tech'` / `SCREEN_THEMES` / `DATA_SOURCE_TYPE_HTTP/SQL`
  - `views/screen/cards/registry.ts`：`createRegistry<T>()` / `cardRegistry` / `registerBuiltinCards()`（Task 5/6 完成前 registerBuiltinCards 是空实现）
  - `views/screen/components/CardErrorBoundary.vue`：默认导出 `<CardErrorBoundary retryAfterMs onRetry>`，捕获子树错误并提供 5s 自动重试 + 手动按钮

**验证命令**：
```bash
cd apps/forge-web
pnpm test:run src/views/screen/components/__tests__/CardErrorBoundary.test.ts
pnpm test:run src/views/screen/cards/__tests__/registry.test.ts
```

**Commit**：
```bash
git add apps/forge-web/src/types/screen.ts \
        apps/forge-web/src/constants/screen.ts \
        apps/forge-web/src/views/screen/cards/types.ts \
        apps/forge-web/src/views/screen/cards/registry.ts \
        apps/forge-web/src/views/screen/components/CardErrorBoundary.vue \
        apps/forge-web/src/views/screen/components/__tests__/CardErrorBoundary.test.ts \
        apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts
git commit -m "feat(screen): 新增大屏基础抽象（types + cardRegistry + 卡片错误边界）"
```

---

## Task 2：大屏 API 模块

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 2（line 521-794）](2026-07-06-large-screen-frontend.md#task-2-大屏-api-模块screenapi--datasourceapi)

**Files:**
- Create: `apps/forge-web/src/api/screen/index.ts`
- Create: `apps/forge-web/src/api/screen/__tests__/screen.test.ts`

**Interfaces:**
- Consumes: `@/utils/request`（已存在）
- Produces（`api/screen/index.ts` exports）：
  - 类型：`ScreenListQuery` / `ScreenDetailResponse` / `ScreenCreateRequest` / `ScreenCopyRequest` / `ScreenDataSource` / `DataSourceListQuery` / `DataSourceExecuteRequest` / `DataSourceExecuteResponse`
  - API 对象：`screenApi`（page/get/getByCode/create/update/remove/publish/copy）、`dataSourceApi`（page/get/create/update/remove/execute）
  - 顶层函数：`getScreenList` / `getScreenDetail` / `getScreenByCode` / `createScreen` / `updateScreen` / `deleteScreen` / `publishScreen` / `copyScreen` / `getDataSourceList` / `getDataSourceDetail` / `createDataSource` / `updateDataSource` / `deleteDataSource` / `executeDataSource`

**验证命令**：
```bash
cd apps/forge-web
pnpm test:run src/api/screen/__tests__/screen.test.ts
# Expected: 14 passed
```

**Commit**：
```bash
git add apps/forge-web/src/api/screen/index.ts apps/forge-web/src/api/screen/__tests__/screen.test.ts
git commit -m "feat(screen): 新增大屏与数据源 API 模块"
```

---

## Task 3：composables

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 3（line 797-1197）](2026-07-06-large-screen-frontend.md#task-3-composablesusescreenscale--usecarddatasource--usescreenhistory)

**Files:**
- Modify: `apps/forge-web/package.json`（新增 `@vueuse/core`）
- Create: `apps/forge-web/src/composables/useScreenScale.ts`
- Create: `apps/forge-web/src/composables/useCardDataSource.ts`
- Create: `apps/forge-web/src/composables/useScreenHistory.ts`
- Create: 3 个 `__tests__/` 测试文件

**Interfaces:**
- Consumes: `executeDataSource`（Task 2）、`@vueuse/core`（新增依赖）、`SCREEN_BASE_WIDTH/HEIGHT`（Task 1）
- Produces:
  - `useScreenScale()` → `{ width, height, scale, containerStyle }`，`scale = min(vw/1920, vh/1080)`
  - `useCardDataSource(card)` → `{ data, loading, error, load, refresh, cancel }`；按 `card.refresh` 周期拉取；组件卸载时取消
  - `useScreenHistory(initial, { max })` → `{ state, canUndo, canRedo, commit, undo, redo, clear }`；`structuredClone` 实现快照

**验证命令**：
```bash
cd apps/forge-web
pnpm add @vueuse/core
pnpm test:run src/composables/__tests__/
# Expected: 4 + 4 + 6 = 14 passed
```

**Commit**：
```bash
git add apps/forge-web/package.json pnpm-lock.yaml \
        apps/forge-web/src/composables/useScreenScale.ts \
        apps/forge-web/src/composables/useScreenHistory.ts \
        apps/forge-web/src/composables/useCardDataSource.ts \
        apps/forge-web/src/composables/__tests__/useScreenScale.test.ts \
        apps/forge-web/src/composables/__tests__/useScreenHistory.test.ts \
        apps/forge-web/src/composables/__tests__/useCardDataSource.test.ts
git commit -m "feat(screen): 新增 useScreenScale / useCardDataSource / useScreenHistory 三个 composable"
```

---

## Task 4：大屏主题系统

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 4（line 1201-1418）](2026-07-06-large-screen-frontend.md#task-4-大屏主题系统3-套-screen-主题--pageconfig-集成)

**Files:**
- Create: `apps/forge-web/src/themes/screen/_shared.scss`
- Create: `apps/forge-web/src/themes/screen/_dark-tech.scss`
- Create: `apps/forge-web/src/themes/screen/_blue-deep.scss`
- Create: `apps/forge-web/src/themes/screen/_black-gold.scss`
- Create: `apps/forge-web/src/themes/screen/index.ts`
- Create: `apps/forge-web/src/themes/screen/__tests__/applyScreenTheme.test.ts`
- Modify: `apps/forge-web/src/styles/index.scss`（追加 `@import '@/themes/screen/index.scss'`）

**Interfaces:**
- Consumes: 无外部依赖
- Produces:
  - 3 套 SCSS 主题（`body.screen-theme-{name}` 切换）
  - `applyScreenTheme(name: ScreenTheme): void` 函数（添加/移除 body className）

**验证命令**：
```bash
cd apps/forge-web
pnpm test:run src/themes/screen/__tests__/applyScreenTheme.test.ts
# Expected: 3 passed
pnpm build
# Expected: dist 生成成功
```

**Commit**：
```bash
git add apps/forge-web/src/themes/screen/ apps/forge-web/src/styles/index.scss
git commit -m "feat(screen): 新增 3 套大屏主题 + applyScreenTheme"
```

---

## Task 5：6 个核心卡片（digital-number / line / bar / pie / scroll-table / text-board）

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 5（line 1420-1953）](2026-07-06-large-screen-frontend.md#task-5-6-个核心卡片digital-number--line--bar--pie--scroll-table--text-board)

**Files:**
- Create: `apps/forge-web/src/views/screen/cards/digital-number/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/digital-number/ScrollNumber.vue`
- Create: `apps/forge-web/src/views/screen/cards/line-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/bar-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/pie-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/scroll-table/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/text-board/index.vue`
- Modify: `apps/forge-web/src/views/screen/cards/registry.ts`（`registerBuiltinCards` 追加 6 个 import + register）
- Create: 6 个 `__tests__/`

**Interfaces:**
- Consumes: `useCardDataSource`（Task 3）、`echarts/core`（按需引入）、`ScreenCardComponent`（Task 1）
- Produces: 6 个 `ScreenCardComponent` 注册到 `cardRegistry`；每个组件 props 契约见新设计 §3.2 表格

**ECharts 按需引入**（关键约束）：
```ts
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])
```

**验证命令**：
```bash
cd apps/forge-web
pnpm test:run src/views/screen/cards/
# Expected: registry(6) + 各卡片至少 1 happy path = ≥ 12 passed
```

**Commit**：
```bash
git add apps/forge-web/src/views/screen/cards/
git commit -m "feat(screen): 新增 6 个核心卡片（digital-number/line/bar/pie/scroll-table/text-board）"
```

---

## Task 6：装饰组件 + 地图/仪表盘卡片

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 6（line 1955-2340）](2026-07-06-large-screen-frontend.md#task-6-装饰组件--地图仪表盘卡片)

**Files:**
- Create: `apps/forge-web/src/views/screen/decorations/TechBorder.vue`
- Create: `apps/forge-web/src/views/screen/decorations/DecorationCorner.vue`
- Create: `apps/forge-web/src/views/screen/decorations/TechTitle.vue`
- Create: `apps/forge-web/src/views/screen/decorations/RadarBackground.vue`
- Create: `apps/forge-web/src/views/screen/cards/map-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/gauge/index.vue`
- Modify: `apps/forge-web/src/views/screen/cards/registry.ts`（追加 map-chart / gauge 注册）
- Create: 2 个 `__tests__/`

**Interfaces:**
- Consumes: `echarts/core`（Task 5 引入）
- Produces: 2 个 ScreenCardComponent（map-chart / gauge）+ 4 个装饰组件

**验证命令**：
```bash
cd apps/forge-web
pnpm test:run src/views/screen/cards/map-chart/ src/views/screen/cards/gauge/
# Expected: ≥ 2 passed
```

**Commit**：
```bash
git add apps/forge-web/src/views/screen/decorations/ \
        apps/forge-web/src/views/screen/cards/map-chart/ \
        apps/forge-web/src/views/screen/cards/gauge/ \
        apps/forge-web/src/views/screen/cards/registry.ts
git commit -m "feat(screen): 新增 4 装饰组件 + map-chart / gauge 卡片"
```

---

## Task 7：大屏列表页

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 7（line 2342-2620）](2026-07-06-large-screen-frontend.md#task-7-大屏列表页crud--复制--发布--新建选模板)

**Files:**
- Create: `apps/forge-web/src/views/screen/index/index.vue`
- Create: `apps/forge-web/src/views/screen/components/TemplateSelector.vue`（先建组件，Task 10 也复用）

**Interfaces:**
- Consumes: `screenApi`（Task 2）、`publishScreen` / `copyScreen` / `createScreen` / `deleteScreen`、vxe-table 全局配置
- Produces: `<ScreenListPage>` 包含搜索栏 + vxe-table + 工具栏按钮（新增 / 编辑 / 删除 / 复制 / 发布）+ 新建时弹 `TemplateSelector`

**关键功能**：
- 列表分页（`vxe-table` + `el-pagination`）
- 新增按钮 → 弹 `TemplateSelector`（6 模板）→ 选完跳编辑器 `/screen/editor/new?template=operations`
- 编辑按钮 → 跳 `/screen/editor/{code}`
- 复制按钮 → 弹 `ElMessageBox` 输入 newCode/newName → `copyScreen` API
- 发布按钮 → `publishScreen` API + 提示「请到菜单管理为 view:{code} 权限分配角色」
- 删除按钮 → 二次确认 + `deleteScreen` API

**验证命令**：
```bash
cd apps/forge-web
pnpm dev
# 浏览器手动验证：登录 → /screen/list → 完整流程
```

**Commit**：
```bash
git add apps/forge-web/src/views/screen/index/ \
        apps/forge-web/src/views/screen/components/TemplateSelector.vue
git commit -m "feat(screen): 新增大屏列表页（CRUD + 复制 + 发布 + 新建选模板）"
```

---

## Task 8：数据源管理

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 8（line 2622-2965）](2026-07-06-large-screen-frontend.md#task-8-数据源管理列表--编辑--字段映射预览)

**Files:**
- Create: `apps/forge-web/src/views/screen/data-source/index.vue`
- Create: `apps/forge-web/src/views/screen/data-source/editor.vue`
- Create: `apps/forge-web/src/views/screen/data-source/__tests__/editor.test.ts`

**Interfaces:**
- Consumes: `dataSourceApi`（Task 2）、`executeDataSource`（测一次用）
- Produces:
  - `<DataSourceListPage>` 列表 + 增删改查
  - `<DataSourceEditor>` 编辑 HTTP/SQL 数据源（按 `dataSource.type` 切换表单）
  - "测一次"按钮 → `executeDataSource` 显示结果（前 5 行）

**关键功能**：
- 类型切换：HTTP → method/url/headers/params/timeout；SQL → sqlTemplate/paramSchema/maxRows
- 测试运行：调 `executeDataSource(id, { params })` → 显示响应前 5 行
- 保存：调 `createDataSource` / `updateDataSource`

**验证命令**：
```bash
cd apps/forge-web
pnpm test:run src/views/screen/data-source/
pnpm dev
# 浏览器手动验证：登录 → /screen/data-source → 增删改 + 测一次
```

**Commit**：
```bash
git add apps/forge-web/src/views/screen/data-source/
git commit -m "feat(screen): 新增数据源管理（HTTP/SQL 编辑 + 测一次）"
```

---

## Task 9：ScreenRenderer + 渲染页 + 预览页 + 6 预设模板

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 9（line 2967-3388）](2026-07-06-large-screen-frontend.md#task-9-screenrenderer--渲染页--预览页--6-预设模板)

**Files:**
- Create: `apps/forge-web/src/views/screen/components/ScreenRenderer.vue`
- Create: `apps/forge-web/src/views/screen/render/index.vue`
- Create: `apps/forge-web/src/views/screen/preview/index.vue`
- Create: `apps/forge-web/src/views/screen/templates/index.ts`
- Create: `apps/forge-web/src/views/screen/templates/__tests__/templates.test.ts`
- Create: `apps/forge-web/src/views/screen/stores/screen.ts`（运行时状态：active config + loading + error）
- Create: `apps/forge-web/src/views/screen/components/__tests__/ScreenRenderer.test.ts`

**Interfaces:**
- Consumes: `getScreenByCode`（Task 2）、`useScreenScale`（Task 3）、`applyScreenTheme`（Task 4）、`cardRegistry` + `registerBuiltinCards`（Task 5/6）、`useCardDataSource`（Task 3）
- Produces:
  - `useScreenStore()` → `{ active, loading, error, load, reset }`
  - `ScreenRenderer.vue` → 遍历 `config.cards` + 套 `CardErrorBoundary` + 按 `card.type` 从 `cardRegistry.get(type).component` 渲染
  - `render/index.vue` → `/screen/:code` 运行时页（`useScreenScale` 等比缩放 + 全屏）
  - `preview/index.vue` → `/screen/preview/:code` 读 `configDraft`（编辑预览）
  - `templates/index.ts` → 6 个预设模板（operations / sales / iot / hr / finance / blank）

**关键功能**：
- 渲染页：调 `getScreenByCode(code)` → 401/403 跳列表 + ElMessage.error；404 同；500 显示空状态
- 预览页：调 `getScreenDetail(id)` 读 `configDraft`（不调 `getScreenByCode`）
- 模板：每个模板返回 `ScreenConfig`，含若干 `ScreenCard`（位置 + 默认 options + 推荐 dataSourceId 列表）

**验证命令**：
```bash
cd apps/forge-web
pnpm test:run src/views/screen/templates/ src/views/screen/components/__tests__/ScreenRenderer.test.ts
pnpm dev
# 浏览器手动验证：登录 → /screen/operations → 等比缩放正常 + 卡片显示
```

**Commit**：
```bash
git add apps/forge-web/src/views/screen/components/ScreenRenderer.vue \
        apps/forge-web/src/views/screen/render/ \
        apps/forge-web/src/views/screen/preview/ \
        apps/forge-web/src/views/screen/templates/ \
        apps/forge-web/src/stores/screen.ts
git commit -m "feat(screen): 新增 ScreenRenderer + 渲染页/预览页 + 6 预设模板"
```

---

## Task 10：拖拽编辑器（**应用 §"JsonSchemaForm v2 完整代码" + §"screenEditor store v2 完整代码"**）

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 10（line 3390-4192）](2026-07-06-large-screen-frontend.md#task-10-拖拽编辑器vue-grid-layout--撤销重做--配置面板--字段映射--模板选择器)

**Files:**
- Modify: `apps/forge-web/package.json`（新增 `grid-layout-plus` + `nanoid`）
- Create: `apps/forge-web/src/stores/screenEditor.ts`（**用 v2 完整代码**）
- Create: `apps/forge-web/src/views/screen/components/JsonSchemaForm.vue`（**用 v2 完整代码**）
- Create: `apps/forge-web/src/views/screen/components/CardPanel.vue`
- Create: `apps/forge-web/src/views/screen/components/PropertyPanel.vue`
- Create: `apps/forge-web/src/views/screen/components/DataSourceBinder.vue`
- Create: `apps/forge-web/src/views/screen/components/FieldMappingEditor.vue`
- Create: `apps/forge-web/src/views/screen/components/HistoryToolbar.vue`
- Create: `apps/forge-web/src/views/screen/components/TemplateSelector.vue`（Task 7 已建，补充测试）
- Create: `apps/forge-web/src/views/screen/editor/index.vue`
- Create: 3 个 `__tests__/`

**Interfaces:**
- Consumes: `cardRegistry` / `registerBuiltinCards`（Task 5/6）、`getDataSourceList` / `executeDataSource` / `updateScreen` / `publishScreen`（Task 2）、`presetTemplates`（Task 9）、`useScreenStore`（Task 7）
- Produces:
  - `stores/screenEditor.ts`：`useScreenEditorStore()` 返回 `{ config, activeCard, isDirty, canUndo, canRedo, applyChange, undo, redo, addCard, removeCard, updateCard, markClean, reset }`
  - `<JsonSchemaForm :schema :modelValue :uiSchema @update:modelValue>`：4 widget + uiSchema 覆盖
  - `<CardPanel @select>`：左侧组件库（按 `cardRegistry.list()`）
  - `<PropertyPanel :card @update>`：右侧属性（标题 / 位置 / 数据源 / 刷新 / 字段映射）
  - `<DataSourceBinder :card @bind>`：选数据源 + 测一次
  - `<FieldMappingEditor :card :dataShape>`：xField / yField / valueField 自动推荐
  - `<HistoryToolbar :store>`：撤销/重做按钮 + 保存/预览/发布
  - `editor/index.vue`：三栏布局（CardPanel / 画布 / PropertyPanel），顶部 HistoryToolbar

**验证命令**：
```bash
cd apps/forge-web
pnpm add grid-layout-plus nanoid
pnpm test:run src/views/screen/components/__tests__/JsonSchemaForm.test.ts
pnpm test:run src/views/screen/components/__tests__/PropertyPanel.test.ts
pnpm test:run src/stores/__tests__/screenEditor.test.ts
pnpm dev
# 浏览器手动验证：登录 → /screen/editor/operations → 拖卡片 + 改属性 + 撤销/重做 + 保存
```

**Commit**：
```bash
git add apps/forge-web/package.json pnpm-lock.yaml \
        apps/forge-web/src/stores/screenEditor.ts \
        apps/forge-web/src/views/screen/components/JsonSchemaForm.vue \
        apps/forge-web/src/views/screen/components/CardPanel.vue \
        apps/forge-web/src/views/screen/components/PropertyPanel.vue \
        apps/forge-web/src/views/screen/components/DataSourceBinder.vue \
        apps/forge-web/src/views/screen/components/FieldMappingEditor.vue \
        apps/forge-web/src/views/screen/components/HistoryToolbar.vue \
        apps/forge-web/src/views/screen/editor/ \
        apps/forge-web/src/views/screen/components/__tests__/
git commit -m "feat(screen): 新增拖拽编辑器（grid-layout-plus + 撤销/重做 + JsonSchemaForm 配置面板）"
```

---

## Task 11：路由接入 + Playwright 视觉回归

**完整 step-by-step 详见原 plan**：[`2026-07-06-large-screen-frontend.md` Task 11（line 4194-4458）](2026-07-06-large-screen-frontend.md#task-11-路由接入--playwright-视觉回归)

**Files:**
- Modify: `apps/forge-web/src/router/constants.ts`（追加 3 条 CONSTANT_ROUTES）
- Create: `apps/forge-web/e2e/screen/render.spec.ts`
- Create: `apps/forge-web/e2e/screen/editor.spec.ts`
- Create: `apps/forge-web/e2e/screen/__screenshots__/`（5 张视觉基线）

**Interfaces:**
- Consumes: 全部前置 Task 产物
- Produces:
  - 3 条 CONSTANT_ROUTES：
    - `/screen/editor/:code`（编辑器）
    - `/screen/preview/:code`（预览）
    - `/screen/new`（新建，从查询参数 `?template=` 选模板）
  - 2 个 E2E 测试 + 5 张视觉基线

**视觉基线**（保存到 `e2e/screen/__screenshots__/`）：
1. `operations-dark-tech.png` - 暗色科技主题 + 运营总览模板
2. `operations-blue-deep.png` - 深空蓝主题 + 运营总览模板
3. `operations-black-gold.png` - 黑金主题 + 运营总览模板
4. `editor-empty.png` - 编辑器空状态
5. `editor-with-linechart.png` - 编辑器含 1 张折线图

**验证命令**：
```bash
cd apps/forge-web
pnpm lint                                  # 0 error
pnpm test:run                              # 全绿（≥ 51 用例）
pnpm build                                 # tsc + vite build 通过
# Playwright 视觉回归
npx playwright test e2e/screen/
```

**Commit**：
```bash
git add apps/forge-web/src/router/constants.ts \
        apps/forge-web/e2e/screen/
git commit -m "feat(screen): 路由接入（3 条静态路由） + Playwright 视觉回归"
```

---

## 验收清单（最终核对）

- [ ] 11 个 Task 全部 commit，commit message 中文，类型前缀正确（feat/fix/docs/test/refactor）
- [ ] `apps/forge-web/src/views/screen/` 目录存在，4 个核心流程页面可访问
- [ ] 大屏列表：能看 / 新增 / 编辑 / 删除 / 复制 / 发布
- [ ] 大屏渲染页：领导投屏能正常显示（1920×1080 等比缩放）
- [ ] 大屏编辑器：拖卡片 / 调位置 / 配置属性 / 撤销重做 / 保存 / 预览
- [ ] 数据源管理：CRUD + execute 测试
- [ ] 3 主题可切换
- [ ] 6 模板可一键应用
- [ ] Vitest ≥ 51 个用例全绿
- [ ] Playwright 视觉基线存在（5 张）
- [ ] `pnpm lint` 0 error
- [ ] `pnpm build` 通过
- [ ] **不引入** `@form-create/element-ui`（验证 `package.json` 依赖）
- [ ] `structuredClone` 替换 `JSON.parse(JSON.stringify(...))`（验证 `stores/screenEditor.ts`）
- [ ] JsonSchemaForm 支持 `uiSchema` 覆盖（验证 `JsonSchemaForm.vue` Step 3 v2 版本）

## 备注（替代原 plan line 4440）

**JSONSchema 表单覆盖度**：使用**自实现 `JsonSchemaForm.vue`**（不引入 `@form-create/element-ui`），覆盖 string(text/textarea) / number / integer / boolean / string+enum / uiSchema.widget 覆盖 6 种场景。复杂类型（array / nested object）按 YAGNI 暂不实现；如后续业务需要可扩展 widget 路由表（resolveWidget 函数）。
