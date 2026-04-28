# OAuth2 功能使用文档

## 概述

forge-admin OAuth2 功能包含两大模块：

1. **第三方登录（OAuth2 Client）**：基于 JustAuth 实现微信、钉钉扫码登录
2. **OAuth2 授权服务器（Authorization Server）**：基于 Spring Authorization Server，允许第三方应用通过本系统进行用户认证

---

## 一、第三方登录（微信 / 钉钉）

### 1.1 配置步骤

#### 微信开放平台

1. 登录 [微信开放平台](https://open.weixin.qq.com/)，创建「网站应用」
2. 获取 `AppID` 和 `AppSecret`
3. 配置授权回调域：`your-domain.com`（不含协议和路径）

#### 钉钉开放平台

1. 登录 [钉钉开放平台](https://open-dev.dingtalk.com/)，创建「企业内部应用」
2. 获取 `AppKey` 和 `AppSecret`
3. 配置回调地址：`https://your-domain.com/api/auth/social/callback/dingtalk`

#### 后端配置

在 `application-dev.yml`（或对应环境配置文件）中填入凭证：

```yaml
justauth:
  type:
    wechat:
      client-id: ${WECHAT_APP_ID:你的微信AppID}
      client-secret: ${WECHAT_APP_SECRET:你的微信AppSecret}
      redirect-uri: https://your-domain.com/api/auth/social/callback/wechat
    dingtalk:
      client-id: ${DINGTALK_APP_KEY:你的钉钉AppKey}
      client-secret: ${DINGTALK_APP_SECRET:你的钉钉AppSecret}
      redirect-uri: https://your-domain.com/api/auth/social/callback/dingtalk
```

也可以通过环境变量注入：

```bash
export WECHAT_APP_ID=wx1234567890
export WECHAT_APP_SECRET=your-secret
export DINGTALK_APP_KEY=ding123456
export DINGTALK_APP_SECRET=your-secret
```

### 1.2 登录流程

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  前端     │    │  后端     │    │ 微信/钉钉 │    │  数据库   │
└────┬─────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘
     │ 点击「微信登录」  │               │               │
     │───────────────→│               │               │
     │                │ 302 重定向到微信 │               │
     │←───────────────│               │               │
     │                │               │               │
     │ 用户扫码授权     │               │               │
     │──────────────────────────────→│               │
     │                │               │               │
     │                │ 回调 code+state│               │
     │                │←──────────────│               │
     │                │               │               │
     │                │ 换取 access_token, 获取用户信息  │
     │                │──────────────────────────────→│
     │                │               │               │
     │                │ 查询 sys_social_user 绑定关系   │
     │                │──────────────────────────────→│
     │                │               │               │
     │     已绑定：302 到 /login/callback?accessToken=xxx
     │←───────────────│               │               │
     │                │               │               │
     │   未绑定：302 到 /login?error=social_not_bound&tempToken=yyy
     │←───────────────│               │               │
     └────────────────┘               │               │
```

### 1.3 未绑定账号时的处理

当用户首次使用第三方账号登录时，由于系统要求**必须绑定已有账号**，页面会显示：

> 该微信账号未绑定系统账号，请登录您的账号进行绑定

用户输入系统用户名和密码后，系统会：
1. 验证用户名密码
2. 自动将第三方账号与系统账号绑定
3. 登录成功

绑定后，下次可直接使用该第三方账号登录。

### 1.4 管理社交账号绑定

登录后，在 **个人中心 → 账号绑定** 标签页中可以：

- 查看当前已绑定的社交账号（显示昵称和头像）
- **绑定**：点击「绑定」按钮，弹窗进行 OAuth2 授权
- **解绑**：点击「解绑」按钮，解除绑定关系

### 1.5 API 端点

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/auth/social/authorize/{source}` | 否 | 发起 OAuth2 授权，302 重定向到第三方 |
| GET | `/auth/social/callback/{source}` | 否 | OAuth2 回调处理 |
| POST | `/auth/social/bind` | 是 | 绑定社交账号到当前用户 |
| POST | `/auth/social/unbind?source=wechat` | 是 | 解绑社交账号 |
| GET | `/auth/social/bindings` | 是 | 获取当前用户已绑定的社交账号列表 |

`source` 参数取值：`wechat`（微信）、`dingtalk`（钉钉）

---

## 二、OAuth2 授权服务器

forge-admin 作为 OAuth2 授权服务器，支持标准 OAuth2 / OIDC 协议，允许其他应用接入本系统的用户认证。

### 2.1 协议支持

| 协议 | 说明 |
|------|------|
| Authorization Code | 授权码模式，适用于 Web 应用（推荐） |
| Client Credentials | 客户端凭证模式，适用于服务间调用 |
| Refresh Token | 刷新令牌 |
| PKCE (S256) | 公共客户端安全增强 |

### 2.2 标准端点

| 端点 | URL |
|------|-----|
| Authorization | `GET /oauth2/authorize` |
| Token | `POST /oauth2/token` |
| JWKS (公钥) | `GET /oauth2/jwks` |
| Token Introspection | `POST /oauth2/introspect` |
| Token Revocation | `POST /oauth2/revoke` |
| OpenID Discovery | `GET /.well-known/openid-configuration` |
| UserInfo | `GET /userinfo` |

> **注意：** 所有端点均在 context-path `/api` 下，完整 URL 如 `http://localhost:8181/api/oauth2/token`

### 2.3 管理客户端

通过 **系统管理 → OAuth2客户端** 菜单管理 OAuth2 客户端。

#### 创建客户端

1. 点击「新增」按钮
2. 填写客户端信息：
   - **客户端ID**：自定义，如 `my-web-app`
   - **客户端名称**：如「我的Web应用」
   - **授权类型**：勾选 `authorization_code`、`refresh_token`（Web 应用推荐）
   - **权限范围**：`openid`、`profile`
   - **重定向URI**：如 `http://localhost:8080/callback`
   - **Token 有效期**：Access Token 3600秒 / Refresh Token 86400秒
3. 点击确定
4. **重要：** 创建成功后弹窗显示 **客户端密钥**，此密钥仅显示一次，请立即保存

#### 客户端管理操作

| 操作 | 说明 |
|------|------|
| 新增 | 创建客户端，密钥仅显示一次 |
| 编辑 | 修改客户端名称、重定向URI、有效期等 |
| 重置密钥 | 生成新密钥，旧密钥立即失效 |
| 删除 | 删除客户端 |

#### 权限范围（Scope）说明

权限范围（Scope）在不同授权模式下作用不同：

**authorization_code 模式：** Scope 表示请求的用户信息范围，使用标准 OIDC scope：

| Scope | 说明 |
|-------|------|
| `openid` | 必须，表示这是一个 OpenID Connect 请求 |
| `profile` | 请求用户基本信息（用户名、昵称、头像等） |
| `email` | 请求用户邮箱 |

**client_credentials 模式：** Scope 仅作为 JWT 声明写入 token，**不参与实际 API 鉴权**。实际 API 访问权限由**服务账号的系统角色**决定。

鉴权链路：

```
OAuth2 token (scope: openid, profile) → 提取 sub (client_id)
    → 查找同名系统用户（服务账号）
    → 加载服务账号的角色权限（system:user:list, system:dict:list, ...）
    → 用系统权限鉴权（@PreAuthorize）
```

如需精确控制不同客户端的 API 访问范围，可通过为不同服务账号分配不同角色实现。

### 2.4 接入示例

#### client_credentials 模式（服务间调用）

适用于微服务间调用、后台定时任务等**无用户参与**的场景。

**流程：**

```
┌──────────────┐                         ┌──────────────────┐
│  服务 A       │                         │  forge-admin      │
│ (第三方应用)   │                         │  (授权服务器)      │
└──────┬───────┘                         └────────┬─────────┘
       │  1. POST /api/oauth2/token                │
       │     grant_type=client_credentials         │
       │     Authorization: Basic base64(id:secret) │
       │───────────────────────────────────────────→│
       │                                           │
       │                                           │ 验证 client_id + client_secret
       │                                           │ 颁发 access_token（无 refresh_token）
       │                                           │
       │  2. 返回 access_token                      │
       │←───────────────────────────────────────────│
       │                                           │
       │  3. 调用业务 API（携带 access_token）        │
       │     Authorization: Bearer <access_token>   │
       │───────────────────────────────────────────→│
       │                                           │
       │  4. 返回业务数据                             │
       │←───────────────────────────────────────────│
```

**Step 1：在 forge-admin 中创建客户端**

在「系统管理 → OAuth2客户端」新增，授权类型勾选 `client_credentials`，记录返回的客户端密钥。

**Step 2：创建服务账号**

client_credentials 模式需要将 OAuth2 客户端映射到系统用户。在「系统管理 → 用户管理」中创建一个用户名与客户端ID相同的用户（即**服务账号**），并分配相应角色和权限。

例如客户端ID为 `service-api`，则创建用户名为 `service-api` 的系统用户，分配超级管理员角色。

> **原理：** 当收到 OAuth2 access_token 时，系统自动以 token 中的 `sub`（即 client_id）作为用户名查找对应的系统用户，加载其角色和权限。因此服务账号拥有其所分配角色的全部权限。

**Step 3：获取 access_token**

```bash
curl -X POST http://localhost:8181/api/oauth2/token \
  -u "your-client-id:your-client-secret" \
  -d "grant_type=client_credentials&scope=openid profile"
```

> `-u` 是 HTTP Basic Auth，等价于 `Authorization: Basic base64(client_id:client_secret)`

响应：

```json
{
  "access_token": "eyJraWQiOi...",
  "scope": "openid profile",
  "token_type": "Bearer",
  "expires_in": 3599
}
```

**Step 3：用 access_token 调用 API**

OAuth2 access_token 可用于调用本系统的任意业务 API（只要服务账号拥有对应权限）：

```bash
# 查询字典列表
curl "http://localhost:8181/api/system/dict-type/list?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer <access_token>"

# 查询角色列表
curl "http://localhost:8181/api/system/role/list?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer <access_token>"

# Token 自省（验证 token 是否有效）
curl -X POST http://localhost:8181/api/oauth2/introspect \
  -u "your-client-id:your-client-secret" \
  --data-urlencode "token=<access_token>"
```

**Step 4：撤销令牌**

```bash
curl -X POST http://localhost:8181/api/oauth2/revoke \
  -u "your-client-id:your-client-secret" \
  --data-urlencode "token=<access_token>"
```

撤销后通过 Introspection 验证：

```bash
curl -X POST http://localhost:8181/api/oauth2/introspect \
  -u "your-client-id:your-client-secret" \
  --data-urlencode "token=<access_token>"
# 返回 {"active": false}
```

**关键特点：**

| 特性 | 说明 |
|------|------|
| 参与方 | 只有客户端（服务），没有用户 |
| 认证方式 | client_id + client_secret |
| 返回内容 | 仅 access_token |
| 不返回 | refresh_token、id_token |
| token 过期 | 直接重新请求获取新 token（无需 refresh_token） |
| 权限控制 | 通过服务账号（与 client_id 同名的系统用户）的角色权限控制 |
| 适用场景 | 微服务间调用、后台定时任务、API 网关 |

#### authorization_code 模式（Web 应用）

适用于有用户参与的 Web 应用，用户在 forge-admin 登录并授权后，第三方应用获取用户信息。

> **注意：** 以下示例中 `redirect_uri=http://127.0.0.1:8080/callback` 是**第三方应用**的回调地址，需与创建客户端时配置的重定向 URI 一致。`localhost:8181` 是本系统（授权服务器）的地址。

**流程：**

```
┌──────────┐     ┌──────────────┐     ┌──────────────────┐
│  用户浏览器 │     │  第三方应用     │     │  forge-admin      │
└─────┬────┘     └──────┬───────┘     └────────┬─────────┘
      │  1. 点击「使用 forge-admin 登录」 │               │
      │──────────────────────────────→│               │
      │                               │               │
      │  2. 302 重定向到授权服务器        │               │
      │←──────────────────────────────│               │
      │                               │               │
      │  3. GET /api/oauth2/authorize?response_type=code&client_id=xxx&redirect_uri=xxx
      │──────────────────────────────────────────────→│
      │                                               │
      │  4. 用户未登录 → 302 重定向到登录页              │
      │←──────────────────────────────────────────────│
      │                                               │
      │  5. 用户输入用户名密码登录                       │
      │──────────────────────────────────────────────→│
      │                                               │
      │  6. 登录成功，自动授权，302 重定向到 redirect_uri │
      │←──────────────────────────────────────────────│
      │  Location: http://127.0.0.1:8080/callback?code=xxxxx
      │                               │               │
      │  7. 浏览器携带 code 回到第三方应用  │               │
      │──────────────────────────────→│               │
      │                               │               │
      │                               │  8. POST /api/oauth2/token（服务端直接调用，不经浏览器）
      │                               │    grant_type=authorization_code&code=xxxxx
      │                               │    Authorization: Basic base64(client_id:secret)
      │                               │──────────────→│
      │                               │               │
      │                               │  9. 返回 access_token + refresh_token + id_token
      │                               │←──────────────│
      │                               │               │
      │                               │  10. 用 access_token 调用 /api/userinfo 获取用户信息
      │                               │──────────────→│
      │                               │               │
      │  11. 登录成功，显示用户信息       │←──────────────│
      │←──────────────────────────────│               │
```

**Step 1：引导用户到授权页面**

```
GET http://localhost:8181/api/oauth2/authorize?response_type=code&client_id=test-client&scope=openid profile&redirect_uri=http://127.0.0.1:8080/callback
```

**Step 2：用户登录并授权后，回调到第三方应用的 redirect_uri**

```
http://127.0.0.1:8080/callback?code=xxxxx
```

**Step 3：用 code 换取 token**

```bash
curl -X POST http://localhost:8181/api/oauth2/token \
  -u "test-client:your-client-secret" \
  -d "grant_type=authorization_code&code=xxxxx&redirect_uri=http://127.0.0.1:8080/callback"
```

**Step 4：使用 access_token 获取用户信息**

```bash
curl http://localhost:8181/api/userinfo \
  -H "Authorization: Bearer eyJraWQiOi..."
```

#### Spring Boot 应用接入

在接入方 Spring Boot 应用中添加依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

配置：

> **注意：** 由于本系统使用了 context-path `/api`，无法直接使用 `issuer-uri` 自动发现，需要手动配置各端点 URL。

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          forge-admin:
            client-id: test-client
            client-secret: your-client-secret
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - profile
        provider:
          forge-admin:
            authorization-uri: http://localhost:8181/api/oauth2/authorize
            token-uri: http://localhost:8181/api/oauth2/token
            jwk-set-uri: http://localhost:8181/api/oauth2/jwks
            user-info-uri: http://localhost:8181/api/userinfo
            user-name-attribute: sub
```

### 2.5 数据库表

OAuth2 授权服务器使用以下数据库表（由 Spring Authorization Server 管理，无需手动操作）：

| 表名 | 说明 |
|------|------|
| `oauth2_registered_client` | 注册的 OAuth2 客户端 |
| `oauth2_authorization` | 授权信息（token、code 等） |
| `oauth2_authorization_consent` | 用户授权同意记录 |

---

## 三、数据库表

### sys_social_user（社交账号绑定）

> 以下为核心业务字段，完整表结构还包括 `access_token`、`refresh_token`、`token_expire_time`、`raw_user_info` 等令牌存储字段，以及 `create_time`、`update_time`、`create_by`、`update_by`、`deleted`、`remark` 等公共审计字段。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| user_id | bigint | 系统用户ID |
| source | varchar(32) | 平台标识（wechat/dingtalk） |
| open_id | varchar(128) | 第三方 open_id |
| union_id | varchar(128) | union_id（微信多应用） |
| access_token | varchar(512) | 加密存储的 access_token |
| refresh_token | varchar(512) | 加密存储的 refresh_token |
| token_expire_time | datetime | 令牌过期时间 |
| nickname | varchar(128) | 第三方昵称 |
| avatar | varchar(512) | 第三方头像 |
| raw_user_info | text | 原始用户信息 JSON |
| status | tinyint | 状态（0:禁用 1:启用） |

唯一约束：`(source, open_id)` — 每个社交账号只能绑定一个系统用户
索引：`idx_user_id(user_id)`、`idx_union_id(union_id)`

---

## 四、权限说明

### 社交登录

社交登录的授权和回调端点不需要认证权限。绑定、解绑操作需要用户已登录。

### OAuth2 客户端管理

| 权限标识 | 说明 |
|----------|------|
| `system:oauth2-client:list` | 查看客户端列表 |
| `system:oauth2-client:query` | 查看客户端详情 |
| `system:oauth2-client:add` | 新增客户端 |
| `system:oauth2-client:edit` | 编辑客户端、重置密钥 |
| `system:oauth2-client:delete` | 删除客户端 |

超级管理员默认拥有以上所有权限。可通过菜单管理为其他角色分配。
