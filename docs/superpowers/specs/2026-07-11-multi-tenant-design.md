# 多租户架构设计（Multi-Tenant）

**日期：** 2026-07-11
**目标项目：** forge-admin
**参考实现：** shi9-boot（`/Users/huangjian/workspace/nbmt/shi9-boot`）

---

## 1. 概述

为 forge-admin 引入多租户（B2B SaaS）能力，支持按租户隔离业务数据，租户间互不干扰。提供 `forge.tenant.enable` 配置开关，关闭时回归单租户行为，零侵入。

## 2. 业务目标

- **租户语义**：每个租户 = 一个独立的企业客户（B2B SaaS）
- **租户识别**：客户端在请求头 `X-Tenant-Id`（可配置）携带 tenantId
- **数据隔离**：业务数据按租户隔离；菜单、字典、系统配置、文件存储配置、定时任务跨租户共享
- **部门维度**：Dept 数据权限作为租户**内部**的子划分，与租户隔离叠加生效
- **平台超管**：`account_type=2` 的"平台超管"不受 tenantId 限制，可管理所有租户
- **套餐制**：租户可绑定一个"套餐"控制其可见菜单
- **可配置开关**：`forge.tenant.enable=false` 时所有多租户能力关闭

## 3. 关键决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 隔离方案 | MyBatis Plus `TenantLineInnerInterceptor` | 官方生态、生产验证、与 `DataPermissionInterceptor` 叠加自然 |
| 用户-租户关系 | **单租户账号**（`sys_user.tenant_id`） | B2B SaaS 业界共识，避开 N×N 关联表爆炸 |
| 用户名唯一性 | 联合唯一 `(tenant_id, username)` | 跨租户可重名、租户内唯一 |
| 登录方式 | 用户输入 `tenantCode + username + password` | 与企业 SaaS 通用做法一致 |
| 套餐机制 | 保留 `sys_tenant_package` + `sys_tenant_package_menu` | 支持差异化定价、套餐化交付 |
| 小程序 | 所有租户共享同一 AppID | 当前阶段简化；`app_user` 仍加 `tenant_id` |
| 数据库迁移 | 手动 SQL（不开 Flyway） | 与 `forge-module-screen` 经验一致 |

## 4. 架构

### 4.1 新增模块

```
apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/
├── config/
│   ├── TenantProperties.java              # 配置属性
│   └── TenantAutoConfiguration.java       # 自动装配（@ConditionalOnProperty）
├── core/
│   ├── context/TenantContextHolder.java   # ThreadLocal（TransmittableThreadLocal）
│   ├── web/
│   │   ├── TenantContextWebFilter.java    # 解析 Header → ContextHolder
│   │   └── TenantSecurityWebFilter.java   # 越权 + 合法性校验
│   ├── db/
│   │   ├── TenantDatabaseInterceptor.java # 实现 TenantLineHandler
│   │   └── TenantBaseDO.java              # 业务实体基类（带 tenantId）
│   ├── aop/
│   │   ├── TenantIgnore.java              # 注解
│   │   └── TenantIgnoreAspect.java        # AOP 切面
│   ├── job/
│   │   ├── TenantJob.java                 # 抽象类
│   │   └── TenantJobAspect.java           # Quartz 跨线程透传
│   ├── redis/
│   │   └── TenantRedisCacheManager.java   # 缓存 key 加 tenantId 前缀
│   ├── api/
│   │   ├── TenantApi.java                 # RPC 接口
│   │   └── TenantApiImpl.java             # RPC 实现（system-biz 提供）
│   └── service/
│       ├── TenantFrameworkService.java
│       └── TenantFrameworkServiceImpl.java
```

### 4.2 数据模型

#### 4.2.1 新增表（4 张）

```sql
-- 租户主表
CREATE TABLE sys_tenant (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64)   NOT NULL COMMENT '租户名称',
  code            VARCHAR(32)   NOT NULL UNIQUE COMMENT '租户标识（登录用）',
  contact_name    VARCHAR(32),
  contact_phone   VARCHAR(32),
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  package_id      BIGINT        COMMENT '套餐ID',
  expire_time     DATETIME      COMMENT '到期时间',
  website         VARCHAR(255),
  remark          VARCHAR(500),
  create_time     DATETIME,
  update_time     DATETIME,
  create_by       BIGINT,
  update_by       BIGINT,
  deleted         TINYINT       DEFAULT 0,
  INDEX idx_code (code),
  INDEX idx_status (status)
);

-- 租户套餐
CREATE TABLE sys_tenant_package (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64)   NOT NULL,
  code            VARCHAR(32)   NOT NULL UNIQUE,
  status          TINYINT       NOT NULL DEFAULT 1,
  remark          VARCHAR(500),
  create_time     DATETIME, update_time, create_by, update_by, deleted
);

-- 套餐-菜单关联
CREATE TABLE sys_tenant_package_menu (
  tenant_package_id  BIGINT NOT NULL,
  menu_id            BIGINT NOT NULL,
  PRIMARY KEY (tenant_package_id, menu_id)
);

-- 套餐-角色关联（管理员创建租户时，自动创建一套基础角色）
CREATE TABLE sys_tenant_package_role (
  tenant_package_id BIGINT NOT NULL,
  role_id           BIGINT NOT NULL,
  PRIMARY KEY (tenant_package_id, role_id)
);
```

#### 4.2.2 改造现有表

- `sys_user`：加 `tenant_id BIGINT NOT NULL DEFAULT 1`；username 唯一索引改为 `(tenant_id, username)`；`account_type` 扩展 `2=平台超管`
- 所有业务实体（继承 `TenantBaseDO`）：增加 `tenant_id` 列 + 索引
- 不改：`sys_menu`、`sys_dict_type`、`sys_dict_data`、`sys_config`、`sys_file_config`、`sys_job`、`sys_tenant*`（共享表）
- 改：`app_user` 加 `tenant_id`（小程序也是后台管理的一部分）

### 4.3 上下文

```java
public class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT_ID = new TransmittableThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE   = new TransmittableThreadLocal<>();

    public static Long getTenantId()         { return TENANT_ID.get(); }
    public static Long getRequiredTenantId() {
        Long t = TENANT_ID.get();
        if (t == null) throw new NullPointerException("缺少租户编号");
        return t;
    }
    public static void setTenantId(Long t)   { TENANT_ID.set(t); }
    public static boolean isIgnore()         { return Boolean.TRUE.equals(IGNORE.get()); }
    public static void setIgnore(boolean v)  { IGNORE.set(v); }
    public static void clear()               { TENANT_ID.remove(); IGNORE.remove(); }
}
```

### 4.4 过滤器链（顺序敏感）

```
HTTP Request
  ↓
[1] TenantContextWebFilter          → 解析请求头 X-Tenant-Id → TenantContextHolder
  ↓
[2] JwtAuthenticationFilter         → 验证 JWT，写入 LoginUser（userId、tenantId、roles）
  ↓
[3] TenantSecurityWebFilter         → 校验越权 + 租户合法性
  ↓
[4] TenantIgnoreAspect              → @TenantIgnore 方法跳过 SQL 拦截
[5] DataPermissionAnnotationInterceptor → @DataPermission 按部门过滤
  ↓
Controller → Service → Mapper
  ↓
[6] TenantLineInnerInterceptor      → SQL 改写：INSERT 填 tenantId；UPDATE/DELETE/SELECT 拼 WHERE tenant_id = ?
[7] DataPermissionInterceptor        → SQL 改写：拼 WHERE dept_id IN (...) 等
```

### 4.5 关键类实现

#### TenantDatabaseInterceptor（核心）

```java
public class TenantDatabaseInterceptor implements TenantLineHandler {
    private final Set<String> ignoreTables;

    public TenantDatabaseInterceptor(TenantProperties properties) {
        this.ignoreTables = new HashSet<>();
        properties.getIgnoreTables().forEach(t -> {
            ignoreTables.add(t.toLowerCase());
            ignoreTables.add(t.toUpperCase());
        });
        ignoreTables.add("DUAL");  // Oracle 序列查询
    }

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContextHolder.getRequiredTenantId());
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return TenantContextHolder.isIgnore() || ignoreTables.contains(tableName);
    }

    @Override
    public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        // 平台超管调用 setIgnore(true) 时，跳过 INSERT 自动填 tenantId
        return TenantContextHolder.isIgnore();
    }
}
```

#### TenantSecurityWebFilter

```java
public class TenantSecurityWebFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        Long tenantId = TenantContextHolder.getTenantId();
        LoginUser user = SecurityFrameworkUtils.getLoginUser();

        // (a) 登录用户：校验越权
        if (user != null && !user.isPlatformAdmin()) {
            if (tenantId == null) {
                // 未传 tenantId，从 LoginUser 取
                tenantId = user.getTenantId();
                TenantContextHolder.setTenantId(tenantId);
            } else if (!Objects.equals(user.getTenantId(), tenantId)) {
                // 越权
                writeError(response, FORBIDDEN, "您无权访问该租户的数据");
                return;
            }
        }

        // (b) 非忽略 URL 必须有 tenantId
        if (!isIgnoreUrl(request)) {
            if (tenantId == null && (user == null || !user.isPlatformAdmin())) {
                writeError(response, BAD_REQUEST, "请求的租户标识未传递");
                return;
            }
            // (c) 校验租户合法
            tenantFrameworkService.validTenant(tenantId);
        } else {
            if (tenantId == null) TenantContextHolder.setIgnore(true);
        }

        chain.doFilter(request, response);
    }
}
```

#### TenantRedisCacheManager

```java
public class TenantRedisCacheManager extends RedisCacheManager {
    private final Set<String> ignoreCaches;

    @Override
    public Cache getCache(String name) {
        if (ignoreCaches.contains(name)) return super.getCache(name);
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || TenantContextHolder.isIgnore()) {
            return super.getCache(name);
        }
        return super.getCache(tenantId + ":" + name);
    }
}
```

#### TenantJobAspect（Quartz 跨线程）

```java
@Aspect
public class TenantJobAspect {
    @Around("@annotation(tenantJob)")
    public Object around(ProceedingJoinPoint pjp, TenantJob tenantJob) throws Throwable {
        Long tenantId = tenantJob.tenantId();  // 由调用方在 @TenantJob 注解上指定
        TenantContextHolder.setTenantId(tenantId);
        try { return pjp.proceed(); }
        finally { TenantContextHolder.clear(); }
    }
}
```

#### TenantIgnore 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantIgnore {}
```

AOP 切面在调用方法前 `TenantContextHolder.setIgnore(true)`，方法返回后清除。

### 4.6 配置

```yaml
forge:
  tenant:
    enable: true                          # 总开关：false 时所有 Bean 不装配
    header: X-Tenant-Id                   # 请求头名
    ignore-urls:                          # 不需要 tenantId 的 URL
      - /admin-api/auth/login
      - /admin-api/auth/refresh
      - /admin-api/system/tenant/public/**
      - /app-api/auth/wx-login
    ignore-tables:                        # 跨租户共享表
      - sys_menu
      - sys_dict_type
      - sys_dict_data
      - sys_config
      - sys_file_config
      - sys_job
      - sys_tenant
      - sys_tenant_package
      - sys_tenant_package_menu
      - sys_tenant_package_role
    ignore-caches:                        # 跨租户共享的缓存
      - dictData
      - dictType
      - sysConfig
      - menu
      - dept
```

### 4.7 登录流程

```
[Web 登录]
POST /admin-api/auth/login
  { tenantCode, username, password, captcha }

1. tenantCode → sys_tenant 校验（存在 + status=1 + 未过期）→ tenantId
2. WHERE tenant_id = tenantId AND username = ? 查 SysUser
3. 密码校验（BCrypt + PasswordPolicy）
4. 加载角色列表 + 加载该租户的套餐菜单
5. 签发 JWT（含 tenantId claim）
6. 返回 { token, userInfo, menus, tenantId, tenantName }

[小程序登录]
POST /app-api/auth/wx-login
  { tenantCode, code, encryptedData, iv }

1. tenantCode → tenantId
2. code + 全局 WX_MINI_APP_ID/SECRET → openId
3. WHERE tenant_id = tenantId AND openid = ? 查 app_user
4. 不存在 → 创建（绑定到该 tenantId）
5. 签发 JWT（含 tenantId claim）
```

### 4.8 平台超管

- `sys_user.account_type = 2`
- `UserContext.isPlatformAdmin()` 方法
- `TenantSecurityWebFilter`：跳过越权校验、跳过 tenantId 必传校验
- `TenantDatabaseInterceptor`：`ignoreTable` 直接返回 `true`（除非显式 `setTenantId`）
- 平台超管相关接口（`/system/tenant/*`）在 Service 层 `TenantContextHolder.setIgnore(true)`

## 5. 数据流

### 5.1 正常业务流程

```
Web 请求：
  Header: X-Tenant-Id: 1, Authorization: Bearer xxx

→ TenantContextWebFilter: holder.setTenantId(1)
→ JwtAuthenticationFilter: LoginUser{ userId=100, tenantId=1, roles=[admin] }
→ TenantSecurityWebFilter: 1 == 1 通过，validTenant(1) 通过
→ SysUserMapper.selectByUsername(tenantId=1, username='zhangsan')
→ SQL: SELECT * FROM sys_user WHERE tenant_id=1 AND username='zhangsan' AND deleted=0
   ↑ TenantLineInnerInterceptor 注入 tenant_id=1
   ↑ DataPermissionInterceptor 注入 dept_id IN (10,11)
→ 返回 User
```

### 5.2 越权场景

```
用户属于租户 1，请求 X-Tenant-Id: 2
→ TenantSecurityWebFilter 检测到 1 != 2
→ 返回 403 "您无权访问该租户的数据"
```

### 5.3 平台超管

```
X-Tenant-Id: 0  (或干脆不传)，account_type=2
→ TenantSecurityWebFilter: isPlatformAdmin → 跳过越权校验
→ 平台超管可主动调用 /system/tenant/list 查看所有租户
→ 该接口 Service 内 setIgnore(true) → 所有 SQL 跳过 tenantId 过滤
```

## 6. 错误处理

| 场景 | HTTP 码 | message |
|------|---------|---------|
| 请求未带 tenantId 且非忽略 URL | 400 | 请求的租户标识未传递 |
| 登录用户 tenantId 与请求 tenantId 不一致 | 403 | 您无权访问该租户的数据 |
| 租户不存在 | 404 | 租户不存在 |
| 租户已禁用 | 403 | 租户已被禁用 |
| 租户已过期 | 403 | 租户已过期 |
| 租户不存在该套餐 | 403 | 套餐无效 |

## 7. 测试策略

### 7.1 单元测试

| 类 | 用例 |
|----|------|
| `TenantContextHolderTest` | set/get/clear、ThreadLocal 隔离 |
| `TenantDatabaseInterceptorTest` | getTenantId、ignoreTable（ignoreTable + isIgnore） |
| `TenantSecurityWebFilterTest` | 越权、忽略 URL、平台超管、租户合法/禁用/过期 |
| `TenantRedisCacheManagerTest` | 缓存 key 加 tenantId 前缀、ignoreCaches 跳过 |
| `TenantIgnoreAspectTest` | @TenantIgnore 注解下 SQL 不拼 tenantId |

### 7.2 集成测试（TestContainers MySQL + Redis）

- 创建租户 A、租户 B；分别创建用户 a1（tenant=A）、b1（tenant=B）
- a1 登录 → CRUD 只能看到 A 的数据；查 B 数据返回空
- 平台超管 p1 登录 → 能看到所有租户
- @TenantIgnore 方法跳过 SQL 拦截验证（手写 SQL 验证）
- Redis 缓存 key 含 `tenantId:` 前缀，跨租户 cache 不命中
- Quartz Job 在指定 tenant 下执行

### 7.3 端到端（手工测试清单）

- [ ] 创建租户 A → 自动创建管理员账号 a_admin / 默认密码
- [ ] 用 a_admin 登录 → 自动绑定套餐菜单 → 创建子用户 a_user1
- [ ] 创建租户 B → 重复上述
- [ ] a_user1 调用 /system/user/list → 仅返回租户 A 的用户
- [ ] 切换 X-Tenant-Id 头 → 越权 403
- [ ] 平台超管 p1 登录 → 看到两个租户的所有数据
- [ ] 小程序登录：a_app_user1 登录 → 数据隔离正确
- [ ] `forge.tenant.enable=false` 重启 → 所有功能恢复单租户行为

## 8. 迁移计划（手动 SQL，不开 Flyway）

文件位置：`apps/forge-server/docs/manual-migrations/`

```
V2026071101__create_tenant_tables.sql
  - CREATE TABLE sys_tenant / sys_tenant_package / sys_tenant_package_menu / sys_tenant_package_role

V2026071102__add_tenant_id_to_sys_user.sql
  - ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1
  - DROP INDEX uk_username ON sys_user
  - CREATE UNIQUE INDEX uk_tenant_username ON sys_user(tenant_id, username)
  - INSERT account_type 扩展说明到文档（无需 DDL 变更）

V2026071103__add_tenant_id_to_business_tables.sql
  - 给所有业务表加 tenant_id 列（详见附录 A）
  - CREATE INDEX idx_tenant ON xxx(tenant_id)

V2026071104__backfill_tenant_data.sql
  - INSERT IGNORE INTO sys_tenant(id, name, code, status) VALUES (1, '默认租户', 'default', 1)
  - INSERT IGNORE INTO sys_tenant_package(id, name, code) VALUES (1, '默认套餐', 'default')
  - UPDATE sys_user SET tenant_id=1 WHERE tenant_id IS NULL OR tenant_id=0
  - UPDATE 所有业务表 SET tenant_id=1 WHERE tenant_id IS NULL OR tenant_id=0

V2026071105__init_app_user_tenant.sql
  - ALTER TABLE app_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1
  - UPDATE app_user SET tenant_id=1
  - CREATE INDEX idx_tenant ON app_user(tenant_id)
```

**附录 A — 需加 tenant_id 的业务表**：
`sys_notice`, `sys_attachment`, `sys_user_password_history`, `sys_login_log`, `sys_operation_log`, `app_user`, 各业务模块的实体表（按扫描结果生成）。

执行顺序：1 → 2 → 3 → 4 → 5。脚本幂等（用 `IF NOT EXISTS` / `IGNORE`）。

## 9. 配置开关行为矩阵

| `forge.tenant.enable` | 行为 |
|----------------------|------|
| `true`（默认） | 完整多租户能力启用 |
| `false` | 所有 Bean 不创建、所有过滤器不注册、TenantLineInnerInterceptor 不加入拦截器链，系统行为完全等同改造前 |

`false` 模式下：
- `X-Tenant-Id` 请求头被忽略
- 业务表 `tenant_id` 列保留（不删除），但不会被使用
- 登录无需 `tenantCode`（`/auth/login` 入参保留兼容，忽略该字段）

## 10. 前端改造

### 10.1 登录页 (`apps/forge-web/src/views/login/index.vue`)

```
[租户标识] (必填)
[用户名]   (必填)
[密码]     (必填)
[验证码]   (按需)
```

调用 `authApi.login({ tenantCode, username, password, captcha })`

### 10.2 Axios 拦截器 (`apps/forge-web/src/utils/request.ts`)

- 登录成功后保存 `tenantId` 到 Pinia user store
- 所有请求自动添加 `X-Tenant-Id` header（除登录、刷新、公开接口）
- 401/403 → 提示租户相关错误信息

### 10.3 租户管理界面 (`apps/forge-web/src/views/system/tenant/`)

- 列表：分页、搜索、状态过滤
- 新增/编辑：表单（name、code、contact、package、expireTime、status）
- 套餐管理：`/system/tenant-package`
- 平台超管可见，普通租户管理员不可见

## 11. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 现有表加 tenant_id 列导致大表 ALTER 慢 | 在低峰期执行；先在测试环境验证 |
| 历史数据全部归到默认租户导致跨租户污染 | 部署前全量备份；migration 脚本先 dry-run 验证条数 |
| TenantLineInnerInterceptor 与多表 JOIN 冲突 | 把 join 的"共享表"加入 `ignoreTables` |
| Quartz Job 未指定 tenantId 导致 SQL 报错 | 提供 `TenantJob` 抽象类，强制要求 tenantId |
| 缓存 key 加前缀后旧 key 失效 | 部署时清理 Redis；版本切换允许短暂空缓存 |
| 前端没传 X-Tenant-Id 时接口 400 | 前端 axios 拦截器统一注入 |

## 12. 未来扩展（不在本次实现范围）

- 租户级 OSS / MinIO 文件隔离
- 租户级 FlowLong 工作流隔离
- 租户级大屏模块数据源隔离（sys_screen_data_source.tenant_id）
- 租户级数据备份 / 导出
- 跨租户数据迁移工具