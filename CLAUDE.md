# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

forge-admin 是一个基于 RBAC 的企业级后台管理系统，采用 monorepo 结构，前后端分离，已通过 GB/T 22239-2019 二级等保技术改造。支持 B2B SaaS 多租户架构（可配置开关）。

**技术栈：**
- 前端：Vue 3.4 + TypeScript + Element Plus + vxe-table + Pinia + Vite 5
- 后端：Spring Boot 3.2.0 + MyBatis Plus 3.5.17 + MySQL + Redis + FlowLong 1.2.5
- 认证：JWT Token（访问令牌 2 小时，刷新令牌 7 天）
- 多租户：MyBatis Plus `TenantLineInnerInterceptor`（SQL 自动注入 `WHERE tenant_id = ?`）
- 加密：AES-256-GCM（敏感字段）+ BCrypt（密码哈希）+ jasypt（配置文件）
- API 文档：Knife4j，地址 `/api/doc.html`

## 关键配置

| 服务 | 端口 | 路径 |
|------|------|------|
| 前端开发 | 3003 | `apps/forge-web` |
| 小程序开发 | - | `apps/forge-miniapp` |
| 后端 API | 8181 | `apps/forge-server` |
| 上下文路径 | - | `/api` |

**数据库：** MySQL `forge_admin`，localhost:3306 | **Redis：** localhost:6379（无密码） | **Java：** 21 | **Node：** 22.9.0 | **pnpm：** 8.15.4

## 开发命令

### 前端（在 `apps/forge-web` 目录下）
```bash
pnpm install    # 安装依赖
pnpm dev        # 启动开发服务器（端口 3003）
pnpm build      # 生产构建（含类型检查）
pnpm lint       # 运行 ESLint
pnpm test       # 运行 vitest 单元测试
```

### 小程序（在 `apps/forge-miniapp` 目录下）
```bash
pnpm install        # 安装依赖
pnpm dev:mp-weixin  # 微信小程序开发模式
pnpm build:mp-weixin # 微信小程序生产构建
```

### 后端（在 `apps/forge-server` 目录下）
```bash
mvn spring-boot:run -pl forge-server          # 启动开发服务器
mvn clean compile                              # 仅编译
mvn clean package -DskipTests                  # 打包 JAR（跳过测试）
mvn clean install -DskipTests                  # 安装到本地仓库
mvn test -pl forge-module-system/forge-module-system-biz  # 运行指定模块测试
mvn test -Dtest=ClassName -pl <module>         # 运行单个测试类
```

## 后端架构

### 多模块结构

后端是一个 14 模块的 Maven 项目，包名基类为 `com.forge`。

```
apps/forge-server/
├── pom.xml                          # 根聚合 POM（parent: spring-boot-starter-parent:3.2.0）
├── forge-dependencies/              # BOM 版本管理（纯 POM，无 Java 代码）
├── forge-framework/                 # 框架层
│   ├── forge-common/                # 纯公共：注解、枚举、异常、响应、JSON 序列化、工具类
│   ├── forge-spring-boot-starter-mybatis/   # MyBatis Plus 配置、数据权限框架
│   ├── forge-spring-boot-starter-redis/     # Redis 配置、缓存配置
│   ├── forge-spring-boot-starter-security/  # JWT、OAuth2、JustAuth 社交登录
│   ├── forge-spring-boot-starter-tenant/    # 多租户（TenantLineInnerInterceptor + 过滤器 + AOP）
│   └── forge-spring-boot-starter-web/       # Jackson、全局异常、WebSocket、Excel、限流
├── forge-module-system/             # 系统模块
│   ├── forge-module-system-api/     # 实体 + DTO（82 个文件）
│   └── forge-module-system-biz/     # Controller/Service/Mapper（含 auth、quartz）
├── forge-module-workflow/           # 工作流模块（基于 FlowLong）
│   ├── forge-module-workflow-api/   # 实体 + DTO（44 个文件）
│   └── forge-module-workflow-biz/   # Controller/Service/Mapper + FlowLong 集成
├── forge-module-ai/                 # AI 模块
│   ├── forge-module-ai-api/         # 实体 + DTO
│   └── forge-module-ai-biz/         # Controller/Service（调用 Python AI 服务）
├── forge-module-screen/             # 大屏模块
│   ├── forge-module-screen-api/     # 实体 + DTO + 枚举
│   └── forge-module-screen-biz/     # Controller/Service/Mapper + 安全层 + 缓存 + 熔断
└── forge-server/                    # Spring Boot 启动入口（ForgeAdminApplication.java）
```

**模块依赖关系：**
```
forge-server ← system-biz, workflow-biz, ai-biz
workflow-biz ← workflow-api, system-api, starters, flowlong
ai-biz ← ai-api, system-api, starters（调用 Python AI 服务）
system-biz ← system-api, starters, quartz, justauth
starter-security ← forge-common, system-api
starter-mybatis ← forge-common
starter-web ← forge-common
starter-redis ← forge-common
```

### 模块分层模式

每个业务模块遵循：`controller/` → `service/`（接口 + impl）→ `mapper/` → 实体在 `api/` 模块，`dto/` 在 `api/` 模块。

**包结构：**
- `com.forge.common` — 公共（annotation, enumeration, exception, json, response, utils）
- `com.forge.framework.mybatis` — MyBatis 配置、数据权限（permission/）
- `com.forge.framework.redis` — Redis 配置
- `com.forge.framework.security` — JWT、OAuth2 配置
- `com.forge.framework.web` — Web 配置、全局异常、WebSocket
- `com.forge.modules.system` — system + auth + quartz
- `com.forge.modules.workflow` — 工作流（FlowLong 集成）
- `com.forge.modules.ai` — AI 模块（调用 Python 服务）
- `com.forge.modules.screen` — 大屏（CRUD + 数据源执行器 + SQL 安全层）

### 大屏模块特殊设计

`forge-module-screen-biz` 是少数按子包组织的模块，关键子包：

- `safety/` — SQL 安全三道闸：`SqlSafetyValidator`（AST 规则：SELECT-only、禁 UNION/SET、禁危险函数、必须 LIMIT）、`SqlSafetyGuard`（编排器）、`WhitelistService`（表/列级白名单校验，列级 fail-closed）
- `executor/` — 数据源执行器：`SqlDataSourceExecutor`（动态 SQL + 参数绑定 + @DataPermission）、`HttpDataSourceExecutor`（SSRF 防护：`ScreenProperties.allowedHosts`）
- `cache/` — Redis 缓存（key = `dataSourceId + paramsJson`）
- `fault/` — 熔断器（Redis 滑动窗口统计失败次数，达阈值拒绝请求）

数据源表 `sys_screen_data_source.config` 含 SQL 原文/HTTP URL 等敏感信息。列表接口通过 `qw.select()` 排除 `config` 字段（详见 `SysScreenDataSourceServiceImpl.page`），详情接口正常返回以支持编辑回显。

### 横切关注点

- `@OperationLog(title, businessType)` — 审计日志（starter-web 中）
- `@DataPermission(deptAlias, userAlias)` — SQL 级数据范围过滤（starter-mybatis 中，5 种范围类型）
- `@RateLimiter(keyType, time, count)` — Redis 令牌桶限流（starter-web 中）
- `@Cacheable/@CacheEvict` — Redis 缓存（缓存名：dictData, dictType, sysConfig, userInfo, menu, dept）
- `@Valid @RequestBody` — Jakarta DTO 校验
- `@EncryptField` — 敏感字段自动加解密（starter-mybatis 中，配合 `EncryptTypeHandler`，使用 AES-256-GCM）
- `@XssIgnore` — 跳过 XSS 过滤（starter-web 中，标注在 Controller 方法或类上）
- `@TenantIgnore` — 跳过租户隔离（starter-tenant 中，标注在 Service 方法上，跨租户访问用）

### 多租户架构

基于 MyBatis Plus 官方 `TenantLineInnerInterceptor` 实现，可配置开关 `forge.tenant.enable`（默认 true）。

**核心组件（`forge-spring-boot-starter-tenant`）：**
- `TenantContextHolder` — ThreadLocal 持有当前请求租户 ID（TransmittableThreadLocal 支持线程池透传）
- `TenantContextWebFilter` — 从请求头 `X-Tenant-Id` 解析到 TenantContextHolder（order=0）
- `TenantSecurityWebFilter` — 越权检测 + 租户合法性校验（status=1、未过期），平台超管绕过
- `TenantDatabaseInterceptor` — 实现 `TenantLineHandler`，SQL 自动注入 `WHERE tenant_id = ?` / INSERT 自动填 tenantId
- `TenantIgnoreAspect` — `@TenantIgnore` 注解 AOP，方法级跳过租户过滤
- `TenantRedisCacheManager` — 缓存 key 自动加 `tenantId:` 前缀（除 `ignore-caches` 白名单）
- `TenantJobAspect` — Quartz 跨线程透传 TenantContextHolder

**过滤器链顺序：**
```
HTTP → TenantContextWebFilter (order=0) → Spring Security (JwtAuthenticationFilter)
     → TenantSecurityWebFilter (order=1) → Controller → Service → Mapper
     → TenantLineInnerInterceptor（自动注入 WHERE tenant_id = ?）
```

**业务约定：**
- 平台超管 `sys_user.account_type = 2`：跨租户访问，可通过 `X-Tenant-Id` header 切换操作租户上下文
- 普通用户 `account_type = 0/1`：JWT.tenantId 必须与 X-Tenant-Id 一致，否则 403
- 用户单租户制：`sys_user.tenant_id` 字段固定，username 联合唯一索引 `(tenant_id, username)` 允许跨租户重名
- 跨租户共享表（不加 tenant_id）：sys_menu / sys_role / sys_role_menu / sys_dict_*  / sys_config / sys_file_config / sys_job / sys_tenant*

**配置开关：**
```yaml
forge:
  tenant:
    enable: ${TENANT_ENABLE:true}      # false 时所有多租户能力关闭，回归单租户行为
    header: X-Tenant-Id
    ignore-urls:                        # 不需要 X-Tenant-Id 的 URL（登录、验证码、WebSocket 等）
      - /admin-api/auth/login
      - /admin-api/system/tenant/public/**
      - /ws/**
    ignore-tables:                      # 跨租户共享表
      - sys_menu
      - sys_role
      - sys_role_menu
    ignore-caches:                      # 跨租户共享缓存（key 不加前缀）
      - dictData
      - sysConfig
```

**关键约束（业务开发必读）：**
- 新增业务表加 `tenant_id BIGINT NOT NULL DEFAULT 1` 列 + 索引
- 业务实体继承 `TenantBaseDO`（带 tenantId 字段）
- 手写 SQL（XML mapper）的 `<where>` 块首行必须显式 `AND xxx.deleted = 0`，`@TableLogic` 不自动注入
- Service 写入操作不需要显式 setTenantId，由 TenantLineInnerInterceptor 自动注入
- 平台超管切换租户场景：业务层用 `TenantContextHolder.getTenantId()`（header 值），不是 `UserContext.getTenantId()`（JWT 值）

### 等保二级安全改造

系统已完成 GB/T 22239-2019 二级等保技术改造，关键能力：

- **密码策略**：`PasswordPolicyProperties` 控制复杂度（8-32位、大小写+数字+特殊字符）、90天有效期、5条历史校验、BCrypt 强度=12
- **登录安全**：`LoginAttemptService` 实现失败锁定（5次→15分钟），`CaptchaService` 图形验证码，单点登录通过 `LoginUserSessionService.kickOutUserSessions` 实现（同步清理 refreshToken）
- **敏感数据加密**：`CryptoUtils`（AES-256-GCM，`ENCv1:` 前缀），`@EncryptField` + `EncryptTypeHandler` 自动加解密
- **配置加密**：jasypt-spring-boot-starter，支持 `ENC()` 格式加密敏感配置
- **应用安全**：`XssFilter` 全局 XSS 过滤，Spring Security 安全响应头（CSP/HSTS/X-Frame-Options），`FileUploadValidator` 文件上传校验
- **审计脱敏**：`SensitiveDataMasker` 在 `OperationLogAspect` 中自动脱敏密码/手机号/邮箱/身份证

**安全配置前缀**：`forge.security.{captcha|password|login|upload}`，详见 `application.yml` / `application-prod.yml`。

**文档：**
- 合规说明：`apps/forge-server/docs/SECURITY-COMPLIANCE.md`
- 部署检查清单：`apps/forge-server/docs/DEPLOYMENT-CHECKLIST.md`
- 手动迁移 SQL：`apps/forge-server/docs/MANUAL-MIGRATION.sql`

### Spring Boot Starter 自动配置

每个 starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册。修改 starter 类时需同步更新此文件。

### 数据库迁移

位置：每个模块**各自的** `db/migration/` 目录下（如 `forge-module-screen-biz/src/main/resources/db/migration/`、`forge-server/src/main/resources/db/migration/`）
命名：`V{YYYYMMDD}{seq}___<description>.sql`
由 Flyway 在启动时按版本号顺序执行。

## 前端架构（`apps/forge-web/src/`）

```
api/           # API 模块，带类型的请求/响应接口
composables/   # Vue 组合式函数（useWebSocket, useDict, useResponsive, useTableHeight, useTableSeq）
directives/    # v-permission, v-role（无权限时移除 DOM 元素）
layouts/       # BasicLayout：侧边栏、头部、标签页、通知
plugins/       # vxe-table 全局配置和插件注册
router/        # 基于后端菜单树动态生成路由
stores/        # Pinia 状态（user, permission, tabs, pageConfig）
utils/         # request.ts（Axios + 自动令牌刷新）
views/         # 页面组件（通过 import.meta.glob 自动发现）
```

**自动导入：** Vue API、vue-router、pinia 通过 `unplugin-auto-import`；Element Plus 组件通过 `unplugin-vue-components`。

**动态路由：** 后端返回菜单树 → `permissionStore.setRoutes()` → `import.meta.glob('/src/views/**/*.vue')` 解析组件 → `router.addRoute()`，404 最后添加。

## 小程序架构（`apps/forge-miniapp/src/`）

```
api/           # API 接口定义（auth, user）
pages/         # 页面组件
  login/       # 微信授权登录页
  profile/     # 个人中心（编辑、手机绑定、注销）
stores/        # Pinia 状态管理（user）
static/        # 静态资源（logo.svg, default-avatar.png）
composables/   # 组合式函数
```

**微信登录流程：**
1. 小程序调用 `uni.login()` 获取 code
2. 后端 `/app-api/auth/wx-login` 用 code 换取 openId
3. 后端查找/创建 `app_user` 记录，返回 JWT Token
4. 小程序存储 Token，用于后续 API 请求

**环境变量配置：**
- `WX_MINI_APP_ID` - 微信小程序 AppID（必填，否则使用 Mock 模式）
- `WX_MINI_APP_SECRET` - 微信小程序 AppSecret

## AI 服务架构（`apps/forge-ai-python/`）

系统采用 Java + Python 双语言架构实现 AI 功能：

```
apps/forge-ai-python/
├── src/
│   ├── api/           # FastAPI 接口（chat, document, health）
│   ├── adapters/      # LLM 适配器（deepseek, qwen, glm, ernie）
│   ├── config/        # 配置管理
│   ├── models/        # Pydantic 模型
│   ├── services/      # 业务服务
│   └── main.py        # 应用入口
└── pyproject.toml
```

**启动命令：**
```bash
cd apps/forge-ai-python
pip install -e .
python -m uvicorn src.main:app --reload --port 8000
```

**架构说明：**
- Java 端（AI 模块）：管理文档元数据、调用 Python 服务、回写摘要结果
- Python 端（AI 服务）：多模型 LLM 对话、文档解析、智能摘要

Java 通过 `WebClient` 调用 Python FastAPI 服务，支持流式响应（SSE）。

## 大屏编辑器架构（`apps/forge-screen/`）

goView 编辑器是独立的前端 SPA，与 forge-admin 主项目解耦，通过 iframe 嵌入到 `apps/forge-web/src/views/screen/editor/` 和 `preview/`：

```
apps/forge-screen/
├── src/
│   ├── api/forge/       # 与 forge-admin 通信的 API（screen.ts、dataSource.ts）
│   ├── store/           # Pinia stores（chartEditStore 持有 componentList，自动保存到 configDraft）
│   ├── hooks/           # useChartDataFetch（数据获取/轮询）、useSync（加载/恢复 componentList）
│   ├── packages/        # 图表组件库（components/Charts、Bars、Lines、Pies、VChart/...）
│   │   └── public/chart.ts # setOption 工具：合并数据到 vChart/echarts 实例
│   ├── views/chart/
│   │   ├── ContentConfigurations/components/ChartData/components/
│   │   │   ├── ChartDataForge/        # forge 数据源配置面板（ID + params + 测试获取）
│   │   │   ├── ChartDataAjax/         # 通用 HTTP 数据源
│   │   │   ├── ChartDataPond/         # 数据池
│   │   │   └── ChartDataMatchingAndShow/ # 字段映射 UI（dimensions ↔ 数据源字段）
│   │   └── hooks/useSync.hook.ts      # 组件加载/重建（createComponent + componentMerge）
│   └── components/GoVChart/index.vue  # vChart 包装（dataset 双向绑定 props.option）
```

**关键数据流：**
1. **加载**：`loadProjectById` → 解析 `configDraft` JSON → `useSync.updateComponent` → 重建 componentList
2. **编辑**：用户在 ChartDataForge 等面板点击"测试获取"→ `testFetch` → 修改 `targetData.option.dataset.source`（**保留 dimensions**）
3. **保存**：`useAutoSave`（`useSyncUpdate.hook.ts`）监听 `componentList` 变更，debounce 3s 调用 `saveProjectToApi` → 后端 `PUT /screen` 写入 `config_draft`
4. **预览/渲染**：`useChartDataFetch` 按 `requestDataType` 分发（AJAX/FORGE/Pond）。FORGE 分支调 `executeDataSource(id)`，**必须保留 dimensions**——已有问题见 commit `e8adbfe`（testFetch）和 `be664e8`（fetch hook）

**vChart dataset 绑定（重要）：** `GoVChart/index.vue` 中 `watch(() => dataset.value)` 监听 `props.option.dataset` 变化并重建图表。组件回调（如 `props.chartConfig.option.dataset = newData`）直接修改 option 会触发自动保存，因此 fetch 回调必须保留 `{dimensions, source}` 结构而非只传数组。

## 重要模式

### API 响应格式
```json
{ "code": 200, "message": "success", "data": {}, "timestamp": 1709635800000 }
```

### 权限格式
- Controller：`@PreAuthorize("hasAuthority('system:user:list')")`
- 前端模板：`v-permission="'system:user:add'"` 或 `v-permission="['perm1', 'perm2']"`（任一匹配）
- 前端脚本：`hasPermission('system:user:add')`

### 数据库约定
- 表名前缀 `sys_`，公共字段：`id`、`create_time`、`update_time`、`create_by`、`update_by`、`deleted`、`status`、`remark`
- MyBatis Plus 自动填充 `create_time`/`update_time`

### Excel 导出
`ExcelUtils.export(response, fileName, sheetName, DtoClass.class, dataList)`，配合 `@ExcelProperty` 注解。

### WebSocket（STOMP over SockJS）
端点 `/ws`，广播 `/topic/notifications`，用户专属 `/user/{userId}/queue/notifications`

## Git 提交规范

**格式：** `<type>(<scope>): <subject>`（中文）

类型：`feat`、`fix`、`docs`、`style`、`refactor`、`perf`、`test`、`chore`、`revert`

**重要：** 禁止在提交信息中添加 `Co-Authored-By`。

## 项目初始化

```bash
pnpm run init <项目名称> "<项目描述>" <Java包名>
# 示例：pnpm run init my-admin "我的管理系统" com.mycompany
```

## 模块管理

### 创建新模块

```bash
node scripts/create-module.js <模块名称> "<模块描述>"
# 示例：node scripts/create-module.js order "订单管理模块"
```

自动创建：
- 模块目录结构（api/biz）
- pom.xml 配置
- 基础类模板（Entity、DTO、Controller、Service、Mapper）
- 数据库迁移脚本模板

### 删除模块

```bash
./scripts/remove-module.sh <模块名称>
# 示例：./scripts/remove-module.sh workflow
```

自动处理：
- 从根 pom.xml 移除模块引用
- 从 forge-dependencies 移除依赖声明
- 从 forge-server 移除依赖引用
- 检查并移除其他模块的依赖
- 清理数据库迁移脚本（交互确认）
- 删除模块目录（交互确认）

**注意：** 删除模块后需运行 `mvn clean compile` 验证编译。

## 关键文件

| 用途 | 路径 |
|------|------|
| 后端配置 | `apps/forge-server/forge-server/src/main/resources/application.yml` |
| 生产环境配置 | `apps/forge-server/forge-server/src/main/resources/application-prod.yml` |
| BOM 版本管理 | `apps/forge-server/forge-dependencies/pom.xml` |
| 系统模块 API 定义 | `apps/forge-server/forge-module-system/forge-module-system-api/` |
| 工作流模块 API | `apps/forge-server/forge-module-workflow/forge-module-workflow-api/` |
| AI 模块 API | `apps/forge-server/forge-module-ai/forge-module-ai-api/` |
| 前端请求工具 | `apps/forge-web/src/utils/request.ts` |
| 路由守卫 | `apps/forge-web/src/router/index.ts` |
| vxe-table 全局配置 | `apps/forge-web/src/plugins/vxe/vxe-table-config.ts` |
| 小程序 API 定义 | `apps/forge-miniapp/src/api/` |
| 小程序登录页 | `apps/forge-miniapp/src/pages/login/index.vue` |
| 微信登录服务 | `apps/forge-server/forge-module-system-biz/.../service/app/AppAuthServiceImpl.java` |
| 加解密工具 | `apps/forge-server/forge-framework/forge-common/.../utils/CryptoUtils.java` |
| 敏感数据脱敏 | `apps/forge-server/forge-framework/forge-common/.../utils/SensitiveDataMasker.java` |
| 安全策略配置 | `apps/forge-server/forge-module-system/forge-module-system-biz/.../auth/properties/` |
| 等保合规文档 | `apps/forge-server/docs/SECURITY-COMPLIANCE.md` |
| 部署检查清单 | `apps/forge-server/docs/DEPLOYMENT-CHECKLIST.md` |
| 数据库迁移目录 | `apps/forge-server/forge-server/src/main/resources/db/migration/` |
| Python AI 服务 | `apps/forge-ai-python/src/main.py` |
| FlowLong 设计器 | `apps/forge-web/src/views/workflow/model/FlowLongModelDesigner.vue` |
| goView 大屏编辑器 | `apps/forge-screen/src/` |
| 大屏模块后端 | `apps/forge-server/forge-module-screen/` |
| 大屏 SQL 安全层 | `apps/forge-server/forge-module-screen/forge-module-screen-biz/.../safety/` |
| 大屏数据源列表（前端） | `apps/forge-web/src/views/screen/data-source/index.vue` |
| 大屏 SQL 白名单（前端） | `apps/forge-web/src/views/screen/sql-whitelist/index.vue` |
| 多租户 Starter | `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/` |
| 多租户配置入口 | `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/.../config/TenantProperties.java` |
| 租户管理 Controller | `apps/forge-server/forge-module-system/forge-module-system-biz/.../controller/admin/SysTenantController.java` |
| 租户管理前端 | `apps/forge-web/src/views/system/tenant/index.vue` |
| 租户切换器（前端） | `apps/forge-web/src/themes/layouts/shared/TenantSwitcher.vue` |
| 多租户 SQL 迁移 | `apps/forge-server/docs/manual-migrations/` |
| 多租户设计文档 | `docs/superpowers/specs/2026-07-11-multi-tenant-design.md` |

## 命名约定

### 后端（Java）
| 类型 | 约定 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.forge.modules.system` |
| 类名 | PascalCase | `SysUserController` |
| 方法名 | camelCase | `getUserById` |
| 表名 | `sys_` 前缀 + 小写下划线 | `sys_user` |

### 前端（TypeScript/Vue）
| 类型 | 约定 | 示例 |
|------|------|------|
| 文件名 | kebab-case | `user-profile.vue` |
| 组件名 | PascalCase | `UserProfile` |
| 变量/函数 | camelCase | `getUserInfo` |

### 小程序（uni-app）
| 类型 | 约定 | 示例 |
|------|------|------|
| 页面目录 | kebab-case | `pages/profile/` |
| 静态资源 | kebab-case | `static/logo.svg` |
| API 模块 | camelCase | `authApi`、`userApi` |

### API 路径
`/api/{module}/{entity}/{action}`，如 `/api/auth/login`、`/api/system/user/list`

### 业务模块权限前缀

用户 `system:user`、角色 `system:role`、菜单 `system:menu`、部门 `system:dept`、岗位 `system:position`、字典 `system:dict`、参数配置 `system:config`、文件配置 `system:file-config`、通知公告 `system:notice`、在线用户 `monitor:online`、登录日志 `monitor:login-log`、操作日志 `monitor:operation-log`、定时任务 `monitor:job`、流程分类 `workflow:category`、流程定义 `workflow:process`、流程实例 `workflow:instance`、待办任务 `workflow:task`、表单管理 `workflow:form`、模型管理 `workflow:model`、表达式管理 `workflow:expression`、监听器管理 `workflow:listener`、AI文档 `ai:document`、**租户管理 `system:tenant`**、**套餐管理 `system:tenant-package`**

## 代码模板

### 后端 Controller

```java
package com.forge.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.system.dto.xxx.XxxQueryRequest;
import com.forge.modules.system.dto.xxx.XxxRequest;
import com.forge.modules.system.dto.xxx.XxxResponse;
import com.forge.modules.system.service.SysXxxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "{模块}管理")
@RestController
@RequestMapping("/system/{entity}")
@RequiredArgsConstructor
public class SysXxxController {

    private final SysXxxService sysXxxService;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:{entity}:list')")
    public Result<PageResult<XxxResponse>> list(XxxQueryRequest request) {
        Page<XxxResponse> page = sysXxxService.pageXxx(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('system:{entity}:add')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody XxxRequest request) {
        sysXxxService.addXxx(request);
        return Result.success();
    }
    // ... 其他方法同理
}
```

### 后端 Service

```java
package com.forge.modules.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.modules.system.entity.SysXxx;
import com.forge.modules.system.mapper.SysXxxMapper;
import com.forge.modules.system.service.SysXxxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysXxxServiceImpl extends ServiceImpl<SysXxxMapper, SysXxx> implements SysXxxService {
    // 分层：controller → service → mapper，实体在 api 模块
}
```

### 前端 API

```typescript
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export const xxxApi = {
  page: (params: XxxQuery) => request.get<PageResult<XxxEntity>>('/system/xxx/list', { params }),
  get: (id: number) => request.get<XxxEntity>(`/system/xxx/${id}`),
  add: (data: Partial<XxxEntity>) => request.post('/system/xxx', data),
  update: (data: Partial<XxxEntity>) => request.put('/system/xxx', data),
  delete: (ids: number[]) => request.delete('/system/xxx', { data: ids })
}
```

### 前端 Vue 页面（CRUD + vxe-table）

```vue
<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
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
          <el-button type="primary" @click="handleAdd">新增</el-button>
        </template>
      </vxe-toolbar>
      <vxe-table ref="tableRef" id="sysXxxTable" :data="tableData" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="name" title="名称" min-width="150" />
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @size-change="getList" @current-change="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

const { tableHeight } = useTableHeight()
const { seqMethod } = useTableSeq({ currentPage: computed(() => queryParams.pageNum), pageSize: computed(() => queryParams.pageSize) })

onMounted(() => { tableRef.value?.connect(toolbarRef.value!) })
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .search-card { margin-bottom: 15px; }
  .table-card .el-pagination { margin-top: 15px; justify-content: flex-end; }
}
</style>
```
