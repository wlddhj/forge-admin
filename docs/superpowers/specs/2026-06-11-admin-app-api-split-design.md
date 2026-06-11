# Admin/App API 双端点分离设计

## 背景

当前 forge-admin 所有 Controller 统一使用 `/api` context-path，仅服务于后台管理系统。为支持移动端（微信小程序），需要将 API 拆分为两套独立端点：

- `/admin-api/**` — 后台管理端（现有功能迁移）
- `/app-api/**` — 移动端（新增微信小程序接口）

参考项目 shi9-boot 的 `**.controller.admin.**` / `**.controller.app.**` 包路径自动匹配模式。

## 决策记录

| 决策项 | 选择 |
|--------|------|
| context-path | 去掉 `/api`，由框架自动注入前缀 |
| 移动端接口范围 | 登录/注册、个人信息（5 个端点） |
| 认证机制 | 独立 Filter + 独立 Redis 前缀 |
| 用户体系 | 独立 `app_user` 表，与 `sys_user` 完全隔离 |
| 登录方式 | 微信授权登录（openid 自动注册） |
| 前缀注入方式 | 方案 A：WebMvcConfigurer 自动前缀 |

## 一、框架层改动

### 1.1 WebProperties（新增）

位置：`forge-spring-boot-starter-web`

```java
@ConfigurationProperties(prefix = "forge.web")
public class WebProperties {
    private Api adminApi = new Api("/admin-api", "**.controller.admin.**");
    private Api appApi   = new Api("/app-api",   "**.controller.app.**");

    @Data
    public static class Api {
        private String prefix;       // 路径前缀
        private String controller;   // Ant 包匹配模式
    }
}
```

### 1.2 WebMvcConfig（修改）

在 `configurePathMatch` 中通过 `AntPathMatcher(".")` 匹配 Controller 包名，自动注入前缀：

- `**.controller.admin.**` → `/admin-api`
- `**.controller.app.**` → `/app-api`

### 1.3 application.yml 变更

```yaml
# 删除
server:
  servlet:
    context-path: /api

# 新增
forge:
  web:
    admin-api:
      prefix: /admin-api
      controller: "**.controller.admin.**"
    app-api:
      prefix: /app-api
      controller: "**.controller.app.**"
```

## 二、安全层改动

### 2.1 AppJwtAuthenticationFilter（新增）

位置：`forge-spring-boot-starter-security`

- Redis key 前缀：`forge:app:token:`（admin 用 `forge:token:`）
- Redis session key：`forge:app:session:{userId}`
- 从 JWT 解析 app 用户 ID，加载 `AppUser` 到 SecurityContext

### 2.2 SecurityConfig 改造

现有单条 `SecurityFilterChain` 按路径匹配拆分：

- `/admin-api/**` → `JwtAuthenticationFilter`（现有，加载 `sys_user`）
- `/app-api/**` → `AppJwtAuthenticationFilter`（新增，加载 `app_user`）

白名单：
- `/admin-api/auth/login`、`/admin-api/auth/captcha` 等放行
- `/app-api/auth/wx-login` 放行
- swagger、ws、error 等不变

### 2.3 JWT Token 区分

Token claims 中携带 `type` 字段：
- admin token：`type: "admin"`
- app token：`type: "app"`

两个 Filter 分别校验 token type，防止交叉使用。

### 2.4 认证流程对比

| 步骤 | Admin（现有） | App（新增） |
|------|--------------|-------------|
| 登录 | 用户名+密码 → `/admin-api/auth/login` | 微信 code → `/app-api/auth/wx-login` |
| 用户表 | `sys_user` | `app_user` |
| Redis key | `forge:token:{token}` | `forge:app:token:{token}` |
| Token type | `admin` | `app` |
| 权限 | `@PreAuthorize` 细粒度 | 登录即可访问 |

## 三、数据模型与 App 模块

### 3.1 app_user 表

```sql
CREATE TABLE app_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    open_id         VARCHAR(64)  NOT NULL COMMENT '微信openid',
    union_id        VARCHAR(64)  DEFAULT NULL COMMENT '微信unionid',
    nickname        VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    avatar          VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态（0正常 1禁用）',
    last_login_time DATETIME     DEFAULT NULL,
    create_time     DATETIME     NOT NULL,
    update_time     DATETIME     NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_open_id (open_id)
) COMMENT '移动端用户表';
```

### 3.2 模块结构

放在 system 模块内，不新建 Maven 模块：

```
forge-module-system-biz/src/main/java/com/forge/modules/system/
├── controller/
│   ├── admin/                  ← 现有 32 个 Controller 移入
│   │   ├── auth/
│   │   │   ├── AuthController.java
│   │   │   └── SocialAuthController.java
│   │   ├── SysUserController.java
│   │   └── ...
│   └── app/                    ← 新增
│       ├── AppAuthController.java
│       └── AppUserController.java
├── service/
│   └── app/
│       ├── AppUserService.java
│       ├── AppUserServiceImpl.java
│       └── AppAuthService.java
│       └── AppAuthServiceImpl.java
└── mapper/
    └── AppUserMapper.java
```

实体和 DTO 放在 `forge-module-system-api`：
```
forge-module-system-api/src/main/java/com/forge/modules/system/
├── entity/
│   └── AppUser.java
└── dto/
    └── app/
        ├── WxLoginRequest.java
        ├── AppLoginResponse.java
        └── AppUserProfileResponse.java
        └── AppUserProfileUpdateRequest.java
```

### 3.3 App Controller 端点清单

**AppAuthController** — `@RequestMapping("/auth")`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/app-api/auth/wx-login` | 微信登录（code 换 token，自动注册） |
| POST | `/app-api/auth/refresh` | 刷新 token |
| POST | `/app-api/auth/logout` | 登出 |

**AppUserController** — `@RequestMapping("/user")`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/app-api/user/profile` | 获取个人信息 |
| PUT | `/app-api/user/profile` | 更新个人信息（昵称、头像） |

### 3.4 微信登录流程

```
小程序 wx.login() → code
    ↓
POST /app-api/auth/wx-login { code }
    ↓
后端：code → 微信 API 换 openid
    ↓
查询 app_user：
  - 存在 → 生成 JWT token（type=app）→ 返回
  - 不存在 → 自动创建 app_user → 生成 JWT token → 返回
    ↓
返回 { token, refreshToken, userInfo }
```

## 四、Controller 迁移与前端适配

### 4.1 Controller 迁移

纯文件移动，不改代码。包名变化触发框架自动注入不同前缀。

**system 模块（18 个）：**
- SysUserController、SysRoleController、SysMenuController、SysDeptController、SysPositionController
- SysDictDataController、SysDictTypeController、SysConfigController、SysFileConfigController
- SysNoticeController、SysOnlineUserController、SysLoginLogController、SysOperationLogController
- SysAttachmentController、KeySequenceController、DashboardController
- SysJobController、SysJobLogController

**auth 模块（4 个）：**
- AuthController、SocialAuthController、OAuth2ClientController、OAuth2UserInfoController

**workflow 模块（10 个）：**
- WfCategoryController、WfFormController、WfModelController、WfTaskController
- WfProcessDefinitionController、WfProcessInstanceController、WfProcessInstanceCopyController
- WfCandidateStrategyController、WfProcessExpressionController、WfProcessListenerController

### 4.2 前端适配

**request.ts：**
- `baseURL` 从 `''` 改为 `'/admin-api'`

**vite.config.ts 代理：**
```typescript
proxy: {
  '/admin-api': { target: 'http://localhost:8181', changeOrigin: true },
  '/app-api':   { target: 'http://localhost:8181', changeOrigin: true }
}
```

**Knife4j：**
- API 文档路径从 `/api/doc.html` 变为 `/doc.html`
- 可配置 Swagger 分组区分 admin/app

### 4.3 数据库迁移

位置：`forge-server/src/main/resources/db/migration/V2026061101__app_user.sql`

### 4.4 改动影响

| 层次 | 改动类型 | 文件数 |
|------|---------|--------|
| 框架层 (starter-web) | 新增 WebProperties、修改 WebMvcConfig | 2 |
| 安全层 (starter-security) | 新增 AppJwtAuthenticationFilter、修改 SecurityConfig | 3 |
| system-api | 新增 AppUser 实体 + DTO | 4 |
| system-biz | 新增 App Controller/Service/Mapper + 迁移 Controller | ~12 新增 + 32 移动 |
| workflow-biz | 迁移 Controller | 10 移动 |
| application.yml | 去 context-path、加 forge.web 配置 | 1 |
| 前端 request.ts | 改 baseURL | 1 |
| 前端 vite.config.ts | 改代理 | 1 |
| DB 迁移 | 新建 app_user 表 | 1 |
