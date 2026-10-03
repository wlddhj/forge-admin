# 大屏模块（forge-module-screen）使用手册

> 本文档面向运维、后端开发者与大屏制作人员，汇总大屏模块的**使用流程**、API、配置、SQL 白名单管理、安全护栏以及常见故障排查。
>
> 模块路径：后端 `apps/forge-server/forge-module-screen/`；管理页面 `apps/forge-web/src/views/screen/`；大屏编辑器（goView）`apps/forge-screen/`
> 数据库迁移：`V202607041`（建表）、`V202607042`（菜单权限）、`V202607080`（公开访问字段）、`V202607081`（角色授权表）、`V202607090`（测试数据源）、`V202607091`（白名单菜单）

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 快速上手](#2-快速上手)
- [3. 前端管理页面操作](#3-前端管理页面操作)
  - [3.1 大屏列表](#31-大屏列表)
  - [3.2 数据源管理](#32-数据源管理)
  - [3.3 SQL 白名单管理（页面）](#33-sql-白名单管理页面)
- [4. 大屏编辑器（goView）使用](#4-大屏编辑器goview使用)
  - [4.1 嵌入机制与打开方式](#41-嵌入机制与打开方式)
  - [4.2 组件数据源类型](#42-组件数据源类型)
  - [4.3 FORGE 数据源面板（核心）](#43-forge-数据源面板核心)
  - [4.4 草稿自动保存与发布](#44-草稿自动保存与发布)
  - [4.5 大屏主题](#45-大屏主题)
- [5. 大屏访问与授权](#5-大屏访问与授权)
- [6. API 端点](#6-api-端点)
- [7. 配置项](#7-配置项)
- [8. SQL 白名单管理（SQL）](#8-sql-白名单管理sql)
- [9. 安全护栏（13 层防御，不可绕过）](#9-安全护栏13-层防御不可绕过)
- [10. 数据库表结构](#10-数据库表结构)
- [11. 故障排查](#11-故障排查)
- [12. 已知限制与运维注意事项](#12-已知限制与运维注意事项)
- [13. 测试运行说明](#13-测试运行说明)

---

## 1. 模块概述

大屏模块提供后台可视化大屏的配置管理、数据源接入与受控 SQL 查询能力。核心设计目标：

- **配置即数据**：大屏布局、组件、主题以 JSON 存储于 `sys_screen.config` / `config_draft`，支持草稿/正式双版本与一键发布。
- **受控 SQL**：管理员预置白名单 SQL 模板，运行时只允许 `SELECT`、表/列必须命中白名单、强制 `LIMIT`、参数化执行，杜绝 SQL 注入与拖库。
- **多源接入**：支持 SQL（MySQL）与 HTTP 两种数据源类型，统一通过熔断 + 缓存 + 执行器三层流水线对外提供服务。
- **灵活授权**：大屏可配置为登录可见、公开访问（免登录）或指定角色可见三种模式。
- **审计可追溯**：所有数据源执行经 `@OperationLog` 审计；安全拦截统一以 `SqlSafetyException` → HTTP 400 返回。

模块依赖：`forge-module-screen-api`（实体/DTO/常量）+ `forge-module-screen-biz`（业务实现），
通过 `ScreenAutoConfiguration` 自动装配（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）。

---

## 2. 快速上手

从零到一块可访问的大屏，共 5 步（约 10 分钟）：

```text
① 配白名单 → ② 建数据源 → ③ 建大屏 → ④ 编辑器内配置组件取数 → ⑤ 发布并分享链接
```

**① 配置 SQL 白名单**（仅 SQL 数据源需要）

登录管理后台 → 「大屏管理 → SQL 白名单」→ 新增：库名 `forge_admin`、表名（如 `sys_user`）、
允许列（逗号分隔，留空 = 该表全部列，**不推荐**）、风险等级、启用。
也可用 SQL 直接插入，见 [§8](#8-sql-白名单管理sql)。

**② 创建数据源**

「大屏管理 → 数据源管理」→ 新增：

- 编码（唯一，如 `user-stats`）、名称、类型选 `SQL` 或 `HTTP`
- SQL 型：填 SQL 模板（必须含 `LIMIT`，参数用 `:name` 占位）：

  ```sql
  SELECT id, username, nickname FROM sys_user
  WHERE create_time > :startTime
  LIMIT 100
  ```

- HTTP 型：填 URL（host 必须在 `forge.security.screen.allowed-hosts` 白名单内）与 Method
- 缓存秒数（0 = 不缓存，最长 3600）、启用开关
- 保存后可点「测试」验证连通性与返回结构

**③ 创建大屏**

「大屏管理」→「新增大屏」→ 填名称即可（`code` 自动生成 `screen-{时间戳}`，主题默认 `dark-tech`）→
系统自动在新标签页打开编辑器。

**④ 编辑器内为组件绑定数据源**

拖拽图表组件到画布 → 右侧「配置 → 数据」→ 接口类型选 **forge 数据源** →
下拉选择第 ② 步创建的数据源 → 需要时填参数 JSON（如 `{"startTime": "2026-01-01"}`）→
「测试获取」验证 → 在「字段映射」中确认图表 dimensions 与数据字段的对应关系。详见 [§4.3](#43-forge-数据源面板核心)。

**⑤ 发布并分享**

编辑器右上角「发布」（或列表页「发布」按钮）将草稿固化为正式配置 →
列表页「渲染」打开正式访问页，或「使用链接」获取分享 URL / iframe 嵌入片段。
公开大屏（免登录）需先在「授权」弹窗中把访问方式设为「公开访问」，详见 [§5](#5-大屏访问与授权)。

---

## 3. 前端管理页面操作

管理页面位于 `apps/forge-web/src/views/screen/`，菜单挂在「大屏管理」目录下
（菜单与权限种子见 `sql/init-screen.sql`，权限前缀 `screen:screen:*`、`screen:data-source:*`、`screen:sql-whitelist:*`）。

### 3.1 大屏列表

页面：`views/screen/index/index.vue`，路由 `/screen`。

**搜索**：名称（模糊）、状态（0=草稿 / 1=已发布）。

**新建**：弹窗仅填名称；`code` 自动生成为 `screen-{Date.now()}`，主题默认 `dark-tech`；
创建成功后自动 `window.open('/screen/editor/{id}?template=blank')` 打开编辑器。

**行操作**（按权限码控制可见性）：

| 操作 | 权限码 | 行为 |
|------|--------|------|
| 编辑 | `screen:screen:edit` | 新标签打开编辑器 `/screen/editor/{id}` |
| 预览 | — | 打开登录态预览页 `/screen/preview/{code}`（需已发布） |
| 渲染 | — | 打开正式访问页 `/screen/render/{code}`（公开大屏可免登录） |
| 使用链接 | — | 弹窗展示分享 URL（`{origin}/screen/render/{code}`）与 iframe 嵌入片段 |
| 授权 | `screen:screen:edit` | 配置 `is_public`（0=登录可访问 / 1=公开访问）与 `access_type`（0=所有登录用户 / 1=指定角色 + 角色多选），保存走 `PUT /screen` |
| 复制 | `screen:screen:copy` | 弹窗填新编码 + 新名称，复制配置/主题/授权，新屏为草稿态 |
| 发布 | `screen:screen:publish` | 草稿覆盖正式配置并置为已发布 |
| 删除 | `screen:screen:remove` | 逻辑删除（支持批量） |

### 3.2 数据源管理

页面：`views/screen/data-source/index.vue`（列表 + 编辑抽屉 `editor.vue`），路由 `/screen/data-source`。

**搜索**：名称（模糊）、类型（HTTP / SQL；类型条件当前为前端展示维度，后端 list 接口仅按名称过滤）。

**编辑抽屉字段与 config JSON 的对应关系**（保存时整体序列化进 `sys_screen_data_source.config`）：

| 类型 | 表单字段 | config 键 | 后端是否读取 |
|------|----------|-----------|--------------|
| SQL | SQL 模板（必填，含 `:name` 占位与 `LIMIT`） | `sqlTemplate` | ✅ 执行依据 |
| SQL | 参数 Schema（JSON） | `paramSchema` | ⚠️ 当前仅存储，执行期未校验 |
| SQL | 最大行数（1-1000） | `maxRows` | ⚠️ 当前仅存储，行数上限由系统常量 `SQL_MAX_ROWS=1000` 强制 |
| HTTP | Method | `method` | ✅（默认 GET） |
| HTTP | URL（必填） | `url` | ✅ host 须在白名单 |
| HTTP | Headers（JSON） | `headers` | ⚠️ 当前仅存储，请求不携带 |
| HTTP | Params（JSON） | `params` | ⚠️ 仅作为 execute 时 query 参数的说明 |
| HTTP | Timeout（1-60s） | `timeout` | ⚠️ 当前仅存储，超时固定 5s |
| 公共 | 缓存秒数（0-3600） | 列 `cache_seconds` | ✅ 0=不缓存 |
| 公共 | 启用开关 | 列 `enabled` | ✅ 禁用后 execute 拒绝 |
| 公共 | 备注 | 列 `remark` | — |

**SQL 模板示例**（占位符 `:name`，执行时由面板/预览 URL 的 params 提供实参）：

```sql
SELECT dept_id, COUNT(*) AS cnt FROM sys_user
WHERE status = :status AND deleted = 0
GROUP BY dept_id
LIMIT 100
```

**HTTP config 示例**：

```json
{ "method": "GET", "url": "http://forge-server:8181/api/xxx", "headers": "{}", "params": "{}", "timeout": 5 }
```

**测试**：编辑已有数据源时点「测试」，调用 `POST /screen/data-source/execute/{id}`，
结果面板展示 `fromCache`、`executedAt` 与数据表格（自动提取首行字段为列）。

**安全提示**：列表接口不返回 `config` 原文（后端 `qw.select` 显式排除），防止 SQL 模板/URL 泄露；
详情接口（编辑回显）正常返回。

### 3.3 SQL 白名单管理（页面）

页面：`views/screen/sql-whitelist/index.vue`，路由 `/screen/sql-whitelist`。

列表列：库名 / 表名 / 允许列（JSON 数组）/ 风险等级 / 启用 / 备注。
表单字段：`schemaName`（库名）、`tableName`（编辑时禁改）、`columnList`（**逗号分隔**输入，留空 = 该表全部列，不推荐）、`riskLevel`（0 公开 / 1 内部 / 2 敏感）、`enabled`、备注。

> 页面操作与 SQL 操作等价，规则细节（风险等级含义、敏感列清单）见 [§8](#8-sql-白名单管理sql)。

---

## 4. 大屏编辑器（goView）使用

### 4.1 嵌入机制与打开方式

编辑器是独立 SPA（`apps/forge-screen`，基于 goView + VChart），通过 iframe 嵌入 forge-web：

| 环境 | 编辑器地址 | 说明 |
|------|-----------|------|
| 开发 | `http://localhost:8001` | forge-screen 自己的 dev server；forge-web 的 vite 已配置代理 |
| 生产 | `/screen-app` | **需部署时将 forge-screen 构建产物放置在该路径下**（当前 forge-web 的 nginx.conf 未内置该路由，见 [§12](#12-已知限制与运维注意事项)） |

iframe URL 格式（`views/screen/editor/index.vue`）：

```text
{编辑器基址}/#/chart/home/{大屏id}?token={JWT}
```

- forge-screen 侧从 URL query 读取 `token` 并设置 `Authorization: Bearer`（`src/api/axios.ts`）
- 大屏 id 由 `getScreenIdFromUrl()` 从 hash 中解析（优先 `?id=`，其次匹配 `/chart/home/(\d+)`）

### 4.2 组件数据源类型

编辑器内每个图表组件的「数据」配置可切换 4 种取数方式（`RequestDataTypeEnum`，`src/enums/httpEnum.ts`）：

| 值 | 类型 | 说明 |
|----|------|------|
| 0 | STATIC 静态数据 | 手工填写 JSON，不走网络 |
| 1 | AJAX 请求数据 | 通用 HTTP 请求（goView 原生） |
| 2 | Pond 数据池 | 多组件共享同一请求（goView 原生） |
| 3 | **FORGE forge 数据源** | 调用本系统 `POST /screen/data-source/execute/{id}`，**推荐方式** |

### 4.3 FORGE 数据源面板（核心）

面板位于 `forge-screen/src/views/chart/ContentConfigurations/components/ChartData/components/ChartDataForge/index.vue`，
当接口类型选「forge 数据源」时显示。操作流程：

1. **选择数据源**：面板挂载时自动调 `GET /screen/data-source/list`（pageSize=100）拉取启用中的数据源，
   下拉（支持过滤搜索）展示 `名称（编码）`，选中后写入组件 `request.forgeDataSourceId`
2. **配置参数**：参数 JSON 文本域，解析成功写入 `request.forgeParams`（解析失败不写入，防误删已有参数）。
   占位符与数据源 SQL 模板的 `:name` 一一对应，如 `{"status": 1}`
3. **测试获取**：调 `POST /screen/data-source/execute/{id}`，结果：
   - 面板下方展示 JSON 原文
   - 自动提取数据（数组 / `records` / `list` / `data` 字段）写入 `option.dataset.source`，
     **保留现有 `dimensions` 映射不被覆盖**
4. **字段映射**：下方 `ChartDataMatchingAndShow` 以时间线表格展示「图表字段 ↔ 数据字段」的匹配结果
   （成功/失败徽标），vChart 类组件可直接在行内编辑映射

**URL 公共参数注入**（2026-10 增强）：预览/渲染页 URL query 中的任意参数（如 `?accountSetId=1`，
保留字 `code`/`token` 除外）会在页面加载前由 `initScreenParams` 收集，
FORGE 取数时与组件静态 `forgeParams` 合并（**URL 优先**）。适用场景：同一块大屏按账套/组织等上下文动态取数，
无需为每个上下文单独配组件参数。

### 4.4 草稿自动保存与发布

- **自动保存**：编辑器监听组件列表与画布配置变更，**3 秒防抖**后自动调 `PUT /screen` 将
  `editCanvasConfig + componentList` 序列化写入 `config_draft`；窗口失焦（blur）立即触发保存。
  机制位于 `ContentEdit/components/EditTools/hooks/useSyncUpdate.hook.ts`。
- **手动同步**：编辑器「同步内容」按钮同样保存草稿并广播 sessionStorage（供编辑器内预览使用）。
- **发布**：编辑器右上角或列表页「发布」调 `PUT /screen/publish/{code}`，将 `config_draft` 覆盖到 `config`
  并置 `status=1`（乐观锁 `version` 防并发覆盖）。
- **编辑器内预览**：基于 sessionStorage 中的**草稿**渲染，可用于发布前检查；
  `/screen/preview/{code}` 与 `/screen/render/{code}` 则读取**已发布配置**（后端对未发布大屏直接拒绝）。

### 4.5 大屏主题

`sys_screen.theme` 可选值（`forge-web/src/types/screen/index.ts`）：

| 值 | 风格 |
|----|------|
| `dark-tech` | 暗色科技（默认） |
| `blue-deep` | 深海蓝 |
| `black-gold` | 黑金 |

渲染/预览壳通过 `applyScreenTheme` 设置 `<html data-screen-theme="...">` 加载对应样式。
注意：该主题作用于**大屏渲染效果**；编辑器自身的 UI 外观（goView 深色模式）是独立配置，二者互不影响。

---

## 5. 大屏访问与授权

### 5.1 三种访问入口

| 入口 | 路由 | 鉴权 | 数据来源 |
|------|------|------|----------|
| 编辑器 | `/screen/editor/{id}` | 登录 + `screen:screen:*` 权限 | 草稿（实时编辑） |
| 预览 | `/screen/preview/{code}` | 登录（前端路由守卫） | 已发布 config |
| 渲染（正式访问） | `/screen/render/{code}` | 前端路由 noAuth；后端按大屏授权配置放行 | 已发布 config |

渲染页 iframe 拼接 `{编辑器基址}/#/chart/preview/forge?code={code}&runtime=1[&token={JWT}]`
（公开大屏不携带 token）。分享链接形如 `https://your-domain/screen/render/{code}`。

### 5.2 授权模型（isPublic / accessType / sys_screen_role）

后端 `SysScreenServiceImpl.getByCode` 的完整判定链：

```text
大屏未发布(status≠1)          → 拒绝："大屏未发布，无法访问"
isPublic = 1（公开访问）        → 免登录直接返回（匿名可访问）
isPublic = 0 且未登录           → 拒绝："大屏未公开，请登录后访问"
isPublic = 0, accessType = 0   → 任意登录用户可访问
isPublic = 0, accessType = 1   → 登录用户角色须命中 sys_screen_role 授权（授权为空同样拒绝）
```

- 公开端点 `GET /admin-api/screen/code/{code}` 标注 `@PreAuthorize("permitAll()")`，
  且 `/admin-api/screen/code/**` 在 `SecurityConfig.ADMIN_WHITE_LIST` 中放行，由上述业务逻辑自行控制
- **授权配置入口**：大屏列表「授权」弹窗（保存到 `PUT /screen`，随 `ScreenRequest` 携带 `isPublic`/`accessType`/`roleIds`）
- **复制大屏**时若 `accessType=1`，授权角色关系（`sys_screen_role`）一并复制
- 角色来源：登录主体 JWT 中的 `roleIds`（回退解析 authorities 中 `role:{id}` 前缀）

---

## 6. API 端点

全部位于 `controller/admin` 包（自动加 `/admin-api` 前缀），无 `controller.app` 端点。

### 6.1 大屏管理（8 个）

| # | 方法 | 路径 | 权限码 | 说明 |
|---|------|------|--------|------|
| 1 | GET  | `/screen/list`              | `screen:screen:list`         | 分页查询（参数：pageNum/pageSize/name 模糊/status） |
| 2 | GET  | `/screen/{id}`              | `screen:screen:query`        | 按 ID 查询（编辑回显，含 config/configDraft/roleIds） |
| 3 | GET  | `/screen/code/{code}`       | **`permitAll()`**            | 按 code 查询（渲染/预览用，授权逻辑见 [§5.2](#52-授权模型ispublic--accesstype--sys_screen_role)） |
| 4 | POST | `/screen`                   | `screen:screen:add`          | 新增（写入 configDraft，code 可自动生成） |
| 5 | PUT  | `/screen`                   | `screen:screen:edit`         | 修改（config 入参写 configDraft；含授权字段） |
| 6 | DELETE | `/screen`                 | `screen:screen:remove`       | 删除（批量） |
| 7 | PUT  | `/screen/publish/{code}`    | `screen:screen:publish`      | 发布（草稿覆盖正式） |
| 8 | POST | `/screen/copy/{code}`       | `screen:screen:copy`         | 复制（携带 config/草稿/主题/授权；不复制 description/remark；新屏为草稿态） |

### 6.2 数据源管理（6 个）

| # | 方法 | 路径 | 权限码 | 说明 |
|---|------|------|--------|------|
| 9  | GET  | `/screen/data-source/list`           | `screen:data-source:list`    | 分页查询（仅 name 模糊过滤；**不返回 config 字段**） |
| 10 | GET  | `/screen/data-source/{id}`           | `screen:data-source:query`   | 查询单个（含 config，编辑回显用） |
| 11 | POST | `/screen/data-source`                | `screen:data-source:add`     | 新增 |
| 12 | PUT  | `/screen/data-source`                | `screen:data-source:edit`    | 修改 |
| 13 | DELETE | `/screen/data-source`              | `screen:data-source:remove`  | 删除（批量） |
| 14 | POST | `/screen/data-source/execute/{id}`   | `screen:data-source:execute` | 执行查询（熔断 + 缓存 + 执行器；IP 限流 60s/60 次） |

**execute 请求/响应：**

```json
// 请求 POST /admin-api/screen/data-source/execute/1
{ "params": { "status": 1 } }

// 响应
{
  "data": [ { "dept_id": 1, "cnt": 25 } ],   // 执行结果（SQL 行集 / HTTP 解析结果）
  "fromCache": false,                         // 当前恒为 false，见 §12 L3
  "executedAt": "2026-10-03T21:00:00"
}
```

### 6.3 SQL 白名单（5 个）

| # | 方法 | 路径 | 权限码 |
|---|------|------|--------|
| 15 | GET | `/screen/sql-whitelist/list` | `screen:sql-whitelist:list` |
| 16 | GET | `/screen/sql-whitelist/{id}` | `screen:sql-whitelist:query` |
| 17 | POST | `/screen/sql-whitelist` | `screen:sql-whitelist:add` |
| 18 | PUT | `/screen/sql-whitelist` | `screen:sql-whitelist:edit` |
| 19 | DELETE | `/screen/sql-whitelist` | `screen:sql-whitelist:remove` |

> 所有写操作均带 `@OperationLog` 审计。请求/响应 DTO 位于 `forge-module-screen-api` 的 `com.forge.modules.screen.dto`。

---

## 7. 配置项

所有配置位于 `forge.security.screen.*`，由 `ScreenProperties` 绑定。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `allowed-hosts`        | `List<String>` | `[]`（运行时由 `ScreenProperties.defaults()` 提供 `localhost/127.0.0.1/forge-server/forge-ai-python`） | HTTP 数据源 host 白名单，**精确、大小写不敏感匹配** |
| `http-timeout-ms`      | `int`    | `5000`         | HTTP 连接 + 读取超时（毫秒） |
| `http-max-body-bytes`  | `long`   | `1048576` (1MB) | HTTP 响应体最大字节 |
| `require-https`        | `boolean` | `false`       | 是否强制 HTTPS（生产强烈建议 `true`） |

### 7.1 开发环境（application-dev.yml）

```yaml
forge:
  security:
    screen:
      allowed-hosts:
        - localhost
        - 127.0.0.1
        - forge-server         # docker-compose 内部服务名
        - forge-ai-python      # Python AI 服务名
      http-timeout-ms: 5000
      http-max-body-bytes: 1048576
      require-https: false     # 开发环境放行 HTTP
```

### 7.2 生产环境（application-prod.yml）

```yaml
forge:
  security:
    screen:
      allowed-hosts:
        - api.internal.example.com    # 显式列出生产域名
        - screen-data.internal        # 内网数据聚合服务
      http-timeout-ms: 5000
      http-max-body-bytes: 1048576
      require-https: true             # 强制 HTTPS，防止链路窃听
```

> ⚠️ **生产环境切勿保留 `localhost`/`127.0.0.1`**，否则可能被构造 `http://localhost/admin` 形式的 SSRF 攻击。
>
> ⚠️ SSRF 防护为 host **精确等值匹配**（`equalsIgnoreCase`），**不**使用子串 `contains`，
> 以避免 `evil.com.127.0.0.1` 这类绕过。详见 [§9](#9-安全护栏13-层防御不可绕过) 第 4 层。

---

## 8. SQL 白名单管理（SQL）

白名单数据存放在 `sys_screen_sql_whitelist` 表，控制"哪些表/列可被大屏 SQL 查询"。
（页面操作见 [§3.3](#33-sql-白名单管理页面)，本节为 SQL 方式。）

### 8.1 新增可查询表/列

```sql
INSERT INTO sys_screen_sql_whitelist
(schema_name, table_name, column_list, risk_level, enabled, remark)
VALUES
('forge_admin', 'sys_position',
 JSON_ARRAY('id', 'position_code', 'position_name', 'status', 'sort_order', 'create_time'),
 1, 1, '岗位表');
```

**字段说明：**
- `schema_name`：库名（项目固定为 `forge_admin`）
- `table_name`：表名
- `column_list`：JSON 数组形式的允许列；为 `null` 或空字符串表示"该表全部列允许"（**不推荐**，等同放弃列级控制）
- `risk_level`：风险等级，详见 [§8.2](#82-风险等级)
- `enabled`：`0` 禁用 / `1` 启用
- `remark`：人类可读说明

### 8.2 风险等级

| 等级 | 含义 | 适用场景 |
|------|------|----------|
| `0` | 公开 | 字典、菜单、部门等基础配置数据，可显示给任何后台角色 |
| `1` | 内部 | 普通业务数据（用户、岗位、登录日志等），普通后台用户可见 |
| `2` | 敏感 | 仅限管理员/审计角色查询的表（如操作日志明细、财务相关） |

> 风险等级当前用于**审计与归类**，列级访问仍由具体 `column_list` 决定。
> 后续可叠加"角色 → 风险等级上限"的细粒度授权（如普通用户禁止 `risk_level=2` 的表）。

### 8.3 修改已有白名单

```sql
-- 给 sys_user 表追加 nickname 列
UPDATE sys_screen_sql_whitelist
SET column_list = JSON_ARRAY_APPEND(column_list, '$', 'nickname')
WHERE schema_name = 'forge_admin' AND table_name = 'sys_user';
```

### 8.4 敏感列永远排除

**默认排除清单（永远不允许出现在 `column_list`）：**

- `password` / `salt` / `password_hash`
- `email` / `phone` / `phone_suffix`
- `avatar`
- `id_card` / 身份证相关
- `last_login_ip` / `operate_ip` / 任何含 `ip` 关键字的列
- 其他 PII（个人身份信息）字段

`V202607041` 初始化白名单已遵循此规则（如 `sys_user` 仅放行 `id/dept_id/username/nickname/account_type/status/create_time/update_time`）。
新增白名单条目时请人工核对，禁止把上述敏感列写入 `column_list`。

---

## 9. 安全护栏（13 层防御，不可绕过）

任何由用户配置的 SQL（数据源 `type=SQL`）必须依次通过下列 13 层校验，
任意一层失败均抛 `SqlSafetyException` → HTTP 400。

> **C1/C2/C3 终审修复（2026-07-06）：** 数据源 `config` 字段在列表接口永不返回；
> `/data-source/execute/{id}` 端点已加 `@RateLimiter`（IP 维度，60s 内 60 次）；
> SQL 执行链路从 `JdbcTemplate` 切换到 `DynamicSqlMapper`（带 `@DataPermission`），真正走 MyBatis Plus 拦截器链。

| # | 层 | 实现位置 | 说明 |
|---|----|----------|------|
| 1 | AST 解析（JSqlParser 4.9）        | `SqlSafetyGuard.guard`            | 解析失败的 SQL 一律拒绝；解析后剥离注释/hint |
| 2 | 仅允许 SELECT                     | `SqlSafetyValidator.assertSelectOnly` | 拒绝 INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER 等 |
| 3 | 表必须命中白名单                   | `WhitelistService.checkTableAllowed` | `sys_screen_sql_whitelist` 中不存在 → 拒绝 |
| 4 | 列必须命中白名单（fail-closed）   | `WhitelistService.checkColumnsAllowed` | `column_list` 为空时拒绝所有请求列（I6 修复）；`password/salt/email/phone/id_card` 等敏感列永不在内 |
| 5 | 强制 LIMIT ≤ 1000                 | `SqlSafetyValidator.assertLimitPresent/WithinMax` | LIMIT 缺失或超过 `ScreenConstants.SQL_MAX_ROWS=1000` 拒绝；OFFSET 同样校验 |
| 6 | 禁用危险函数                       | `SqlSafetyValidator.assertNoDangerousFunctions` | 拒绝 `LOAD_FILE/SLEEP/BENCHMARK/OUTFILE/DUMPFILE/PG_SLEEP` 等，递归扫描子查询 |
| 7 | 禁用系统表                         | `SqlSafetyValidator.assertNoSystemTable` | 拒绝 `information_schema/mysql/performance_schema/sys/pg_catalog` 等，归一化反引号/双引号防绕过 |
| 8 | MyBatis 参数化执行（绝不字符串拼接）| `SqlDataSourceExecutor` + `SqlParamBinder` + `DynamicSqlMapper` | `:name` → `?` → `#{pi}`，所有外部值经 `PreparedStatement` 绑定；通过 MyBatis Plus 拦截器链 |
| 9 | `@DataPermission` 自动追加数据权限（C3 修复）| `DynamicSqlMapper.executeDynamicSql` + `forge-spring-boot-starter-mybatis` 的 `DataPermissionInterceptor` | 按角色 `data_scope` 自动追加部门/个人过滤条件 |
| 10 | HTTP 响应体硬上限 1MB（I1 修复）   | `HttpDataSourceExecutor.readBoundedBody` | 流式读取，超过 `props.httpMaxBodyBytes` 立即断流抛 `SqlSafetyException` |
| 11 | 熔断器：1 分钟 10 次失败 → 熔断 30 秒 | `DataSourceCircuitBreaker`        | Redis 计数（`screen:cb:count:{id}` TTL 60s）+ 熔断标志（`screen:cb:tripped:{id}` TTL 30s） |
| 12 | Redis 缓存（按 `cache_seconds`）    | `DataSourceCacheService.getOrLoad` | single-flight 锁防雪崩；`ttlSeconds ≤ 0` 跳过缓存 |
| 13 | 审计日志（`@OperationLog`）+ 限流   | `SysScreenDataSourceController.execute` + `@RateLimiter`（C2 修复） | 记录 `dataSourceId`、操作人、参数（敏感字段自动脱敏）；IP 维度限流 60s/60 次 |

**HTTP 数据源（`type=HTTP`）的 SSRF 防护三道闸门**（在 `HttpDataSourceExecutor`）：

| 闸门 | 实现 | 说明 |
|------|------|------|
| ① URI 合法性        | `new URI(url)` + `host != null` | 拒绝 `file:///etc/passwd`、`http:///api/x` 等畸形 URL |
| ② host 精确白名单    | `equalsIgnoreCase`              | **不使用 `contains`**，防 `evil.com.127.0.0.1` 绕过 |
| ③ HTTPS 强制         | `require-https=true` 时校验 scheme | 生产环境强制 `https` |

> **执行器统一异常契约：** 所有 SQL/HTTP 执行失败均包装为 `SqlSafetyException`，
> 由 `ScreenExceptionHandler`（`@Order(HIGHEST_PRECEDENCE)`）映射为 HTTP 400，
> 错误信息直接回显给前端（不暴露堆栈）。

---

## 10. 数据库表结构

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `sys_screen`                  | 大屏主体 | `code`(unique), `config`(JSON 已发布), `config_draft`(JSON 草稿), `theme`(dark-tech/blue-deep/black-gold), `status`(0=草稿/1=已发布), `is_public`(0=登录可访问/1=公开免登录), `access_type`(0=所有登录用户/1=指定角色), `version`(乐观锁) |
| `sys_screen_data_source`      | 数据源   | `code`(unique), `type`(SQL/HTTP), `config`(JSON，结构见 [§3.2](#32-数据源管理)), `cache_seconds`(0=不缓存), `enabled` |
| `sys_screen_role`             | 大屏↔角色授权 | `screen_id`, `role_id`, `create_time`（`access_type=1` 时生效；idx_screen_id / idx_role_id） |
| `sys_screen_sql_whitelist`    | SQL 白名单 | `schema_name`, `table_name`(联合 unique), `column_list`(JSON), `risk_level`, `enabled` |

> 历史上的 `sys_screen_data_source_ref`（大屏↔数据源关系表）已随编辑器直连数据源模式移除，当前版本无此表。

迁移脚本（`forge-module-screen-biz/src/main/resources/db/migration/`）：

| 脚本 | 内容 |
|------|------|
| `V202607041__create_screen_tables.sql` | 创建大屏/数据源/白名单 3 张表并初始化白名单数据 |
| `V202607042__insert_screen_menu_permissions.sql` | 菜单与静态权限码种子 |
| `V202607080__add_screen_public_fields.sql` | sys_screen 增加 is_public / access_type |
| `V202607081__add_screen_role_table.sql` | 创建 sys_screen_role |
| `V202607090__seed_test_data_sources.sql` | 测试数据源种子 |
| `V202607091__add_sql_whitelist_menu.sql` | 白名单管理菜单 |

---

## 11. 故障排查

| 现象 | 根因 | 处置 |
|------|------|------|
| 渲染/预览页打开报"大屏未发布，无法访问" | `sys_screen.status=0`（仅草稿） | 编辑器右上角或列表页「发布」 |
| 渲染页提示"大屏未公开，请登录后访问" | `is_public=0` 且未携带登录态 | 列表页「授权」设为公开访问，或先登录再访问 |
| 渲染页提示"您没有访问该大屏的权限" | `access_type=1` 且角色未授权 | 「授权」弹窗勾选对应角色，或改回"所有登录用户" |
| 调用 `/data-source/execute/{id}` 返回 400，消息"数据源已熔断" | 1 分钟内失败 ≥10 次触发熔断（[§9 第 11 层](#9-安全护栏13-层防御不可绕过)） | 等待 30 秒自动半开恢复；同时检查 SQL/HTTP 配置（连接串、超时、白名单） |
| HTTP 数据源返回 400，消息"host 不在白名单: xxx" | SSRF 防护（[§9 闸门 ②](#9-安全护栏13-层防御不可绕过)） | 在 `forge.security.screen.allowed-hosts` 显式追加该 host；**禁止**为图省事直接放 `*` 或加内网段 |
| SQL 数据源返回 400，消息"列不在白名单: forge_admin.sys_xxx.yyy" | 列级控制（[§9 第 4 层](#9-安全护栏13-层防御不可绕过)） | 评估是否真的需要该列；若是，扩 `sys_screen_sql_whitelist.column_list`；若否，修改大屏 SQL 移除 |
| SQL 数据源返回 400，消息"表不在白名单" | 表级控制（[§9 第 3 层](#9-安全护栏13-层防御不可绕过)） | 在 `sys_screen_sql_whitelist` 新增条目（按 [§8.1](#81-新增可查询表列)） |
| SQL 数据源返回 400，消息缺少 LIMIT 或 LIMIT 超限 | 强制 LIMIT（[§9 第 5 层](#9-安全护栏13-层防御不可绕过)） | SQL 模板补 `LIMIT` 且 ≤ 1000；注意 LIMIT 值来自参数（`:limit`）时同样受校验 |
| 数据源执行返回 429"访问过于频繁" | 触发 `@RateLimiter`（C2 修复，IP 维度 60s/60 次） | 等待窗口期；如确需更高 QPS，按业务拆分数据源或联系运维调整限流策略 |
| HTTP 数据源返回 400，消息"HTTP 响应体超过上限 N 字节，已截断" | 响应体超 `props.httpMaxBodyBytes`（默认 1MB，I1 修复） | 调高 `forge.security.screen.http-max-body-bytes` 或上游裁剪响应；切勿直接设超大值，避免 OOM |
| HTTP 数据源返回 200 但内容与预期不符 | `HttpDataSourceExecutor` 默认跟随重定向 | [§12 已知限制](#12-已知限制与运维注意事项)；如目标站点重定向到外部域，请改用 SQL 数据源或在白名单中追加最终域 |
| 跨部门用户执行 SQL 返回行数与预期不符 | `@DataPermission` 已生效（C3 修复），按 `data_scope` 自动过滤 | 此为预期行为；如确需全量数据，请使用 `data_scope=ALL` 的管理员账号 |
| 编辑器打开后组件数据不刷新 | 编辑器内预览基于 sessionStorage 草稿 | 点「同步内容」重新广播，或刷新页面重新加载草稿 |
| 生产环境编辑器/渲染页 iframe 404 | `/screen-app` 路径未部署 forge-screen 产物 | 见 [§4.1](#41-嵌入机制与打开方式) 与 [§12](#12-已知限制与运维注意事项) |
| 集成测试 `SqlSafetyEndToEndIT` 启动失败"Could not find a valid Docker environment" | Testcontainers 需要 Docker daemon | 启动 Docker Desktop；或在无 Docker 环境跳过该 IT（`-Dtest='!*IT'`） |
| 单元测试报"Mockito cannot mock ... ByteBuddy" | JDK 版本不匹配 | 项目**固定 JDK 21**；若切到 JDK 25，surefire 已加 `-Dnet.bytebuddy.experimental=true --add-opens java.base/java.lang=ALL-UNNAMED`，仍失败请回到 JDK 21 |
| `@SpringBootTest` 启动卡在 flowable/工作流 Bean 装配 | workflow 模块预存的 flowable 依赖冲突 | 当前推荐的集成测试（T18）使用 minimal `@SpringBootConfiguration` 仅装载大屏模块 Bean，绕开 workflow；切勿将大屏 IT 改为全栈 `@SpringBootTest` |
| 单测通过但 `mvn test -pl forge-module-screen/forge-module-screen-biz` 全量失败 | 局部 surefire `argLine` 未生效 | 确认 `forge-module-screen-biz/pom.xml` 的 surefire `argLine` 配置未被父 pom 覆盖 |

---

## 12. 已知限制与运维注意事项

| 编号 | 限制 | 当前处置 | 跟进 |
|------|------|----------|------|
| L1 | 数据源 config 中 SQL 型的 `paramSchema`/`maxRows`、HTTP 型的 `headers`/`params`/`timeout` **当前仅存储不生效**（前端表单可填） | SQL 行数上限由常量 `SQL_MAX_ROWS=1000` 强制；HTTP 超时固定 5s | 后端按 config 键逐步启用 |
| L2 | `HttpDataSourceExecutor` **默认跟随 HTTP 重定向**（`SimpleClientHttpRequestFactory` 默认行为） | 如目标 URL 会 302 到非白名单域，会被 SSRF 闸门拒绝并返回 400 | 后续可显式关闭自动重定向，或维护"重定向目标白名单"二级清单 |
| L3 | `DataSourceCacheService.getOrLoad` 当前**不暴露缓存命中/未命中信号**给上层（`fromCache` 字段始终为 `false`） | 通过 Redis `TTL` 与日志间接观察 | 后续在 `DataSourceExecuteResponse` 增加 `cacheHit` 字段时同步改造 |
| L4 | `single-flight` 锁为 `DataSourceCacheService` 实例字段，**所有数据源共享一把锁** | 不同 key 也会串行 loader，性能轻微损失但语义安全 | 后续按 key 分桶改造为 striped locks |
| L5 | workflow 模块的 flowable 依赖存在冲突，**阻塞全栈 `@SpringBootTest` 启动** | 大屏 IT 使用 minimal 配置绕开（参见 [§11](#11-故障排查)） | 待 workflow 模块独立修复 |
| L6 | DNS rebinding **未防御**（攻击者控制 DNS 让同 host 解析到内网 IP） | 当前依赖 host 字面量精确匹配；生产环境网络层应禁用公网 DNS 解析到内网 | 后续可在 `HttpDataSourceExecutor` 增加 `InetAddress` 私网段校验 |
| L7 | 风险等级 `risk_level` 当前**仅用于归类**，未在运行时拦截 | 列级访问由 `column_list` 决定 | 后续可叠加"角色 → 风险等级上限"运行时校验 |
| L8 | 等保专项"低权用户实际执行 SQL 跨部门裁剪"集成测试用例未跑通 | 本机沙箱无 Docker，Testcontainers IT 无法启动；C3 修复后链路已具备该能力 | 待 CI 提供 Docker 后补一个跨部门 IT 用例（参见 `SqlSafetyEndToEndIT.column_list_empty_fails_closed_through_guard` 占位） |
| L9 | `@RateLimiter` 当前仅支持 IP 维度（C2 修复落地版本） | spec §8.3 期望"用户 + dataSourceId 复合维度"，但项目 `@RateLimiter` 注解仅支持 IP / USERNAME | 后续扩展 `@RateLimiter` 支持 SpEL 后改为复合维度 |
| L10 | `version` 乐观锁已切由 `OptimisticLockerInnerInterceptor` 自动管理（I4 修复） | `SysScreenServiceImpl.publish` 不再手动 +1；并发 publish 由 MP 拦截器 WHERE version=? 兜底 | 无需跟进 |
| L11 | forge-web 的 `nginx.conf` **未内置 `/screen-app` 路由**，生产部署编辑器需自行将 forge-screen 构建产物放到该路径（或增加 nginx location 指向） | Docker Compose 当前只编排 web/server，不含 forge-screen | 部署时按 [§4.1](#41-嵌入机制与打开方式) 处理；后续可纳入 compose 编排 |
| L12 | 数据源下拉一次拉取 pageSize=100，**超过 100 个数据源时下拉不完整** | 现阶段数据源规模远小于 100 | 数据源增多后改造为搜索式下拉 |

---

## 13. 测试运行说明

### 13.1 单元测试

模块内已覆盖 11 个测试类（`safety` / `executor` / `cache` / `fault` / `service`）。

```bash
# 在 apps/forge-server 目录下
mvn test -pl forge-module-screen/forge-module-screen-biz
```

**Mockito + JDK 兼容：** `forge-module-screen-biz/pom.xml` 的 surefire 插件已配置：

```
-Dnet.bytebuddy.experimental=true --add-opens java.base/java.lang=ALL-UNNAMED
```

- `net.bytebuddy.experimental=true`：开启 ByteBuddy 实验性支持，允许其在官方仅声明支持 Java 22 的情况下加载 Java 25（class file v69）字节码。
- `--add-opens java.base/java.lang=ALL-UNNAMED`：打开模块访问，部分 Mockito 操作仍会反射 `java.lang` 私有成员。

### 13.2 集成测试（T18 `SqlSafetyEndToEndIT`）

```bash
mvn test -pl forge-module-screen/forge-module-screen-biz -Dtest=SqlSafetyEndToEndIT
```

**前置条件：**
- 本机或 CI 环境**必须启动 Docker daemon**（Testcontainers 启动 MySQL 8.0 容器）。
- 启动后 Testcontainers 会自动：
  1. 拉取 `mysql:8.0` 镜像
  2. 应用 `V202607041__create_screen_tables.sql` 建表
  3. Spring 装配 `SqlSafetyGuard` / `WhitelistService` / `SqlSafetyValidator`
  4. 跑 12 用例 SQL 安全测试

**无 Docker 环境（如沙箱 CI）请显式跳过：**

```bash
mvn test -pl forge-module-screen/forge-module-screen-biz -Dtest='!*IT'
```

### 13.3 JDK 版本

项目**固定 JDK 21**（参见根 `pom.xml` 与 `CLAUDE.md`）。
偶有 reviewer 在 JDK 25 上跑测试触发 Mockito/ByteBuddy 不兼容，请保持 21；
若必须升级 JDK，请同步更新 surefire `argLine` 并跑全量回归。

---

**维护人：** standadmin
**最后更新：** 2026-10-03（补充使用流程/前端操作/编辑器指南/访问授权模型，同步 V202607080/081 能力）
