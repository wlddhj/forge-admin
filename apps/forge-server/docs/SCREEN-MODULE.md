# 大屏模块（forge-module-screen）运行手册

> 本文档面向运维与后端开发者，汇总大屏模块的 API、配置、SQL 白名单管理、安全护栏以及常见故障排查。
>
> 模块路径：`apps/forge-server/forge-module-screen/`
> 设计规范：`.superpowers/sdd/screen-spec.md`（如存在）
> 数据库迁移：`V202607041__create_screen_tables.sql`、`V202607042__insert_screen_menu_permissions.sql`

## 目录

- [1. 模块概述](#1-模块概述)
- [2. API 端点](#2-api-端点)
  - [2.1 大屏管理（8 个）](#21-大屏管理8-个)
  - [2.2 数据源管理（6 个）](#22-数据源管理6-个)
  - [2.3 动态 view 权限说明](#23-动态-view-权限说明)
- [3. 配置项](#3-配置项)
  - [3.1 开发环境（application-dev.yml）](#31-开发环境application-devyml)
  - [3.2 生产环境（application-prod.yml）](#32-生产环境application-prodyml)
- [4. SQL 白名单管理](#4-sql-白名单管理)
  - [4.1 新增可查询表/列](#41-新增可查询表列)
  - [4.2 风险等级](#42-风险等级)
  - [4.3 敏感列永远排除](#43-敏感列永远排除)
- [5. 安全护栏（13 层防御，不可绕过）](#5-安全护栏13-层防御不可绕过)
- [6. 数据库表结构](#6-数据库表结构)
- [7. 故障排查](#7-故障排查)
- [8. 已知限制与运维注意事项](#8-已知限制与运维注意事项)
- [9. 测试运行说明](#9-测试运行说明)

---

## 1. 模块概述

大屏模块提供后台可视化大屏的配置管理、数据源接入与受控 SQL 查询能力。核心设计目标：

- **配置即数据**：大屏布局、组件、主题以 JSON 存储于 `sys_screen.config` / `config_draft`，支持草稿/正式双版本与一键发布。
- **受控 SQL**：管理员预置白名单 SQL 模板，运行时只允许 `SELECT`、表/列必须命中白名单、强制 `LIMIT`、参数化执行，杜绝 SQL 注入与拖库。
- **多源接入**：支持 SQL（MySQL）与 HTTP 两种数据源类型，统一通过熔断 + 缓存 + 执行器三层流水线对外提供服务。
- **审计可追溯**：所有数据源执行经 `@OperationLog` 审计；安全拦截统一以 `SqlSafetyException` → HTTP 400 返回。

模块依赖：`forge-module-screen-api`（实体/DTO/常量）+ `forge-module-screen-biz`（业务实现），
通过 `ScreenAutoConfiguration` 自动装配（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）。

---

## 2. API 端点

### 2.1 大屏管理（8 个）

| # | 方法 | 路径 | 权限码 | 说明 |
|---|------|------|--------|------|
| 1 | GET  | `/api/screen/list`              | `screen:screen:list`         | 分页查询大屏 |
| 2 | GET  | `/api/screen/{id}`              | `screen:screen:query`        | 按 ID 查询大屏（管理端） |
| 3 | GET  | `/api/screen/code/{code}`       | `screen:screen:view:{code}`  | 按 code 查询大屏（运行时使用，**动态权限**） |
| 4 | POST | `/api/screen`                   | `screen:screen:add`          | 新增大屏 |
| 5 | PUT  | `/api/screen`                   | `screen:screen:edit`         | 修改大屏 |
| 6 | DELETE | `/api/screen`                 | `screen:screen:remove`       | 删除大屏（批量） |
| 7 | PUT  | `/api/screen/publish/{code}`    | `screen:screen:publish`      | 发布大屏（草稿覆盖到正式） |
| 8 | POST | `/api/screen/copy/{code}`       | `screen:screen:copy`         | 复制大屏（携带源 config/草稿/主题） |

**说明：**
- `publish` 与 `copy` 是 **独立权限码**（迁移脚本 `V202607042` 已预置 `screen:screen:publish` 与 `screen:screen:copy`），
  与 `edit`/`add` 分开授权，便于将"只发布不上线"或"只复制不创建"等角色拆分。
- 请求/响应 DTO 位于 `forge-module-screen-api` 的 `com.forge.modules.screen.dto` 包。

### 2.2 数据源管理（6 个）

| # | 方法 | 路径 | 权限码 | 说明 |
|---|------|------|--------|------|
| 9  | GET  | `/api/screen/data-source/list`           | `screen:data-source:list`    | 分页查询数据源 |
| 10 | GET  | `/api/screen/data-source/{id}`           | `screen:data-source:query`   | 查询单个数据源 |
| 11 | POST | `/api/screen/data-source`                | `screen:data-source:add`     | 新增数据源 |
| 12 | PUT  | `/api/screen/data-source`                | `screen:data-source:edit`    | 修改数据源 |
| 13 | DELETE | `/api/screen/data-source`              | `screen:data-source:remove`  | 删除数据源（批量） |
| 14 | POST | `/api/screen/data-source/execute/{id}`   | `screen:data-source:execute` | 执行数据源查询（经熔断 + 缓存 + 执行器） |

**权限总数：14 个静态权限码（`screen:screen:*` 7 个 + `screen:data-source:*` 7 个）+ 1 类动态权限（`screen:screen:view:{code}`）。
其中 `screen:screen:view:{code}` 不在 `V202607042` 中预置，由大屏实例动态生成（见下节）。**

### 2.3 动态 view 权限说明

`GET /api/screen/code/{code}` 用于运行时大屏渲染，权限表达式为：

```java
@PreAuthorize("hasAuthority('screen:screen:view:' + #code)")
```

**含义：** 每个大屏实例对应一个独立的权限码 `screen:screen:view:{code}`，
必须为每个具体大屏单独授权，实现"同一角色可看 A 大屏但看不到 B 大屏"的最小权限原则。

**当前实现状态（已知限制）：**
- 后端权限表达式已就位（T16 完成）。
- **大屏实例创建时尚未自动写入 `sys_menu` 的 `view:{code}` 权限条目**（待前端集成时一并实现）。
- 在自动化落地之前，需由管理员手动在菜单管理中为每个大屏新增一条 `screen:screen:view:{code}` 按钮（`menu_type=2`）权限，
  再通过角色管理 UI 勾选授予对应角色。

---

## 3. 配置项

所有配置位于 `forge.security.screen.*`，由 `ScreenProperties` 绑定。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `allowed-hosts`        | `List<String>` | `[]`（运行时由 `ScreenProperties.defaults()` 提供 `localhost/127.0.0.1/forge-server/forge-ai-python`） | HTTP 数据源 host 白名单，**精确、大小写不敏感匹配** |
| `http-timeout-ms`      | `int`    | `5000`         | HTTP 连接 + 读取超时（毫秒） |
| `http-max-body-bytes`  | `long`   | `1048576` (1MB) | HTTP 响应体最大字节 |
| `require-https`        | `boolean` | `false`       | 是否强制 HTTPS（生产强烈建议 `true`） |

### 3.1 开发环境（application-dev.yml）

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

### 3.2 生产环境（application-prod.yml）

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
> 以避免 `evil.com.127.0.0.1` 这类绕过。详见 [§5 安全护栏](#5-安全护栏13-层防御不可绕过) 第 4 层。

---

## 4. SQL 白名单管理

白名单数据存放在 `sys_screen_sql_whitelist` 表，控制"哪些表/列可被大屏 SQL 查询"。

### 4.1 新增可查询表/列

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
- `risk_level`：风险等级，详见 [§4.2](#42-风险等级)
- `enabled`：`0` 禁用 / `1` 启用
- `remark`：人类可读说明

**修改已有白名单：**

```sql
-- 给 sys_user 表追加 nickname 列
UPDATE sys_screen_sql_whitelist
SET column_list = JSON_ARRAY_APPEND(column_list, '$', 'nickname')
WHERE schema_name = 'forge_admin' AND table_name = 'sys_user';
```

### 4.2 风险等级

| 等级 | 含义 | 适用场景 |
|------|------|----------|
| `0` | 公开 | 字典、菜单、部门等基础配置数据，可显示给任何后台角色 |
| `1` | 内部 | 普通业务数据（用户、岗位、登录日志等），普通后台用户可见 |
| `2` | 敏感 | 仅限管理员/审计角色查询的表（如操作日志明细、财务相关） |

> 风险等级当前用于**审计与归类**，列级访问仍由具体 `column_list` 决定。
> 后续可叠加"角色 → 风险等级上限"的细粒度授权（如普通用户禁止 `risk_level=2` 的表）。

### 4.3 敏感列永远排除

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

## 5. 安全护栏（13 层防御，不可绕过）

任何由用户配置的 SQL（数据源 `type=SQL`）必须依次通过下列 13 层校验，
任意一层失败均抛 `SqlSafetyException` → HTTP 400。

| # | 层 | 实现位置 | 说明 |
|---|----|----------|------|
| 1 | AST 解析（JSqlParser 4.9）        | `SqlSafetyGuard.guard`            | 解析失败的 SQL 一律拒绝；解析后剥离注释/hint |
| 2 | 仅允许 SELECT                     | `SqlSafetyValidator.assertSelectOnly` | 拒绝 INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER 等 |
| 3 | 表必须命中白名单                   | `WhitelistService.checkTableAllowed` | `sys_screen_sql_whitelist` 中不存在 → 拒绝 |
| 4 | 列必须命中白名单                   | `WhitelistService.checkColumnsAllowed` | `password/salt/email/phone/id_card` 等敏感列永不在内 |
| 5 | 强制 LIMIT ≤ 1000                 | `SqlSafetyValidator.assertLimitPresent/WithinMax` | LIMIT 缺失或超过 `ScreenConstants.SQL_MAX_ROWS=1000` 拒绝；OFFSET 同样校验 |
| 6 | 禁用危险函数                       | `SqlSafetyValidator.assertNoDangerousFunctions` | 拒绝 `LOAD_FILE/SLEEP/BENCHMARK/OUTFILE/DUMPFILE/PG_SLEEP` 等，递归扫描子查询 |
| 7 | 禁用系统表                         | `SqlSafetyValidator.assertNoSystemTable` | 拒绝 `information_schema/mysql/performance_schema/sys/pg_catalog` 等，归一化反引号/双引号防绕过 |
| 8 | MyBatis 参数化执行（绝不字符串拼接）| `SqlDataSourceExecutor` + `SqlParamBinder` | `:name` → `?`，所有外部值经 `PreparedStatement` 绑定 |
| 9 | `@DataPermission` 自动追加数据权限 | `forge-spring-boot-starter-mybatis` | 按角色 `data_scope` 自动追加部门/个人过滤条件 |
| 10 | 5 秒查询超时                       | `SqlDataSourceExecutor.execute` + `JdbcTemplate.setQueryTimeout` | 超时由 `ScreenConstants.SQL_TIMEOUT_MS=5000` 控制 |
| 11 | 熔断器：1 分钟 10 次失败 → 熔断 30 秒 | `DataSourceCircuitBreaker`        | Redis 计数（`screen:cb:count:{id}` TTL 60s）+ 熔断标志（`screen:cb:tripped:{id}` TTL 30s） |
| 12 | Redis 缓存（按 `cache_seconds`）    | `DataSourceCacheService.getOrLoad` | single-flight 锁防雪崩；`ttlSeconds ≤ 0` 跳过缓存 |
| 13 | 审计日志（`@OperationLog`）         | `SysScreenDataSourceController.execute` | 记录 `dataSourceId`、操作人、参数（敏感字段自动脱敏） |

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

## 6. 数据库表结构

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `sys_screen`                  | 大屏主体 | `code`(unique), `config`(JSON 已发布), `config_draft`(JSON 草稿), `theme`, `status`(0=草稿/1=已发布), `version`(乐观锁) |
| `sys_screen_data_source`      | 数据源   | `code`(unique), `type`(SQL/HTTP), `config`(JSON), `cache_seconds`, `enabled` |
| `sys_screen_data_source_ref`  | 大屏↔数据源关系 | `screen_id`, `data_source_id`（联合主键） |
| `sys_screen_sql_whitelist`    | SQL 白名单 | `schema_name`, `table_name`(联合 unique), `column_list`(JSON), `risk_level`, `enabled` |

迁移脚本：
- `apps/forge-server/forge-module-screen/forge-module-screen-biz/src/main/resources/db/migration/V202607041__create_screen_tables.sql`
- `apps/forge-server/forge-module-screen/forge-module-screen-biz/src/main/resources/db/migration/V202607042__insert_screen_menu_permissions.sql`

---

## 7. 故障排查

| 现象 | 根因 | 处置 |
|------|------|------|
| 调用 `/data-source/execute/{id}` 返回 400，消息"数据源已熔断" | 1 分钟内失败 ≥10 次触发熔断（[§5 第 11 层](#5-安全护栏13-层防御不可绕过)） | 等待 30 秒自动半开恢复；同时检查 SQL/HTTP 配置（连接串、超时、白名单） |
| HTTP 数据源返回 400，消息"host 不在白名单: xxx" | SSRF 防护（[§5 闸门 ②](#5-安全护栏13-层防御不可绕过)） | 在 `forge.security.screen.allowed-hosts` 显式追加该 host；**禁止**为图省事直接放 `*` 或加内网段 |
| SQL 数据源返回 400，消息"列不在白名单: forge_admin.sys_xxx.yyy" | 列级控制（[§5 第 4 层](#5-安全护栏13-层防御不可绕过)） | 评估是否真的需要该列；若是，扩 `sys_screen_sql_whitelist.column_list`；若否，修改大屏 SQL 移除 |
| SQL 数据源返回 400，消息"表不在白名单" | 表级控制（[§5 第 3 层](#5-安全护栏13-层防御不可绕过)） | 在 `sys_screen_sql_whitelist` 新增条目（按 [§4.1](#41-新增可查询表列)） |
| `GET /screen/code/{code}` 返回 403 | 当前角色未授予 `screen:screen:view:{code}` | 在菜单管理手动新增该按钮权限并授予角色（[§2.3](#23-动态-view-权限说明)） |
| `GET /screen/code/{code}` 返回业务错误"大屏未配置" | `sys_screen.config` 字段为 `null`（仅草稿未发布） | 进入大屏编辑器编辑并点击"发布"（`PUT /screen/publish/{code}`） |
| 集成测试 `SqlSafetyEndToEndIT` 启动失败"Could not find a valid Docker environment" | Testcontainers 需要 Docker daemon | 启动 Docker Desktop；或在无 Docker 环境跳过该 IT（`-Dtest='!*IT'`） |
| 单元测试报"Mockito cannot mock ... ByteBuddy" | JDK 版本不匹配 | 项目**固定 JDK 21**；若切到 JDK 25，surefire 已加 `-Dnet.bytebuddy.experimental=true --add-opens java.base/java.lang=ALL-UNNAMED`，仍失败请回到 JDK 21 |
| `@SpringBootTest` 启动卡在 flowable/工作流 Bean 装配 | workflow 模块预存的 flowable 依赖冲突 | 当前推荐的集成测试（T18）使用 minimal `@SpringBootConfiguration` 仅装载大屏模块 Bean，绕开 workflow；切勿将大屏 IT 改为全栈 `@SpringBootTest` |
| 单测通过但 `mvn test -pl forge-module-screen/forge-module-screen-biz` 全量失败 | 局部 surefire `argLine` 未生效 | 确认 `forge-module-screen-biz/pom.xml` 的 surefire `argLine` 配置未被父 pom 覆盖 |
| HTTP 数据源返回 200 但内容与预期不符 | `HttpDataSourceExecutor` 默认跟随重定向 | [§8 已知限制](#8-已知限制与运维注意事项)；如目标站点重定向到外部域，请改用 SQL 数据源或在白名单中追加最终域 |

---

## 8. 已知限制与运维注意事项

| 编号 | 限制 | 当前处置 | 跟进 |
|------|------|----------|------|
| L1 | 大屏创建时**不会**自动写入 `screen:screen:view:{code}` 权限到 `sys_menu` | 管理员手动新增菜单按钮权限 | 待前端集成大屏管理 UI 时一并实现（参见 [§2.3](#23-动态-view-权限说明)） |
| L2 | `HttpDataSourceExecutor` **默认跟随 HTTP 重定向**（`SimpleClientHttpRequestFactory` 默认行为） | 如目标 URL 会 302 到非白名单域，会被 SSRF 闸门拒绝并返回 400 | 后续可显式 `setOutputStreaming(false)` 关闭自动重定向，或维护"重定向目标白名单"二级清单 |
| L3 | `DataSourceCacheService.getOrLoad` 当前**不暴露缓存命中/未命中信号**给上层（`fromCache` 字段始终为 `false`） | 通过 Redis `TTL` 与日志间接观察 | 后续在 `DataSourceExecuteResponse` 增加 `cacheHit` 字段时同步改造 |
| L4 | `single-flight` 锁为 `DataSourceCacheService` 实例字段，**所有数据源共享一把锁** | 不同 key 也会串行 loader，性能轻微损失但语义安全 | 后续按 key 分桶改造为 striped locks |
| L5 | workflow 模块的 flowable 依赖存在冲突，**阻塞全栈 `@SpringBootTest` 启动** | 大屏 IT 使用 minimal 配置绕开（参见 [§7](#7-故障排查)） | 待 workflow 模块独立修复 |
| L6 | DNS rebinding **未防御**（攻击者控制 DNS 让同 host 解析到内网 IP） | 当前依赖 host 字面量精确匹配；生产环境网络层应禁用公网 DNS 解析到内网 | 后续可在 `HttpDataSourceExecutor` 增加 `InetAddress` 私网段校验 |
| L7 | 风险等级 `risk_level` 当前**仅用于归类**，未在运行时拦截 | 列级访问由 `column_list` 决定 | 后续可叠加"角色 → 风险等级上限"运行时校验 |

---

## 9. 测试运行说明

### 9.1 单元测试

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

### 9.2 集成测试（T18 `SqlSafetyEndToEndIT`）

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

### 9.3 JDK 版本

项目**固定 JDK 21**（参见根 `pom.xml` 与 `CLAUDE.md`）。
偶有 reviewer 在 JDK 25 上跑测试触发 Mockito/ByteBuddy 不兼容，请保持 21；
若必须升级 JDK，请同步更新 surefire `argLine` 并跑全量回归。

---

**维护人：** standadmin
**最后更新：** 2026-07-06（T19 提交）
