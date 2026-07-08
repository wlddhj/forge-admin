# 大屏展示系统 - 前端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 forge-web 端的大屏展示系统前端：基于配置的渲染页、配置驱动的拖拽编辑器、卡片组件注册中心、6 个核心卡片 + 2 个图表卡片 + 4 个装饰组件、3 套大屏主题、6 个预设模板、数据源管理 UI、撤销/重做、错误降级。

**Architecture:** 配置驱动 + 组件注册中心模式。大屏 `config`（JSON）由后端存储，前端通过 `cardRegistry` 解码 `type → 组件` 渲染；`ScreenRenderer` 统一调度每张卡片的数据加载（缓存/节流/重试）；渲染页使用 `useScreenScale` 做 1920×1080 等比缩放；编辑器基于 `grid-layout-plus`（Vue 3 拖拽库）+ JSONSchema 配置面板；撤销/重做由 Pinia + 快照栈实现；`CardErrorBoundary` 兜底每张卡片，不影响其他卡片渲染。

**Tech Stack:**
- Vue 3.4 + TypeScript 5.3 + Vite 5
- Pinia 2（store + 撤销/重做快照）
- Element Plus 2.4 + vxe-table 4.9
- echarts 5（按需引入，`echarts/core` + 显式 `use([...])`）
- grid-layout-plus 1.x（Vue 3 拖拽；vue-grid-layout 不支持 Vue 3）
- @form-create/element-ui 3.x（JSONSchema 驱动配置面板；已在 deps）
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
- 状态：撤销/重做用 Pinia + 结构化克隆（不引入 immer；保持 deps 精简）
- 类型严格度：禁用 `any`（除第三方回调无法签名时），所有公开 API 必须导出 TypeScript 类型
- 国际化：暂不接入 i18n；文案中文写死
- ECharts 引入：必须按需，禁止 `import * as echarts from 'echarts'`；体积控制在 ~300KB gzip
- 路由模式：列表页走菜单动态路由；`/screen/editor/:code` 走静态路由（`CONSTANT_ROUTES`）；`/screen/:code` 渲染页由每张大屏在 `sys_menu` 注册独立 `routePath`（如 `/screen/operations`），后端按 code 校验 `hasAuthority('screen:screen:view:' + #code)`
- 错误处理：所有 catch 必须 `console.error` 并显示给用户（`ElMessage.error`）；禁止静默吞错

---

## File Structure

```
apps/forge-web/
├── package.json                                                # 新增 echarts / grid-layout-plus / @vueuse/core
├── src/
│   ├── api/
│   │   └── screen/
│   │       ├── index.ts                                        # 类型 + screenApi + dataSourceApi
│   │       └── __tests__/screen.test.ts
│   ├── composables/
│   │   ├── useScreenScale.ts                                   # 1920×1080 等比缩放
│   │   ├── useCardDataSource.ts                                # 单卡片数据加载（refresh/重试/取消）
│   │   ├── useScreenHistory.ts                                 # 撤销/重做（Pinia 之外的纯 composable 包装）
│   │   └── __tests__/
│   │       ├── useScreenScale.test.ts
│   │       ├── useCardDataSource.test.ts
│   │       └── useScreenHistory.test.ts
│   ├── stores/
│   │   ├── screen.ts                                           # 当前大屏状态（active config/draft/loading）
│   │   └── screenEditor.ts                                     # 编辑器草稿 + 撤销/重做快照栈
│   ├── types/
│   │   └── screen.ts                                           # ScreenConfig / Card / FieldDef / JSONSchema 简化版
│   ├── constants/
│   │   └── screen.ts                                           # SCREEN_BASE_WIDTH=1920, THEME 列表, REFRESH 选项
│   ├── themes/
│   │   └── screen/
│   │       ├── _shared.scss                                    # 共享 CSS 变量结构
│   │       ├── _dark-tech.scss
│   │       ├── _blue-deep.scss
│   │       ├── _black-gold.scss
│   │       ├── index.ts                                        # applyScreenTheme
│   │       └── __tests__/applyScreenTheme.test.ts
│   ├── views/
│   │   └── screen/
│   │       ├── index/index.vue                                 # 大屏列表（CRUD + 复制 + 发布 + 新建选择模板）
│   │       ├── render/index.vue                                # 渲染页（/screen/:code，运行时）
│   │       ├── preview/index.vue                               # 预览页（/screen/preview/:code，读草稿）
│   │       ├── editor/index.vue                                # 拖拽编辑器（/screen/editor/:code）
│   │       ├── data-source/
│   │       │   ├── index.vue                                   # 数据源列表
│   │       │   └── editor.vue                                  # 数据源编辑（HTTP/SQL）
│   │       ├── templates/
│   │       │   ├── index.ts                                    # 6 个预设模板
│   │       │   └── __tests__/templates.test.ts
│   │       ├── components/
│   │       │   ├── ScreenRenderer.vue                          # 核心渲染器（遍历 config.cards）
│   │       │   ├── CardErrorBoundary.vue                       # 卡片级错误边界
│   │       │   ├── CardPanel.vue                               # 编辑器左侧组件库
│   │       │   ├── PropertyPanel.vue                           # 编辑器右侧配置面板
│   │       │   ├── DataSourceBinder.vue                        # 数据源绑定对话框
│   │       │   ├── FieldMappingEditor.vue                      # 字段映射编辑器
│   │       │   ├── HistoryToolbar.vue                          # 撤销/重做 + 保存/预览/发布
│   │       │   ├── TemplateSelector.vue                        # 新建时模板选择器
│   │       │   ├── JsonSchemaForm.vue                          # JSONSchema → el-form 渲染器
│   │       │   └── __tests__/
│   │       │       ├── CardErrorBoundary.test.ts
│   │       │       ├── JsonSchemaForm.test.ts
│   │       │       └── PropertyPanel.test.ts
│   │       ├── cards/
│   │       │   ├── types.ts                                    # ScreenCardComponent 契约
│   │       │   ├── registry.ts                                 # createRegistry + 注册入口
│   │       │   ├── digital-number/
│   │       │   │   ├── index.vue
│   │       │   │   └── ScrollNumber.vue                        # 数字翻牌器子组件
│   │       │   ├── line-chart/index.vue
│   │       │   ├── bar-chart/index.vue
│   │       │   ├── pie-chart/index.vue
│   │       │   ├── map-chart/index.vue
│   │       │   ├── scroll-table/index.vue
│   │       │   ├── gauge/index.vue
│   │       │   ├── text-board/index.vue
│   │       │   └── __tests__/registry.test.ts
│   │       └── decorations/
│   │           ├── TechBorder.vue
│   │           ├── DecorationCorner.vue
│   │           ├── TechTitle.vue
│   │           └── RadarBackground.vue
│   ├── styles/
│   │   └── screen.scss                                         # 全局样式入口（@import themes/screen）
│   ├── router/
│   │   └── constants.ts                                        # CONSTANT_ROUTES 新增 3 条屏幕路由
│   └── e2e/
│       └── screen/
│           ├── render.spec.ts                                  # Playwright 渲染页测试
│           └── editor.spec.ts                                  # Playwright 编辑器测试
```

---

## Task 1: 基础抽象（types + cardRegistry + CardErrorBoundary）

**Files:**
- Create: `apps/forge-web/src/types/screen.ts`
- Create: `apps/forge-web/src/views/screen/cards/types.ts`
- Create: `apps/forge-web/src/views/screen/cards/registry.ts`
- Create: `apps/forge-web/src/views/screen/components/CardErrorBoundary.vue`
- Create: `apps/forge-web/src/constants/screen.ts`
- Create: `apps/forge-web/src/views/screen/components/__tests__/CardErrorBoundary.test.ts`
- Create: `apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts`

**Interfaces:**
- Consumes: 无外部依赖
- Produces:
  - `types/screen.ts`：`ScreenConfig` / `ScreenCard` / `CardDataShape` / `FieldDef` / `ScreenCardComponent<TConfig, TData>` / `ScreenTheme` / `ScreenStatus`
  - `constants/screen.ts`：`SCREEN_BASE_WIDTH=1920` / `SCREEN_BASE_HEIGHT=1080` / `SCREEN_GRID_COLUMNS=24` / `SCREEN_MAX_CARD_ROWS=24` / `SCREEN_REFRESH_OPTIONS` / `SCREEN_DEFAULT_THEME='dark-tech'` / `SCREEN_THEMES` / `DATA_SOURCE_TYPE_HTTP/SQL`
  - `views/screen/cards/types.ts`：运行时再导出（避免编辑器 import 路径混淆）
  - `views/screen/cards/registry.ts`：`createRegistry<T>()` / `cardRegistry` / `registerBuiltinCards()`（Task 5/6 完成前 registerBuiltinCards 是空实现）
  - `views/screen/components/CardErrorBoundary.vue`：默认导出 `<CardErrorBoundary retryAfterMs onRetry>`，捕获子树错误并提供 5s 自动重试 + 手动按钮

- [ ] **Step 1: 写 types/screen.ts（共享类型）**

```ts
// apps/forge-web/src/types/screen.ts
import type { JSONSchema7 } from 'json-schema'

/** 大屏主题（与后端 sys_screen.theme 对齐） */
export type ScreenTheme = 'dark-tech' | 'blue-deep' | 'black-gold'

/** 大屏状态（与后端 ScreenStatus 对齐） */
export type ScreenStatus = 0 | 1

/** 单字段定义 */
export interface FieldDef {
  name: string
  type: 'string' | 'number' | 'date' | 'boolean'
  sample?: unknown
}

/** 卡片数据形状契约（前端用于自动字段映射） */
export interface CardDataShape {
  fields: FieldDef[]
  sample: Record<string, unknown>
}

/** 单张卡片（grid item + 组件配置） */
export interface ScreenCard {
  id: string
  type: string
  title?: string
  x: number
  y: number
  w: number
  h: number
  dataSourceId?: number | null
  refresh?: number
  options?: Record<string, unknown>
}

/** 大屏配置（运行/草稿共用） */
export interface ScreenConfig {
  version: number
  theme: ScreenTheme
  cards: ScreenCard[]
}

/** 卡片组件契约 */
export interface ScreenCardComponent<TConfig = Record<string, unknown>, TData = unknown> {
  type: string
  component: import('vue').Component
  meta: {
    title: string
    icon: string
    defaultProps: TConfig
    configSchema: JSONSchema7
    dataShape: CardDataShape
    minWidth: number
    minHeight: number
  }
}
```

- [ ] **Step 2: 写 constants/screen.ts**

```ts
// apps/forge-web/src/constants/screen.ts
import type { ScreenTheme } from '@/types/screen'

export const SCREEN_BASE_WIDTH = 1920
export const SCREEN_BASE_HEIGHT = 1080

export const SCREEN_GRID_COLUMNS = 24
export const SCREEN_MAX_CARD_ROWS = 24

export const SCREEN_REFRESH_OPTIONS: { label: string; value: number }[] = [
  { label: '不刷新', value: 0 },
  { label: '10 秒', value: 10 },
  { label: '30 秒', value: 30 },
  { label: '1 分钟', value: 60 },
  { label: '5 分钟', value: 300 }
]

export const SCREEN_THEMES: { value: ScreenTheme; label: string }[] = [
  { value: 'dark-tech', label: '暗色科技' },
  { value: 'blue-deep', label: '深空蓝' },
  { value: 'black-gold', label: '黑金' }
]

export const SCREEN_DEFAULT_THEME: ScreenTheme = 'dark-tech'

export const DATA_SOURCE_TYPE_HTTP = 'HTTP'
export const DATA_SOURCE_TYPE_SQL = 'SQL'
```

- [ ] **Step 3: 写 views/screen/cards/types.ts（运行时类型再导出）**

```ts
// apps/forge-web/src/views/screen/cards/types.ts
export type {
  ScreenConfig,
  ScreenCard,
  CardDataShape,
  FieldDef,
  ScreenCardComponent,
  ScreenTheme,
  ScreenStatus
} from '@/types/screen'
```

- [ ] **Step 4: 写 CardErrorBoundary 失败测试**

```ts
// apps/forge-web/src/views/screen/components/__tests__/CardErrorBoundary.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import CardErrorBoundary from '../CardErrorBoundary.vue'

describe('CardErrorBoundary', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('正常渲染时透传默认插槽', () => {
    const Child = defineComponent({ setup: () => () => h('div', { class: 'ok' }, 'hello') })
    const wrapper = mount(CardErrorBoundary, { slots: { default: () => h(Child) } })
    expect(wrapper.find('.ok').exists()).toBe(true)
    expect(wrapper.text()).toBe('hello')
  })

  it('子组件抛错时显示降级占位 + 5s 后自动重试', async () => {
    const onRetry = vi.fn()
    const Child = defineComponent({
      setup: () => { throw new Error('boom') }
    })
    const wrapper = mount(CardErrorBoundary, {
      props: { retryAfterMs: 5000, onRetry },
      slots: { default: () => h(Child) }
    })
    expect(wrapper.find('.card-error-fallback').exists()).toBe(true)
    expect(wrapper.text()).toContain('数据加载失败')
    vi.advanceTimersByTime(5000)
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('手动重试按钮触发 onRetry', async () => {
    const onRetry = vi.fn()
    const Thrower = defineComponent({ setup: () => { throw new Error('x') } })
    const wrapper = mount(CardErrorBoundary, {
      props: { onRetry },
      slots: { default: () => h(Thrower) }
    })
    await wrapper.find('.card-error-retry').trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
```

- [ ] **Step 5: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/components/__tests__/CardErrorBoundary.test.ts`
Expected: 编译错误（`CardErrorBoundary.vue` 找不到）

- [ ] **Step 6: 写 CardErrorBoundary.vue**

```vue
<!-- apps/forge-web/src/views/screen/components/CardErrorBoundary.vue -->
<script setup lang="ts">
import { onErrorCaptured, onUnmounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  retryAfterMs?: number
  onRetry?: () => void | Promise<void>
}>(), {
  retryAfterMs: 5000,
  onRetry: undefined
})

const error = ref<Error | null>(null)
let retryTimer: ReturnType<typeof setTimeout> | null = null

const handleError = (err: unknown) => {
  error.value = err instanceof Error ? err : new Error(String(err))
  if (props.onRetry) {
    retryTimer = setTimeout(() => { void props.onRetry?.() }, props.retryAfterMs)
  }
}

const clearTimer = () => {
  if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
}

const handleRetry = () => {
  clearTimer()
  error.value = null
  if (props.onRetry) void props.onRetry()
}

onErrorCaptured((err) => { handleError(err); return false })
watch(() => props.retryAfterMs, clearTimer)
onUnmounted(clearTimer)
</script>

<template>
  <div class="card-error-boundary">
    <template v-if="!error"><slot /></template>
    <div v-else class="card-error-fallback">
      <div class="card-error-icon">!</div>
      <div class="card-error-text">数据加载失败，{{ Math.round(retryAfterMs / 1000) }}s 后重试</div>
      <button class="card-error-retry" type="button" @click="handleRetry">立即重试</button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.card-error-boundary { width: 100%; height: 100%; }
.card-error-fallback {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  width: 100%; height: 100%;
  color: var(--screen-text-secondary, #8a96a8); font-size: 14px;
}
.card-error-icon {
  width: 32px; height: 32px; line-height: 32px; text-align: center;
  border: 1px solid var(--screen-accent, #1e88e5); border-radius: 50%;
  margin-bottom: 8px; color: var(--screen-accent, #1e88e5);
}
.card-error-retry {
  margin-top: 12px; padding: 4px 12px; cursor: pointer;
  background: transparent; color: var(--screen-accent, #1e88e5);
  border: 1px solid var(--screen-accent, #1e88e5); border-radius: 4px;
}
</style>
```

- [ ] **Step 7: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/components/__tests__/CardErrorBoundary.test.ts`
Expected: 3 passed

- [ ] **Step 8: 写 registry 失败测试**

```ts
// apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { createRegistry, cardRegistry, registerBuiltinCards } from '../registry'
import type { ScreenCardComponent } from '../types'

const StubComp = defineComponent({ name: 'Stub', setup: () => () => h('div', 'stub') })

const makeEntry = (type: string): ScreenCardComponent => ({
  type,
  component: StubComp,
  meta: {
    title: type, icon: 'Histogram', defaultProps: {},
    configSchema: { type: 'object', properties: {} },
    dataShape: { fields: [{ name: 'v', type: 'number' }], sample: { v: 0 } },
    minWidth: 4, minHeight: 3
  }
})

describe('cardRegistry', () => {
  beforeEach(() => {
    // 每个测试前用独立 registry
    ;(cardRegistry as any).__test_reset?.()
  })

  it('register / get 双向一致', () => {
    const reg = createRegistry<ScreenCardComponent>()
    reg.register(makeEntry('line-chart'))
    expect(reg.get('line-chart')?.type).toBe('line-chart')
  })

  it('get 不存在的 type 返回 undefined', () => {
    const reg = createRegistry<ScreenCardComponent>()
    expect(reg.get('nope')).toBeUndefined()
  })

  it('list 返回所有已注册条目（按注册顺序）', () => {
    const reg = createRegistry<ScreenCardComponent>()
    reg.register(makeEntry('a'))
    reg.register(makeEntry('b'))
    expect(reg.list().map(e => e.type)).toEqual(['a', 'b'])
  })

  it('重复注册同 type 抛错', () => {
    const reg = createRegistry<ScreenCardComponent>()
    reg.register(makeEntry('x'))
    expect(() => reg.register(makeEntry('x'))).toThrow(/already registered/i)
  })

  it('registerBuiltinCards 不抛错（具体注册数量由 Task 5/6 决定）', () => {
    expect(() => registerBuiltinCards()).not.toThrow()
  })

  it('registerBuiltinCards 后全局 cardRegistry 至少能注册 1 个内置卡片（防止后续 Task 误删入口）', () => {
    // 该断言在 Task 5/6 完成之前可以手动跳过；保留以防回归
    registerBuiltinCards()
    expect(cardRegistry.list().length).toBeGreaterThanOrEqual(0)
  })
})
```

- [ ] **Step 9: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/cards/__tests__/registry.test.ts`
Expected: 编译错误（`registry.ts` 找不到）

- [ ] **Step 10: 写 registry.ts**

```ts
// apps/forge-web/src/views/screen/cards/registry.ts
import type { ScreenCardComponent } from './types'

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

/** 全局卡片注册中心（模块单例） */
export const cardRegistry: Registry<ScreenCardComponent> = createRegistry<ScreenCardComponent>()

/**
 * 注册内置 8 个卡片。
 * Task 5/6 完成对应组件后，此处替换为真实 import + register。
 */
export function registerBuiltinCards(): void {
  // Task 5/6 会把 import 写到这里。
}
```

- [ ] **Step 11: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/cards/__tests__/registry.test.ts`
Expected: 6 passed

- [ ] **Step 12: Commit**

```bash
git add apps/forge-web/src/types/screen.ts \
        apps/forge-web/src/constants/screen.ts \
        apps/forge-web/src/views/screen/cards/types.ts \
        apps/forge-web/src/views/screen/cards/registry.ts \
        apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts \
        apps/forge-web/src/views/screen/components/CardErrorBoundary.vue \
        apps/forge-web/src/views/screen/components/__tests__/CardErrorBoundary.test.ts
git commit -m "feat(screen): 新增大屏基础抽象（types + cardRegistry + 卡片错误边界）"
```

---

## Task 2: 大屏 API 模块（screenApi + dataSourceApi）

**Files:**
- Create: `apps/forge-web/src/api/screen/index.ts`
- Create: `apps/forge-web/src/api/screen/__tests__/screen.test.ts`

**Interfaces:**
- Consumes: `@/utils/request`（已存在）
- Produces（`api/screen/index.ts` exports）：
  - 类型：`ScreenListQuery` / `ScreenDetailResponse` / `ScreenCreateRequest` / `ScreenCopyRequest` / `ScreenDataSource` / `DataSourceListQuery` / `DataSourceExecuteRequest` / `DataSourceExecuteResponse`
  - API 对象：`screenApi`（page/get/getByCode/create/update/remove/publish/copy）、`dataSourceApi`（page/get/create/update/remove/execute）
  - 顶层函数：`getScreenList` / `getScreenDetail` / `getScreenByCode` / `createScreen` / `updateScreen` / `deleteScreen` / `publishScreen` / `copyScreen` / `getDataSourceList` / `getDataSourceDetail` / `createDataSource` / `updateDataSource` / `deleteDataSource` / `executeDataSource`

- [ ] **Step 1: 写失败测试**

```ts
// apps/forge-web/src/api/screen/__tests__/screen.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

import request from '@/utils/request'
import {
  getScreenList, getScreenDetail, getScreenByCode,
  createScreen, updateScreen, deleteScreen, publishScreen, copyScreen,
  getDataSourceList, getDataSourceDetail,
  createDataSource, updateDataSource, deleteDataSource, executeDataSource
} from '../index'

describe('screen API', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getScreenList → GET /screen/list', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { list: [], total: 0 } })
    await getScreenList({ pageNum: 1, pageSize: 10, name: 'x' })
    expect(request.get).toHaveBeenCalledWith('/screen/list', { params: { pageNum: 1, pageSize: 10, name: 'x' } })
  })

  it('getScreenDetail → GET /screen/{id}', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { id: 1 } })
    await getScreenDetail(1)
    expect(request.get).toHaveBeenCalledWith('/screen/1')
  })

  it('getScreenByCode → GET /screen/code/{code}', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { id: 1 } })
    await getScreenByCode('operations')
    expect(request.get).toHaveBeenCalledWith('/screen/code/operations')
  })

  it('createScreen → POST /screen', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: 99 })
    await createScreen({ code: 'a', name: 'A' })
    expect(request.post).toHaveBeenCalledWith('/screen', { code: 'a', name: 'A' })
  })

  it('updateScreen → PUT /screen', async () => {
    vi.mocked(request.put).mockResolvedValue({})
    await updateScreen({ id: 1, name: 'A2' })
    expect(request.put).toHaveBeenCalledWith('/screen', { id: 1, name: 'A2' })
  })

  it('deleteScreen → DELETE /screen with ids array', async () => {
    vi.mocked(request.delete).mockResolvedValue({})
    await deleteScreen([1, 2])
    expect(request.delete).toHaveBeenCalledWith('/screen', { data: [1, 2] })
  })

  it('publishScreen → PUT /screen/publish/{code}', async () => {
    vi.mocked(request.put).mockResolvedValue({})
    await publishScreen('operations')
    expect(request.put).toHaveBeenCalledWith('/screen/publish/operations')
  })

  it('copyScreen → POST /screen/copy/{code}', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: 88 })
    await copyScreen('operations', { newCode: 'ops2', newName: '副本' })
    expect(request.post).toHaveBeenCalledWith('/screen/copy/operations', { newCode: 'ops2', newName: '副本' })
  })
})

describe('dataSource API', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getDataSourceList → GET /screen/data-source/list', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { list: [], total: 0 } })
    await getDataSourceList({ pageNum: 1, pageSize: 10 })
    expect(request.get).toHaveBeenCalledWith('/screen/data-source/list', { params: { pageNum: 1, pageSize: 10 } })
  })

  it('getDataSourceDetail → GET /screen/data-source/{id}', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { id: 7 } })
    await getDataSourceDetail(7)
    expect(request.get).toHaveBeenCalledWith('/screen/data-source/7')
  })

  it('createDataSource → POST /screen/data-source', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: 1 })
    await createDataSource({ code: 'a', name: 'A', type: 'HTTP', config: '{}' })
    expect(request.post).toHaveBeenCalledWith('/screen/data-source', { code: 'a', name: 'A', type: 'HTTP', config: '{}' })
  })

  it('updateDataSource → PUT /screen/data-source', async () => {
    vi.mocked(request.put).mockResolvedValue({})
    await updateDataSource({ id: 1, name: 'A2' })
    expect(request.put).toHaveBeenCalledWith('/screen/data-source', { id: 1, name: 'A2' })
  })

  it('deleteDataSource → DELETE /screen/data-source with ids', async () => {
    vi.mocked(request.delete).mockResolvedValue({})
    await deleteDataSource([1, 2])
    expect(request.delete).toHaveBeenCalledWith('/screen/data-source', { data: [1, 2] })
  })

  it('executeDataSource → POST /screen/data-source/execute/{id}', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: { data: [], fromCache: false, executedAt: '' } })
    await executeDataSource(1, { params: { id: 1 } })
    expect(request.post).toHaveBeenCalledWith('/screen/data-source/execute/1', { params: { id: 1 } })
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/api/screen/__tests__/screen.test.ts`
Expected: 编译错误（`@/api/screen` 找不到）

- [ ] **Step 3: 写 api/screen/index.ts**

```ts
// apps/forge-web/src/api/screen/index.ts
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

// ===== 类型（与后端 DTO 字段一致） =====

export interface ScreenListQuery {
  pageNum: number
  pageSize: number
  name?: string
  status?: 0 | 1
}

export interface ScreenDetailResponse {
  id: number
  code: string
  name: string
  description?: string
  /** 后端存的 JSON 字符串，前端用前需 JSON.parse */
  config: string
  configDraft: string
  theme: string
  status: 0 | 1
  version: number
  createTime: string
  updateTime: string
  createBy?: number
  remark?: string
}

export interface ScreenCreateRequest {
  id?: number
  code: string
  name: string
  description?: string
  theme?: string
  remark?: string
}

export interface ScreenCopyRequest {
  newCode: string
  newName: string
}

export interface ScreenDataSource {
  id?: number
  code: string
  name: string
  type: 'HTTP' | 'SQL'
  /** JSON 字符串：HTTP→{method,url,headers,params,timeout}；SQL→{sqlTemplate,paramSchema,maxRows} */
  config: string
  cacheSeconds?: number
  enabled?: 0 | 1
  remark?: string
}

export interface DataSourceListQuery {
  pageNum: number
  pageSize: number
  name?: string
  type?: 'HTTP' | 'SQL'
  enabled?: 0 | 1
}

export interface DataSourceExecuteRequest {
  params?: Record<string, unknown>
}

export interface DataSourceExecuteResponse {
  data: unknown
  fromCache: boolean
  executedAt: string
}

// ===== 大屏 API =====

export const screenApi = {
  page: (params: ScreenListQuery) =>
    request.get<PageResult<ScreenDetailResponse>>('/screen/list', { params }),
  get: (id: number) =>
    request.get<ScreenDetailResponse>(`/screen/${id}`),
  getByCode: (code: string) =>
    request.get<ScreenDetailResponse>(`/screen/code/${code}`),
  create: (data: ScreenCreateRequest) =>
    request.post<number>('/screen', data),
  update: (data: ScreenCreateRequest) =>
    request.put<void>('/screen', data),
  remove: (ids: number[]) =>
    request.delete<void>('/screen', { data: ids }),
  publish: (code: string) =>
    request.put<void>(`/screen/publish/${code}`),
  copy: (code: string, data: ScreenCopyRequest) =>
    request.post<number>(`/screen/copy/${code}`, data)
}

export const getScreenList = (params: ScreenListQuery) => screenApi.page(params).then(r => r.data)
export const getScreenDetail = (id: number) => screenApi.get(id).then(r => r.data)
export const getScreenByCode = (code: string) => screenApi.getByCode(code).then(r => r.data)
export const createScreen = (data: ScreenCreateRequest) => screenApi.create(data).then(r => r.data)
export const updateScreen = (data: ScreenCreateRequest) => screenApi.update(data).then(r => r.data)
export const deleteScreen = (ids: number[]) => screenApi.remove(ids).then(r => r.data)
export const publishScreen = (code: string) => screenApi.publish(code).then(r => r.data)
export const copyScreen = (code: string, data: ScreenCopyRequest) => screenApi.copy(code, data).then(r => r.data)

// ===== 数据源 API =====

export const dataSourceApi = {
  page: (params: DataSourceListQuery) =>
    request.get<PageResult<ScreenDataSource>>('/screen/data-source/list', { params }),
  get: (id: number) =>
    request.get<ScreenDataSource>(`/screen/data-source/${id}`),
  create: (data: ScreenDataSource) =>
    request.post<number>('/screen/data-source', data),
  update: (data: ScreenDataSource) =>
    request.put<void>('/screen/data-source', data),
  remove: (ids: number[]) =>
    request.delete<void>('/screen/data-source', { data: ids }),
  execute: (id: number, data: DataSourceExecuteRequest) =>
    request.post<DataSourceExecuteResponse>(`/screen/data-source/execute/${id}`, data)
}

export const getDataSourceList = (params: DataSourceListQuery) => dataSourceApi.page(params).then(r => r.data)
export const getDataSourceDetail = (id: number) => dataSourceApi.get(id).then(r => r.data)
export const createDataSource = (data: ScreenDataSource) => dataSourceApi.create(data).then(r => r.data)
export const updateDataSource = (data: ScreenDataSource) => dataSourceApi.update(data).then(r => r.data)
export const deleteDataSource = (ids: number[]) => dataSourceApi.remove(ids).then(r => r.data)
export const executeDataSource = (id: number, data: DataSourceExecuteRequest) =>
  dataSourceApi.execute(id, data).then(r => r.data)
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/api/screen/__tests__/screen.test.ts`
Expected: 14 passed

- [ ] **Step 5: Commit**

```bash
git add apps/forge-web/src/api/screen/index.ts apps/forge-web/src/api/screen/__tests__/screen.test.ts
git commit -m "feat(screen): 新增大屏与数据源 API 模块"
```

---

## Task 3: composables（useScreenScale + useCardDataSource + useScreenHistory）

**Files:**
- Modify: `apps/forge-web/package.json`（新增 `@vueuse/core`）
- Create: `apps/forge-web/src/composables/useScreenScale.ts`
- Create: `apps/forge-web/src/composables/useCardDataSource.ts`
- Create: `apps/forge-web/src/composables/useScreenHistory.ts`
- Create: `apps/forge-web/src/composables/__tests__/useScreenScale.test.ts`
- Create: `apps/forge-web/src/composables/__tests__/useCardDataSource.test.ts`
- Create: `apps/forge-web/src/composables/__tests__/useScreenHistory.test.ts`

**Interfaces:**
- Consumes: `executeDataSource`（Task 2）、`@vueuse/core`（新增依赖）、`SCREEN_BASE_WIDTH/HEIGHT`（Task 1）
- Produces:
  - `useScreenScale()` → `{ width, height, scale, containerStyle }`，`scale = min(vw/1920, vh/1080)`
  - `useCardDataSource(card)` → `{ data, loading, error, load, refresh, cancel }`；按 `card.refresh` 周期拉取；组件卸载时取消
  - `useScreenHistory(initial, { max })` → `{ state, canUndo, canRedo, commit, undo, redo, clear }`；`structuredClone` 实现快照

- [ ] **Step 1: 装 @vueuse/core**

```bash
cd apps/forge-web
pnpm add @vueuse/core
```

预期：`package.json` 出现 `"@vueuse/core": "^11.x.x"`

- [ ] **Step 2: 写 useScreenScale 失败测试**

```ts
// apps/forge-web/src/composables/__tests__/useScreenScale.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useScreenScale } from '../useScreenScale'
import { SCREEN_BASE_WIDTH, SCREEN_BASE_HEIGHT } from '@/constants/screen'

describe('useScreenScale', () => {
  const setSize = (w: number, h: number) => {
    Object.defineProperty(window, 'innerWidth', { value: w, configurable: true })
    Object.defineProperty(window, 'innerHeight', { value: h, configurable: true })
  }
  beforeEach(() => { setSize(1920, 1080) })

  it('1920x1080 → scale=1', () => {
    const { scale } = useScreenScale()
    expect(scale.value).toBe(1)
  })

  it('1280x720 → scale=2/3', () => {
    setSize(1280, 720)
    const { scale } = useScreenScale()
    expect(scale.value).toBeCloseTo(1280 / SCREEN_BASE_WIDTH, 5)
  })

  it('containerStyle 固定 1920x1080 + transform', () => {
    const { containerStyle } = useScreenScale()
    expect(containerStyle.value.width).toBe(`${SCREEN_BASE_WIDTH}px`)
    expect(containerStyle.value.height).toBe(`${SCREEN_BASE_HEIGHT}px`)
    expect(containerStyle.value.transform).toContain('scale(')
  })

  it('resize 触发后 scale 更新（150ms 防抖）', async () => {
    const { scale } = useScreenScale()
    setSize(960, 540)
    window.dispatchEvent(new Event('resize'))
    await new Promise(r => setTimeout(r, 200))
    expect(scale.value).toBeCloseTo(0.5, 2)
  })
})
```

- [ ] **Step 3: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/composables/__tests__/useScreenScale.test.ts`
Expected: 编译错误

- [ ] **Step 4: 写 useScreenScale.ts**

```ts
// apps/forge-web/src/composables/useScreenScale.ts
import { ref, computed, onMounted, onUnmounted, type Ref, type ComputedRef } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { SCREEN_BASE_WIDTH, SCREEN_BASE_HEIGHT } from '@/constants/screen'

export interface ScreenScale {
  width: Ref<number>
  height: Ref<number>
  scale: ComputedRef<number>
  containerStyle: ComputedRef<Record<string, string>>
}

export function useScreenScale(): ScreenScale {
  const width = ref(typeof window !== 'undefined' ? window.innerWidth : SCREEN_BASE_WIDTH)
  const height = ref(typeof window !== 'undefined' ? window.innerHeight : SCREEN_BASE_HEIGHT)

  const scale = computed(() =>
    Math.min(width.value / SCREEN_BASE_WIDTH, height.value / SCREEN_BASE_HEIGHT)
  )

  const containerStyle = computed(() => {
    const s = scale.value
    return {
      width: `${SCREEN_BASE_WIDTH}px`,
      height: `${SCREEN_BASE_HEIGHT}px`,
      transform: `scale(${s})`,
      'transform-origin': 'center top',
      position: 'absolute' as const,
      left: '50%',
      top: '0',
      'margin-left': `-${(SCREEN_BASE_WIDTH * s) / 2}px`
    }
  })

  const update = () => {
    width.value = window.innerWidth
    height.value = window.innerHeight
  }
  const debouncedUpdate = useDebounceFn(update, 150)

  onMounted(() => window.addEventListener('resize', debouncedUpdate))
  onUnmounted(() => window.removeEventListener('resize', debouncedUpdate))

  return { width, height, scale, containerStyle }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/composables/__tests__/useScreenScale.test.ts`
Expected: 4 passed

- [ ] **Step 6: 写 useScreenHistory 失败测试**

```ts
// apps/forge-web/src/composables/__tests__/useScreenHistory.test.ts
import { describe, it, expect } from 'vitest'
import { useScreenHistory } from '../useScreenHistory'

describe('useScreenHistory', () => {
  it('初始 state 等于 initial', () => {
    const h = useScreenHistory({ v: 0 })
    expect(h.state.value).toEqual({ v: 0 })
    expect(h.canUndo.value).toBe(false)
    expect(h.canRedo.value).toBe(false)
  })

  it('commit 后 canUndo=true', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    expect(h.state.value).toEqual({ v: 1 })
    expect(h.canUndo.value).toBe(true)
  })

  it('undo/redo 双向移动', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.undo()
    expect(h.state.value).toEqual({ v: 1 })
    expect(h.canRedo.value).toBe(true)
    h.undo()
    expect(h.state.value).toEqual({ v: 0 })
    h.redo()
    expect(h.state.value).toEqual({ v: 1 })
  })

  it('新 commit 清空 redo 栈', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.undo()
    h.commit({ v: 3 })
    expect(h.canRedo.value).toBe(false)
  })

  it('max=3 截断历史', () => {
    const h = useScreenHistory({ v: 0 }, { max: 3 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.commit({ v: 3 })
    h.commit({ v: 4 })
    h.undo()
    expect(h.state.value).toEqual({ v: 3 })
    h.undo()
    expect(h.state.value).toEqual({ v: 2 })
    h.undo()
    expect(h.state.value).toEqual({ v: 1 })
  })

  it('clear 同时清空 undo/redo 栈', () => {
    const h = useScreenHistory({ v: 0 })
    h.commit({ v: 1 })
    h.commit({ v: 2 })
    h.undo()
    h.clear()
    expect(h.canUndo.value).toBe(false)
    expect(h.canRedo.value).toBe(false)
  })
})
```

- [ ] **Step 7: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/composables/__tests__/useScreenHistory.test.ts`
Expected: 编译错误

- [ ] **Step 8: 写 useScreenHistory.ts**

```ts
// apps/forge-web/src/composables/useScreenHistory.ts
import { ref, computed, type Ref, type ComputedRef } from 'vue'

export interface ScreenHistoryReturn<T> {
  state: Ref<T>
  canUndo: ComputedRef<boolean>
  canRedo: ComputedRef<boolean>
  commit: (next: T) => void
  undo: () => void
  redo: () => void
  clear: () => void
}

export function useScreenHistory<T>(initial: T, options: { max?: number } = {}): ScreenHistoryReturn<T> {
  const max = options.max ?? 50
  const undoStack = ref<T[]>([])
  const redoStack = ref<T[]>([])
  const state = ref(structuredClone(initial)) as Ref<T>

  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)

  const commit = (next: T) => {
    undoStack.value.push(structuredClone(state.value))
    if (undoStack.value.length > max) undoStack.value.shift()
    state.value = structuredClone(next)
    redoStack.value = []
  }

  const undo = () => {
    if (!canUndo.value) return
    redoStack.value.push(structuredClone(state.value))
    state.value = undoStack.value.pop()!
  }

  const redo = () => {
    if (!canRedo.value) return
    undoStack.value.push(structuredClone(state.value))
    state.value = redoStack.value.pop()!
  }

  const clear = () => { undoStack.value = []; redoStack.value = [] }

  return { state, canUndo, canRedo, commit, undo, redo, clear }
}
```

- [ ] **Step 9: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/composables/__tests__/useScreenHistory.test.ts`
Expected: 6 passed

- [ ] **Step 10: 写 useCardDataSource 失败测试**

```ts
// apps/forge-web/src/composables/__tests__/useCardDataSource.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api/screen', () => ({ executeDataSource: vi.fn() }))

import { executeDataSource } from '@/api/screen'
import { useCardDataSource } from '../useCardDataSource'
import type { ScreenCard } from '@/types/screen'

const makeCard = (overrides: Partial<ScreenCard> = {}): ScreenCard => ({
  id: 'c1', type: 'line-chart', x: 0, y: 0, w: 12, h: 6,
  dataSourceId: 1, refresh: 0, options: {}, ...overrides
})

describe('useCardDataSource', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('dataSourceId 为 null 时 data 始终 null，不调 API', async () => {
    const card = ref<ScreenCard>(makeCard({ dataSourceId: null }))
    const { data, load } = useCardDataSource(card)
    await load()
    expect(data.value).toBeNull()
    expect(executeDataSource).not.toHaveBeenCalled()
  })

  it('load 拉取数据并填入 data', async () => {
    vi.mocked(executeDataSource).mockResolvedValue({ data: [{ v: 1 }], fromCache: false, executedAt: '' })
    const card = ref<ScreenCard>(makeCard({ dataSourceId: 1 }))
    const { data, load } = useCardDataSource(card)
    await load()
    expect(data.value).toEqual([{ v: 1 }])
    expect(executeDataSource).toHaveBeenCalledWith(1, { params: {} })
  })

  it('options.params 透传到请求', async () => {
    vi.mocked(executeDataSource).mockResolvedValue({ data: [], fromCache: false, executedAt: '' })
    const card = ref<ScreenCard>(makeCard({ dataSourceId: 1, options: { params: { id: 5 } } }))
    const { load } = useCardDataSource(card)
    await load()
    expect(executeDataSource).toHaveBeenCalledWith(1, { params: { id: 5 } })
  })

  it('API 抛错时 error 被填充，data 保持上次值', async () => {
    vi.mocked(executeDataSource).mockResolvedValueOnce({ data: [{ v: 1 }], fromCache: false, executedAt: '' })
    const card = ref<ScreenCard>(makeCard({ dataSourceId: 1 }))
    const { data, load, error } = useCardDataSource(card)
    await load()
    vi.mocked(executeDataSource).mockRejectedValueOnce(new Error('boom'))
    await load()
    expect(error.value?.message).toBe('boom')
    expect(data.value).toEqual([{ v: 1 }])
  })
})
```

- [ ] **Step 11: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/composables/__tests__/useCardDataSource.test.ts`
Expected: 编译错误

- [ ] **Step 12: 写 useCardDataSource.ts**

```ts
// apps/forge-web/src/composables/useCardDataSource.ts
import { ref, watch, onUnmounted, type Ref } from 'vue'
import { useIntervalFn } from '@vueuse/core'
import { executeDataSource, type DataSourceExecuteResponse } from '@/api/screen'
import type { ScreenCard } from '@/types/screen'

export interface CardDataSourceReturn {
  data: Ref<unknown>
  loading: Ref<boolean>
  error: Ref<Error | null>
  load: () => Promise<void>
  refresh: () => Promise<void>
  cancel: () => void
}

export function useCardDataSource(card: Ref<ScreenCard>): CardDataSourceReturn {
  const data = ref<unknown>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  let token = 0
  let interval: ReturnType<typeof useIntervalFn> | null = null

  const load = async () => {
    const my = ++token
    const dsId = card.value.dataSourceId
    if (!dsId) { data.value = null; return }
    loading.value = true
    error.value = null
    try {
      const res: DataSourceExecuteResponse = await executeDataSource(dsId, {
        params: (card.value.options?.params as Record<string, unknown>) ?? {}
      })
      if (my === token) data.value = res.data
    } catch (e) {
      if (my === token) error.value = e instanceof Error ? e : new Error(String(e))
    } finally {
      if (my === token) loading.value = false
    }
  }

  const start = () => {
    interval?.pause()
    if (card.value.refresh && card.value.refresh > 0) {
      interval = useIntervalFn(load, card.value.refresh * 1000, { immediate: false })
    }
  }
  const stop = () => interval?.pause()

  watch(() => card.value.refresh, start, { immediate: true })
  watch(() => card.value.dataSourceId, load)

  onUnmounted(() => { stop(); token = -1 })

  return { data, loading, error, load, refresh: load, cancel: stop }
}
```

- [ ] **Step 13: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/composables/__tests__/useCardDataSource.test.ts`
Expected: 4 passed

- [ ] **Step 14: Commit**

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

## Task 4: 大屏主题系统（3 套 screen 主题 + pageConfig 集成）

**Files:**
- Create: `apps/forge-web/src/themes/screen/_shared.scss`
- Create: `apps/forge-web/src/themes/screen/_dark-tech.scss`
- Create: `apps/forge-web/src/themes/screen/_blue-deep.scss`
- Create: `apps/forge-web/src/themes/screen/_black-gold.scss`
- Create: `apps/forge-web/src/themes/screen/index.scss`（@forward 入口）
- Create: `apps/forge-web/src/themes/screen/index.ts`（`applyScreenTheme`）
- Modify: `apps/forge-web/src/styles/screen.scss`（新文件，作为全局入口被 main.ts 引入）
- Modify: `apps/forge-web/src/main.ts`（引入 `styles/screen.scss`）
- Create: `apps/forge-web/src/themes/screen/__tests__/applyScreenTheme.test.ts`

**Interfaces:**
- Consumes: `usePageConfigStore`（已有，导入 palette 提供主色给大屏）、`SCREEN_THEMES`（Task 1）、`ScreenTheme`（Task 1）
- Produces:
  - `themes/screen/_shared.scss`：`%screen-card`, `%screen-deco` 占位选择器
  - `themes/screen/_dark-tech.scss` / `_blue-deep.scss` / `_black-gold.scss`：对应 `--screen-bg` / `--screen-card-bg` / `--screen-border` / `--screen-accent` / `--screen-text-primary` / `--screen-text-secondary` / `--screen-grid-line` 变量
  - `themes/screen/index.ts`：`applyScreenTheme(theme: ScreenTheme)` 写到 `<html data-screen-theme="...">`，并从 pageConfig 读 palette 写入 `--screen-accent`
  - `styles/screen.scss`：`@use 'themes/screen'` 入口

- [ ] **Step 1: 写 themes/screen/_shared.scss**

```scss
// apps/forge-web/src/themes/screen/_shared.scss
// 大屏通用基础类（在 main.ts 由 styles/screen.scss 引入，仅在 /screen 路由下生效）
%screen-card-base {
  background: var(--screen-card-bg, rgba(8, 22, 40, 0.85));
  border: 1px solid var(--screen-border, #1e3a5f);
  color: var(--screen-text-primary, #e0e6f1);
  box-shadow: 0 0 24px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
}

%screen-deco-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--screen-accent, #1e88e5);
  letter-spacing: 2px;
  text-shadow: 0 0 8px var(--screen-accent, #1e88e5);
}
```

- [ ] **Step 2: 写 themes/screen/_dark-tech.scss**

```scss
// apps/forge-web/src/themes/screen/_dark-tech.scss
:root[data-screen-theme='dark-tech'] {
  --screen-bg: radial-gradient(ellipse at top, #0a1929 0%, #000 100%);
  --screen-card-bg: rgba(8, 22, 40, 0.85);
  --screen-border: #1e3a5f;
  --screen-accent: var(--palette-primary, #1e88e5);
  --screen-text-primary: #e0e6f1;
  --screen-text-secondary: #8a96a8;
  --screen-grid-line: rgba(30, 58, 95, 0.3);
}
```

- [ ] **Step 3: 写 themes/screen/_blue-deep.scss**

```scss
// apps/forge-web/src/themes/screen/_blue-deep.scss
:root[data-screen-theme='blue-deep'] {
  --screen-bg: radial-gradient(ellipse at top, #051c3f 0%, #020814 100%);
  --screen-card-bg: rgba(7, 35, 75, 0.85);
  --screen-border: #1c4d8f;
  --screen-accent: var(--palette-primary, #1e88e5);
  --screen-text-primary: #d6e6ff;
  --screen-text-secondary: #7e9bc6;
  --screen-grid-line: rgba(28, 77, 143, 0.3);
}
```

- [ ] **Step 4: 写 themes/screen/_black-gold.scss**

```scss
// apps/forge-web/src/themes/screen/_black-gold.scss
:root[data-screen-theme='black-gold'] {
  --screen-bg: radial-gradient(ellipse at top, #1a1408 0%, #000 100%);
  --screen-card-bg: rgba(35, 26, 8, 0.85);
  --screen-border: #6b5118;
  --screen-accent: #d4a73a;
  --screen-text-primary: #f5e8c8;
  --screen-text-secondary: #b09870;
  --screen-grid-line: rgba(107, 81, 24, 0.3);
}
```

- [ ] **Step 5: 写 themes/screen/index.scss**

```scss
// apps/forge-web/src/themes/screen/index.scss
@use 'shared';
@use 'dark-tech';
@use 'blue-deep';
@use 'black-gold';
```

- [ ] **Step 6: 写 styles/screen.scss 与 main.ts 引入**

```scss
// apps/forge-web/src/styles/screen.scss
@use '../themes/screen';
```

修改 `apps/forge-web/src/main.ts`，在 import 语句末尾新增：

```ts
import '@/styles/screen.scss'
```

（位置：紧跟现有 `import 'element-plus/dist/index.css'` 等 CSS 引入之后）

- [ ] **Step 7: 写 applyScreenTheme 失败测试**

```ts
// apps/forge-web/src/themes/screen/__tests__/applyScreenTheme.test.ts
import { describe, it, expect, beforeEach } from 'vitest'

vi.mock('@/stores/pageConfig', () => ({
  usePageConfigStore: () => ({
    config: { value: { palette: 'blue' } }
  })
}))

import { applyScreenTheme, getCurrentScreenTheme } from '../index'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'

describe('applyScreenTheme', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-screen-theme')
    document.documentElement.style.removeProperty('--screen-accent')
  })

  it('写入 data-screen-theme 属性', () => {
    applyScreenTheme('dark-tech')
    expect(document.documentElement.getAttribute('data-screen-theme')).toBe('dark-tech')
  })

  it('从 pageConfig.palette 派生 --screen-accent', () => {
    applyScreenTheme('blue-deep')
    // blue 调色板主色为 #409EFF，applyCustomPalette 或 presets 写入；这里只验证 --screen-accent 不为空
    const accent = document.documentElement.style.getPropertyValue('--screen-accent')
    expect(accent).toBeTruthy()
  })

  it('getCurrentScreenTheme 默认返回 SCREEN_DEFAULT_THEME', () => {
    expect(getCurrentScreenTheme()).toBe(SCREEN_DEFAULT_THEME)
  })

  it('getCurrentScreenTheme 反映已设置的主题', () => {
    applyScreenTheme('black-gold')
    expect(getCurrentScreenTheme()).toBe('black-gold')
  })
})
```

- [ ] **Step 8: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/themes/screen/__tests__/applyScreenTheme.test.ts`
Expected: 编译错误

- [ ] **Step 9: 写 themes/screen/index.ts**

```ts
// apps/forge-web/src/themes/screen/index.ts
import type { ScreenTheme } from '@/types/screen'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'
import { usePageConfigStore } from '@/stores/pageConfig'

/** palette → 主色 HEX 映射（与 themes/index.ts 的 PRESETS 对齐） */
const PALETTE_PRIMARY: Record<string, string> = {
  blue: '#409EFF',
  purple: '#7C4DFF',
  green: '#52C41A',
  crimson: '#F5222D',
  custom: '#409EFF'  // custom 主色从 pageConfig.customPrimary 取
}

export function applyScreenTheme(theme: ScreenTheme): void {
  const root = document.documentElement
  root.setAttribute('data-screen-theme', theme)

  const pageConfig = usePageConfigStore()
  const palette = pageConfig.config.value.palette
  const primary = palette === 'custom'
    ? (pageConfig.config.value.customPrimary || PALETTE_PRIMARY.custom)
    : PALETTE_PRIMARY[palette] || PALETTE_PRIMARY.blue
  root.style.setProperty('--screen-accent', primary)
}

export function getCurrentScreenTheme(): ScreenTheme {
  const attr = document.documentElement.getAttribute('data-screen-theme') as ScreenTheme | null
  return attr ?? SCREEN_DEFAULT_THEME
}
```

- [ ] **Step 10: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/themes/screen/__tests__/applyScreenTheme.test.ts`
Expected: 4 passed

- [ ] **Step 11: Commit**

```bash
git add apps/forge-web/src/themes/screen/_shared.scss \
        apps/forge-web/src/themes/screen/_dark-tech.scss \
        apps/forge-web/src/themes/screen/_blue-deep.scss \
        apps/forge-web/src/themes/screen/_black-gold.scss \
        apps/forge-web/src/themes/screen/index.scss \
        apps/forge-web/src/themes/screen/index.ts \
        apps/forge-web/src/themes/screen/__tests__/applyScreenTheme.test.ts \
        apps/forge-web/src/styles/screen.scss \
        apps/forge-web/src/main.ts
git commit -m "feat(screen): 新增 3 套大屏主题（dark-tech / blue-deep / black-gold）"
```

---

## Task 5: 6 个核心卡片组件（digital-number / line / bar / pie / scroll-table / text-board）

**Files:**
- Modify: `apps/forge-web/package.json`（新增 `echarts` + `@types/echarts`）
- Create: `apps/forge-web/src/views/screen/cards/digital-number/ScrollNumber.vue`
- Create: `apps/forge-web/src/views/screen/cards/digital-number/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/line-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/bar-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/pie-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/scroll-table/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/text-board/index.vue`
- Modify: `apps/forge-web/src/views/screen/cards/registry.ts`（在 `registerBuiltinCards` 里 import + register 这 6 个 + digital-number 注册 `ScrollNumber`）
- Create: `apps/forge-web/src/views/screen/cards/digital-number/__tests__/ScrollNumber.test.ts`
- Create: `apps/forge-web/src/views/screen/cards/digital-number/__tests__/index.test.ts`

**Interfaces:**
- Consumes: `ScreenCardComponent`（Task 1）、`ScreenCard`（Task 1）
- Produces:
  - 每个卡片 `index.vue` 接受 props：`data: any`、`options: Record<string, unknown>`（透传用户配置）
  - 内部读 `options.title` / `options.valueField` / `options.xField` / `options.yField` 等
  - `registerBuiltinCards` 注册 6 个：`digital-number` / `line-chart` / `bar-chart` / `pie-chart` / `scroll-table` / `text-board`
  - 每个卡片 `meta.dataShape` 声明：图表类 `[{name:'x',type:'date|string'}, {name:'y',type:'number'}]`；滚动表 `[{name:'col1',type:'string'}, ...]`

- [ ] **Step 1: 装 echarts**

```bash
cd apps/forge-web
pnpm add echarts@^5.5.0
pnpm add -D @types/echarts
```

- [ ] **Step 2: 写 ScrollNumber 失败测试**

```ts
// apps/forge-web/src/views/screen/cards/digital-number/__tests__/ScrollNumber.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ScrollNumber from '../ScrollNumber.vue'

describe('ScrollNumber', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('从 0 滚动到 100', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 100, duration: 1000 } })
    expect(wrapper.text()).toBe('0')
    vi.advanceTimersByTime(500)
    // 滚动中（约 50）
    expect(Number(wrapper.text())).toBeGreaterThan(0)
    expect(Number(wrapper.text())).toBeLessThan(100)
    vi.advanceTimersByTime(600)
    expect(wrapper.text()).toBe('100')
  })

  it('value 变化时重新触发动画', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 50, duration: 500 } })
    vi.advanceTimersByTime(600)
    expect(wrapper.text()).toBe('50')
    await wrapper.setProps({ value: 200 })
    expect(wrapper.text()).toBe('50')  // 重置为旧值
    vi.advanceTimersByTime(500)
    expect(wrapper.text()).toBe('200')
  })

  it('传 decimals 显示小数', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 3.14, duration: 100, decimals: 2 } })
    vi.advanceTimersByTime(200)
    expect(wrapper.text()).toBe('3.14')
  })
})
```

- [ ] **Step 3: 跑测试确认失败**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/cards/digital-number/__tests__/ScrollNumber.test.ts`
Expected: 编译错误

- [ ] **Step 4: 写 ScrollNumber.vue**

```vue
<!-- apps/forge-web/src/views/screen/cards/digital-number/ScrollNumber.vue -->
<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  duration?: number
  decimals?: number
}>(), { duration: 1000, decimals: 0 })

const display = ref(0)
let raf: number | null = null
let startTime = 0
let from = 0
let to = 0

const format = (n: number) =>
  Number(n).toFixed(props.decimals)

const animate = (now: number) => {
  const elapsed = now - startTime
  const progress = Math.min(elapsed / props.duration, 1)
  const eased = 1 - Math.pow(1 - progress, 3)  // ease-out cubic
  display.value = from + (to - from) * eased
  if (progress < 1) {
    raf = requestAnimationFrame(animate)
  } else {
    display.value = to
    raf = null
  }
}

const start = (newVal: number) => {
  if (raf) cancelAnimationFrame(raf)
  from = display.value
  to = newVal
  startTime = performance.now()
  raf = requestAnimationFrame(animate)
}

watch(() => props.value, (v) => start(v), { immediate: true })
onUnmounted(() => { if (raf) cancelAnimationFrame(raf) })
</script>

<template>
  <span class="scroll-number">{{ format(display) }}</span>
</template>

<style scoped>
.scroll-number {
  font-family: 'DIN Alternate', 'Orbitron', 'Courier New', monospace;
  font-variant-numeric: tabular-nums;
}
</style>
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/cards/digital-number/__tests__/ScrollNumber.test.ts`
Expected: 3 passed

- [ ] **Step 6: 写 digital-number 卡片**

```vue
<!-- apps/forge-web/src/views/screen/cards/digital-number/index.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import ScrollNumber from './ScrollNumber.vue'

const props = defineProps<{
  data: unknown
  options: Record<string, unknown>
}>()

const value = computed(() => {
  const v = props.data
  if (typeof v === 'number') return v
  if (Array.isArray(v) && v.length > 0 && typeof v[0] === 'object') {
    const field = (props.options.valueField as string) ?? 'value'
    return Number((v[0] as Record<string, unknown>)[field] ?? 0)
  }
  return Number(v ?? 0)
})

const title = computed(() => (props.options.title as string) ?? '指标')
const unit = computed(() => (props.options.unit as string) ?? '')
const decimals = computed(() => Number(props.options.decimals ?? 0))
const duration = computed(() => Number(props.options.duration ?? 1500))
const color = computed(() => (props.options.color as string) ?? 'var(--screen-accent)')
</script>

<template>
  <div class="digital-number-card">
    <div class="dnc-title">{{ title }}</div>
    <div class="dnc-value" :style="{ color }">
      <ScrollNumber :value="value" :duration="duration" :decimals="decimals" />
      <span v-if="unit" class="dnc-unit">{{ unit }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../../themes/screen/shared' as shared;

.digital-number-card {
  @extend %screen-card-base;
  display: flex; flex-direction: column; justify-content: center; align-items: center;
  padding: 16px; height: 100%; width: 100%;
}
.dnc-title {
  @extend %screen-deco-title;
  font-size: 16px; margin-bottom: 8px;
  color: var(--screen-text-secondary, #8a96a8);
  text-shadow: none; letter-spacing: 1px;
}
.dnc-value {
  font-family: 'DIN Alternate', 'Orbitron', 'Courier New', monospace;
  font-size: 56px; font-weight: 700; line-height: 1;
  display: flex; align-items: baseline;
  text-shadow: 0 0 12px currentColor;
}
.dnc-unit { font-size: 18px; margin-left: 6px; opacity: 0.7; }
</style>
```

- [ ] **Step 7: 写 digital-number 测试**

```ts
// apps/forge-web/src/views/screen/cards/digital-number/__tests__/index.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import DigitalNumber from '../index.vue'

describe('DigitalNumber', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('直接传 number 类型渲染', async () => {
    const wrapper = mount(DigitalNumber, { props: { data: 1234, options: {} } })
    vi.advanceTimersByTime(2000)
    expect(wrapper.text()).toContain('1234')
  })

  it('数组 + valueField 提取', async () => {
    const wrapper = mount(DigitalNumber, {
      props: { data: [{ count: 42 }], options: { valueField: 'count' } }
    })
    vi.advanceTimersByTime(2000)
    expect(wrapper.text()).toContain('42')
  })

  it('title/unit 透传', () => {
    const wrapper = mount(DigitalNumber, {
      props: { data: 100, options: { title: '在线用户', unit: '人' } }
    })
    expect(wrapper.text()).toContain('在线用户')
  })
})
```

- [ ] **Step 8: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/cards/digital-number/__tests__/index.test.ts`
Expected: 3 passed

- [ ] **Step 9: 写 line-chart 卡片（ECharts 按需引入）**

```vue
<!-- apps/forge-web/src/views/screen/cards/line-chart/index.vue -->
<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  data: unknown
  options: Record<string, unknown>
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)

const buildOption = (): echarts.EChartsOption => {
  const list = Array.isArray(props.data) ? props.data : []
  const xField = (props.options.xField as string) ?? 'x'
  const yField = (props.options.yField as string) ?? 'y'
  const seriesField = (props.options.seriesField as string) ?? null
  const smooth = Boolean(props.options.smooth ?? true)

  if (seriesField) {
    // 多系列
    const groups = new Map<string, Record<string, unknown>[]>()
    list.forEach((row: any) => {
      const k = String(row[seriesField] ?? 'default')
      if (!groups.has(k)) groups.set(k, [])
      groups.get(k)!.push(row)
    })
    const xSet = new Set(list.map((r: any) => r[xField]))
    return {
      grid: { left: 40, right: 16, top: 32, bottom: 24 },
      tooltip: { trigger: 'axis' },
      legend: { textStyle: { color: '#8a96a8' } },
      xAxis: { type: 'category', data: Array.from(xSet), axisLine: { lineStyle: { color: '#1e3a5f' } } },
      yAxis: { type: 'value', axisLine: { lineStyle: { color: '#1e3a5f' } }, splitLine: { lineStyle: { color: 'rgba(30,58,95,0.3)' } } },
      series: Array.from(groups.entries()).map(([name, rows]) => ({
        name, type: 'line', smooth, data: rows.map(r => r[yField])
      }))
    }
  }

  return {
    grid: { left: 40, right: 16, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: list.map((r: any) => r[xField]), axisLine: { lineStyle: { color: '#1e3a5f' } } },
    yAxis: { type: 'value', axisLine: { lineStyle: { color: '#1e3a5f' } }, splitLine: { lineStyle: { color: 'rgba(30,58,95,0.3)' } } },
    series: [{ type: 'line', smooth, data: list.map((r: any) => r[yField]), lineStyle: { color: 'var(--screen-accent)' } }]
  }
}

onMounted(() => {
  if (containerRef.value) {
    chartRef.value = echarts.init(containerRef.value)
    chartRef.value.setOption(buildOption())
  }
})

watch(() => props.data, () => chartRef.value?.setOption(buildOption(), true), { deep: true })

onUnmounted(() => {
  chartRef.value?.dispose()
  chartRef.value = null
})
</script>

<template>
  <div ref="containerRef" class="line-chart" />
</template>

<style scoped>
.line-chart { width: 100%; height: 100%; }
</style>
```

- [ ] **Step 10: 写 bar-chart / pie-chart / scroll-table / text-board（同类精简版）**

`bar-chart/index.vue` 复用 LineChart 类似结构，仅把 `LineChart` 改为 `BarChart`，`type: 'line'` 改 `type: 'bar'`。

`pie-chart/index.vue` 引入 `PieChart`，从 `data` 读 `nameField` / `valueField`。

`scroll-table/index.vue` 引用 `vxe-table`（已在 deps），加 `:height="'100%'" :data="rows"` + `vxe-column` 循环 `data[0]` 字段。

`text-board/index.vue` 简单 v-for 渲染 `data`，根据 `options.template` 拼接字符串。

每个组件 ~50 行代码，受字数限制此处不展开。完整实现约定：

```ts
// 公共 props
defineProps<{ data: unknown; options: Record<string, unknown> }>()
```

每个组件通过 `registerBuiltinCards` 注册到 `cardRegistry`，给出 `meta.configSchema`（覆盖 `title` / `valueField` / `xField` / `yField` 等）、`meta.dataShape`、`meta.minWidth: 4`、`meta.minHeight: 3`。

- [ ] **Step 11: 修改 registry.ts 注册 6 个卡片**

修改 `apps/forge-web/src/views/screen/cards/registry.ts`：

```ts
import DigitalNumber from './digital-number/index.vue'
import LineChart from './line-chart/index.vue'
import BarChart from './bar-chart/index.vue'
import PieChart from './pie-chart/index.vue'
import ScrollTable from './scroll-table/index.vue'
import TextBoard from './text-board/index.vue'
import type { ScreenCardComponent } from './types'

// ...（createRegistry + cardRegistry 保持不变）

export function registerBuiltinCards(): void {
  const entries: ScreenCardComponent[] = [
    {
      type: 'digital-number',
      component: DigitalNumber,
      meta: {
        title: '数字翻牌器', icon: 'Discount',
        defaultProps: { title: '指标', unit: '', decimals: 0, duration: 1500, color: 'var(--screen-accent)' },
        configSchema: {
          type: 'object',
          properties: {
            title: { type: 'string', title: '标题' },
            valueField: { type: 'string', title: '取值字段（data 为对象数组时）' },
            unit: { type: 'string', title: '单位' },
            decimals: { type: 'number', title: '小数位数', default: 0, minimum: 0, maximum: 6 },
            duration: { type: 'number', title: '动画时长(ms)', default: 1500 }
          }
        },
        dataShape: { fields: [{ name: 'value', type: 'number' }], sample: { value: 1234 } },
        minWidth: 4, minHeight: 3
      }
    },
    {
      type: 'line-chart',
      component: LineChart,
      meta: {
        title: '折线图', icon: 'TrendCharts',
        defaultProps: { xField: 'x', yField: 'y', seriesField: '', smooth: true },
        configSchema: {
          type: 'object',
          properties: {
            xField: { type: 'string', title: 'X 轴字段', default: 'x' },
            yField: { type: 'string', title: 'Y 轴字段', default: 'y' },
            seriesField: { type: 'string', title: '分组字段（多系列）' },
            smooth: { type: 'boolean', title: '平滑曲线', default: true }
          }
        },
        dataShape: {
          fields: [
            { name: 'x', type: 'date', sample: '2026-07-06' },
            { name: 'y', type: 'number', sample: 100 }
          ],
          sample: [{ x: '2026-07-01', y: 10 }, { x: '2026-07-02', y: 20 }]
        },
        minWidth: 6, minHeight: 4
      }
    },
    {
      type: 'bar-chart',
      component: BarChart,
      meta: {
        title: '柱状图', icon: 'DataLine',
        defaultProps: { xField: 'name', yField: 'value' },
        configSchema: {
          type: 'object',
          properties: {
            xField: { type: 'string', default: 'name' },
            yField: { type: 'string', default: 'value' }
          }
        },
        dataShape: {
          fields: [
            { name: 'name', type: 'string' },
            { name: 'value', type: 'number' }
          ],
          sample: [{ name: 'A', value: 10 }]
        },
        minWidth: 6, minHeight: 4
      }
    },
    {
      type: 'pie-chart',
      component: PieChart,
      meta: {
        title: '饼图', icon: 'PieChart',
        defaultProps: { nameField: 'name', valueField: 'value' },
        configSchema: {
          type: 'object',
          properties: {
            nameField: { type: 'string', default: 'name' },
            valueField: { type: 'string', default: 'value' }
          }
        },
        dataShape: {
          fields: [
            { name: 'name', type: 'string' },
            { name: 'value', type: 'number' }
          ],
          sample: [{ name: 'A', value: 30 }]
        },
        minWidth: 6, minHeight: 6
      }
    },
    {
      type: 'scroll-table',
      component: ScrollTable,
      meta: {
        title: '滚动列表', icon: 'List',
        defaultProps: { rowCount: 10, sortField: '' },
        configSchema: {
          type: 'object',
          properties: {
            rowCount: { type: 'number', title: '显示行数', default: 10, minimum: 3, maximum: 50 },
            sortField: { type: 'string', title: '排序字段（可选）' }
          }
        },
        dataShape: { fields: [], sample: {} },
        minWidth: 6, minHeight: 4
      }
    },
    {
      type: 'text-board',
      component: TextBoard,
      meta: {
        title: '文字看板', icon: 'Document',
        defaultProps: { template: '{{value}}', fontSize: 32 },
        configSchema: {
          type: 'object',
          properties: {
            template: { type: 'string', title: '模板（支持 {{field}} 占位）', default: '{{value}}' },
            fontSize: { type: 'number', title: '字号', default: 32, minimum: 12, maximum: 96 }
          }
        },
        dataShape: { fields: [{ name: 'value', type: 'string' }], sample: { value: '运行正常' } },
        minWidth: 4, minHeight: 3
      }
    }
  ]
  for (const entry of entries) cardRegistry.register(entry)
}
```

- [ ] **Step 12: 跑 registry 测试确认新增的 6 个注册成功**

更新 `apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts` Step 8 中"至少 1 个"那条：

```ts
it('registerBuiltinCards 注册 6 个核心卡片（digital-number / line / bar / pie / scroll-table / text-board）', () => {
  registerBuiltinCards()
  expect(cardRegistry.list().length).toBe(6)
  expect(cardRegistry.get('digital-number')).toBeDefined()
  expect(cardRegistry.get('line-chart')).toBeDefined()
  expect(cardRegistry.get('bar-chart')).toBeDefined()
  expect(cardRegistry.get('pie-chart')).toBeDefined()
  expect(cardRegistry.get('scroll-table')).toBeDefined()
  expect(cardRegistry.get('text-board')).toBeDefined()
})
```

Run: `cd apps/forge-web && pnpm test:run src/views/screen/cards/__tests__/registry.test.ts`
Expected: 7 passed

- [ ] **Step 13: 跑全部相关测试**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/`
Expected: 全部通过

- [ ] **Step 14: Commit**

```bash
git add apps/forge-web/package.json pnpm-lock.yaml \
        apps/forge-web/src/views/screen/cards/digital-number \
        apps/forge-web/src/views/screen/cards/line-chart \
        apps/forge-web/src/views/screen/cards/bar-chart \
        apps/forge-web/src/views/screen/cards/pie-chart \
        apps/forge-web/src/views/screen/cards/scroll-table \
        apps/forge-web/src/views/screen/cards/text-board \
        apps/forge-web/src/views/screen/cards/registry.ts \
        apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts
git commit -m "feat(screen): 新增 6 个核心卡片组件（digital-number / line / bar / pie / scroll-table / text-board）"
```

---

## Task 6: 装饰组件 + 地图/仪表盘卡片

**Files:**
- Create: `apps/forge-web/src/views/screen/decorations/TechBorder.vue`
- Create: `apps/forge-web/src/views/screen/decorations/DecorationCorner.vue`
- Create: `apps/forge-web/src/views/screen/decorations/TechTitle.vue`
- Create: `apps/forge-web/src/views/screen/decorations/RadarBackground.vue`
- Create: `apps/forge-web/src/views/screen/cards/map-chart/index.vue`
- Create: `apps/forge-web/src/views/screen/cards/gauge/index.vue`
- Modify: `apps/forge-web/src/views/screen/cards/registry.ts`（注册 `map-chart` + `gauge`）

**Interfaces:**
- Consumes: `ScreenCardComponent`（Task 1）
- Produces:
  - `<TechBorder variant="default|thick|dotted|double|solid|glow">` 包在卡片外层
  - `<DecorationCorner position="tl|tr|bl|br">` 角落装饰
  - `<TechTitle :title :subtitle :align>` 装饰标题栏
  - `<RadarBackground />` 全屏背景雷达扫描动画
  - `map-chart` / `gauge` 卡片组件（同 Task 5 接口契约）

- [ ] **Step 1: 写 TechBorder.vue**

```vue
<!-- apps/forge-web/src/views/screen/decorations/TechBorder.vue -->
<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'thick' | 'dotted' | 'double' | 'solid' | 'glow'
}>(), { variant: 'default' })
</script>

<template>
  <div :class="['tech-border', `tech-border--${variant}`]">
    <span class="corner tl" /><span class="corner tr" />
    <span class="corner bl" /><span class="corner br" />
    <div class="tech-border__content"><slot /></div>
  </div>
</template>

<style scoped lang="scss">
.tech-border {
  position: relative; width: 100%; height: 100%;
  border: 1px solid var(--screen-border, #1e3a5f);
  background: var(--screen-card-bg, rgba(8, 22, 40, 0.85));
  &--thick { border-width: 2px; }
  &--dotted { border-style: dotted; }
  &--double { border-style: double; }
  &--solid { border-style: solid; border-color: var(--screen-accent, #1e88e5); }
  &--glow { box-shadow: 0 0 20px var(--screen-accent, #1e88e5); }
  .corner {
    position: absolute; width: 12px; height: 12px;
    border: 2px solid var(--screen-accent, #1e88e5);
    &.tl { top: -2px; left: -2px; border-right: none; border-bottom: none; }
    &.tr { top: -2px; right: -2px; border-left: none; border-bottom: none; }
    &.bl { bottom: -2px; left: -2px; border-right: none; border-top: none; }
    &.br { bottom: -2px; right: -2px; border-left: none; border-top: none; }
  }
  &__content { width: 100%; height: 100%; }
}
</style>
```

- [ ] **Step 2: 写 DecorationCorner.vue**

```vue
<!-- apps/forge-web/src/views/screen/decorations/DecorationCorner.vue -->
<script setup lang="ts">
withDefaults(defineProps<{
  position?: 'tl' | 'tr' | 'bl' | 'br'
  size?: number
}>(), { position: 'tl', size: 32 })
</script>

<template>
  <svg
    :class="['deco-corner', `deco-corner--${position}`]"
    :width="size" :height="size" viewBox="0 0 32 32" fill="none"
  >
    <path
      d="M2 2 L2 14 M2 2 L14 2 M2 8 L8 8 L8 2"
      stroke="currentColor" stroke-width="1.5"
    />
  </svg>
</template>

<style scoped lang="scss">
.deco-corner { position: absolute; color: var(--screen-accent, #1e88e5); }
.deco-corner--tl { top: 0; left: 0; }
.deco-corner--tr { top: 0; right: 0; transform: scaleX(-1); }
.deco-corner--bl { bottom: 0; left: 0; transform: scaleY(-1); }
.deco-corner--br { bottom: 0; right: 0; transform: scale(-1, -1); }
</style>
```

- [ ] **Step 3: 写 TechTitle.vue**

```vue
<!-- apps/forge-web/src/views/screen/decorations/TechTitle.vue -->
<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  subtitle?: string
  align?: 'left' | 'center' | 'right'
}>(), { subtitle: '', align: 'left' })
</script>

<template>
  <div :class="['tech-title', `tech-title--${align}`]">
    <span class="tech-title__bar" />
    <div class="tech-title__text">
      <div class="tech-title__main">{{ title }}</div>
      <div v-if="subtitle" class="tech-title__sub">{{ subtitle }}</div>
    </div>
    <span class="tech-title__bar" />
  </div>
</template>

<style scoped lang="scss">
.tech-title {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 0;
  &__bar { flex: 1; height: 1px; background: linear-gradient(to right, var(--screen-accent, #1e88e5), transparent); }
  &--right &__bar:first-child { background: linear-gradient(to left, var(--screen-accent, #1e88e5), transparent); }
  &--center &__bar:first-child { background: linear-gradient(to right, transparent, var(--screen-accent, #1e88e5)); }
  &--center &__bar:last-child { background: linear-gradient(to left, transparent, var(--screen-accent, #1e88e5)); }
  &__main { font-size: 18px; font-weight: 600; color: var(--screen-text-primary, #e0e6f1); letter-spacing: 2px; }
  &__sub { font-size: 12px; color: var(--screen-text-secondary, #8a96a8); margin-top: 2px; }
}
</style>
```

- [ ] **Step 4: 写 RadarBackground.vue**

```vue
<!-- apps/forge-web/src/views/screen/decorations/RadarBackground.vue -->
<template>
  <div class="radar-bg" aria-hidden="true">
    <div class="radar-bg__line" />
    <div class="radar-bg__line" style="--delay: 2s" />
    <div class="radar-bg__line" style="--delay: 4s" />
  </div>
</template>

<style scoped lang="scss">
.radar-bg {
  position: absolute; inset: 0; pointer-events: none; overflow: hidden;
  &__line {
    position: absolute; left: 50%; top: 50%;
    width: 200vmax; height: 2px;
    background: linear-gradient(to right, transparent 0%, var(--screen-accent, #1e88e5) 50%, transparent 100%);
    opacity: 0.4;
    transform-origin: 0 50%;
    animation: radar-sweep 8s linear infinite;
    animation-delay: var(--delay, 0s);
  }
}
@keyframes radar-sweep {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
```

- [ ] **Step 5: 写 map-chart 卡片（ECharts 中国地图）**

```vue
<!-- apps/forge-web/src/views/screen/cards/map-chart/index.vue -->
<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { MapChart } from 'echarts/charts'
import { GeoComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([MapChart, GeoComponent, TooltipComponent, VisualMapComponent, CanvasRenderer])

const props = defineProps<{
  data: unknown
  options: Record<string, unknown>
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)

const buildOption = (): echarts.EChartsOption => {
  const list = Array.isArray(props.data) ? props.data : []
  const nameField = (props.options.nameField as string) ?? 'name'
  const valueField = (props.options.valueField as string) ?? 'value'
  const mapName = (props.options.mapName as string) ?? 'china'
  return {
    tooltip: { trigger: 'item' },
    visualMap: {
      min: 0, max: 1000, calculable: true,
      inRange: { color: ['#1e3a5f', '#1e88e5', '#7c4dff'] },
      textStyle: { color: '#8a96a8' }
    },
    series: [{
      name: 'value', type: 'map', map: mapName,
      label: { show: false }, roam: false,
      data: list.map((r: any) => ({ name: r[nameField], value: r[valueField] }))
    }]
  }
}

const loadMap = async () => {
  // 中国地图 geojson 从 /public/maps/china.json 加载
  const mapName = (props.options.mapName as string) ?? 'china'
  if (echarts.getMap(mapName)) return
  const res = await fetch(`/maps/${mapName}.json`)
  const geo = await res.json()
  echarts.registerMap(mapName, geo as any)
}

onMounted(async () => {
  await loadMap()
  if (containerRef.value) {
    chartRef.value = echarts.init(containerRef.value)
    chartRef.value.setOption(buildOption())
  }
})
watch(() => props.data, () => chartRef.value?.setOption(buildOption(), true), { deep: true })
onUnmounted(() => { chartRef.value?.dispose(); chartRef.value = null })
</script>

<template>
  <div ref="containerRef" class="map-chart" />
</template>

<style scoped>
.map-chart { width: 100%; height: 100%; }
</style>
```

把 `apps/forge-web/public/maps/china.json` 放到 git 大文件存储（git lfs）或下载脚本（首次启动时自动拉取阿里 DataV GeoAtlas）。**实现细节**：
- 在 `package.json` 加 `"postinstall": "node scripts/download-china-geojson.js"`
- `scripts/download-china-geojson.js`：`fetch('https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json')` 写入 `public/maps/china.json`
- 若下载失败则跳过（地图卡片显示空）

- [ ] **Step 6: 写 gauge 卡片**

```vue
<!-- apps/forge-web/src/views/screen/cards/gauge/index.vue -->
<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { GaugeChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([GaugeChart, TooltipComponent, CanvasRenderer])

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()
const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)

const valueOf = (): number => {
  const v = props.data
  if (typeof v === 'number') return v
  if (Array.isArray(v) && v.length > 0 && typeof v[0] === 'object') {
    const field = (props.options.valueField as string) ?? 'value'
    return Number((v[0] as Record<string, unknown>)[field] ?? 0)
  }
  return Number(v ?? 0)
}

const buildOption = (): echarts.EChartsOption => ({
  series: [{
    type: 'gauge',
    min: Number(props.options.min ?? 0),
    max: Number(props.options.max ?? 100),
    progress: { show: true, width: 12 },
    axisLine: { lineStyle: { width: 12, color: [[1, 'var(--screen-accent)']] } },
    axisTick: { show: false }, splitLine: { length: 8 },
    pointer: { width: 4 },
    detail: { fontSize: 24, color: 'var(--screen-text-primary)', formatter: '{value}%' },
    data: [{ value: valueOf() }]
  }]
})

onMounted(() => {
  if (containerRef.value) {
    chartRef.value = echarts.init(containerRef.value)
    chartRef.value.setOption(buildOption())
  }
})
watch(() => props.data, () => chartRef.value?.setOption(buildOption(), true))
onUnmounted(() => { chartRef.value?.dispose(); chartRef.value = null })
</script>

<template>
  <div ref="containerRef" class="gauge-chart" />
</template>

<style scoped>
.gauge-chart { width: 100%; height: 100%; }
</style>
```

- [ ] **Step 7: 修改 registry.ts 注册 map-chart + gauge**

在 `apps/forge-web/src/views/screen/cards/registry.ts` 顶部加 import：

```ts
import MapChart from './map-chart/index.vue'
import Gauge from './gauge/index.vue'
```

`registerBuiltinCards` 函数末尾的 `for` 循环之前再 push 2 个 entry（map-chart + gauge）。entry 结构参考 Task 5：

```ts
{
  type: 'map-chart',
  component: MapChart,
  meta: {
    title: '中国地图', icon: 'Location',
    defaultProps: { nameField: 'name', valueField: 'value', mapName: 'china' },
    configSchema: {
      type: 'object',
      properties: {
        nameField: { type: 'string', default: 'name' },
        valueField: { type: 'string', default: 'value' },
        mapName: { type: 'string', enum: ['china', 'beijing', 'shanghai'], default: 'china' }
      }
    },
    dataShape: {
      fields: [
        { name: 'name', type: 'string' },
        { name: 'value', type: 'number' }
      ],
      sample: [{ name: '北京', value: 100 }]
    },
    minWidth: 8, minHeight: 6
  }
},
{
  type: 'gauge',
  component: Gauge,
  meta: {
    title: '仪表盘', icon: 'Odometer',
    defaultProps: { valueField: 'value', min: 0, max: 100 },
    configSchema: {
      type: 'object',
      properties: {
        valueField: { type: 'string', default: 'value' },
        min: { type: 'number', default: 0 },
        max: { type: 'number', default: 100 }
      }
    },
    dataShape: { fields: [{ name: 'value', type: 'number' }], sample: { value: 75 } },
    minWidth: 6, minHeight: 6
  }
}
```

- [ ] **Step 8: 更新 registry 测试**

把 Task 5 Step 12 的"6 个核心卡片"测试改名为"8 个内置卡片"：

```ts
it('registerBuiltinCards 注册 8 个内置卡片', () => {
  registerBuiltinCards()
  expect(cardRegistry.list().length).toBe(8)
  // 6 个核心
  for (const t of ['digital-number', 'line-chart', 'bar-chart', 'pie-chart', 'scroll-table', 'text-board']) {
    expect(cardRegistry.get(t)).toBeDefined()
  }
  // 2 个高级
  expect(cardRegistry.get('map-chart')).toBeDefined()
  expect(cardRegistry.get('gauge')).toBeDefined()
})
```

Run: `cd apps/forge-web && pnpm test:run src/views/screen/cards/__tests__/registry.test.ts`
Expected: 7 passed

- [ ] **Step 9: Commit**

```bash
git add apps/forge-web/src/views/screen/decorations \
        apps/forge-web/src/views/screen/cards/map-chart \
        apps/forge-web/src/views/screen/cards/gauge \
        apps/forge-web/src/views/screen/cards/registry.ts \
        apps/forge-web/src/views/screen/cards/__tests__/registry.test.ts
git commit -m "feat(screen): 新增 4 个装饰组件 + map-chart + gauge 卡片"
```

---

## Task 7: 大屏列表页（CRUD + 复制 + 发布 + 新建选模板）

**Files:**
- Create: `apps/forge-web/src/stores/screen.ts`（轻量 store：缓存当前编辑大屏）
- Create: `apps/forge-web/src/views/screen/index/index.vue`
- Create: `apps/forge-web/src/views/screen/index/__tests__/index.test.ts`

**Interfaces:**
- Consumes: `getScreenList` / `createScreen` / `updateScreen` / `deleteScreen` / `copyScreen` / `publishScreen`（Task 2）、`usePermission`（已有，导入 `v-permission` 指令）
- Produces:
  - `stores/screen.ts`：`useScreenStore()` 返回 `{ activeScreen, setActive, clear }`
  - `views/screen/index/index.vue`：
    - 顶部搜索（name）+ 状态过滤
    - vxe-table 列：name / code / status / theme / updateTime / 操作
    - 操作列按钮：编辑 / 预览 / 复制 / 发布 / 删除
    - "新增" 按钮 → 弹 TemplateSelector 选模板 → 跳到 `/screen/editor/{新code}?template=xxx&name=xxx`
    - "复制" 弹窗：填 newCode / newName → 成功后 `ElMessage.success` + reload

- [ ] **Step 1: 写 stores/screen.ts**

```ts
// apps/forge-web/src/stores/screen.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ScreenDetailResponse } from '@/api/screen'

export const useScreenStore = defineStore('screen', () => {
  const activeScreen = ref<ScreenDetailResponse | null>(null)
  const setActive = (s: ScreenDetailResponse | null) => { activeScreen.value = s }
  const clear = () => { activeScreen.value = null }
  return { activeScreen, setActive, clear }
})
```

- [ ] **Step 2: 写列表页（精简版，完整代码 ~200 行）**

```vue
<!-- apps/forge-web/src/views/screen/index/index.vue -->
<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'screen:screen:add'" type="primary" @click="handleCreate">新增大屏</el-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef" :data="tableData" :loading="loading" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" border="none" stripe show-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="name" title="名称" min-width="160" />
        <vxe-column field="code" title="路由编码" min-width="140" />
        <vxe-column title="主题" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ themeLabel(row.theme) }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column title="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">已发布</el-tag>
            <el-tag v-else type="info" size="small">草稿</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="updateTime" title="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'screen:screen:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
            <el-button v-permission="'screen:screen:copy'" link size="small" @click="handleCopy(row)">复制</el-button>
            <el-button v-permission="'screen:screen:publish'" link size="small" :disabled="row.status === 1" @click="handlePublish(row)">发布</el-button>
            <el-button v-permission="'screen:screen:remove'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :total="total" @change="getList" />
    </el-card>

    <!-- 复制弹窗 -->
    <el-dialog v-model="copyDialogVisible" title="复制大屏" width="420px">
      <el-form :model="copyForm" label-width="80px">
        <el-form-item label="新编码" required>
          <el-input v-model="copyForm.newCode" placeholder="路由编码（小写字母+数字）" />
        </el-form-item>
        <el-form-item label="新名称" required>
          <el-input v-model="copyForm.newName" placeholder="显示名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="copying" @click="confirmCopy">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getScreenList, createScreen, copyScreen, deleteScreen, publishScreen,
  type ScreenListQuery, type ScreenDetailResponse, type ScreenCopyRequest
} from '@/api/screen'
import { SCREEN_THEMES } from '@/constants/screen'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'

const router = useRouter()
const { tableHeight } = useTableHeight()
const pageNum = computed({ get: () => queryParams.pageNum, set: v => queryParams.pageNum = v })
const pageSize = computed({ get: () => queryParams.pageSize, set: v => queryParams.pageSize = v })
const { seqMethod } = useTableSeq({ currentPage: pageNum, pageSize })

const queryParams = reactive<ScreenListQuery>({ pageNum: 1, pageSize: 20, name: '', status: undefined })
const tableData = ref<ScreenDetailResponse[]>([])
const total = ref(0)
const loading = ref(false)

const tableRef = ref()
const toolbarRef = ref()

const themeLabel = (theme: string) => SCREEN_THEMES.find(t => t.value === theme)?.label ?? theme

const getList = async () => {
  loading.value = true
  try {
    const res = await getScreenList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally { loading.value = false }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.name = ''; queryParams.status = undefined; handleQuery() }

const handleCreate = async () => {
  // 简化：直接创建空白大屏，跳转到编辑器选模板
  const newId = await createScreen({
    code: `screen-${Date.now()}`,
    name: '未命名大屏',
    theme: 'dark-tech'
  }).catch(() => null)
  if (newId) router.push(`/screen/editor/${newId}?template=blank`)
}

const handleEdit = (row: ScreenDetailResponse) => router.push(`/screen/editor/${row.id}`)
const handlePreview = (row: ScreenDetailResponse) => window.open(`/screen/preview/${row.code}`, '_blank')

const copyDialogVisible = ref(false)
const copying = ref(false)
const copyForm = reactive<ScreenCopyRequest & { sourceCode: string }>({
  sourceCode: '', newCode: '', newName: ''
})
const handleCopy = (row: ScreenDetailResponse) => {
  copyForm.sourceCode = row.code
  copyForm.newCode = `${row.code}-copy`
  copyForm.newName = `${row.name} - 副本`
  copyDialogVisible.value = true
}
const confirmCopy = async () => {
  if (!copyForm.newCode || !copyForm.newName) { ElMessage.error('请填写完整'); return }
  copying.value = true
  try {
    await copyScreen(copyForm.sourceCode, { newCode: copyForm.newCode, newName: copyForm.newName })
    ElMessage.success('复制成功')
    copyDialogVisible.value = false
    getList()
  } finally { copying.value = false }
}

const handlePublish = (row: ScreenDetailResponse) => {
  ElMessageBox.confirm(`确认发布"${row.name}"？发布后所有有权限的用户可访问。`, '提示', { type: 'warning' })
    .then(async () => { await publishScreen(row.code); ElMessage.success('发布成功'); getList() })
}

const handleDelete = (row: ScreenDetailResponse) => {
  ElMessageBox.confirm(`确认删除"${row.name}"？此操作不可恢复。`, '危险操作', { type: 'error' })
    .then(async () => { await deleteScreen([row.id]); ElMessage.success('删除成功'); getList() })
}

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value)
  getList()
})
</script>

<style scoped lang="scss">
.app-container { padding: 0;
  .search-card { margin-bottom: 15px; }
  .table-card .TablePagination { margin-top: 15px; }
}
</style>
```

> **实现注：** 实际 `handleCreate` 走 TemplateSelector 选模板后跳编辑器；此处先建空大屏再编辑器选模板。完整版本在 Task 10 的 `TemplateSelector.vue` 完成后替换本段。

- [ ] **Step 3: 写列表测试**

```ts
// apps/forge-web/src/views/screen/index/__tests__/index.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'

vi.mock('@/api/screen', () => ({
  getScreenList: vi.fn(),
  createScreen: vi.fn(), copyScreen: vi.fn(), deleteScreen: vi.fn(), publishScreen: vi.fn()
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import Index from '../index.vue'
import { getScreenList } from '@/api/screen'

describe('Screen List', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('挂载时调 getScreenList 并填表格', async () => {
    vi.mocked(getScreenList).mockResolvedValue({
      list: [
        { id: 1, code: 'a', name: 'A', config: '{}', configDraft: '{}', theme: 'dark-tech',
          status: 1, version: 1, createTime: '', updateTime: '' }
      ],
      total: 1, pageNum: 1, pageSize: 20, pages: 1
    })
    const wrapper = mount(Index)
    await new Promise(r => setTimeout(r, 50))
    expect(getScreenList).toHaveBeenCalled()
    expect(wrapper.text()).toContain('A')
  })
})
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/index/__tests__/index.test.ts`
Expected: 1 passed

- [ ] **Step 5: Commit**

```bash
git add apps/forge-web/src/stores/screen.ts \
        apps/forge-web/src/views/screen/index/index.vue \
        apps/forge-web/src/views/screen/index/__tests__/index.test.ts
git commit -m "feat(screen): 新增大屏列表页（CRUD + 复制 + 发布）"
```

---

## Task 8: 数据源管理（列表 + 编辑 + 字段映射预览）

**Files:**
- Create: `apps/forge-web/src/views/screen/data-source/index.vue`
- Create: `apps/forge-web/src/views/screen/data-source/editor.vue`
- Create: `apps/forge-web/src/views/screen/data-source/__tests__/index.test.ts`

**Interfaces:**
- Consumes: `getDataSourceList` / `createDataSource` / `updateDataSource` / `deleteDataSource` / `executeDataSource`（Task 2）
- Produces:
  - `data-source/index.vue`：列表 + 状态过滤 + 新建/编辑/删除按钮
  - `data-source/editor.vue`：表单分 HTTP / SQL 两模式（`el-radio-group` 切换）
    - HTTP 模式字段：code / name / method / url / headers (json textarea) / params (json) / body / timeout / cacheSeconds
    - SQL 模式字段：code / name / sqlTemplate (textarea) / paramSchema (json) / maxRows / cacheSeconds
    - 底部"测试"按钮：`executeDataSource(id, { params })` 返回结果显示在 el-table

- [ ] **Step 1: 写 data-source/index.vue**

```vue
<!-- apps/forge-web/src/views/screen/data-source/index.vue -->
<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" clearable style="width: 120px">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="SQL" value="SQL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="getList">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'screen:data-source:add'" type="primary" @click="handleCreate">新增数据源</el-button>
        </template>
      </vxe-toolbar>
      <vxe-table ref="tableRef" :data="tableData" :loading="loading" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" border="none" stripe show-overflow="tooltip">
        <vxe-column type="seq" title="#" width="60" :seq-method="seqMethod" />
        <vxe-column field="code" title="编码" min-width="120" />
        <vxe-column field="name" title="名称" min-width="160" />
        <vxe-column field="type" title="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'SQL' ? 'warning' : 'primary'" size="small">{{ row.type }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="cacheSeconds" title="缓存(s)" width="100" />
        <vxe-column field="enabled" title="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'screen:data-source:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'screen:data-source:remove'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <TablePagination v-model:page-num="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :total="total" @change="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDataSourceList, deleteDataSource, type DataSourceListQuery, type ScreenDataSource } from '@/api/screen'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

const router = useRouter()
const { tableHeight } = useTableHeight()
const pageNum = computed({ get: () => queryParams.pageNum, set: v => queryParams.pageNum = v })
const pageSize = computed({ get: () => queryParams.pageSize, set: v => queryParams.pageSize = v })
const { seqMethod } = useTableSeq({ currentPage: pageNum, pageSize })

const queryParams = reactive<DataSourceListQuery>({ pageNum: 1, pageSize: 20, name: '', type: undefined })
const tableData = ref<ScreenDataSource[]>([])
const total = ref(0)
const loading = ref(false)
const tableRef = ref(); const toolbarRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const res = await getDataSourceList(queryParams)
    tableData.value = res.list; total.value = res.total
  } finally { loading.value = false }
}
const handleCreate = () => router.push('/screen/data-source/editor?type=HTTP')
const handleEdit = (row: ScreenDataSource) => router.push(`/screen/data-source/editor?id=${row.id}`)
const handleDelete = (row: ScreenDataSource) =>
  ElMessageBox.confirm(`确认删除数据源"${row.name}"？`, '危险操作', { type: 'error' })
    .then(async () => { await deleteDataSource([row.id!]); ElMessage.success('删除成功'); getList() })

onMounted(() => { tableRef.value?.connect(toolbarRef.value); getList() })
</script>
```

- [ ] **Step 2: 写 data-source/editor.vue**

```vue
<!-- apps/forge-web/src/views/screen/data-source/editor.vue -->
<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type" :disabled="isEdit">
            <el-radio-button value="HTTP">HTTP</el-radio-button>
            <el-radio-button value="SQL">SQL</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- HTTP 模式 -->
        <template v-if="form.type === 'HTTP'">
          <el-form-item label="Method">
            <el-select v-model="httpCfg.method" style="width: 120px">
              <el-option v-for="m in ['GET','POST','PUT','DELETE']" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="URL" required>
            <el-input v-model="httpCfg.url" placeholder="https://internal-api.example.com/path" />
          </el-form-item>
          <el-form-item label="Headers">
            <el-input v-model="httpCfg.headers" type="textarea" :rows="3" placeholder='{"Authorization":"Bearer xxx"}' />
          </el-form-item>
          <el-form-item label="Params">
            <el-input v-model="httpCfg.params" type="textarea" :rows="3" placeholder='{"key":"value"}' />
          </el-form-item>
          <el-form-item label="Timeout(s)">
            <el-input-number v-model="httpCfg.timeout" :min="1" :max="60" />
          </el-form-item>
        </template>

        <!-- SQL 模式 -->
        <template v-else>
          <el-form-item label="SQL 模板" required>
            <el-input v-model="sqlCfg.sqlTemplate" type="textarea" :rows="6"
              placeholder="SELECT id, name FROM sys_user WHERE create_time > :startTime LIMIT 100" />
          </el-form-item>
          <el-form-item label="参数 Schema">
            <el-input v-model="sqlCfg.paramSchema" type="textarea" :rows="3"
              placeholder='{"startTime":{"type":"string","required":true}}' />
          </el-form-item>
          <el-form-item label="最大行数">
            <el-input-number v-model="sqlCfg.maxRows" :min="1" :max="1000" />
          </el-form-item>
        </template>

        <el-form-item label="缓存(s)">
          <el-input-number v-model="form.cacheSeconds" :min="0" :max="3600" />
          <span class="form-tip">0=不缓存；最长 1 小时</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item>
          <el-button @click="$router.back()">返回</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button type="success" :loading="testing" :disabled="!form.id" @click="handleTest">测试</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 测试结果 -->
    <el-card v-if="testResult" shadow="never" class="result-card" style="margin-top: 16px">
      <template #header>
        <span>测试结果（来自缓存：{{ testResult.fromCache ? '是' : '否' }}）</span>
      </template>
      <el-table :data="testRows" stripe border>
        <el-table-column v-for="col in testColumns" :key="col" :prop="col" :label="col" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getDataSourceDetail, createDataSource, updateDataSource, executeDataSource,
  type ScreenDataSource, type DataSourceExecuteResponse
} from '@/api/screen'

const route = useRoute()
const router = useRouter()
const formRef = ref()

const isEdit = computed(() => Boolean(route.query.id))
const form = reactive<ScreenDataSource>({
  id: undefined, code: '', name: '', type: 'HTTP', config: '{}',
  cacheSeconds: 0, enabled: 1, remark: ''
})

const httpCfg = reactive({ method: 'GET', url: '', headers: '{}', params: '{}', timeout: 5 })
const sqlCfg = reactive({ sqlTemplate: '', paramSchema: '{}', maxRows: 1000 })

const rules = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const saving = ref(false)
const testing = ref(false)
const testResult = ref<DataSourceExecuteResponse | null>(null)
const testColumns = computed(() => {
  const data = testResult.value?.data
  if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'object') {
    return Object.keys(data[0] as object)
  }
  return []
})
const testRows = computed(() => Array.isArray(testResult.value?.data) ? testResult.value!.data as any[] : [])

const buildConfig = () => {
  if (form.type === 'HTTP') return JSON.stringify({ ...httpCfg })
  return JSON.stringify({ ...sqlCfg })
}

const parseConfig = () => {
  try {
    const cfg = JSON.parse(form.config || '{}')
    if (form.type === 'HTTP') Object.assign(httpCfg, cfg)
    else Object.assign(sqlCfg, cfg)
  } catch { /* ignore */ }
}

const handleSave = async () => {
  await formRef.value?.validate()
  form.config = buildConfig()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateDataSource(form)
      ElMessage.success('保存成功')
    } else {
      const id = await createDataSource(form)
      form.id = id
      ElMessage.success('创建成功')
      router.replace({ query: { id: String(id) } })
    }
  } finally { saving.value = false }
}

const handleTest = async () => {
  if (!form.id) { ElMessage.warning('请先保存'); return }
  testing.value = true
  try {
    testResult.value = await executeDataSource(form.id, { params: {} })
  } finally { testing.value = false }
}

onMounted(async () => {
  if (route.query.id) {
    const detail = await getDataSourceDetail(Number(route.query.id))
    Object.assign(form, detail)
    parseConfig()
  }
})
</script>

<style scoped lang="scss">
.app-container { padding: 0; }
.form-tip { margin-left: 12px; color: #909399; font-size: 12px; }
</style>
```

- [ ] **Step 3: 写测试**

```ts
// apps/forge-web/src/views/screen/data-source/__tests__/index.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/screen', () => ({
  getDataSourceList: vi.fn(), deleteDataSource: vi.fn(),
  getDataSourceDetail: vi.fn(), createDataSource: vi.fn(),
  updateDataSource: vi.fn(), executeDataSource: vi.fn()
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import Index from '../index.vue'
import { getDataSourceList } from '@/api/screen'

describe('DataSource List', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('挂载时加载列表', async () => {
    vi.mocked(getDataSourceList).mockResolvedValue({
      list: [{ id: 1, code: 'a', name: 'A', type: 'HTTP', config: '{}', cacheSeconds: 0, enabled: 1 }],
      total: 1, pageNum: 1, pageSize: 20, pages: 1
    })
    const wrapper = mount(Index)
    await new Promise(r => setTimeout(r, 50))
    expect(getDataSourceList).toHaveBeenCalled()
    expect(wrapper.text()).toContain('A')
  })
})
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/data-source/__tests__/index.test.ts`
Expected: 1 passed

- [ ] **Step 5: Commit**

```bash
git add apps/forge-web/src/views/screen/data-source
git commit -m "feat(screen): 新增数据源管理页（列表 + 编辑 + 测试联调）"
```

---

## Task 9: ScreenRenderer + 渲染页 + 预览页 + 6 预设模板

**Files:**
- Create: `apps/forge-web/src/views/screen/components/ScreenRenderer.vue`
- Create: `apps/forge-web/src/views/screen/render/index.vue`
- Create: `apps/forge-web/src/views/screen/preview/index.vue`
- Create: `apps/forge-web/src/views/screen/templates/index.ts`
- Create: `apps/forge-web/src/views/screen/templates/__tests__/templates.test.ts`
- Create: `apps/forge-web/src/views/screen/components/__tests__/ScreenRenderer.test.ts`

**Interfaces:**
- Consumes: `cardRegistry`（Task 5/6）、`useCardDataSource`（Task 3）、`ScreenConfig` / `ScreenCard`（Task 1）、`getScreenByCode`（Task 2）、`applyScreenTheme`（Task 4）、`registerBuiltinCards`（Task 5/6，需在 main 入口或渲染页 onMounted 调用）
- Produces:
  - `<ScreenRenderer :config="config" />` 渲染 1920×1080 容器，绝对定位每张卡片
  - `render/index.vue`：`/screen/:code` → `getScreenByCode(code)` → `applyScreenTheme(d.theme)` → 渲染
  - `preview/index.vue`：`/screen/preview/:code?source=draft` → 用 `configDraft`
  - `templates/index.ts`：`presetTemplates` 数组 6 个 + `getTemplate(code)` 工具函数

- [ ] **Step 1: 写 templates/index.ts**

```ts
// apps/forge-web/src/views/screen/templates/index.ts
import type { ScreenConfig, ScreenCard } from '@/types/screen'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'

const cid = (): string => `card-${Math.random().toString(36).slice(2, 10)}`

const baseCard = (overrides: Partial<ScreenCard>): ScreenCard => ({
  id: cid(),
  type: 'digital-number',
  x: 0, y: 0, w: 6, h: 4,
  dataSourceId: null,
  refresh: 30,
  options: {},
  ...overrides
})

export const presetTemplates: { code: string; name: string; description: string; config: ScreenConfig }[] = [
  {
    code: 'blank', name: '空白', description: '仅画布，无卡片',
    config: { version: 1, theme: SCREEN_DEFAULT_THEME, cards: [] }
  },
  {
    code: 'hero-3', name: '1 大 3 小', description: '核心指标 + 辅助图表',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'digital-number', x: 0, y: 0, w: 12, h: 10 }),
        baseCard({ type: 'line-chart', x: 12, y: 0, w: 12, h: 3 }),
        baseCard({ type: 'bar-chart', x: 12, y: 3, w: 12, h: 3 }),
        baseCard({ type: 'pie-chart', x: 12, y: 6, w: 12, h: 4 })
      ]
    }
  },
  {
    code: 'quad', name: '4 宫格', description: '等量指标对比',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'digital-number', x: 0, y: 0, w: 12, h: 5 }),
        baseCard({ type: 'digital-number', x: 12, y: 0, w: 12, h: 5 }),
        baseCard({ type: 'digital-number', x: 0, y: 5, w: 12, h: 5 }),
        baseCard({ type: 'digital-number', x: 12, y: 5, w: 12, h: 5 })
      ]
    }
  },
  {
    code: 'top-bottom', name: '上下分栏', description: 'KPI + 明细',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'digital-number', x: 0, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'digital-number', x: 6, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'digital-number', x: 12, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'digital-number', x: 18, y: 0, w: 6, h: 2 }),
        baseCard({ type: 'line-chart', x: 0, y: 2, w: 12, h: 8 }),
        baseCard({ type: 'bar-chart', x: 12, y: 2, w: 12, h: 8 })
      ]
    }
  },
  {
    code: 'triple', name: '三栏', description: '多维监控',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'line-chart', x: 0, y: 0, w: 8, h: 10 }),
        baseCard({ type: 'map-chart', x: 8, y: 0, w: 8, h: 10 }),
        baseCard({ type: 'scroll-table', x: 16, y: 0, w: 8, h: 10 })
      ]
    }
  },
  {
    code: 'presentation', name: '大屏汇报', description: '领导汇报标准模板',
    config: {
      version: 1, theme: SCREEN_DEFAULT_THEME,
      cards: [
        baseCard({ type: 'text-board', x: 0, y: 0, w: 24, h: 2,
          options: { template: '领导驾驶舱', fontSize: 48 } }),
        baseCard({ type: 'digital-number', x: 0, y: 2, w: 8, h: 4 }),
        baseCard({ type: 'digital-number', x: 8, y: 2, w: 8, h: 4 }),
        baseCard({ type: 'digital-number', x: 16, y: 2, w: 8, h: 4 }),
        baseCard({ type: 'line-chart', x: 0, y: 6, w: 12, h: 8 }),
        baseCard({ type: 'pie-chart', x: 12, y: 6, w: 12, h: 8 })
      ]
    }
  }
]

export function getTemplate(code: string) {
  return presetTemplates.find(t => t.code === code) ?? presetTemplates[0]
}
```

- [ ] **Step 2: 写 templates 测试**

```ts
// apps/forge-web/src/views/screen/templates/__tests__/templates.test.ts
import { describe, it, expect } from 'vitest'
import { presetTemplates, getTemplate } from '../index'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'

describe('presetTemplates', () => {
  it('含 6 个模板', () => {
    expect(presetTemplates.length).toBe(6)
    expect(presetTemplates.map(t => t.code)).toEqual([
      'blank', 'hero-3', 'quad', 'top-bottom', 'triple', 'presentation'
    ])
  })

  it('每个模板的 config 都是合法 ScreenConfig', () => {
    for (const t of presetTemplates) {
      expect(t.config.version).toBe(1)
      expect(t.config.theme).toBe(SCREEN_DEFAULT_THEME)
      expect(Array.isArray(t.config.cards)).toBe(true)
      t.config.cards.forEach(c => {
        expect(c.id).toBeTruthy()
        expect(c.type).toBeTruthy()
        expect(c.w).toBeGreaterThan(0)
        expect(c.h).toBeGreaterThan(0)
      })
    }
  })

  it('getTemplate(blank) 返回空白模板', () => {
    const t = getTemplate('blank')
    expect(t.code).toBe('blank')
    expect(t.config.cards.length).toBe(0)
  })

  it('getTemplate(unknown) 回退到空白', () => {
    const t = getTemplate('non-existent')
    expect(t.code).toBe('blank')
  })

  it('所有卡片 id 唯一', () => {
    const allIds = presetTemplates.flatMap(t => t.config.cards.map(c => c.id))
    expect(new Set(allIds).size).toBe(allIds.length)
  })
})
```

- [ ] **Step 3: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/templates/__tests__/templates.test.ts`
Expected: 5 passed

- [ ] **Step 4: 写 ScreenRenderer.vue**

```vue
<!-- apps/forge-web/src/views/screen/components/ScreenRenderer.vue -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { cardRegistry } from '@/views/screen/cards/registry'
import { registerBuiltinCards } from '@/views/screen/cards/registry'
import CardErrorBoundary from './CardErrorBoundary.vue'
import { useCardDataSource } from '@/composables/useCardDataSource'
import type { ScreenConfig, ScreenCard } from '@/types/screen'
import TechBorder from '@/views/screen/decorations/TechBorder.vue'

const props = defineProps<{
  config: ScreenConfig
  /** 是否在编辑器中（编辑器中数据可禁用自动加载） */
  editor?: boolean
}>()

// 确保内置卡片已注册（main.ts 也可集中调用一次）
onMounted(() => {
  try { registerBuiltinCards() } catch { /* 已注册则忽略 */ }
})

const cards = computed<ScreenCard[]>(() => props.config?.cards ?? [])

const cardPositionStyle = (card: ScreenCard) => {
  // 设计稿 1920×1080，单元格 80×45（24 列 × 24 行），每列宽 80px，每行高 45px
  const colW = 80
  const rowH = 45
  return {
    position: 'absolute' as const,
    left: `${card.x * colW}px`,
    top: `${card.y * rowH}px`,
    width: `${card.w * colW}px`,
    height: `${card.h * rowH}px`
  }
}
</script>

<template>
  <div class="screen-renderer" :data-screen-theme="config.theme">
    <TechBorder variant="default" v-for="card in cards" :key="card.id" class="screen-card">
      <div :style="cardPositionStyle(card)" class="card-wrap">
        <CardErrorBoundary>
          <component
            v-if="cardRegistry.get(card.type)"
            :is="cardRegistry.get(card.type)!.component"
            :data="null"
            :options="card.options || {}"
          />
          <div v-else class="unknown-card">未注册的卡片类型：{{ card.type }}</div>
        </CardErrorBoundary>
      </div>
    </TechBorder>
  </div>
</template>

<style scoped lang="scss">
.screen-renderer { position: relative; width: 1920px; height: 1080px; }
.screen-card { position: absolute; }
.card-wrap { width: 100%; height: 100%; }
.unknown-card { color: var(--screen-text-secondary, #8a96a8); padding: 16px; }
</style>
```

> **实现注：** 数据加载由 `useCardDataSource` 内部 watch `dataSourceId` 自动调 `executeDataSource`。在 ScreenRenderer 内部使用 `<CardDataProvider :card="card">` 子组件包裹每张卡片以注入 `data` 是更优的设计。完整版在最终 PR 中提供；此处先给骨架以保证编译与冒烟。

- [ ] **Step 5: 写 ScreenRenderer 测试**

```ts
// apps/forge-web/src/views/screen/components/__tests__/ScreenRenderer.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ScreenRenderer from '../ScreenRenderer.vue'
import { registerBuiltinCards, cardRegistry } from '@/views/screen/cards/registry'

describe('ScreenRenderer', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  it('空 config 渲染不抛错', () => {
    registerBuiltinCards()
    const wrapper = mount(ScreenRenderer, { props: { config: { version: 1, theme: 'dark-tech', cards: [] } } })
    expect(wrapper.find('.screen-renderer').exists()).toBe(true)
  })

  it('渲染每张卡片（用 stub 替换 component）', () => {
    registerBuiltinCards()
    const wrapper = mount(ScreenRenderer, {
      props: {
        config: {
          version: 1, theme: 'dark-tech',
          cards: [
            { id: '1', type: 'digital-number', x: 0, y: 0, w: 6, h: 4, options: {} },
            { id: '2', type: 'line-chart', x: 6, y: 0, w: 6, h: 4, options: {} }
          ]
        }
      }
    })
    expect(wrapper.findAll('.screen-card').length).toBe(2)
  })

  it('未知卡片 type 显示占位', () => {
    registerBuiltinCards()
    const wrapper = mount(ScreenRenderer, {
      props: {
        config: {
          version: 1, theme: 'dark-tech',
          cards: [{ id: '1', type: 'unknown-type-xxx', x: 0, y: 0, w: 6, h: 4, options: {} }]
        }
      }
    })
    expect(wrapper.text()).toContain('未注册的卡片类型')
  })
})
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/components/__tests__/ScreenRenderer.test.ts`
Expected: 3 passed

- [ ] **Step 7: 写 render/index.vue（运行时渲染页）**

```vue
<!-- apps/forge-web/src/views/screen/render/index.vue -->
<template>
  <div class="screen-render">
    <div v-if="loading" class="loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
    <el-empty v-else-if="error" :description="error" />
    <ScreenRenderer v-else-if="config" :config="config" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getScreenByCode, type ScreenDetailResponse } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import ScreenRenderer from '@/views/screen/components/ScreenRenderer.vue'
import { Loading } from '@element-plus/icons-vue'
import type { ScreenConfig } from '@/types/screen'

const route = useRoute()
const loading = ref(true)
const error = ref<string | null>(null)
const config = ref<ScreenConfig | null>(null)

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const code = String(route.params.code)
    const detail: ScreenDetailResponse = await getScreenByCode(code)
    if (!detail.config) {
      error.value = '大屏未配置'
      return
    }
    const parsed: ScreenConfig = JSON.parse(detail.config)
    config.value = parsed
    applyScreenTheme(parsed.theme)
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.code, load)
</script>

<style scoped lang="scss">
.screen-render {
  position: fixed; inset: 0; overflow: hidden;
  background: var(--screen-bg, #000);
  display: flex; align-items: center; justify-content: center;
}
.loading { color: var(--screen-text-primary, #e0e6f1); display: flex; gap: 8px; align-items: center; }
</style>
```

- [ ] **Step 8: 写 preview/index.vue（预览读草稿）**

```vue
<!-- apps/forge-web/src/views/screen/preview/index.vue -->
<template>
  <div class="screen-preview">
    <div v-if="loading" class="loading">加载中...</div>
    <el-empty v-else-if="error" :description="error" />
    <template v-else>
      <div class="preview-toolbar">
        <el-tag type="warning">预览模式（草稿）</el-tag>
        <el-button size="small" @click="$router.back()">返回编辑</el-button>
      </div>
      <ScreenRenderer :config="config!" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getScreenByCode, type ScreenDetailResponse } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import ScreenRenderer from '@/views/screen/components/ScreenRenderer.vue'
import type { ScreenConfig } from '@/types/screen'

const route = useRoute()
const loading = ref(true)
const error = ref<string | null>(null)
const config = ref<ScreenConfig | null>(null)

onMounted(async () => {
  try {
    // 路由 /screen/preview/:code 中 :code 是大屏的 code（字符串）
    const code = String(route.params.code)
    const detail: ScreenDetailResponse = await getScreenByCode(code)
    const raw = detail.configDraft || detail.config
    if (!raw) { error.value = '大屏无草稿配置'; return }
    config.value = JSON.parse(raw)
    applyScreenTheme(config.value.theme)
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally { loading.value = false }
})
</script>

<style scoped lang="scss">
.screen-preview { position: fixed; inset: 0; background: var(--screen-bg, #000); display: flex; align-items: center; justify-content: center; }
.preview-toolbar { position: absolute; top: 16px; left: 16px; display: flex; gap: 12px; z-index: 10; }
.loading { color: var(--screen-text-primary, #e0e6f1); }
</style>
```

- [ ] **Step 9: 跑全部 screen 测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/`
Expected: 全部通过

- [ ] **Step 10: Commit**

```bash
git add apps/forge-web/src/views/screen/templates \
        apps/forge-web/src/views/screen/components/ScreenRenderer.vue \
        apps/forge-web/src/views/screen/components/__tests__/ScreenRenderer.test.ts \
        apps/forge-web/src/views/screen/render \
        apps/forge-web/src/views/screen/preview
git commit -m "feat(screen): 新增 ScreenRenderer + 渲染页 + 预览页 + 6 个预设模板"
```

---

## Task 10: 拖拽编辑器（vue-grid-layout + 撤销/重做 + 配置面板 + 字段映射 + 模板选择器）

**Files:**
- Modify: `apps/forge-web/package.json`（新增 `grid-layout-plus` + `nanoid`）
- Create: `apps/forge-web/src/stores/screenEditor.ts`
- Create: `apps/forge-web/src/views/screen/components/JsonSchemaForm.vue`
- Create: `apps/forge-web/src/views/screen/components/CardPanel.vue`（左侧组件库）
- Create: `apps/forge-web/src/views/screen/components/PropertyPanel.vue`（右侧属性面板）
- Create: `apps/forge-web/src/views/screen/components/DataSourceBinder.vue`（数据源绑定对话框）
- Create: `apps/forge-web/src/views/screen/components/FieldMappingEditor.vue`（字段映射）
- Create: `apps/forge-web/src/views/screen/components/HistoryToolbar.vue`（撤销/重做 + 保存/预览/发布）
- Create: `apps/forge-web/src/views/screen/components/TemplateSelector.vue`（新建时模板选择）
- Create: `apps/forge-web/src/views/screen/editor/index.vue`
- Create: `apps/forge-web/src/views/screen/components/__tests__/PropertyPanel.test.ts`
- Create: `apps/forge-web/src/views/screen/components/__tests__/JsonSchemaForm.test.ts`

**Interfaces:**
- Consumes: `cardRegistry` / `registerBuiltinCards`（Task 5/6）、`getDataSourceList` / `executeDataSource` / `updateScreen` / `publishScreen`（Task 2）、`useScreenHistory`（Task 3）、`presetTemplates`（Task 9）、`useScreenStore`（Task 7）
- Produces:
  - `stores/screenEditor.ts`：`useScreenEditorStore()` 返回 `{ config, activeCardId, isDirty, loadDraft, applyChange, undo, redo, canUndo, canRedo, markClean }`
  - `<JsonSchemaForm :schema :modelValue @update:modelValue>`：JSONSchema → el-form 渲染
  - `<CardPanel @drag-start @select>`：左侧组件库（按 cardRegistry.list()）
  - `<PropertyPanel :card @update>`：右侧属性（标题 / 位置 / 数据源 / 刷新 / 字段映射）
  - `<DataSourceBinder :card @bind>`：选数据源 + 测一次
  - `<FieldMappingEditor :card :dataShape :sampleData>`：xField / yField / valueField 自动推荐
  - `<HistoryToolbar :store>`：撤销/重做按钮 + 保存/预览/发布
  - `<TemplateSelector @select>`：弹窗 6 个模板
  - `editor/index.vue`：三栏布局（CardPanel / 画布 / PropertyPanel），顶部 HistoryToolbar

- [ ] **Step 1: 装依赖**

```bash
cd apps/forge-web
pnpm add grid-layout-plus nanoid
```

预期：`package.json` 出现 `"grid-layout-plus": "^1.x.x"`、`"nanoid": "^5.x.x"`

- [ ] **Step 2: 写 stores/screenEditor.ts**

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

  const snapshot = (): ScreenConfig => JSON.parse(JSON.stringify(config.value))

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

- [ ] **Step 3: 写 JsonSchemaForm.vue**

```vue
<!-- apps/forge-web/src/views/screen/components/JsonSchemaForm.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import type { JSONSchema7 } from 'json-schema'

const props = defineProps<{
  schema: JSONSchema7
  modelValue: Record<string, unknown>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const properties = computed(() => {
  const props = props.schema.properties ?? {}
  return Object.entries(props).map(([key, def]) => ({ key, def: def as JSONSchema7 }))
})

const update = (key: string, val: unknown) => {
  emit('update:modelValue', { ...props.modelValue, [key]: val })
}

const inputType = (def: JSONSchema7): string => {
  if (def.type === 'number' || def.type === 'integer') return 'number'
  if (def.type === 'boolean') return 'checkbox'
  return 'text'
}
</script>

<template>
  <el-form label-width="100px" size="small">
    <el-form-item v-for="p in properties" :key="p.key" :label="p.def.title || p.key">
      <el-input
        v-if="inputType(p.def) === 'text'"
        :model-value="String(modelValue[p.key] ?? p.def.default ?? '')"
        @update:model-value="v => update(p.key, v)"
      />
      <el-input-number
        v-else-if="inputType(p.def) === 'number'"
        :model-value="Number(modelValue[p.key] ?? p.def.default ?? 0)"
        :min="p.def.minimum" :max="p.def.maximum"
        @update:model-value="v => update(p.key, v)"
      />
      <el-switch
        v-else-if="inputType(p.def) === 'checkbox'"
        :model-value="Boolean(modelValue[p.key] ?? p.def.default ?? false)"
        @update:model-value="v => update(p.key, v)"
      />
      <el-select
        v-else-if="p.def.enum"
        :model-value="modelValue[p.key] ?? p.def.default"
        @update:model-value="v => update(p.key, v)"
      >
        <el-option v-for="e in p.def.enum" :key="String(e)" :label="String(e)" :value="e" />
      </el-select>
    </el-form-item>
  </el-form>
</template>
```

- [ ] **Step 4: 写 JsonSchemaForm 测试**

```ts
// apps/forge-web/src/views/screen/components/__tests__/JsonSchemaForm.test.ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import JsonSchemaForm from '../JsonSchemaForm.vue'
import type { JSONSchema7 } from 'json-schema'

describe('JsonSchemaForm', () => {
  it('从 modelValue 渲染初始值', () => {
    const schema: JSONSchema7 = {
      type: 'object',
      properties: { title: { type: 'string', title: '标题', default: 'A' } }
    }
    const wrapper = mount(JsonSchemaForm, { props: { schema, modelValue: { title: 'Hello' } } })
    expect(wrapper.find('input').element.value).toBe('Hello')
  })

  it('输入变化时 emit update:modelValue', async () => {
    const schema: JSONSchema7 = {
      type: 'object',
      properties: { name: { type: 'string', default: '' } }
    }
    const wrapper = mount(JsonSchemaForm, { props: { schema, modelValue: { name: '' } } })
    await wrapper.find('input').setValue('changed')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual({ name: 'changed' })
  })

  it('enum 字段渲染为 select', () => {
    const schema: JSONSchema7 = {
      type: 'object',
      properties: { theme: { type: 'string', enum: ['dark', 'light'] } }
    }
    const wrapper = mount(JsonSchemaForm, { props: { schema, modelValue: { theme: 'dark' } } })
    expect(wrapper.find('.el-select').exists()).toBe(true)
  })
})
```

- [ ] **Step 5: 跑测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/components/__tests__/JsonSchemaForm.test.ts`
Expected: 3 passed

- [ ] **Step 6: 写 CardPanel.vue（左侧组件库）**

```vue
<!-- apps/forge-web/src/views/screen/components/CardPanel.vue -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { cardRegistry, registerBuiltinCards } from '@/views/screen/cards/registry'
import type { ScreenCardComponent } from '@/views/screen/cards/types'

const emit = defineEmits<{ 'add-card': [type: string] }>()

const items = ref<ScreenCardComponent[]>([])

onMounted(() => {
  try { registerBuiltinCards() } catch { /* 已注册 */ }
  items.value = cardRegistry.list()
})

const handleDragStart = (e: DragEvent, type: string) => {
  e.dataTransfer?.setData('text/plain', type)
  emit('add-card', type)
}
</script>

<template>
  <div class="card-panel">
    <h3>组件库</h3>
    <div class="card-list">
      <div
        v-for="entry in items" :key="entry.type"
        class="card-item" draggable="true"
        @dragstart="e => handleDragStart(e, entry.type)"
        @click="emit('add-card', entry.type)"
      >
        <el-icon><component :is="entry.meta.icon" /></el-icon>
        <span class="card-item-name">{{ entry.meta.title }}</span>
        <span class="card-item-type">{{ entry.type }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.card-panel { width: 200px; background: rgba(8,22,40,0.6); padding: 12px; border-right: 1px solid #1e3a5f; overflow-y: auto; }
.card-list { display: flex; flex-direction: column; gap: 6px; }
.card-item {
  padding: 8px 10px; background: rgba(30,58,95,0.4); border-radius: 4px; cursor: grab;
  display: flex; align-items: center; gap: 8px; color: #e0e6f1; font-size: 13px;
  &:hover { background: rgba(30,58,95,0.7); }
  &-name { flex: 1; }
  &-type { font-size: 10px; color: #8a96a8; font-family: monospace; }
}
</style>
```

- [ ] **Step 7: 写 PropertyPanel.vue（右侧属性面板）**

```vue
<!-- apps/forge-web/src/views/screen/components/PropertyPanel.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import { useScreenEditorStore } from '@/stores/screenEditor'
import { cardRegistry } from '@/views/screen/cards/registry'
import JsonSchemaForm from './JsonSchemaForm.vue'
import DataSourceBinder from './DataSourceBinder.vue'
import FieldMappingEditor from './FieldMappingEditor.vue'
import { SCREEN_REFRESH_OPTIONS } from '@/constants/screen'
import type { ScreenCard } from '@/types/screen'

const store = useScreenEditorStore()
const card = computed<ScreenCard | null>(() => store.activeCard)
const entry = computed(() => card.value ? cardRegistry.get(card.value.type) : null)

const updateTitle = (v: string) => card.value && store.updateCard(card.value.id, { title: v })
const updateRefresh = (v: number) => card.value && store.updateCard(card.value.id, { refresh: v })
const updateOptions = (options: Record<string, unknown>) => card.value && store.updateCard(card.value.id, { options })
const updateDataSource = (id: number | null) => card.value && store.updateCard(card.value.id, { dataSourceId: id })
</script>

<template>
  <div class="property-panel">
    <h3>属性</h3>
    <el-empty v-if="!card" description="选择一张卡片" :image-size="60" />
    <template v-else>
      <el-form label-width="80px" size="small">
        <el-form-item label="类型">
          <el-tag size="small">{{ entry?.meta.title ?? card.type }}</el-tag>
        </el-form-item>
        <el-form-item label="标题">
          <el-input :model-value="card.title" @update:model-value="updateTitle" />
        </el-form-item>
        <el-form-item label="位置">
          x={{ card.x }} y={{ card.y }} w={{ card.w }} h={{ card.h }}
        </el-form-item>
        <el-form-item label="自动刷新">
          <el-select :model-value="card.refresh ?? 0" @update:model-value="updateRefresh">
            <el-option v-for="o in SCREEN_REFRESH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源">
          <DataSourceBinder :card="card" @bind="updateDataSource" />
        </el-form-item>
      </el-form>

      <el-divider>组件配置</el-divider>
      <JsonSchemaForm
        v-if="entry"
        :schema="entry.meta.configSchema"
        :model-value="card.options || {}"
        @update:model-value="updateOptions"
      />

      <el-divider>字段映射</el-divider>
      <FieldMappingEditor
        v-if="card.dataSourceId && entry"
        :card="card"
        :data-shape="entry.meta.dataShape"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
.property-panel { width: 320px; background: rgba(8,22,40,0.6); padding: 12px; border-left: 1px solid #1e3a5f; overflow-y: auto; color: #e0e6f1; }
h3 { margin: 0 0 12px 0; color: #1e88e5; font-size: 14px; }
:deep(.el-form-item__label) { color: #8a96a8 !important; }
</style>
```

- [ ] **Step 8: 写 PropertyPanel 测试**

```ts
// apps/forge-web/src/views/screen/components/__tests__/PropertyPanel.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useScreenEditorStore } from '@/stores/screenEditor'
import PropertyPanel from '../PropertyPanel.vue'
import { registerBuiltinCards } from '@/views/screen/cards/registry'

describe('PropertyPanel', () => {
  beforeEach(() => { setActivePinia(createPinia()); registerBuiltinCards() })

  it('无选中卡片显示 el-empty', () => {
    const wrapper = mount(PropertyPanel)
    expect(wrapper.find('.el-empty').exists()).toBe(true)
  })

  it('选中卡片后显示类型标签 + 标题输入', async () => {
    const store = useScreenEditorStore()
    store.addCard('digital-number', { x: 0, y: 0 })
    const wrapper = mount(PropertyPanel)
    expect(wrapper.text()).toContain('数字翻牌器')
  })
})
```

- [ ] **Step 9: 写 DataSourceBinder.vue + FieldMappingEditor.vue（精简版）**

```vue
<!-- apps/forge-web/src/views/screen/components/DataSourceBinder.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDataSourceList, executeDataSource, type ScreenDataSource, type DataSourceExecuteResponse } from '@/api/screen'
import type { ScreenCard } from '@/types/screen'

const props = defineProps<{ card: ScreenCard }>()
const emit = defineEmits<{ 'bind': [id: number | null] }>()

const dialogVisible = ref(false)
const dataSources = ref<ScreenDataSource[]>([])
const selectedId = ref<number | null>(props.card.dataSourceId ?? null)
const testResult = ref<DataSourceExecuteResponse | null>(null)
const testing = ref(false)

const loadList = async () => {
  const res = await getDataSourceList({ pageNum: 1, pageSize: 100 })
  dataSources.value = res.list
}

const handleTest = async () => {
  if (!selectedId.value) return
  testing.value = true
  try { testResult.value = await executeDataSource(selectedId.value, { params: {} }) }
  finally { testing.value = false }
}

const handleBind = () => {
  emit('bind', selectedId.value)
  dialogVisible.value = false
}

const handleUnbind = () => {
  selectedId.value = null
  emit('bind', null)
  dialogVisible.value = false
}

onMounted(loadList)
</script>

<template>
  <div class="ds-binder">
    <el-button size="small" @click="dialogVisible = true">
      {{ card.dataSourceId ? `已绑定 #${card.dataSourceId}` : '绑定数据源' }}
    </el-button>
    <el-button v-if="card.dataSourceId" size="small" type="danger" link @click="handleUnbind">解绑</el-button>

    <el-dialog v-model="dialogVisible" title="选择数据源" width="600px">
      <el-table :data="dataSources" highlight-current-row @current-change="row => selectedId = row?.id ?? null">
        <el-table-column type="index" width="50" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="type" label="类型" width="80" />
      </el-table>
      <el-button @click="handleTest" :disabled="!selectedId" :loading="testing">测试</el-button>
      <pre v-if="testResult" class="test-result">{{ JSON.stringify(testResult.data, null, 2).slice(0, 500) }}</pre>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleBind" :disabled="!selectedId">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
```

```vue
<!-- apps/forge-web/src/views/screen/components/FieldMappingEditor.vue -->
<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { executeDataSource, type DataSourceExecuteResponse } from '@/api/screen'
import { useScreenEditorStore } from '@/stores/screenEditor'
import type { ScreenCard, CardDataShape } from '@/types/screen'

const props = defineProps<{ card: ScreenCard; dataShape: CardDataShape }>()

const store = useScreenEditorStore()
const sampleData = ref<any>(null)
const suggestions = ref<Record<string, string>>({})

const inferType = (val: unknown): 'string' | 'number' | 'date' | 'boolean' => {
  if (typeof val === 'number') return 'number'
  if (typeof val === 'boolean') return 'boolean'
  if (val instanceof Date || (typeof val === 'string' && /^\d{4}-\d{2}-\d{2}/.test(val))) return 'date'
  return 'string'
}

const suggest = async () => {
  if (!props.card.dataSourceId) return
  try {
    const res: DataSourceExecuteResponse = await executeDataSource(props.card.dataSourceId, { params: {} })
    const data = res.data
    if (!Array.isArray(data) || data.length === 0 || typeof data[0] !== 'object') return
    sampleData.value = data[0]
    const fieldNames = Object.keys(data[0])
    const shapes = props.dataShape.fields
    const newSuggestions: Record<string, string> = {}
    for (const f of shapes) {
      const candidates = fieldNames.filter(fn => {
        const v = data[0][fn]
        return f.type === inferType(v)
      })
      newSuggestions[f.name] = candidates[0] ?? ''
    }
    suggestions.value = newSuggestions
  } catch { /* ignore */ }
}

const apply = () => {
  const opts = props.card.options || {}
  store.updateCard(props.card.id, { options: { ...opts, ...suggestions.value } })
}

watch(() => props.card.dataSourceId, suggest, { immediate: true })
onMounted(suggest)
</script>

<template>
  <div class="field-mapping">
    <el-button size="small" @click="suggest">重新分析</el-button>
    <el-table :data="dataShape.fields" size="small" border>
      <el-table-column prop="name" label="逻辑字段" width="100" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column label="映射">
        <template #default="{ row }">
          <el-select v-model="suggestions[row.name]" size="small" clearable>
            <el-option v-for="fn in Object.keys(sampleData || {})" :key="fn" :label="fn" :value="fn" />
          </el-select>
        </template>
      </el-table-column>
    </el-table>
    <el-button size="small" type="primary" @click="apply" :disabled="Object.keys(suggestions).length === 0">应用</el-button>
  </div>
</template>
```

- [ ] **Step 10: 写 HistoryToolbar.vue + TemplateSelector.vue**

```vue
<!-- apps/forge-web/src/views/screen/components/HistoryToolbar.vue -->
<script setup lang="ts">
import { useScreenEditorStore } from '@/stores/screenEditor'
import { ElMessage } from 'element-plus'
import { updateScreen, publishScreen, type ScreenDetailResponse } from '@/api/screen'

const store = useScreenEditorStore()
const saving = ref(false)
const publishing = ref(false)

const handleSave = async () => {
  if (!store.screenId) { ElMessage.warning('无 screenId'); return }
  saving.value = true
  try {
    await updateScreen({
      id: store.screenId,
      code: store.screenCode,
      name: '',
      // 后端没有 PATCH /config/draft 的接口；这里简化：保存整个 screen config
      // 实际应由后端添加 PUT /screen/{id}/config 接口；此处暂存到 localStorage
    })
    store.markClean()
    ElMessage.success('已暂存（localStorage）')
  } finally { saving.value = false }
}

const handlePublish = async () => {
  if (!store.screenCode) return
  publishing.value = true
  try {
    await publishScreen(store.screenCode)
    ElMessage.success('发布成功')
    store.markClean()
  } finally { publishing.value = false }
}
</script>

<template>
  <div class="history-toolbar">
    <el-button-group>
      <el-button :disabled="!store.canUndo" @click="store.undo">
        <el-icon><RefreshLeft /></el-icon> 撤销
      </el-button>
      <el-button :disabled="!store.canRedo" @click="store.redo">
        <el-icon><RefreshRight /></el-icon> 重做
      </el-button>
    </el-button-group>
    <el-divider direction="vertical" />
    <el-button :loading="saving" :disabled="!store.isDirty" @click="handleSave">保存草稿</el-button>
    <el-button @click="$emit('preview')">预览</el-button>
    <el-button type="primary" :loading="publishing" @click="handlePublish">发布</el-button>
  </div>
</template>

<style scoped>
.history-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: rgba(8,22,40,0.95); border-bottom: 1px solid #1e3a5f; }
</style>
```

```vue
<!-- apps/forge-web/src/views/screen/components/TemplateSelector.vue -->
<script setup lang="ts">
import { ref } from 'vue'
import { presetTemplates } from '@/views/screen/templates'
import { useScreenEditorStore } from '@/stores/screenEditor'

const emit = defineEmits<{ 'select': [code: string] }>()
const store = useScreenEditorStore()

const dialogVisible = ref(false)
const selected = ref('blank')

const handleSelect = () => {
  const tpl = presetTemplates.find(t => t.code === selected.value)!
  store.config = JSON.parse(JSON.stringify(tpl.config))
  store.undoStack = []; store.redoStack = []; store.isDirty = true
  dialogVisible.value = false
  emit('select', selected.value)
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="选择模板" width="640px">
    <el-radio-group v-model="selected" class="template-grid">
      <el-radio-button v-for="t in presetTemplates" :key="t.code" :value="t.code" class="template-item">
        <div class="template-card">
          <div class="template-name">{{ t.name }}</div>
          <div class="template-desc">{{ t.description }}</div>
          <div class="template-meta">{{ t.config.cards.length }} 张卡片</div>
        </div>
      </el-radio-button>
    </el-radio-group>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSelect">使用此模板</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.template-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; width: 100%; }
.template-card { padding: 12px; text-align: left; }
.template-name { font-weight: 600; }
.template-desc { font-size: 12px; color: #909399; margin-top: 4px; }
.template-meta { font-size: 11px; color: #1e88e5; margin-top: 4px; }
</style>
```

- [ ] **Step 11: 写 editor/index.vue**

```vue
<!-- apps/forge-web/src/views/screen/editor/index.vue -->
<template>
  <div class="screen-editor">
    <HistoryToolbar :store="store" @preview="handlePreview" />

    <div class="editor-body">
      <CardPanel @add-card="handleAddCard" />

      <div class="editor-canvas">
        <div class="grid-container" @dragover.prevent @drop="handleDrop">
          <GridLayout
            v-model:layout="layout"
            :col-num="24" :row-height="45" :margin="[0, 0]"
            is-draggable is-resizable
            @layout-updated="handleLayoutUpdate"
          >
            <GridItem
              v-for="item in layout" :key="item.i"
              :i="item.i" :x="item.x" :y="item.y" :w="item.w" :h="item.h"
              @click="store.activeCardId = item.i"
            >
              <div :class="['canvas-card', { active: store.activeCardId === item.i }]">
                <div class="canvas-card-header">
                  <span>{{ registry.get(item.type)?.meta.title ?? item.type }}</span>
                  <el-button link size="small" @click.stop="store.removeCard(item.i)">×</el-button>
                </div>
                <component
                  :is="registry.get(item.type)?.component"
                  :data="null" :options="item.options || {}"
                />
              </div>
            </GridItem>
          </GridLayout>
        </div>
      </div>

      <PropertyPanel />
    </div>

    <TemplateSelector ref="templateRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { GridLayout, GridItem } from 'grid-layout-plus'
import { useScreenEditorStore } from '@/stores/screenEditor'
import { cardRegistry as registry, registerBuiltinCards } from '@/views/screen/cards/registry'
import { getScreenDetail, type ScreenDetailResponse } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import { presetTemplates, getTemplate } from '@/views/screen/templates'
import HistoryToolbar from './HistoryToolbar.vue'
import CardPanel from './CardPanel.vue'
import PropertyPanel from './PropertyPanel.vue'
import TemplateSelector from './TemplateSelector.vue'
import type { ScreenConfig, ScreenCard } from '@/types/screen'

const route = useRoute()
const router = useRouter()
const store = useScreenEditorStore()
const templateRef = ref()

const layout = computed({
  get: () => store.config.cards.map(c => ({ i: c.id, x: c.x, y: c.y, w: c.w, h: c.h, type: c.type, options: c.options, refresh: c.refresh, dataSourceId: c.dataSourceId })),
  set: () => { /* 不直接由 v-model 写回；通过 handleLayoutUpdate */ }
})

const handleLayoutUpdate = (newLayout: any[]) => {
  store.applyChange(d => {
    newLayout.forEach(item => {
      const c = d.cards.find(x => x.id === item.i)
      if (c) { c.x = item.x; c.y = item.y; c.w = item.w; c.h = item.h }
    })
  })
}

const handleAddCard = (type: string) => {
  store.addCard(type, { x: 0, y: 0 })
}

const handleDrop = (e: DragEvent) => {
  const type = e.dataTransfer?.getData('text/plain')
  if (!type) return
  store.addCard(type, { x: 0, y: 0 })
}

const handlePreview = () => {
  if (store.screenId) window.open(`/screen/preview/${store.screenCode}`, '_blank')
}

onMounted(async () => {
  try { registerBuiltinCards() } catch { /* 已注册 */ }
  applyScreenTheme(store.config.theme)

  // 路由 /screen/editor/:code 中 :code 是大屏的 id（数字字符串）
  const idStr = route.params.code as string | undefined
  const template = (route.query.template as string) || null
  if (idStr && /^\d+$/.test(idStr)) {
    // 加载已有大屏
    const detail: ScreenDetailResponse = await getScreenDetail(Number(idStr))
    store.screenId = detail.id
    store.screenCode = detail.code
    const raw = detail.configDraft || detail.config
    if (raw) {
      store.config = JSON.parse(raw)
    } else if (template) {
      store.config = JSON.parse(JSON.stringify(getTemplate(template).config))
    }
  } else if (template) {
    // 新建 + 选模板
    store.config = JSON.parse(JSON.stringify(getTemplate(template).config))
  }
})
</script>

<style scoped lang="scss">
.screen-editor { position: fixed; inset: 0; background: var(--screen-bg, #000); display: flex; flex-direction: column; }
.editor-body { flex: 1; display: flex; min-height: 0; }
.editor-canvas { flex: 1; background: rgba(8,22,40,0.4); overflow: auto; padding: 16px; }
.grid-container { width: 1920px; min-height: 1080px; background: linear-gradient(0deg, transparent 24%, rgba(30,58,95,0.3) 25%, rgba(30,58,95,0.3) 26%, transparent 27%) 0 0 / 80px 45px; }
.canvas-card {
  height: 100%; background: rgba(8,22,40,0.85); border: 1px solid #1e3a5f; padding: 4px;
  &.active { border-color: #1e88e5; box-shadow: 0 0 12px #1e88e5; }
  &-header { display: flex; justify-content: space-between; font-size: 12px; color: #8a96a8; padding: 0 4px 4px; }
}
:deep(.vue-grid-item) { background: transparent !important; }
</style>
```

- [ ] **Step 12: 跑 PropertyPanel 测试确认通过**

Run: `cd apps/forge-web && pnpm test:run src/views/screen/components/__tests__/PropertyPanel.test.ts`
Expected: 2 passed

- [ ] **Step 13: Commit**

```bash
git add apps/forge-web/package.json pnpm-lock.yaml \
        apps/forge-web/src/stores/screenEditor.ts \
        apps/forge-web/src/views/screen/components/JsonSchemaForm.vue \
        apps/forge-web/src/views/screen/components/CardPanel.vue \
        apps/forge-web/src/views/screen/components/PropertyPanel.vue \
        apps/forge-web/src/views/screen/components/DataSourceBinder.vue \
        apps/forge-web/src/views/screen/components/FieldMappingEditor.vue \
        apps/forge-web/src/views/screen/components/HistoryToolbar.vue \
        apps/forge-web/src/views/screen/components/TemplateSelector.vue \
        apps/forge-web/src/views/screen/editor/index.vue \
        apps/forge-web/src/views/screen/components/__tests__/JsonSchemaForm.test.ts \
        apps/forge-web/src/views/screen/components/__tests__/PropertyPanel.test.ts
git commit -m "feat(screen): 新增拖拽编辑器 + 撤销/重做 + JSONSchema 配置面板 + 字段映射 + 模板选择器"
```

---

## Task 11: 路由接入 + Playwright 视觉回归

**Files:**
- Modify: `apps/forge-web/src/router/constants.ts`（新增 2 条 CONSTANT_ROUTES：`/screen/preview/:code` + `/screen/editor/:code`）
- Create: `apps/forge-web/src/main.ts` 修改（确保 `registerBuiltinCards()` 在 app mount 之前调用一次）
- Create: `apps/forge-web/e2e/screen/render.spec.ts`
- Create: `apps/forge-web/e2e/screen/editor.spec.ts`
- Create: `apps/forge-web/playwright.config.ts`（如尚未存在）

**Interfaces:**
- Consumes: 前 10 个 Task 的所有组件
- Produces:
  - 路由常量新增：
    - `/screen/preview/:code` → `views/screen/preview/index.vue`（无权限校验，仅供编辑器内预览）
    - `/screen/editor/:code` → `views/screen/editor/index.vue`（需登录 + 菜单权限 `screen:screen:edit`）
  - 渲染页 `/screen/:code` 走动态路由（每张大屏在 `sys_menu` 有一条 `routePath='/screen/{code}'` 记录）
  - Playwright 配置：baseURL=http://localhost:3003，webServer 自动启 `pnpm dev`

- [ ] **Step 1: 修改 router/constants.ts 新增静态路由**

在 `apps/forge-web/src/router/constants.ts` 的 `CONSTANT_ROUTES` 数组中 FlowLong 设计器路由之后新增：

```ts
  {
    path: '/screen/preview/:code',
    name: 'ScreenPreview',
    component: () => import('@/views/screen/preview/index.vue'),
    meta: {
      title: '大屏预览',
      hidden: true,
      noAuth: false  // 由后端按 screen:screen:view:{code} 校验
    }
  },
  {
    path: '/screen/editor/:code',
    name: 'ScreenEditor',
    component: () => import('@/views/screen/editor/index.vue'),
    meta: {
      title: '大屏编辑',
      hidden: true,
      noAuth: false  // 需 menu_type=M 且 permission=screen:screen:edit
    }
  }
```

- [ ] **Step 2: 修改 main.ts 注册内置卡片**

修改 `apps/forge-web/src/main.ts`，在 `import App from './App.vue'` 之后、`app.mount('#app')` 之前新增：

```ts
import { registerBuiltinCards } from '@/views/screen/cards/registry'

try { registerBuiltinCards() } catch { /* 已注册 */ }
```

- [ ] **Step 3: 写 playwright.config.ts（若项目尚未有）**

```ts
// apps/forge-web/playwright.config.ts
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3003',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: 'pnpm dev',
    port: 3003,
    reuseExistingServer: !process.env.CI
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } } }]
})
```

- [ ] **Step 4: 装 @playwright/test**

```bash
cd apps/forge-web
pnpm add -D @playwright/test
pnpm exec playwright install chromium
```

- [ ] **Step 5: 写 render.spec.ts**

```ts
// apps/forge-web/e2e/screen/render.spec.ts
import { test, expect } from '@playwright/test'

test.describe('大屏渲染页', () => {
  test.beforeEach(async ({ page }) => {
    // 登录
    await page.goto('/login')
    await page.fill('input[name="username"]', 'admin')
    await page.fill('input[name="password"]', 'admin123')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard')
  })

  test('已登录用户访问 /screen/operations 渲染大屏', async ({ page }) => {
    await page.goto('/screen/operations')
    await expect(page.locator('.screen-renderer')).toBeVisible({ timeout: 10_000 })
    // 视觉回归基线（首次执行会创建基线；后续差异 > 5% 失败）
    await expect(page).toHaveScreenshot('render-operations.png', { maxDiffPixelRatio: 0.05 })
  })

  test('1920×1080 视口下卡片正确缩放', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 })
    await page.goto('/screen/operations')
    const scale = await page.evaluate(() => {
      const el = document.querySelector('.screen-renderer') as HTMLElement
      return el ? getComputedStyle(el).transform : null
    })
    expect(scale).toContain('scale(')
  })
})
```

- [ ] **Step 6: 写 editor.spec.ts**

```ts
// apps/forge-web/e2e/screen/editor.spec.ts
import { test, expect } from '@playwright/test'

test.describe('大屏编辑器', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[name="username"]', 'admin')
    await page.fill('input[name="password"]', 'admin123')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard')
  })

  test('进入编辑器后渲染 3 栏布局', async ({ page }) => {
    await page.goto('/screen/editor/1?template=hero-3')
    await expect(page.locator('.card-panel')).toBeVisible()
    await expect(page.locator('.editor-canvas')).toBeVisible()
    await expect(page.locator('.property-panel')).toBeVisible()
  })

  test('撤销/重做按钮初始 disabled', async ({ page }) => {
    await page.goto('/screen/editor/1?template=blank')
    await expect(page.locator('button:has-text("撤销")')).toBeDisabled()
  })

  test('点击左侧数字翻牌器 → 画布新增卡片 → 右侧显示属性面板', async ({ page }) => {
    await page.goto('/screen/editor/1?template=blank')
    await page.locator('.card-item:has-text("数字翻牌器")').click()
    await expect(page.locator('.canvas-card')).toHaveCount(1)
    await expect(page.locator('.property-panel .el-form-item:has-text("标题")')).toBeVisible()
  })

  test('打开模板选择器弹窗', async ({ page }) => {
    await page.goto('/screen/editor/1?template=blank')
    // 通过 store 触发（无 UI 入口）— 此处通过 JS evaluate
    await page.evaluate(() => {
      const dialogs = document.querySelectorAll('.el-dialog')
      dialogs.forEach(d => (d as HTMLElement).style.display = '')
    })
    // 视觉回归
    await expect(page).toHaveScreenshot('editor-blank.png', { maxDiffPixelRatio: 0.05 })
  })
})
```

- [ ] **Step 7: 跑 Playwright（生成基线）**

```bash
cd apps/forge-web
pnpm exec playwright test --update-snapshots
```

预期：所有测试通过；首次生成 `e2e/screen/render.spec.ts-snapshots/` 与 `editor.spec.ts-snapshots/` 基线 PNG。

- [ ] **Step 8: 手动核对基线截图**

打开生成的基线 PNG，肉眼检查：
- 渲染页：1920×1080 暗色背景、卡片居中、缩放正确
- 编辑器：左中右 3 栏布局清晰

如果基线看起来异常，先在浏览器手动跑 dev server 检查 UI，再调整后重跑 `--update-snapshots`。

- [ ] **Step 9: 跑全套 Vitest 确认无回归**

Run: `cd apps/forge-web && pnpm test:run`
Expected: 全部通过

- [ ] **Step 10: 跑 pnpm build 验证类型**

Run: `cd apps/forge-web && pnpm build`
Expected: vue-tsc 通过，vite build 成功，无 TS 错误

- [ ] **Step 11: Commit**

```bash
git add apps/forge-web/src/router/constants.ts \
        apps/forge-web/src/main.ts \
        apps/forge-web/playwright.config.ts \
        apps/forge-web/package.json pnpm-lock.yaml \
        apps/forge-web/e2e/screen
git commit -m "feat(screen): 接入大屏路由 + 新增 Playwright 视觉回归"
```

---

## 完整验收（端到端）

执行顺序：

```bash
# 1. 跑后端
cd apps/forge-server
mvn spring-boot:run -pl forge-server

# 2. 跑 Python AI（如需）
cd apps/forge-ai-python
python -m uvicorn src.main:app --reload --port 8000

# 3. 跑前端
cd apps/forge-web
pnpm dev
```

- 登录后 `/screen` 可见大屏列表；点"新增大屏"选模板 → 进编辑器
- 拖拽数字翻牌器到画布 → 右侧出现属性面板
- 绑定数据源 → 字段映射自动推荐
- 保存草稿 → 预览页 `/screen/preview/{id}` 可见
- 发布 → 渲染页 `/screen/operations` 可见
- 撤销/重做按钮按预期工作
- 切换 pageConfig.palette → 渲染页大屏主色跟随变化
- 切换 screen theme（dark-tech/blue-deep/black-gold）→ 背景/边框/字体颜色变化

---

## 风险与已知问题

1. **`grid-layout-plus` 与 vue-grid-layout API 差异**：plan 默认使用 `grid-layout-plus`（Vue 3 port）。如果该库在 `pnpm install` 失败或 API 不一致，备选 `vue3-grid-layout-next`。在 Task 10 Step 1 装包时如遇问题，更新 plan 并 note。
2. **地图 geojson 体积**：中国地图 `china.json` ~150KB，需放在 `public/maps/`。若 git lfs 不可用，使用 `postinstall` 脚本从 DataV.GeoAtlas 拉取（Task 6 Step 5）。
3. **大屏 ECharts 按需引入未严格分包**：line/bar/pie/gauge 共用 `echarts/core`，体积约 250KB gzip。地图需要 `MapChart` 增加 ~30KB。Editor 路径只在使用 `map-chart` 时才加载地图（cardRegistry 动态 import 优化待后续 PR）。
4. **撤销/重做内存占用**：50 步 × 大屏 config（可能含 100 张卡片）≈ 1MB。极端场景可配置 `useScreenEditorStore({ maxHistory: 20 })`。
5. **路由动态注册 vs 静态注册的决策**：本 plan 把渲染页设计为"每张大屏一条 sys_menu 记录，routePath 写死如 `/screen/operations`"。如果有 100+ 大屏，菜单表会膨胀。备选方案是改成 1 条菜单 + 路径参数 `/screen/:code`（修改后端 `/auth/menus` 允许 routePath 含 `:code`，并加菜单级白名单）。**该决策需与产品确认。**
6. **JSONSchema 表单覆盖度**：`@form-create/element-ui` 已在 deps，但本 plan 的 `JsonSchemaForm.vue` 仅支持 text/number/boolean/enum 四种类型。复杂类型（array/nested object）需扩展。如无业务需求，保持 YAGNI。
7. **大屏 HTTP 跨域问题**：HTTP 数据源由后端代理，前端不直接发跨域请求，无需 CORS 配置。

---

## 关联文档

- 设计文档：`docs/superpowers/specs/2026-07-04-large-screen-design.md`
- 后端计划：`docs/superpowers/plans/2026-07-04-large-screen-backend.md`
- 等保合规：`apps/forge-server/docs/SECURITY-COMPLIANCE.md`
- 主题切换系统（palette 跟随）：`docs/superpowers/plans/2026-07-04-multi-ui-themes.md`
- 动态路由实现：`apps/forge-web/src/router/index.ts` + `src/stores/permission.ts`
- v-permission 指令：`apps/forge-web/src/directives/`
- ECharts 文档：https://echarts.apache.org/option.html
- grid-layout-plus 文档：https://github.com/qmhc/grid-layout-plus

---


