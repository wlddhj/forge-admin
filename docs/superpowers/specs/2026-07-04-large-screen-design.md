# 大屏展示系统设计文档

**版本：** v1.0
**日期：** 2026-07-04
**作者：** huangjian
**状态：** 待评审

---

## 1. 背景与目标

### 1.1 背景

forge-admin 已完成 GB/T 22239-2019 二级等保技术改造，目前拥有用户/权限、工作流、AI 文档等模块。领导汇报、参观接待场景需要"大屏形态"展示，但现有 `views/dashboard/index.vue` 是常规管理首页（轮播图 + 统计卡片），无法满足：

- **视觉冲击不足**：缺少科技感、暗色氛围、装饰元素
- **无法快速适配**：每次汇报内容不同，需要重新开发页面
- **业务方接入成本高**：新增一个看板需要前端写代码、后端写接口

### 1.2 目标

构建一个**配置驱动 + 拖拽编辑器**的大屏展示框架，具备：

1. **通用框架**：业务方写一个 Vue 组件并注册即可上屏
2. **拖拽编辑**：非开发人员也能调整大屏布局、绑定数据
3. **多套大屏**：一套大屏对应一个独立路由，按菜单权限控制访问
4. **数据源抽象**：支持 HTTP API 和受控 SQL 两种接入方式
5. **领导汇报视觉**：1920×1080 暗色科技感，3 套内置主题

### 1.3 非目标

- 不构建完整的 BI 产品（不做维度建模、不做 OLAP、不做报表导出）
- 不支持移动端展示（仅 PC 横屏）
- 不实现"无代码 SQL 查询构建器"（用户直接写 SQL）
- 不引入第三方 BI 嵌入

### 1.4 用户画像

| 角色 | 操作 |
|------|------|
| 管理员（拥有 `screen:screen:edit` 权限） | 创建/编辑/发布大屏 |
| 普通角色（拥有 `screen:screen:view:{code}` 权限） | 查看已发布大屏 |
| 领导 | 通过投屏查看 `/screen/{code}` |

---

## 2. 整体架构

### 2.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      浏览器（前端）                          │
├─────────────────────────────────────────────────────────────┤
│  /screen/:code  渲染页（运行时）                             │
│    └─ useScreenScale()       等比缩放 1920×1080 → 视口        │
│    └─ ScreenRenderer         读 config → 遍历卡片 → 渲染组件  │
│                                                              │
│  /screen/editor/:code  编辑器（设计时）                      │
│    └─ vue-grid-layout        拖拽 + 缩放                     │
│    └─ CardPanel              组件 registry + 配置面板        │
│    └─ DataSourceBinder       HTTP / SQL 绑定 + 字段映射      │
└─────────────────────────────────────────────────────────────┘
                          ↕ HTTP
┌─────────────────────────────────────────────────────────────┐
│              forge-module-screen（后端新模块）               │
├─────────────────────────────────────────────────────────────┤
│  Controller                                                  │
│    /screen/list, /screen/{code}/config (GET/PUT)             │
│    /screen/data-source/execute (POST)                        │
│  Service                                                     │
│    ScreenService             CRUD + 发布 + 复制              │
│    DataSourceService         HTTP 代理 / SQL 执行            │
│    SqlSafetyGuard            AST 解析 + 白名单 + 限制        │
└─────────────────────────────────────────────────────────────┘
                          ↕
            现有基础设施：MySQL / Redis / Actuator
```

### 2.2 模块边界

- 新建 `forge-module-screen`（api + biz）作为独立 Maven 模块，避免污染 system 模块
- 受控 SQL 走 `forge-spring-boot-starter-mybatis` 的数据权限框架，复用 `@DataPermission` / `@OperationLog`
- 大屏路由通过现有菜单表注册（`menu_type = 'S'`），动态路由生成不需改动
- 与刚完成的"主题三维度切换系统"协调：跟随 palette、忽略 layout/style、独立 screen-theme

### 2.3 配置存储分离（关键决策）

| 字段 | 内容 | 可见性 |
|------|------|--------|
| `sys_screen.config` | 已发布的运行时配置（布局+卡片+绑定引用） | 拥有 `screen:screen:view:{code}` 权限可读 |
| `sys_screen.config_draft` | 编辑中的草稿 | 仅拥有 `screen:screen:edit` 权限可读（预览路由也走此权限） |
| `sys_screen_data_source.config` | 敏感配置（SQL 原文、HTTP URL） | **仅后端可读**，前端永远只引用 `dataSourceId` |

前端永远拿不到 SQL 原文，只能通过 `dataSourceId` 调用 `/screen/data-source/execute`，由后端按 dataSource 类型分发（HTTP 代理 or SQL 执行）。

---

## 3. 数据模型

### 3.1 表结构

```sql
-- 大屏主体（一套大屏一行）
CREATE TABLE sys_screen (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(64)  NOT NULL UNIQUE COMMENT '路由编码，如 operations',
  name            VARCHAR(128) NOT NULL                COMMENT '显示名',
  description     VARCHAR(512)                         COMMENT '说明',
  -- config: 已发布配置（运行时读这个）
  -- 结构: { version, theme, layout: [...], cards: [{id, type, title, x, y, w, h, dataSourceId, refresh, options}] }
  config          JSON,
  -- config_draft: 编辑中草稿
  config_draft    JSON,
  theme           VARCHAR(32)  DEFAULT 'dark-tech'     COMMENT '大屏主题',
  status          TINYINT      DEFAULT 0               COMMENT '0=草稿 1=已发布',
  version         INT          DEFAULT 1               COMMENT '乐观锁',
  -- 标准字段
  create_time     DATETIME     NOT NULL,
  update_time     DATETIME     NOT NULL,
  create_by       BIGINT,
  update_by       BIGINT,
  deleted         TINYINT      DEFAULT 0,
  remark          VARCHAR(255),
  INDEX idx_status_code (status, code)
);

-- 数据源（敏感配置，仅后端可读，前端只引用 id）
CREATE TABLE sys_screen_data_source (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(64)  NOT NULL,
  name            VARCHAR(128) NOT NULL,
  type            VARCHAR(16)  NOT NULL                 COMMENT 'HTTP / SQL',
  -- HTTP: { method, url, headers, body, params, timeout }
  -- SQL:  { dialect(default mysql), sqlTemplate, paramSchema, maxRows }
  config          JSON         NOT NULL,
  cache_seconds   INT          DEFAULT 0                COMMENT '0=不缓存',
  enabled         TINYINT      DEFAULT 1,
  -- 标准字段（create_time/update_time/create_by/update_by/deleted/remark）
  UNIQUE KEY uk_screen_ds_code (code)
);

-- 大屏与数据源的关系：用于删除/依赖检查
CREATE TABLE sys_screen_data_source_ref (
  screen_id       BIGINT NOT NULL,
  data_source_id  BIGINT NOT NULL,
  PRIMARY KEY (screen_id, data_source_id)
);

-- SQL 白名单（列级控制）
CREATE TABLE sys_screen_sql_whitelist (
  id           BIGINT PRIMARY KEY,
  schema_name  VARCHAR(64) NOT NULL,
  table_name   VARCHAR(64) NOT NULL,
  column_list  JSON,                                -- 允许的列；null=全部
  risk_level   TINYINT,                             -- 0=公开 1=内部 2=敏感
  enabled      TINYINT DEFAULT 1,
  remark       VARCHAR(255)
);
```

### 3.2 关键决策

1. **`config` / `config_draft` 用 JSON 字段而非子表**：卡片布局是嵌套结构，JSON 更自然；MySQL 8 已支持 JSON_PATH 索引，必要时可建函数索引
2. **草稿/发布双态**：编辑器修改时只动 `config_draft`；发布时把 `config_draft` 覆盖到 `config`，运行时只读已发布版本，避免领导汇报时看到半成品
3. **`sys_screen_data_source` 独立表**：数据源可被多个大屏复用；删除大屏时不删数据源，但有 `ref` 关系表做反向依赖检查
4. **`version` 乐观锁**：多人编辑同一大屏时不互相覆盖
5. **`cache_seconds`**：领导汇报时多个卡片可能命中同一数据源，缓存能扛刷新峰值
6. **白名单初始化（仅系统表，最保守）**：初始放 `sys_user`（排除 password/salt/email/phone/id_card）、`sys_role`、`sys_dept`、`sys_menu`、`sys_dict`、`sys_login_log`、`sys_operation_log`；业务模块（workflow/ai 等）的数据源接入需先由管理员扩白名单

### 3.3 菜单注册

创建大屏时自动在 `sys_menu` 表插入一条记录：
- `menu_type = 'S'`
- `path = /screen/{code}`
- `permission = screen:screen:view:{code}`

大屏被角色绑定后即可访问，编辑器入口通过独立权限 `screen:screen:edit` 控制。

---

## 4. 前端组件 Registry 与渲染机制

### 4.1 组件契约

```ts
interface ScreenCardComponent<TConfig, TData> {
  type: string                                        // 'line-chart' | 'digital-number' | ...
  component: ConcreteComponent                        // Vue 组件
  meta: {
    title: string                                     // 显示名
    icon: string                                      // 选择器图标
    defaultProps: TConfig                             // 默认配置
    configSchema: JSONSchema7                         // 配置面板 schema
    dataShape: { fields: FieldDef[]; sample: TData }  // 数据形状 + 样例
    minWidth: number; minHeight: number               // 网格约束
  }
}

export const cardRegistry = createRegistry<ScreenCardComponent<any, any>>()
```

### 4.2 内置组件清单（首批 8 个）

| 类型 | 用途 | 备注 |
|------|------|------|
| `digital-number` | KPI 数字翻牌器 | 自研，带动画 |
| `line-chart` | 折线/面积图 | ECharts |
| `bar-chart` | 柱状/条形图 | ECharts |
| `pie-chart` | 饼图/环形图 | ECharts |
| `map-chart` | 中国地图分布 | ECharts + geojson |
| `scroll-table` | 滚动列表 | vxe-table（已在依赖里） |
| `gauge` | 仪表盘 | ECharts |
| `text-board` | 文字看板（标题/时间） | 自研 |

### 4.3 装饰组件（布局元素，非卡片）

- `TechBorder`（科技感边框，6 种风格）
- `DecorationCorner`（装饰角）
- `TechTitle`（带装饰的标题栏）
- `ScrollNumber`（数字翻牌器内部用）
- `RadarBackground`（雷达扫描背景，可选）

### 4.4 渲染流程

```
useScreenScale()                 // 1. 等比缩放（设计稿 1920×1080）
config = fetchScreen(code)       // 2. 加载已发布 config
for card in config.cards:
    comp = cardRegistry.get(card.type)            // 3.1 查 registry
    data = watchEffect(() => fetchCardData(card)) // 3.2 按卡片自己的 refresh 节奏拉数据
    <comp :data="data" :options="card.options" /> // 3.3 渲染
```

### 4.5 缩放策略

```
设计稿: 1920×1080
scale = min(viewport.w / 1920, viewport.h / 1080)
容器 transform: scale(scale) + transform-origin: center top
窗口 resize 防抖 150ms
```

### 4.6 关键决策

1. **配置面板 schema 驱动**：JSONSchema 驱动表单，加新组件只需写 `meta.configSchema`
2. **数据加载不进组件**：组件只接收 `data` props，由 `ScreenRenderer` 统一调度（缓存、节流、错误降级）
3. **业务方扩展路径**：在 `apps/forge-web/src/views/screen/cards/` 下新建文件并 `cardRegistry.register(...)`
4. **ECharts 5 按需引入**：用 `echarts/core` + 显式 `use([...])`，包体积控制在 ~300KB gzip
5. **vue-grid-layout 仅编辑器加载**：渲染页不引入，减少运行时体积

---

## 5. 拖拽编辑器

### 5.1 布局

```
┌──────────────────────────────────────────────────────────────────┐
│ 工具栏：[保存草稿] [预览] [发布] [撤销] [重做] [切换主题预览]      │
├────────┬───────────────────────────────────────┬─────────────────┤
│ 组件库  │              画布（1920×1080）          │ 配置面板         │
│        │   ┌──────────┬───────────┐            │                 │
│ ▼ 数值  │   │ 数字卡片  │  折线图   │            │ ▼ 卡片属性       │
│  数字  │   ├──────────┴───────────┤            │ ▼ 数据源         │
│ ▼ 图表  │   │     柱状图            │            │ ▼ 样式           │
│ ▼ 装饰  │   └───────────────────────┘            │                 │
└────────┴───────────────────────────────────────┴─────────────────┘
```

### 5.2 核心交互

1. **加卡片**：左侧组件库拖拽到画布 → 默认占 4×3 网格 → 自动选中 → 右侧弹出配置
2. **移动 / 缩放**：vue-grid-layout 拖拽手柄 + 右下角 resize 句柄，实时写回 `config.cards[].{x,y,w,h}`
3. **配置**：右侧面板根据 `cardRegistry.get(type).meta.configSchema` 自动渲染表单
4. **数据源绑定**：
   - HTTP 模式：填 URL + Method + 参数 → "测试"按钮 → 弹窗显示返回数据 → 字段映射
   - SQL 模式：填 SQL（带 `:param` 占位符）→ 后端校验返回字段 → 自动推荐映射
5. **预览**：弹新窗口路由到 `/screen/preview/{code}?source=draft`，加载草稿配置
6. **发布**：`PUT /screen/{code}/publish` → 后端把 `config_draft` 拷到 `config`，`status=1`，`version+1`

### 5.3 关键决策

1. **撤销/重做**：Pinia + `immer` 维护 `config` 历史栈（最多 50 步），不依赖编辑器库自带的 history
2. **自动保存**：草稿态每 30 秒、或编辑操作后 5 秒静默保存到 `PUT /screen/{code}/config/draft`
3. **预览用 query 区分**：`?source=draft` 让 `ScreenRenderer` 读 `config_draft`
4. **字段映射自动化**：用 `dataShape` 元数据驱动，前端拉数据样例后自动尝试匹配（`timestampLike` → X 轴，`numberLike` → Y 轴），用户可手动调整
5. **组件库与画布解耦**：组件库读 `cardRegistry.list()`，注册新组件后自动出现
6. **复制大屏**：后端 `POST /screen/{code}/copy` 接受 `{ newCode, newName }`，复制源大屏的 `config` + `config_draft`，新大屏 `status=0`（草稿）；前端在大屏列表页提供"复制"按钮
7. **预设布局模板**：前端内置 6 个模板（详见 5.4），编辑器新建大屏时让用户选择，无需后端表

---

### 5.4 预设布局模板（前端内置）

新建大屏时弹出模板选择器，选择后写入 `config_draft`：

| 模板 | 适用场景 | 布局描述 |
|------|---------|---------|
| 空白 | 自由设计 | 仅画布，无卡片 |
| 1 大 3 小 | 核心指标 + 辅助图表 | 左 1/2 大数字卡（占 12×10）；右侧 3 个折线/柱状（各 12×3） |
| 4 宫格 | 等量指标对比 | 2×2 布局，每块 12×5 |
| 上下分栏 | KPI + 明细 | 顶部 4 个数字卡横排（24×2）；下方两个图表（各 12×8） |
| 三栏 | 多维监控 | 左 8×10 趋势线、中 8×10 地图、右 8×10 滚动表 |
| 大屏汇报 | 领导汇报标准模板 | 顶部 TechTitle 横幅 + 中部 3 个 KPI + 下方 2 个图表 + 装饰边框 |

模板结构示例（前端 hardcode，位于 `apps/forge-web/src/views/screen/templates/`）：

```ts
export const presetTemplates = [
  {
    code: 'blank', name: '空白',
    config: { version: 1, theme: 'dark-tech', cards: [] }
  },
  {
    code: 'hero-3', name: '1 大 3 小',
    config: {
      version: 1, theme: 'dark-tech',
      cards: [
        { id: cid(), type: 'digital-number', x:0, y:0, w:12, h:10, dataSourceId:null, refresh:30, options:{} },
        { id: cid(), type: 'line-chart',     x:12,y:0, w:12, h:3,  dataSourceId:null, refresh:30, options:{} },
        { id: cid(), type: 'bar-chart',      x:12,y:3, w:12, h:3,  dataSourceId:null, refresh:30, options:{} },
        { id: cid(), type: 'pie-chart',      x:12,y:6, w:12, h:4,  dataSourceId:null, refresh:30, options:{} },
      ]
    }
  },
  // ... 其他模板
]
```

业务方选中模板后，编辑器立刻可拖拽调整或绑定数据源。

---

## 6. 受控 SQL 安全模型（最敏感）

### 6.1 执行流水线

```java
String sql = dataSource.config.sqlTemplate;
Map<String,Object> params = request.params;

// ① AST 解析（JSqlParser）
Statement stmt = CCJSqlParserUtil.parse(sql);
SqlSafetyValidator validator = new SqlSafetyValidator();
validator.assertSelectOnly(stmt);
validator.assertNoSubqueryOutside(stmt);
validator.assertNoSystemTable(stmt);
validator.assertTablesInWhitelist(stmt, allowedTables);
validator.assertNoDangerousFunctions(stmt);
validator.assertLimitPresent(stmt);

// ② 参数化执行（绝不字符串拼接）
String namedSql = convertPlaceholders(sql);
Query query = sqlSession.selectList(namedSql, params);

// ③ 数据权限作用域（沿用现有框架）
@DataPermission(deptAlias="d", userAlias="u")

// ④ 超时熔断
query.setStatementTimeout(5_000);

// ⑤ 审计
@OperationLog(title="大屏SQL查询", businessType=SELECT,
              extendField="#dataSource.code")

// ⑥ 结果脱敏
List<Map> rows = maskIfSensitive(query.getResultList());
```

### 6.2 安全护栏清单

| 限制 | 阈值 |
|------|------|
| 仅 SELECT | AST 拒绝 INSERT/UPDATE/DELETE/DDL/CALL |
| 表白名单 | 仅 `sys_screen_sql_whitelist` 中的表 |
| 列级控制 | `column_list` 显式列出允许的列；password/salt/email/phone/id_card 等排除 |
| 系统表禁用 | `mysql.*`、`information_schema.*`、`pg_*`、`sys.*` |
| 危险函数禁用 | `LOAD_FILE`、`SLEEP`、`BENCHMARK`、`INTO OUTFILE`、`LOAD DATA` |
| 强制 LIMIT | 缺失则拒绝；`LIMIT > 1000` 强制改写为 1000 |
| 行数硬上限 | 1000 行 |
| 执行超时 | 5 秒 |
| 子查询限制 | 不允许引用白名单外的表 |
| 堆叠查询 | JSqlParser 拒绝多语句 |

### 6.3 关键决策

1. **AST 解析而非正则**：能 100% 识别子查询、UNION、注释投毒、嵌套危险函数
2. **白名单 + 列级控制**：等保二级要求"最小授权"，列级控制保证密码字段绝对不漏
3. **强制 LIMIT**：AST 阶段检查，缺失直接拒绝，> 1000 强制改写
4. **审计记录 code 不记 SQL 原文**：避免审计表本身泄露敏感 SQL；forensics 时通过 code 反查 `sys_screen_data_source.config`
5. **缓存键含 `params` 哈希**：避免不同参数命中错误缓存
6. **`@DataPermission` 自动生效**：用户配置的 SQL 不能绕过数据权限

### 6.4 HTTP 数据源的同等保护

- 仅允许内网/白名单域名（防 SSRF）：`forge.security.screen.allowed-hosts`
- 强制 HTTPS（生产环境）
- 透传当前用户 Token，让下游服务能再校验权限
- 超时 5 秒，响应体上限 1MB

---

## 7. 主题与视觉风格

### 7.1 半独立主题策略

```
全局主题（用户在 SettingsPanel 切换的）
  └─ palette（蓝/绿/紫/橙/红/custom）  ← 大屏跟随
  └─ layout（side/top）                 ← 大屏忽略
  └─ style（standard/compact/glass）    ← 大屏忽略

大屏内置主题（独立）
  └─ screen-theme（dark-tech / blue-deep / black-gold）
```

**理由：**
- 跟随 palette：让"科技蓝"切换成"活力橙"时，大屏主色也跟着变，与企业文化色一致
- 不跟随 layout/style：大屏没有侧栏、没有卡片间距概念
- 独立 screen-theme：领导汇报场景有 3 套成熟风格可选

### 7.2 CSS 变量驱动

```scss
:root[data-screen-theme='dark-tech'] {
  --screen-bg: radial-gradient(ellipse at top, #0a1929 0%, #000 100%);
  --screen-card-bg: rgba(8, 22, 40, 0.85);
  --screen-border: #1e3a5f;
  --screen-accent: var(--palette-primary);
  --screen-text-primary: #e0e6f1;
  --screen-text-secondary: #8a96a8;
  --screen-grid-line: rgba(30, 58, 95, 0.3);
}
:root[data-screen-theme='blue-deep'] {
  --screen-bg: radial-gradient(ellipse at top, #051c3f 0%, #020814 100%);
  --screen-card-bg: rgba(7, 35, 75, 0.85);
  --screen-border: #1c4d8f;
  --screen-accent: var(--palette-primary);
  --screen-text-primary: #d6e6ff;
  --screen-text-secondary: #7e9bc6;
  --screen-grid-line: rgba(28, 77, 143, 0.3);
}
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

### 7.3 字体

- 数字：`DIN Alternate` / `Orbitron`（科技感数字字体，本地加载）
- 中文：默认 `PingFang SC` / `Microsoft YaHei`（系统字体，不打包）
- 通过 `@font-face` 加载，仅在大屏路由懒加载字体文件

---

## 8. 错误处理与降级策略

### 8.1 卡片级错误边界

```vue
<CardErrorBoundary
  :fallback="/* '数据加载失败，5s 后重试' */"
  :on-retry="() => refetch()"
  :retry-after-ms="5000"
>
  <component :is="card.component" :data="data" :options="card.options" />
</CardErrorBoundary>
```

### 8.2 降级矩阵

| 场景 | 处理 |
|------|------|
| 数据源请求超时（>5s） | 显示"加载中"，超时后切到上次缓存；缓存也无 → 错误占位 + 5s 后重试 |
| HTTP 数据源返回错误 | 同上；后端审计日志记录；连续 3 次失败 → 标记数据源为"异常" |
| SQL 校验失败 | 编辑器即时反馈：标红 + AST 错误位置（Monaco Editor），不允许保存 |
| SQL 运行时失败 | 后端捕获 SQLException → 记录 → 返回标准错误响应 → 卡片显示"数据源异常" |
| 缓存击穿 | Redis 单飞（Singleflight）：同一 dataSourceId + params 哈希的并发请求合并 |
| ECharts 渲染异常 | 卡片级 error boundary 接住，不影响其他卡片 |
| 大屏 config 加载失败 | 整屏显示"大屏未配置或已下线" |
| WebSocket 断连 | 静默重连，2s/4s/8s 退避；连续 5 次失败降级为 HTTP 轮询 |

### 8.3 后端容错

1. **SQL 执行超时**：MyBatis statement timeout 5s + HikariCP 连接超时 10s
2. **缓存雪崩防护**：`cache_seconds` 由用户配置；为缓存值加 ±10% 随机抖动
3. **熔断**：单个 dataSource 1 分钟内失败 ≥10 次 → 熔断 30s
4. **限流**：`@RateLimiter` 加在 `/screen/data-source/execute`，按用户 + dataSourceId 维度，60s 内最多 60 次

### 8.4 审计告警

- 数据源熔断时推送 WebSocket 通知到管理员（复用 `/user/{userId}/queue/notifications`）
- 后台运维页（`/monitor/screen-health`）显示所有数据源健康度

---

## 9. 测试策略

### 9.1 测试矩阵

| 层 | 工具 | 重点 |
|----|------|------|
| 后端单元 | JUnit 5 + Mockito | `SqlSafetyValidator`、`ScreenService` |
| 后端集成 | Spring Boot Test + Testcontainers MySQL | 真实数据库执行受控 SQL，验证 `@DataPermission`、超时、缓存 |
| 前端单元 | Vitest | `cardRegistry`、`useScreenScale`、JSONSchema 表单、撤销/重做 |
| 前端组件 | Vitest + @vue/test-utils | 内置卡片组件渲染 + 数据更新、编辑器拖拽、错误边界 |
| E2E | Playwright | 编辑→保存→预览全流程、1920×1080 视觉回归 |
| 安全专项 | 手工 + 自动化扫描 | SQL 注入 12 个 Payload、白名单绕过、SSRF |

### 9.2 SQL 安全专项测试（必须覆盖的 12 种）

```java
@Test void rejectUnionInjection()          // SELECT * FROM t UNION SELECT password FROM sys_user
@Test void rejectCommentInjection()        // SELECT * FROM t -- ; DROP TABLE
@Test void rejectSystemTable()             // SELECT * FROM information_schema.tables
@Test void rejectDangerousFunction()       // SELECT LOAD_FILE('/etc/passwd')
@Test void rejectNoLimit()                 // SELECT * FROM sys_user
@Test void rejectLimitTooLarge()           // SELECT * FROM sys_user LIMIT 100000
@Test void rejectNonSelect()               // DELETE FROM sys_user WHERE 1=1
@Test void rejectStoredProcedure()         // CALL sys.do_something()
@Test void rejectIntoOutfile()             // SELECT * INTO OUTFILE '/tmp/x'
@Test void rejectSleepBenchmark()          // SELECT SLEEP(100000)
@Test void rejectInformationSchemaAliased()// SELECT * FROM sys_user u, information_schema.tables t
@Test void rejectStackedQueries()          // SELECT 1; DROP TABLE sys_user
```

### 9.3 等保二级专项测试

- 数据权限：不同 dept 的用户执行同一 SQL，结果集必须按 `@DataPermission` 范围裁剪
- 审计完整性：每次 SQL 执行必须能在 `sys_operation_log` 找到记录（含 code、操作人、参数）
- 字段脱敏：白名单外的字段即使 SELECT 也不能返回

### 9.4 覆盖率目标

- 后端核心模块（`SqlSafetyValidator`、`ScreenService`）≥ 90%
- 前端组件 ≥ 70%
- E2E 覆盖编辑器→发布→查看主流程
- 视觉回归：每个内置主题截图存基线，差异 > 5% 时失败

### 9.5 注意

- **不为 SQL 执行写"内存数据库"测试**：必须用真实 MySQL（Testcontainers），AST 校验依赖真实 SQL 方言特性

---

## 10. 实施分阶段建议

| 阶段 | 内容 | 工作量估算 |
|------|------|-----------|
| P0 | 后端模块骨架 + 数据模型 + 基本 CRUD + 复制接口 | 2 天 |
| P1 | `SqlSafetyValidator` + 12 种安全用例 | 2 天 |
| P2 | 前端渲染页 + `useScreenScale` + 缩放 + 4 个核心卡片 | 3 天 |
| P3 | 拖拽编辑器 + 配置面板 + 字段映射 + 6 个预设模板 | 3.5 天 |
| P4 | 自研装饰组件库 + 3 套主题 | 2 天 |
| P5 | 错误降级 + 熔断 + 审计告警 | 1.5 天 |
| P6 | 安全测试 + E2E + 性能压测 | 1.5 天 |
| **合计** | | **~15.5 天** |

每阶段独立可发布；P0-P2 完成后即可用 JSON 直接写配置做内部演示。

---

## 11. 风险与开放问题

| 风险 | 影响 | 缓解 |
|------|------|------|
| 受控 SQL 出现 0-day 绕过 | 数据泄露 | AST 校验 + 白名单 + 列级控制三层防御；安全用例持续积累 |
| 拖拽编辑器体验不达预期 | 推广困难 | P3 末做用户测试，必要时引入 VueDraggablePlus 替换 vue-grid-layout |
| 包体积增长（ECharts + grid-layout） | 首屏慢 | 路由级懒加载；ECharts 按需引入；渲染页不引编辑器依赖 |
| 等保复评不通过 | 上线阻塞 | P1 完成后做一次内部等保预审 |

**开放问题：** 无（v1.0 评审已全部决策）

**已决策（v1.0 评审结论）：**

| 问题 | 决策 |
|------|------|
| 大屏版本历史 | **不做**。已发布的 `config` 被覆盖即丢失历史；草稿场景已由 `config_draft` 隔离。如未来有审计回溯需求，再补 `sys_screen_history` |
| 复制大屏 | **支持**。`POST /screen/{code}/copy`，复制 `config` + `config_draft`，新大屏为草稿态 |
| 预设布局模板 | **支持**。前端内置 6 个模板（空白/1大3小/4宫格/上下分栏/三栏/大屏汇报），新建大屏时让用户选择 |
| 白名单初始化范围 | **仅系统表**（最保守）。初始放 sys_user/sys_role/sys_dept/sys_menu/sys_dict/sys_login_log/sys_operation_log，排除 password/salt/email/phone/id_card 等敏感列。业务方接入新数据源需先扩白名单 |

---

## 12. 参考与关联

- 关联文档：`apps/forge-server/docs/SECURITY-COMPLIANCE.md`（等保合规）
- 关联代码：`apps/forge-web/src/composables/useResponsive.ts`（响应式参考）
- 关联代码：`apps/forge-server/forge-framework/forge-spring-boot-starter-mybatis/`（数据权限框架）
- 关联代码：`apps/forge-web/src/router/index.ts`（动态路由生成）
