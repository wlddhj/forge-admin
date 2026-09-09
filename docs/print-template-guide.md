# 打印模板使用指南

## 概述

forge-admin 集成了 [vue-plugin-hiprint](https://github.com/fxhp/vue-plugin-hiprint) 实现拖拽式打印模板设计与浏览器原生打印。模板以 JSON 形式存储在 `sys_print_template` 表中，业务方通过模板编号（template_code）调用打印。

**核心特性：**

- 拖拽式设计器：支持文本/表格/图片/条码等元素，纸张/缩放/旋转可视化操作
- 浏览器原生打印：无需安装客户端，调用 `window.print()` 触发浏览器打印预览
- 按编号调用：业务方通过 `template_code` 取模板 JSON，与业务数据合并后打印
- 跨租户共享：`sys_print_template` 表在 `ignore-tables` 白名单，所有租户共享同一份模板池

## 架构说明

### 后端（`forge-module-system`）

| 层 | 类 | 路径 |
|----|----|----|
| 实体 | `SysPrintTemplate` | `forge-module-system-api/.../entity/` |
| DTO | `PrintTemplateQueryRequest` / `PrintTemplateRequest` / `PrintTemplateResponse` | `forge-module-system-api/.../dto/print/` |
| Mapper | `SysPrintTemplateMapper` | `forge-module-system-biz/.../mapper/` |
| Service | `SysPrintTemplateService` + `SysPrintTemplateServiceImpl` | `forge-module-system-biz/.../service/` |
| Controller | `SysPrintTemplateController` | `forge-module-system-biz/.../controller/admin/` |

### 前端（`apps/forge-web/src/`）

| 类型 | 文件 | 路径 |
|----|----|----|
| API | `print-template.ts` | `api/system/` |
| 工具 | `template-helper.js` / `modal.js` | `utils/hiprint/` |
| 组件 | `preview.vue` / `print-view.vue` | `components/hiprint/` |
| 视图 | `index.vue` / `PrintTemplateForm.vue` / `design/index.vue` | `views/system/print/` |
| 示例 | `index.vue` + 辅助 js | `views/demo/hiprint/` |

### 数据表 `sys_print_template`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| template_code | VARCHAR(64) | 模板编号（全表唯一，含 deleted 区分软删） |
| template_name | VARCHAR(100) | 模板名称 |
| contents | LONGTEXT | 模板内容（hiprint JSON） |
| version | INT | 版本号 |
| status | TINYINT | 状态（0:禁用 1:启用） |
| remark | VARCHAR(255) | 备注 |
| create_time / update_time | DATETIME | 时间戳 |
| create_by / update_by | BIGINT | 操作人 |
| deleted | TINYINT | 软删标记 |

**唯一索引**：`uk_template_code (template_code, deleted)` —— 全表唯一（跨租户共享）

## 快速开始

### 1. 创建模板

1. 登录系统，访问「打印管理 → 打印模板」菜单（路由 `/system/print`）
2. 点击「新增模板」按钮，填写模板编号（如 `ORDER_PRINT`）、模板名称、状态、备注
3. 点击「确定」保存，列表中会出现新建的模板记录

> ⚠️ 模板编号一旦创建不可修改（前端禁用编辑），请提前规划好编号命名

### 2. 设计模板

1. 在列表中点击「设计」按钮，跳转到设计器页面（路由 `/system/print/design?id={模板ID}`）
2. 左侧面板展示可拖拽元素（自定义元素、自定义表格、基础元素）
3. 中间画布是设计区域，右侧面板是元素参数设置
4. 顶部工具栏操作：纸张大小（A3/A4/A5/B3/B4/B5/自定义）、缩放、旋转、清空、导出 JSON、保存模板、预览、浏览器打印

**拖拽元素到画布：**

- 从左侧面板按住元素拖入中间画布
- 选中元素后右侧面板出现参数设置（字段名、字体、对齐、边距等）
- 字段名需与业务数据的 JSON key 对应（如 `title`、`items[0].name`）

**纸张设置：**

- 点击 A3/A4/A5/B3/B4/B5 按钮切换预设纸张
- 点击「自定义」输入宽高（mm）
- 点击「旋转纸张」宽高互换（横向/纵向切换）

**保存模板：**

- 点击「保存模板」按钮，将画布内容序列化为 JSON 写入 `contents` 字段
- 保存成功后提示「保存成功」

### 3. 调用打印

#### 方式一：列表页「测试打印」

1. 在列表页点击「测试打印」按钮
2. 弹出 `print-view.vue` 打印预览弹窗
3. 点击「打印」按钮触发浏览器打印预览

#### 方式二：业务页面调用 `print-view.vue` 组件

```vue
<template>
  <div>
    <el-button @click="handlePrint">打印订单</el-button>
    <print-view ref="printViewRef" @success="onPrintSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PrintView from '@/components/hiprint/print-view.vue'

const printViewRef = ref()

const handlePrint = () => {
  // 传入模板编号 + 业务数据
  printViewRef.value.prePrint('ORDER_PRINT', {
    orderNo: 'ORD20260909001',
    customerName: '张三',
    items: [
      { name: '商品A', qty: 2, price: 100 },
      { name: '商品B', qty: 1, price: 200 }
    ],
    totalAmount: 400
  })
}

const onPrintSuccess = (code: string) => {
  console.log('打印成功', code)
}
</script>
```

#### 方式三：Demo 示例页

访问 `/demo/hiprint`，输入模板编号 + JSON 格式的业务数据，点击「调用打印」即可预览。

## 后端 API 详解

所有接口前缀由双端点架构自动注入 `/admin-api`，Controller 类无需手写前缀。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin-api/system/print-template/list` | `system:print-template:list` | 分页查询（templateName/templateCode/status 过滤） |
| GET | `/admin-api/system/print-template/{id}` | `system:print-template:query` | 按 ID 查询详情 |
| GET | `/admin-api/system/print-template/code/{code}` | 登录即可 | 按编号查询（业务调用入口） |
| POST | `/admin-api/system/print-template` | `system:print-template:add` | 新增模板 |
| PUT | `/admin-api/system/print-template` | `system:print-template:edit` | 修改模板（含 contents） |
| DELETE | `/admin-api/system/print-template/{ids}` | `system:print-template:remove` | 批量删除（支持多 ID 逗号分隔） |

**响应格式：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "templateCode": "ORDER_PRINT",
    "templateName": "订单打印模板",
    "contents": "{...hiprint JSON...}",
    "version": 1,
    "status": 1,
    "remark": "标准订单打印",
    "createTime": "2026-09-09T10:00:00",
    "updateTime": "2026-09-09T10:00:00"
  }
}
```

**唯一性约束：**

- 新增时若 `template_code` 已存在，返回 `BusinessException("模板编号已存在")`
- 修改时若 `template_code` 改变且与现有记录冲突，返回 `BusinessException("模板编号已存在")`

## 前端组件用法

### `print-view.vue` — 调用打印

**Props / Methods：**

| 名称 | 类型 | 说明 |
|------|------|------|
| `prePrint(code, data)` | method | 传入模板编号 + 业务数据，加载模板并显示预览弹窗 |
| `webPrint()` | method | 触发浏览器打印（弹窗内「打印」按钮调用） |
| `close()` | method | 关闭弹窗 |
| `@success` | emit | 打印成功回调，参数为 template_code |

**内部流程：**

1. `prePrint(code, data)` → 设置 templateCode + printData
2. `loadTemplateData()` → 调 `PrintTemplateApi.getByCode(code)` 取模板 JSON
3. `showPrint()` → `newHiprintPrintTemplate()` 创建模板对象 → `getHtml(printData)` 生成 HTML
4. `showModal(html)` → el-dialog 显示 + jQuery `.html()` 填充内容
5. 用户点击「打印」→ `webPrint()` → `hiprintTemplate.print()` 触发浏览器打印

### `preview.vue` — 静态预览

**Methods：**

| 名称 | 类型 | 说明 |
|------|------|------|
| `showModal(...html)` | method | 传入 hiprint 生成的 HTML，显示预览弹窗 |
| `close()` | method | 关闭弹窗 |

> 设计器内的「预览」按钮用此组件，业务方一般直接用 `print-view.vue`

### `design/index.vue` — 模板设计器

**核心方法：**

| 方法 | 说明 |
|------|------|
| `setPaper(type, value)` | 切换预设纸张 |
| `setPaperOther()` | 应用自定义纸张 |
| `changeScale(big)` | 缩放（+0.1/-0.1，范围 0.5~3） |
| `rotatePaper()` | 旋转纸张（宽高互换） |
| `clearPaper()` | 清空所有元素 |
| `exportJson()` | 导出模板 JSON 文件 |
| `saveJson()` | 保存模板到后端 |
| `getHtml()` | 预览（调用 `preview.vue`） |
| `print()` | 浏览器打印（用设计器内置 printData） |

**生命周期：**

1. `onMounted` → `hiprint.init({ providers })` 初始化 + `buildLeftElement()` 构建左侧可拖拽元素
2. `loadTemplateData()` → 从后端加载模板 contents → `buildDesigner()` 构建设计器

## hiprint 设计器操作指南

### 可拖拽元素分类

| 分类 | 说明 |
|------|------|
| 自定义元素 | 文本、表格、图片、条码、二维码等（provider1 定义） |
| 自定义表格 | 复杂表格元素（provider2 定义） |
| 基础元素 | hiprint 内置默认元素（defaultElementTypeProvider） |

> 如需新增可拖拽元素，编辑 `views/system/print/design/provider1.js` 或 `provider2.js`

### 元素字段绑定

hiprint 元素通过「字段名」与业务数据关联：

- 简单字段：`orderNo`、`customerName`
- 嵌套字段：`customer.address.city`
- 数组字段：`items[0].name`、`items[1].qty`
- 表格循环：表格元素绑定数组字段（如 `items`），hiprint 自动循环渲染每行

### 纸张与打印

- 预设纸张：A3(420×297) / A4(210×297) / A5(210×148) / B3(500×353) / B4(250×353) / B5(250×176)（mm）
- 自定义纸张：输入宽高（mm）
- 浏览器打印时纸张尺寸由 hiprint 自动应用到 `@page`

### 全局格式化函数

`apps/forge-web/index.html` 中定义了全局函数，可在 hiprint 元素的「格式化」属性中引用：

| 函数 | 用途 | 示例 |
|------|------|------|
| `formatDateJs(value)` | 格式化日期为 `YYYY-MM-DD` | `formatDateJs(createTime)` |
| `formatTimeJs(value)` | 格式化日期时间为 `YYYY-MM-DD HH:mm:ss` | `formatTimeJs(createTime)` |
| `formatNumber(value, scale)` | 格式化数字（千分位 + 保留小数） | `formatNumber(amount, 2)` |

## 多租户行为说明

`sys_print_template` 表在 `application.yml` 的 `ignore-tables` 白名单中：

```yaml
forge:
  tenant:
    ignore-tables:
      - sys_menu
      - sys_role
      # ...
      - sys_print_template   # 跨租户共享
```

**影响：**

- `TenantLineInnerInterceptor` 不会给 `sys_print_template` 的 SQL 注入 `tenant_id` 条件
- 所有租户看到同一份打印模板池
- `template_code` 全表唯一（不是同租户唯一）
- 平台超管维护模板，所有租户的业务方都可调用

> 如果未来需要按租户隔离模板，需要：① 表加 `tenant_id` 列 + 联合唯一索引；② 从 `ignore-tables` 移除；③ 实体加 `tenantId` 字段

## 常见问题

### Q1: 浏览器打印弹出空白页？

**A:** 检查 `contents` 字段是否有内容（在列表页「设计」按钮进入设计器，拖入元素后点「保存模板」）。如果 `contents` 为空，`hiprintTemplate.getHtml()` 返回空 HTML。

### Q2: 打印内容字段不显示？

**A:** 检查元素绑定的字段名与业务数据的 JSON key 是否一致。在设计器选中元素 → 右侧参数面板 → 「字段名」属性。例如元素绑定 `orderNo`，业务数据必须有 `orderNo` 字段。

### Q3: 设计器左侧元素不显示？

**A:** 确认 `main.ts` 已引入 hiprint 样式：

```ts
import '@/assets/css/hiprint-style.css'
import '@/assets/css/iconfont.css'   // hiprint 图标字体
```

且 `window.$ = window.jQuery = jQuery` 已全局暴露（hiprint 依赖 jQuery）。

### Q4: 模板编号能不能改？

**A:** 不能。前端编辑表单中 `templateCode` 字段在编辑模式下禁用（避免唯一索引冲突）。如需改编号，建议：① 新建模板用新编号 → ② 用旧编号的模板数据迁移 → ③ 删除旧模板。

### Q5: 如何在第三方页面（非 forge-admin）调用打印？

**A:** 调用 `GET /admin-api/system/print-template/code/{code}` 取模板 JSON，自行用 `vue-plugin-hiprint` 在第三方页面渲染。需要携带 JWT Token + `X-Tenant-Id` header。

## 相关文件索引

### 后端

- 实体：`apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/SysPrintTemplate.java`
- DTO：`apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/dto/print/`
- Mapper：`apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/mapper/SysPrintTemplateMapper.java`
- Service：`apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/SysPrintTemplateService.java`
- ServiceImpl：`apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/impl/SysPrintTemplateServiceImpl.java`
- Controller：`apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/controller/admin/SysPrintTemplateController.java`
- 迁移脚本：`apps/forge-server/forge-server/src/main/resources/db/migration/V2026090801__create_sys_print_template.sql`

### 前端

- API：`apps/forge-web/src/api/system/print-template.ts`
- 工具：`apps/forge-web/src/utils/hiprint/template-helper.js`、`modal.js`
- 组件：`apps/forge-web/src/components/hiprint/preview.vue`、`print-view.vue`
- 视图：`apps/forge-web/src/views/system/print/index.vue`、`PrintTemplateForm.vue`、`design/index.vue`
- Demo：`apps/forge-web/src/views/demo/hiprint/index.vue`
- 全局入口：`apps/forge-web/src/main.ts`（hiprint 样式 + jQuery 暴露）
- 全局格式化函数：`apps/forge-web/index.html`（`formatDateJs` / `formatTimeJs` / `formatNumber`）
- tsconfig：`apps/forge-web/tsconfig.json`（`allowJs: true`，因迁移的 .vue 含纯 JS `<script>`）

