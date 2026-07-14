# forge-admin

企业级后台管理系统模板，基于 RBAC（基于角色的访问控制）权限模型，前后端分离，开箱即用。

## 项目简介

forge-admin 是一款现代化的企业级后台管理解决方案，采用前后端分离架构设计。后端基于 Spring Boot 3.2 构建，采用多模块 Maven 项目结构，使用 MyBatis Plus 简化数据操作，JWT 实现无状态认证；前端采用 Vue 3 + TypeScript + Element Plus + vxe-table 技术栈，提供流畅的用户体验、完善的类型支持和强大的表格功能。前端内置多套 UI 主题切换系统，支持 4 套预设套餐、调色板/布局/风格三维度独立切换、自定义主色以及明暗双模式，用户可按需选择并持久化到 localStorage。系统内置完整的 B2B SaaS 多租户能力，支持按租户隔离业务数据、套餐化菜单分配、平台超管代管等场景，可通过配置开关回归单租户模式。

系统内置完整的权限管理模块，支持用户、角色、菜单、部门的层级管理，并实现细粒度的数据权限控制（全部/本部门/本部门及以下/仅本人）。集成 OAuth2 授权服务器（基于 Spring Authorization Server），支持微信、钉钉等第三方登录（基于 JustAuth），允许第三方应用通过本系统进行用户认证。此外还集成了 Quartz 定时任务调度、在线 API 文档（Knife4j）、操作日志审计、登录日志等企业级功能。

系统同时内置**数据可视化大屏**模块，提供基于 goView 的拖拽式大屏编辑器、多数据源（SQL / HTTP）配置能力，以及面向纵深防御的 SQL 安全层（AST 校验 + 表/列白名单 + 运行时 LIMIT 限制），可安全地支持业务指标监控、运营驾驶舱等场景。支持 Docker 容器化部署，提供项目模板化工具，可快速基于此项目创建新的管理系统。

## 项目截图

### 登录页面

![登录页面](./docs/screenshots/login.png)

### 仪表盘

![仪表盘](./docs/screenshots/dashboard.png)

### 用户管理

![用户管理](./docs/screenshots/user.png)

### 角色管理

![角色管理](./docs/screenshots/role.png)

### 菜单管理

![菜单管理](./docs/screenshots/menu.png)

### 定时任务

![定时任务](./docs/screenshots/job.png)


## 功能清单

### 系统管理

| 功能 | 说明 |
|------|------|
| 用户管理 | 用户增删改查、状态控制、密码重置、部门关联、岗位分配 |
| 角色管理 | 角色增删改查、菜单权限分配、数据权限设置 |
| 菜单管理 | 菜单增删改查、路由配置、权限标识、图标设置 |
| 部门管理 | 部门树形结构、增删改查、负责人设置 |
| 岗位管理 | 岗位增删改查、状态控制 |
| 字典管理 | 字典类型和数据管理、缓存刷新 |
| 参数配置 | 系统参数增删改查、缓存刷新 |
| 文件配置 | 文件存储配置（本地/OSS） |
| 通知公告 | 公告增删改查、发布状态管理 |
| 在线用户 | 查看在线用户、强制下线 |
| 租户管理 | 租户 CRUD（仅平台超管可见），创建时自动生成租户管理员账号 + 强密码 + 首次登录强制改密 |
| 套餐管理 | 租户套餐（菜单包）维护，支持差异化菜单分配（待接入运行时菜单过滤） |
| 租户切换 | 平台超管通过 `X-Tenant-Id` 切换操作租户上下文，前端头部下拉切换 |

### 监控中心

| 功能 | 说明 |
|------|------|
| 登录日志 | 登录记录查询、状态统计 |
| 操作日志 | 操作记录查询、审计追踪 |
| 定时任务 | 任务增删改查、执行控制、日志查看 |
| 服务监控 | 服务器状态、JVM 信息、Redis 监控 |

### 流程管理

| 功能 | 说明 |
|------|------|
| 模型管理 | 流程模型设计（FlowLong可视化设计器）、模型部署、版本管理 |
| 审批流程 | 已部署流程查看、启用/停用、版本切换 |
| 表单管理 | 表单设计（可视化拖拽）、表单配置 |
| 流程分类 | 分类树形结构、增删改查 |
| 流程实例 | 实例查询、详情查看、取消流程 |
| 待办任务 | 待办列表、审批操作（通过/驳回/委派/转办/退回/加签） |
| 已办任务 | 已办列表、审批记录查看 |
| 抄送列表 | 抄送记录查看 |
| 表达式管理 | 流程表达式增删改查 |
| 监听器管理 | 流程监听器增删改查 |

### AI 功能

| 功能 | 说明 |
|------|------|
| AI 文档管理 | 文档上传、智能摘要生成、对话问答 |
| 多模型对话 | 支持 DeepSeek、Qwen、GLM、ERNIE 等模型 |
| 文档解析 | PDF、DOCX、TXT 文档解析 |
| 流式响应 | SSE 实时流式输出 |

### 大屏管理

| 功能 | 说明 |
|------|------|
| 大屏列表 | 大屏 CRUD、按 code 访问、发布、复制、版本管理 |
| 大屏编辑器 | 基于 goView 拖拽式编辑器（iframe 嵌入），支持柱/线/饼/地图/散点等图表库 |
| 大屏渲染 | 公开访问 / 登录访问、主题切换、字段映射与自动保存草稿 |
| 数据源管理 | SQL / HTTP 两种类型，启用/禁用、缓存时长配置；敏感配置不返回列表 |
| SQL 白名单 | 表/列级白名单维护，三级风险等级（公开/内部/敏感） |
| 大屏安全 | SQL AST 校验 + 列级白名单 + HTTP SSRF 防护 + 数据源熔断器 |

### 移动端（小程序）

| 功能 | 说明 |
|------|------|
| 微信授权登录 | 一键授权、自动注册 |
| 个人中心 | 信息编辑、头像修改、手机绑定 |
| 账号注销 | 用户账号注销 |

### 系统工具

| 功能 | 说明 |
|------|------|
| 项目模板化 | 基于模板创建新项目 |
| 模块管理 | 创建/删除业务模块 |
| API 文档 | Knife4j 在线文档 |

### 主题系统

| 功能 | 说明 |
|------|------|
| 预设套餐 | 4 套精心搭配的视觉套餐（默认/极客紫/商务器/酷暗黑），每套含配色 + 布局 + 风格组合 |
| 明暗双模式 | 每个套餐都支持 light/dark 双版本，独立切换不互相影响 |
| 高级设置 | 三维度独立切换：调色板（蓝/紫/绿/红/自定义）、布局（侧栏/顶栏）、风格（扁平/玻璃/卡片/紧凑） |
| 自定义色板 | 输入 HEX 主色，运行时自动派生 EP 颜色阶梯（light-3/5/7/9 + dark-2），实时预览 |
| 响应式布局 | 移动端始终为侧栏布局（顶栏在小屏自动回落），保证小屏可用性 |
| 错误降级 | localStorage 损坏、未知套餐 ID、不支持 backdrop-filter 等场景均静默降级 |
| 跨版本兼容 | 老 localStorage 数据自动迁移（preset → 三维度派生），用户无感知升级 |
| 设置持久化 | localStorage 持久化（key 为 `forge_admin-page-config`），不引入后端依赖 |

## 特性

- **权限管理**：完整的 RBAC 权限系统，支持用户、角色、菜单、部门管理
- **数据权限**：支持部门数据权限隔离（5 种范围类型）
- **多租户**：B2B SaaS 多租户隔离（MyBatis Plus `TenantLineInnerInterceptor` + SQL 自动注入 + 缓存 key 前缀），可配置开关 `forge.tenant.enable`
- **OAuth2 授权服务器**：基于 Spring Authorization Server，支持授权码、客户端凭证等标准 OAuth2/OIDC 协议
- **第三方登录**：支持微信、钉钉扫码登录，基于 JustAuth 实现
- **工作流引擎**：集成 FlowLong 国产工作流引擎，支持流程设计、审批管理、待办任务、流程实例监控
- **可视化大屏**：基于 goView 的拖拽式大屏编辑器、多数据源配置、列级 SQL 白名单、SSRF 防护、数据源熔断
- **字典管理**：灵活的数据字典配置，支持缓存刷新
- **定时任务**：基于 Quartz 的定时任务管理和日志
- **文件存储**：支持本地存储，可扩展 OSS 等
- **AI 功能**：集成 Python AI 服务，支持多模型对话、文档解析、智能摘要
- **API 文档**：集成 Knife4j，提供在线 API 文档
- **移动端支持**：独立的 `/app-api` 端点，支持微信小程序授权登录
- **强大表格**：vxe-table 提供列自定义、导出、打印等功能
- **多套 UI 主题**：4 套预设套餐 + 调色板/布局/风格三维度独立切换 + 自定义主色 + 明暗双模式，CSS 变量两阶桥接（业务变量 → Element Plus/vxe-table）实现无刷新切换
- **等保二级合规**：符合 GB/T 22239-2019 二级等保要求的安全改造
  - **密码安全**：复杂度校验（8-32位、大小写+数字+特殊字符）、历史校验（5条不可重复）、90天有效期、首次登录强制改密、BCrypt 强度=12
  - **登录安全**：失败锁定（5次→15分钟）、图形验证码、单点登录（踢掉旧会话+refreshToken 同步失效）
  - **数据加密**：AES-256-GCM 敏感字段加密（phone/email）、jasypt 配置文件加密
  - **应用安全**：XSS 过滤器、安全响应头（CSP/HSTS/X-Frame-Options 等）、文件上传校验（扩展名+Magic Number）
  - **审计安全**：操作日志敏感字段自动脱敏（密码/手机号/邮箱/身份证）
- **大屏模块安全**：
  - **SQL 三道闸门**：AST 静态规则（SELECT-only / 禁 UNION/SET / 禁危险函数 / 禁系统表 / 必须 LIMIT）+ 表/列白名单（列级 fail-closed）+ 运行时 LIMIT/OFFSET 实参上限
  - **HTTP SSRF 防护**：`forge.security.screen.allowed-hosts` host 白名单、5s 连接/读取超时、1MB 响应体上限、可选强制 HTTPS
  - **数据源熔断器**：基于 Redis 滑动窗口，1 分钟内失败 10 次触发 30 秒熔断
  - **访问控制**：大屏支持公开访问（免登录）和登录访问，敏感 `config` 字段不在列表接口返回

## 技术栈

### 后端

- Java 21
- Spring Boot 3.2.0
- MyBatis Plus 3.5.17
- MySQL 8.0+
- Redis 6.0+
- JWT 认证
- MyBatis Plus TenantLineInnerInterceptor（多租户）
- Spring Authorization Server (OAuth2/OIDC)
- JustAuth（第三方登录）
- Knife4j (Swagger)
- Quartz 定时任务
- FlowLong 1.2.5（国产工作流引擎）
- JSqlParser 4.9（SQL 解析与 AST 校验）
- jasypt-spring-boot-starter（配置文件加密）
- AES-256-GCM + BCrypt（数据加密与密码哈希）

### 前端

- Vue 3.4
- TypeScript 5.3
- Element Plus 2.4
- vxe-table 4.9（强大表格组件）
- vxe-pc-ui 4.6
- Pinia 2.1
- Vite 5.0

### 大屏编辑器

- goView 1.3.2（基于 VChart 的拖拽式大屏编辑器）
- VChart 2.0（图表渲染引擎）
- 通过 iframe 嵌入到 forge-web，组件编辑、自动保存草稿

### 移动端（小程序）

- uni-app + Vue 3
- 微信小程序授权登录
- Pinia 状态管理
- 独立的 app_user 用户表

### AI 服务（Python）

- Python 3.10+
- FastAPI
- 多模型支持：DeepSeek、Qwen（通义）、GLM（智谱）、ERNIE（百度）
- 文档解析：PDF、DOCX、TXT
- SSE 流式响应

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+（推荐 22.9.0）
- pnpm 8.15.4+
- MySQL 8.0+
- Redis 6.0+
- 微信开发者工具（小程序开发）

### 安装

1. **克隆项目**

```bash
git clone <repository-url>
cd forge-admin
```

2. **创建数据库**

```bash
mysql -u root -p < sql/init.sql
```

3. **启动后端**

```bash
cd apps/forge-server
mvn spring-boot:run -pl forge-server
```

后端服务运行在 http://localhost:8181

4. **启动前端**

```bash
cd apps/forge-web
pnpm install
pnpm dev
```

前端服务运行在 http://localhost:3003

5. **启动大屏编辑器**（可选）

```bash
cd apps/forge-screen
pnpm install
pnpm dev
```

大屏编辑器运行在 http://localhost:8001，通过 iframe 嵌入到 forge-web 的"大屏管理 → 编辑器"页面。

6. **启动小程序**（可选）

```bash
cd apps/forge-miniapp
pnpm install
pnpm dev:mp-weixin
```

小程序开发工具导入 `dist/dev/mp-weixin` 目录

7. **启动 AI 服务**（可选）

```bash
cd apps/forge-ai-python
pip install -e .
python -m uvicorn src.main:app --reload --port 8000
```

AI 服务运行在 http://localhost:8000，需配置 API Key（见 [apps/forge-ai-python/README.md](apps/forge-ai-python/README.md)）

8. **访问系统**

- 前端地址：http://localhost:3003
- 大屏编辑器：http://localhost:8001
- API 文档：http://localhost:8181/doc.html
- 默认账号：`admin` / `password`（启用多租户时登录页需输入租户标识，默认 `default`）
- OAuth2 使用文档：[docs/oauth2-guide.md](docs/oauth2-guide.md)

## 开发命令

### 前端（在 `apps/forge-web` 目录下）

```bash
pnpm install    # 安装依赖
pnpm dev        # 启动开发服务器（端口 3003）
pnpm build      # 生产构建（含类型检查）
pnpm preview    # 预览生产构建
pnpm lint       # 运行 ESLint
pnpm test       # 运行 vitest 单元测试
```

### 大屏编辑器（在 `apps/forge-screen` 目录下）

```bash
pnpm install    # 安装依赖
pnpm dev        # 启动开发服务器（端口 8001）
pnpm build      # 生产构建
pnpm lint       # 运行 ESLint
```

### 小程序（在 `apps/forge-miniapp` 目录下）

```bash
pnpm install    # 安装依赖
pnpm dev:mp-weixin  # 微信小程序开发模式
pnpm build:mp-weixin # 微信小程序生产构建
```

### 后端（在 `apps/forge-server` 目录下）

```bash
mvn spring-boot:run -pl forge-server    # 启动开发服务器
mvn clean compile                       # 仅编译
mvn clean package -DskipTests           # 打包 JAR（跳过测试）
mvn test                                # 运行所有测试
mvn test -Dtest=ClassName -pl <module>  # 运行指定模块的单个测试类
```

## 目录结构

```
forge-admin/
├── apps/
│   ├── forge-server/                    # 后端（多模块 Maven 项目）
│   │   ├── pom.xml                      # 根聚合 POM
│   │   ├── forge-dependencies/          # BOM 版本管理
│   │   ├── forge-framework/             # 框架层
│   │   │   ├── forge-common/            # 公共模块（注解、异常、响应、工具类）
│   │   │   ├── forge-spring-boot-starter-mybatis/   # MyBatis + 数据权限
│   │   │   ├── forge-spring-boot-starter-redis/     # Redis 配置
│   │   │   ├── forge-spring-boot-starter-security/  # JWT + OAuth2
│   │   │   ├── forge-spring-boot-starter-tenant/    # 多租户（SQL 自动注入 + 过滤器 + AOP）
│   │   │   └── forge-spring-boot-starter-web/       # Web + WebSocket
│   │   ├── forge-module-system/         # 系统模块
│   │   │   ├── forge-module-system-api/ # API 接口 + 实体 + DTO
│   │   │   └── forge-module-system-biz/ # 业务实现（含 auth、quartz）
│   │   ├── forge-module-workflow/       # 工作流模块
│   │   │   ├── forge-module-workflow-api/
│   │   │   └── forge-module-workflow-biz/
│   │   ├── forge-module-ai/             # AI 模块
│   │   │   ├── forge-module-ai-api/
│   │   │   └── forge-module-ai-biz/
│   │   ├── forge-module-screen/         # 大屏模块
│   │   │   ├── forge-module-screen-api/ # 实体 + DTO + 枚举
│   │   │   └── forge-module-screen-biz/ # Controller/Service/Mapper + safety/executor/cache/fault
│   │   └── forge-server/                # Spring Boot 启动入口
│   │
│   ├── forge-ai-python/                 # Python AI 服务
│   │   ├── src/
│   │   │   ├── api/                     # FastAPI 接口（chat、document、health）
│   │   │   ├── adapters/                # LLM 适配器（deepseek、qwen、glm、ernie）
│   │   │   ├── config/                  # 配置管理
│   │   │   ├── models/                  # Pydantic 模型
│   │   │   ├── services/                # 业务服务
│   │   │   └── main.py                  # 应用入口
│   │   └── pyproject.toml
│   │
│   ├── forge-web/                       # 前端应用
│   │   ├── src/
│   │   │   ├── api/                     # API 接口定义
│   │   │   ├── components/              # 公共组件
│   │   │   ├── composables/             # 组合式函数（useTableHeight、useTableSeq 等）
│   │   │   ├── layouts/                 # 布局组件（BasicLayout 分发器 + LayoutSidebar + LayoutTop）
│   │   │   ├── plugins/                 # vxe-table 全局配置
│   │   │   ├── router/                  # 路由配置（动态路由）
│   │   │   ├── stores/                  # Pinia 状态管理
│   │   │   ├── styles/                  # 样式文件
│   │   │   ├── themes/                  # 主题系统（套餐注册表 + 调色板 + 风格 + 运行时颜色派生）
│   │   │   ├── types/                   # TypeScript 类型定义
│   │   │   ├── utils/                   # 工具函数
│   │   │   └── views/                   # 页面组件（含 views/screen 大屏管理）
│   │   │       └── screen/              # 大屏管理页面
│   │   │           ├── index/           #   大屏列表
│   │   │           ├── editor/          #   编辑器入口（iframe 嵌入 goView）
│   │   │           ├── preview/         #   大屏预览
│   │   │           ├── render/          #   公开/登录大屏渲染
│   │   │           ├── data-source/     #   数据源管理
│   │   │           └── sql-whitelist/   #   SQL 白名单管理
│   │   └── package.json
│   │
│   ├── forge-screen/                    # 大屏编辑器（goView，独立 SPA）
│   │   ├── src/
│   │   │   ├── api/forge/               # 与 forge-admin 通信的 API
│   │   │   ├── store/                   # Pinia stores（chartEditStore，自动保存草稿）
│   │   │   ├── hooks/                   # useSync、useChartDataFetch
│   │   │   ├── packages/                # 图表组件库（Bars/Lines/Pies/Maps/...）
│   │   │   └── views/chart/             # 图表编辑视图
│   │   └── package.json
│   │
│   └── forge-miniapp/                   # 小程序应用
│       ├── src/
│       │   ├── api/                     # API 接口定义
│       │   ├── pages/                   # 页面组件
│       │   │   ├── login/               # 登录页
│       │   │   └── profile/             # 个人中心
│       │   ├── stores/                  # Pinia 状态管理
│       │   ├── static/                  # 静态资源（logo、头像）
│       │   └── composables/             # 组合式函数
│       └── package.json
│
├── docker/                              # Docker 配置
├── scripts/                             # 项目初始化脚本
├── sql/                                 # 数据库脚本
└── .template/                           # 模板配置
```

## 架构要点

### 后端多模块架构

后端采用 17 模块的 Maven 多项目结构，各模块职责清晰：

```
forge-dependencies        → BOM 版本管理
forge-framework (6模块)   → 框架层，可独立版本管理（含多租户 starter）
forge-module-system (2)   → 系统模块，api/biz 分离
forge-module-workflow (2) → 工作流模块，api/biz 分离（基于 FlowLong）
forge-module-ai (2)       → AI 模块，api/biz 分离
forge-module-screen (2)   → 大屏模块，api/biz 分离（含 SQL 安全层）
forge-server              → Spring Boot 启动入口
```

每个业务模块遵循分层结构：`controller/admin/` + `controller/app/` → `service/` → `mapper/`，实体和 DTO 放在 `api` 子模块供跨模块引用。

**双端点架构：**
- `/admin-api/**` — 后台管理端点，使用 `sys_user` 表 + `@PreAuthorize` 权限控制
- `/app-api/**` — 移动端端点，使用独立的 `app_user` 表 + 微信授权登录

框架通过 `WebMvcConfigurer.configurePathMatch()` 根据 Controller 包名（`controller.admin` / `controller.app`）自动注入路径前缀，Controller 代码无需手动指定前缀。

**模块依赖关系：**
- `forge-server` ← `system-biz`、`workflow-biz`、`ai-biz`、`screen-biz`
- `workflow-biz` ← `workflow-api`、`system-api`、`starters`、`flowlong`
- `ai-biz` ← `ai-api`、`system-api`、`starters`（调用 Python AI 服务）
- `system-biz` ← `system-api`、`starters`、`quartz`
- `screen-biz` ← `screen-api`、`system-api`、`starters`

**横切关注点：**
- `@OperationLog` — 基于 AOP 的审计日志
- `@DataPermission` — SQL 级数据范围过滤
- `@RateLimiter` — 基于 Redis 令牌桶的限流
- `@Cacheable/@CacheEvict` — Redis 缓存

### 大屏模块分层

`forge-module-screen-biz` 是少数按子包组织的模块，关键子包：

- `safety/` — SQL 安全三道闸门：`SqlSafetyValidator`（AST 规则：SELECT-only、禁 UNION/SET、禁危险函数、必须 LIMIT）、`SqlSafetyGuard`（编排器）、`WhitelistService`（表/列级白名单校验，列级 fail-closed）
- `executor/` — 数据源执行器：`SqlDataSourceExecutor`（动态 SQL + 参数绑定 + @DataPermission）、`HttpDataSourceExecutor`（SSRF 防护：`ScreenProperties.allowedHosts`）
- `cache/` — Redis 缓存（key = `dataSourceId + paramsJson`）
- `fault/` — 熔断器：基于 Redis 滑动窗口统计失败次数，达阈值拒绝请求

数据源表 `sys_screen_data_source.config` 含 SQL 原文/HTTP URL 等敏感信息。列表接口通过 `qw.select()` 排除 `config` 字段（详见 `SysScreenDataSourceServiceImpl.page`），详情接口正常返回以支持编辑回显。

**大屏安全 API：**
- `POST /admin-api/screen/data-source/{id}/execute` — 执行数据源（内部走 SQL 三道闸门 + 熔断器）
- `GET /admin-api/screen/code/{code}` — 按 code 加载大屏（运行时使用，`permitAll`，支持公开大屏）
- `PUT /admin-api/screen/publish/{code}` — 发布大屏（草稿覆盖到正式 config）

### 前端表格组件（vxe-table）

所有列表页面统一使用 vxe-table，提供：
- 列自定义（显示/隐藏、排序、固定）
- 数据导出（CSV、HTML、XML、TXT）
- 打印功能
- 虚拟滚动（大数据量优化）
- 树形表格（菜单、部门）

**表格命名约定：** `sys{Module}Table`（如 `sysUserTable`、`sysRoleTable`）

### 大屏编辑器集成

goView 编辑器是独立的前端 SPA（`apps/forge-screen`），通过 iframe 嵌入到 `apps/forge-web/src/views/screen/editor/` 和 `preview/`：

- 编辑：用户在 goView 中拖拽组件、绑定 forge 数据源 → 自动保存草稿到后端 `config_draft`
- 渲染：访问 `/screen/render/{code}` → 加载正式 `config` → 渲染图表
- 数据源绑定：编辑器内 `executeDataSource(id, params)` 走 SQL 三道闸门 + 熔断器

### 动态路由

后端返回菜单树 → 转换为 Vue Router 配置 → 通过 `import.meta.glob` 解析组件 → `router.addRoute()` 添加路由。

### AI 服务架构

系统采用 Java + Python 双语言架构实现 AI 功能：

- **Java 端（AI 模块）**：管理文档元数据、调用 Python 服务、回写摘要结果
- **Python 端（AI 服务）**：多模型 LLM 对话、文档解析、智能摘要

Java 通过 `WebClient` 调用 Python FastAPI 服务，支持流式响应（SSE）。Python 服务支持多提供商（DeepSeek、Qwen、GLM、ERNIE），可根据配置动态切换。

**API 端点：**
- `/admin-api/ai/document/**` — Java 文档管理接口
- `http://localhost:8000/api/**` — Python AI 服务接口

### 权限控制

- **后端**：`@PreAuthorize("hasAuthority('system:user:list')")`
- **前端模板**：`v-permission="'system:user:add'"`
- **前端脚本**：`hasPermission('system:user:add')`

## 基于模板创建新项目

```bash
pnpm run init <项目名称> "<项目描述>" <包名>
# 示例：pnpm run init my-admin "我的管理系统" com.mycompany
```

自动扫描所有模块的 Java 源码目录，重命名包名、更新配置文件、前端标题和数据库名。详见 [docs/template-guide.md](docs/template-guide.md)。

## 模块管理

### 创建新模块

```bash
node scripts/create-module.js <模块名称> "<模块描述>"
# 示例：node scripts/create-module.js order "订单管理模块"
```

自动创建模块目录结构、pom.xml、基础类模板。

### 删除模块

```bash
./scripts/remove-module.sh <模块名称>
# 示例：./scripts/remove-module.sh workflow
```

自动从 pom.xml 移除模块引用、删除依赖关系、清理迁移脚本和模块目录。

## Docker 部署

```bash
cp .env.example .env
docker-compose up -d
```

访问：http://localhost（前端）、http://localhost/doc.html（API 文档）

## 配置说明

### 后端配置（application.yml）

| 配置项 | 默认值 |
|--------|--------|
| 服务端口 | 8181 |
| 数据库 | localhost:3306/forge_admin |
| Redis | localhost:6379 |
| JWT 密钥 | （生产环境请修改） |

### 前端环境变量

| 变量 | 开发环境 | 生产环境 |
|------|----------|----------|
| 后台 API 地址 | http://localhost:8181/admin-api | /admin-api |
| 移动端 API 地址 | http://localhost:8181/app-api | /app-api |
| 大屏编辑器地址 | http://localhost:8001 | /screen-app |

### 等保安全配置（application.yml）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `forge.security.captcha.enabled` | true | 是否启用验证码 |
| `forge.security.captcha.length` | 4 | 验证码长度 |
| `forge.security.password.min-length` | 8 | 密码最小长度 |
| `forge.security.password.max-length` | 32 | 密码最大长度 |
| `forge.security.password.expire-days` | 90 | 密码有效期（天） |
| `forge.security.password.history-size` | 5 | 密码历史校验条数 |
| `forge.security.password.bcrypt-strength` | 12 | BCrypt 强度 |
| `forge.security.password.aes-key` | （必须配置） | AES-256 加密密钥 |
| `forge.security.login.max-fail-count` | 5 | 登录失败锁定阈值 |
| `forge.security.login.lock-minutes` | 15 | 锁定时长（分钟） |
| `forge.security.login.single-session` | true | 单点登录模式 |
| `forge.security.upload.max-size` | 10485760 | 文件上传大小限制（字节） |

**生产环境必填环境变量：**

| 变量 | 用途 |
|------|------|
| `APP_AES_KEY` | AES-256 加密密钥（32字节） |
| `JWT_SECRET` | JWT 签名密钥（≥256位） |
| `JASYPT_PASSWORD` | jasypt 配置解密密钥 |

详见 [apps/forge-server/docs/SECURITY-COMPLIANCE.md](apps/forge-server/docs/SECURITY-COMPLIANCE.md) 和 [apps/forge-server/docs/DEPLOYMENT-CHECKLIST.md](apps/forge-server/docs/DEPLOYMENT-CHECKLIST.md)。

### 大屏安全配置（application.yml）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `forge.security.screen.allowed-hosts` | localhost / 127.0.0.1 / forge-server / forge-ai-python | HTTP 数据源 host 白名单（SSRF 防护） |
| `forge.security.screen.http-timeout-ms` | 5000 | HTTP 连接 + 读取超时（毫秒） |
| `forge.security.screen.http-max-body-bytes` | 1048576 | HTTP 响应体最大字节（1MB） |
| `forge.security.screen.require-https` | false | 是否强制 HTTPS（生产环境建议 true） |

### 多租户配置（application.yml）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `forge.tenant.enable` | true | 是否启用多租户（false 时所有多租户能力关闭，回归单租户） |
| `forge.tenant.header` | X-Tenant-Id | 租户标识请求头名 |
| `forge.tenant.ignore-urls` | 登录/验证码/WebSocket 等 | 不需要 X-Tenant-Id 的 URL 白名单 |
| `forge.tenant.ignore-tables` | sys_menu / sys_role / sys_dict_* 等 | 跨租户共享表，不注入 tenant_id |
| `forge.tenant.ignore-caches` | dictData / sysConfig / menu / dept | 跨租户共享缓存，key 不加 tenantId 前缀 |

**多租户角色：**
| 角色 code | 角色 | 说明 |
|-----------|------|------|
| `SUPER_ADMIN` (id=1) | 平台超管（admin） | `account_type=2`，跨租户，可通过 `X-Tenant-Id` 切换操作租户 |
| `TENANT_ADMIN` (id=3) | 租户管理员 | 创建租户时自动生成，仅管本租户 |
| `USER` (id=2) | 普通用户 | 单租户内的普通成员 |

**多租户部署：**
- 全新初始化：直接执行 `sql/init.sql`（已集成多租户结构）
- 已有数据库升级：按顺序执行 `apps/forge-server/docs/manual-migrations/V2026071101~V2026071301*.sql`（共 7 个增量脚本）
- 设计文档：`docs/superpowers/specs/2026-07-11-multi-tenant-design.md`

## 数据库迁移

### 等保改造

等保改造的数据库迁移脚本位于 `apps/forge-server/forge-server/src/main/resources/db/migration/V2026061901__sys_user_security_extend.sql`，扩展了 `sys_user` 表的安全字段并新增 `sys_user_password_history` 密码历史表。

手动执行可使用幂等版本：`apps/forge-server/docs/MANUAL-MIGRATION.sql`

```bash
mysql -h <host> -u <user> -p <database> < apps/forge-server/docs/MANUAL-MIGRATION.sql
```

### 大屏模块

大屏模块提供两种使用方式：**全新初始化**使用 `sql/init-screen.sql`；**已有数据库增量更新**按版本号顺序手动执行模块的 `db/migration/` 脚本。

#### 全新初始化（`sql/init-screen.sql`）

`sql/init-screen.sql` 整合了模块 6 个增量脚本的全部内容（4 张表 + 7 条 SQL 白名单 + 19 条菜单/权限 + 4 条测试数据源），使用 `DROP TABLE IF EXISTS + CREATE TABLE` 模式可重复执行，仅用于一次性建库。脚本中已将 `V202607080` 的 `ALTER TABLE` 合并到 `sys_screen` 的建表语句，无需后续 `ALTER` 步骤。

```bash
mysql -u root -p < sql/init.sql
mysql -u root -p < sql/init-screen.sql
```

#### 增量脚本（参考）

`sql/init-screen.sql` 与模块下的增量脚本**互斥使用**。如需逐版本演进，可手动执行下列脚本（文件位于 `apps/forge-server/forge-module-screen/forge-module-screen-biz/src/main/resources/db/migration/`，命名遵循 `V{YYYYMMDD}{seq}__<description>.sql`）：

| 增量脚本 | 说明 |
|---------|------|
| `V202607041__create_screen_tables.sql` | 创建大屏主体、数据源、SQL 白名单 3 张表，并初始化白名单数据 |
| `V202607042__insert_screen_menu_permissions.sql` | 大屏管理目录、菜单与按钮权限种子数据 |
| `V202607080__add_screen_public_fields.sql` | 大屏主体增加 `is_public` / `access_type` 字段 |
| `V202607081__add_screen_role_table.sql` | 大屏角色授权表（access_type=1 时使用） |
| `V202607090__seed_test_data_sources.sql` | 测试数据源种子数据 |
| `V202607091__add_sql_whitelist_menu.sql` | SQL 白名单菜单与按钮权限种子数据 |

## 许可证

MIT License
