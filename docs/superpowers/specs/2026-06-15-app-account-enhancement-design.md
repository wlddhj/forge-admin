# App 端账号管理增强 - 设计文档

**日期：** 2026-06-15
**作者：** huangjian
**状态：** Draft（待审核）

## 背景

forge-admin 项目刚完成 admin/app 双端点架构拆分，app 端（移动端）目前只有最小骨架：

- `AppAuthController`（`/app-api/auth/*`）：微信登录、刷新 token、登出
- `AppUserController`（`/app-api/user/*`）：仅 GET/PUT 个人信息（nickname/avatar）
- `AppUser` 实体：id、openId、unionId、nickname、avatar、phone、status、lastLoginTime

移动端用户在登录后缺少基础账号管理能力，且 admin 后台没有 app 用户运营页面。

## 目标

1. **手机号绑定/换绑**：短信验证码 + 限流防刷
2. **账号注销**：软删除 + 允许同一微信重新注册
3. **头像上传**：独立 app 端上传端点
4. **后台 app 用户管理**：admin 端列表 + 封禁/解封 + 重置资料 + 软删除
5. **小程序端 UI（新增）**：新建 uni-app 工程 `forge-miniapp`，实现上述 4 项能力的闭环

## 非目标（YAGNI）

- 真实短信服务接入（本 spec 仅抽象接口 + Mock 实现，将来接阿里云/腾讯云时换实现即可）
- app 端登录日志表（`sys_login_log` 不覆盖 app 端，本 spec 不补；待独立特性处理）
- 工作流/通知公告等业务能力向 app 端开放（独立特性处理）
- 实名认证、密码登录（app 端保持纯微信登录）
- access_token 主动失效黑名单（依赖 Redis session 失效 + token 自然过期）

## 总体架构

### 模块边界

```
forge-module-system-api/
├── entity/AppUser.java                  [修改] 新增 phone_verified、deactivated_time 字段
├── dto/app/
│   ├── SmsCodeRequest.java              [新增]
│   ├── PhoneBindRequest.java            [新增]
│   ├── DeactivateRequest.java           [新增]
│   ├── AppUploadResponse.java           [新增]
│   ├── AppUserDetailResponse.java       [新增]
│   └── AppUserQueryRequest.java         [新增]
├── service/sms/
│   ├── SmsService.java                  [新增] 抽象接口
│   └── SmsProperties.java               [新增] forge.sms 配置类
└── annotation/
    └── AssertAppUserActive.java         [新增] AOP 注解

forge-module-system-biz/
├── controller/app/
│   ├── AppAuthController.java           [修改] 接入注销
│   ├── AppUserController.java           [修改] 接入手机号绑定、profile 接口扩展
│   └── AppAttachmentController.java     [新增] /attachment/upload
├── controller/admin/
│   └── AppUserAdminController.java      [新增] /admin-api/system/app-user/*
├── service/app/
│   ├── AppUserService.java              [修改]
│   ├── AppUserServiceImpl.java          [修改]
│   └── sms/
│       ├── MockSmsServiceImpl.java      [新增] @ConditionalOnMissingBean(SmsService.class)
│       └── SmsCodeManager.java          [新增] 验证码生成/限流/校验
├── aop/
│   └── AssertAppUserActiveAspect.java   [新增] AOP 切面
└── mapper/AppUserMapper.java            [修改] 如需后台查询扩展

apps/forge-server/forge-server/src/main/resources/
└── db/migration/V2026061501__app_user_extend.sql   [新增]

apps/forge-miniapp/                     [新增整个工程]
apps/forge-web/src/
├── api/system/app-user.ts              [新增]
├── api/attachment.ts                   [新增]
└── views/system/app-user/
    ├── index.vue                       [新增]
    └── detail.vue                      [新增]
```

### 关键架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 短信服务位置 | `forge-module-system-api/service/sms/` | 工作流等模块将来可直接注入；不抽 starter 符合 YAGNI |
| 头像上传是否复用 admin 的 `SysAttachmentController` | 否，新建 `AppAttachmentController` | 避免跨 `/admin-api`/`/app-api` 前缀耦合 |
| `Mock` 实现激活方式 | `@ConditionalOnMissingBean(SmsService.class)` | 未来引入阿里云实现自动覆盖 |
| access_token 失效策略 | 不入黑名单，依赖 Redis session + 自然过期 | 维护成本/收益不成正比 |
| 已注销用户的写操作拦截 | AOP `@AssertAppUserActive` 切面 | 比手写 `assertNotDeactivated` 在每个 service 入口，代码集中可控 |
| 强制下线（封禁/注销）精准失效 | Redis SET `app_user_sessions:{userId}` | 比 SCAN `app_session:*` 性能好 |
| `open_id` 唯一约束调整 | 改为联合唯一 `(open_id, deleted)` + 注销时改写 open_id | 兼容软删除 + 允许重注册 |
| 小程序端 UI 库 | uview-plus | 社区成熟、组件丰富、Vue 3 兼容 |
| 小程序端状态管理 | Pinia + `pinia-plugin-persistedstate` | 与 admin 一致 |
| 头像选择走原生还是 uview-plus | 原生 `uni.chooseMedia` + `button open-type="chooseAvatar"` | 拿得到微信原图，u-upload 走通用 chooseImage 不行 |

## 数据模型

### `app_user` 表结构变更

迁移脚本：`V2026061501__app_user_extend.sql`

```sql
-- 新增字段
ALTER TABLE app_user
    ADD COLUMN phone_verified TINYINT NOT NULL DEFAULT 0
        COMMENT '手机号是否已验证（0否 1是）' AFTER phone,
    ADD COLUMN deactivated_time DATETIME DEFAULT NULL
        COMMENT '注销时间，NULL表示未注销' AFTER last_login_time;

-- 调整 open_id 唯一约束：支持"软删除 + 允许重注册"
ALTER TABLE app_user DROP INDEX uk_open_id;
ALTER TABLE app_user ADD UNIQUE KEY uk_open_id_active (open_id, deleted);
```

**说明：**

- 原 `UNIQUE KEY uk_open_id (open_id)` 与"软删除 + 允许重注册"冲突：用户注销后 `deleted=1` 但 `open_id` 仍占用，新用户注册时撞唯一约束。
- 改为联合唯一 `(open_id, deleted)` 后，注销时同步把 `open_id` 改写为 `<原值>#del_<epoch_second>`（如 `oABC#del_1718400000`），原值腾出，UNIQUE 约束保持不变。
- `phone` 字段不加唯一约束：service 层校验"同一手机号未被其他有效用户绑定"，比联合唯一索引灵活。

### `AppUser` 实体新增字段

```java
private Integer phoneVerified;     // 0/1
private LocalDateTime deactivatedTime;
```

### Redis Key 设计

| Key 模板 | 类型 | TTL | 用途 |
|---------|-----|-----|------|
| `app:sms:code:{phone}` | String | 5 分钟 | 存验证码 |
| `app:sms:lock:{phone}` | String | 60 秒 | 单次发送冷却 |
| `app:sms:count:{phone}:{date}` | String | 24 小时 | 当日发送次数（限 5 次） |
| `app:sms:error:{phone}` | String | 5 分钟 | 验证错误次数（错 5 次后失效需重新发） |
| `app:lock:bind-phone:{phone}` | String | 5 秒 | 同手机号绑定的分布式锁 |
| `app_user_sessions:{userId}` | Set | 与 refresh_token 同（7 天） | 该用户所有 tokenId 和 refreshToken，用于精准强制下线 |

`app_user_sessions:{userId}` 维护点：

- 登录成功：`SADD` tokenId 和 refreshToken
- 登出：`SREM` 当前 tokenId 和 refreshToken
- 注销/封禁/后台删除：`SMEMBERS` 后批量 `DEL` 各 session/refresh key，最后 `DEL` 该 SET 自身

### 配置项

`application.yml` 新增 `forge.sms`：

```yaml
forge:
  sms:
    code-length: 6
    code-ttl-seconds: 300        # 5 分钟
    send-cooldown-seconds: 60
    daily-limit: 5
    verify-error-limit: 5
```

## API 设计

### app-api（移动端用）

#### 发送验证码

```
POST /app-api/user/sms-code
Auth: Bearer <token>
Body: { "phone": "13800138000", "scene": "BIND_PHONE" }
Resp: { "code": 200, "data": { "expireSeconds": 300 } }
```

错误码：`PARAM_INVALID` / `SMS_COOLDOWN` / `SMS_DAILY_EXCEEDED` / `PHONE_ALREADY_BOUND`（预检失败）

#### 绑定/换绑手机号

```
POST /app-api/user/bind-phone
Auth: Bearer <token>
Body: { "phone": "13800138000", "code": "123456" }
Resp: { "code": 200, "data": null }
```

错误码：`PARAM_INVALID` / `SMS_CODE_NOT_FOUND` / `SMS_CODE_ERROR`（带剩余次数）/ `SMS_CODE_LOCKED` / `PHONE_ALREADY_BOUND`

#### 注销账号

```
DELETE /app-api/user/deactivate
Auth: Bearer <token>
Body: { "confirm": true }
Resp: { "code": 200, "data": null }
```

副作用：
- `app_user.deleted=1`，`open_id` 改写，`deactivated_time=now`
- 清除 Redis 中的 session 和 refresh_token
- 当前 access_token 由前端丢弃，服务端不主动失效

#### 上传头像

```
POST /app-api/attachment/upload
Auth: Bearer <token>
Content-Type: multipart/form-data
Form: file=<binary>, bizType=APP_AVATAR
Resp: { "code": 200, "data": { "url": "https://...", "attachmentId": 123 } }
```

限制：
- 仅接受 image/jpeg, image/png, image/webp
- 单文件 ≤ 2 MB
- 不调用 `/user/profile`，前端拿到 url 后再调 PUT /user/profile 更新

#### GET / PUT /app-api/user/profile（扩展）

GET 响应新增字段：

```json
{
  "id": 1,
  "nickname": "张三",
  "avatar": "https://...",
  "phone": "138****0000",
  "phoneVerified": 1,
  "openId": "oABC****WXYZ",
  "status": 0,
  "lastLoginTime": "2026-06-15T14:30:00"
}
```

### admin-api（后台用）

统一路径前缀 `/admin-api/system/app-user`，权限前缀 `system:app-user`。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/list` | `:list` | 分页查询 |
| GET | `/{id}` | `:detail` | 详情 |
| PUT | `/{id}/status` | `:update` | 封禁/解封 |
| PUT | `/{id}/profile` | `:update` | 重置昵称/头像 |
| DELETE | `/{id}` | `:delete` | 后台软删除 |

#### 列表查询

```
GET /admin-api/system/app-user/list?nickname=x&phone=x&status=0&pageNum=1&pageSize=10
Resp: PageResult<AppUserDetailResponse>
```

`AppUserDetailResponse` 字段：id, nickname, avatar, phone(脱敏), phoneVerified, openId(脱敏), status, lastLoginTime, deactivatedTime, createTime, updateTime

#### 封禁/解封

```
PUT /admin-api/system/app-user/{id}/status
Body: { "status": 1 }
```

副作用：封禁时同步清 Redis `app_user_sessions:{userId}` 中所有 session（强制下线）。

#### 重置资料

```
PUT /admin-api/system/app-user/{id}/profile
Body: { "nickname": "x", "avatar": "https://..." }   // null 字段忽略
```

### 菜单与权限 SQL

迁移脚本同步插入菜单和权限：

```sql
-- 一级菜单：App用户（挂在"系统管理"下）
INSERT INTO sys_menu(name, parent_id, path, component, perms, type, icon, ...)
VALUES('App用户', <system_root_id>, '/system/app-user', 'system/app-user/index',
       'system:app-user:list', 0, 'user', ...);

-- 二级按钮权限
INSERT INTO sys_menu(...) VALUES
  ('详情', <app_user_menu_id>, '', '', 'system:app-user:detail', 2, '', ...),
  ('封禁/解封/重置', ..., '', '', 'system:app-user:update', 2, '', ...),
  ('删除', ..., '', '', 'system:app-user:delete', 2, '', ...);
```

## 关键流程

### 流程 1：发送验证码

```
App → POST /user/sms-code {phone, scene}
  → checkCooldown(phone)         失败: SMS_COOLDOWN
  → checkDailyLimit(phone)       失败: SMS_DAILY_EXCEEDED
  → verifyPhoneNotBound(phone)   失败: PHONE_ALREADY_BOUND（BIND_PHONE 场景预检）
  → 生成 6 位数字（SecureRandom）
  → Redis SET code / lock / INCR count
  → smsService.send(phone, code)  Mock 实现: log.info("验证码: {}")
  → 返回 { expireSeconds: 300 }
```

### 流程 2：绑定/换绑手机号

```
App → POST /user/bind-phone {phone, code}
  → SmsCodeManager.verify(phone, code)
      匹配: DEL code, reset error count
      不匹配: INCR error，到 5 次 DEL code（返回 SMS_CODE_LOCKED）
  → AppUserService.bindPhone(userId, phone)
      取分布式锁 app:lock:bind-phone:{phone}
      二次校验 phone 未被其他有效用户占用（防并发）
      更新 app_user: phone, phone_verified=1
      释放锁
```

**关键点：发码预检 + 绑定时二次校验**，防止发码后别人抢绑。

### 流程 3：账号注销

```
App → DELETE /user/deactivate {confirm:true}
  → AOP @AssertAppUserActive 先验证当前用户未注销
  → AppUserService.deactivate(userId)
      查 user，确认未注销
      更新: deleted=1, open_id="<原>#del_<ts>", deactivated_time=now
      SMEMBERS app_user_sessions:{userId}
      批量 DEL session / refresh_token
      DEL app_user_sessions:{userId}
  → 返回 success
```

**为什么不在 Redis 维护 access_token 黑名单？**

- access_token 短（2 小时），黑名单维护成本高于收益
- 关键写操作有 AOP `@AssertAppUserActive` 二次拦截，注销用户调用必失败

### 流程 4：头像上传

```
App → POST /attachment/upload {file, bizType=APP_AVATAR}
  → 类型 + 大小校验
  → SysAttachmentService.upload(file, BizType.APP_AVATAR, appUserId)
      → Storage 存储
      → insert sys_attachment
  → 返回 { url, attachmentId }

App → PUT /user/profile { avatar: url }   // 客户端拿到 url 后再调
```

**为什么不让 `/attachment/upload` 顺手改 `app_user.avatar`？**

- 单一职责：上传归上传，资料更新归资料更新
- 复用：未来 `bizType=APP_ID_CARD` 等场景同样走这套

### 流程 5：后台封禁

```
Admin → PUT /admin-api/system/app-user/{id}/status {status:1}
  → AppUserService.updateStatus(id, 1)
      更新 app_user.status=1
      SMEMBERS app_user_sessions:{userId}
      批量 DEL session / refresh_token
      DEL app_user_sessions:{userId}
```

强制下线后，已签发的 access_token 还能用 2 小时（自然过期），但：

- 调写操作 → AOP 拦截返回 `USER_DISABLED`
- access_token 过期后 refresh_token 已被清，无法续签

### AOP 切面：`@AssertAppUserActive`

注解打在 app-api 的写 controller 方法上（`bindPhone`、`deactivate`、`updateProfile`、`upload` 等）。

```java
@Around("@annotation(assertAppUserActive)")
public Object check(ProceedingJoinPoint pjp) {
    Long userId = (Long) RequestContextHolder.currentRequestAttributes()
            .getAttribute("appUserId", RequestAttributes.SCOPE_REQUEST);
    AppUser user = appUserService.getById(userId);
    if (user == null || user.getDeleted() == 1
            || user.getDeactivatedTime() != null
            || user.getStatus() == 1) {
        throw new BusinessException(USER_DEACTIVATED);
    }
    return pjp.proceed();
}
```

**走 controller 层而非 service 层**：切面只想拦 app 端写操作，admin 端调用 service 不应受影响（admin 端可能需要操作已封禁用户的数据）。

## 后台管理页 UI

参考 `views/system/user/index.vue` 现有模式（搜索卡 + vxe-table + 操作列），保持视觉一致。

### 列表页 `views/system/app-user/index.vue`

```
┌─ 搜索卡 ───────────────────────────────────────────────────────┐
│ 昵称 [____] 手机号 [____] OpenId [____] 状态 [▼全部]            │
│ 创建时间 [____] ~ [____]            [搜索] [重置]               │
└────────────────────────────────────────────────────────────────┘
┌─ 表格卡 ─ vxe-toolbar ─────────────────────────────────────────┐
│ [刷新] [显隐列]                              (无新增按钮)        │
├────────────────────────────────────────────────────────────────┤
│ # │头像│昵称  │手机号      │OpenId     │状态│最后登录 │操作     │
│ 1 │ 🧑 │张三  │138****0000 │oABC****XYZ│正常│06-15 14:│详情 封禁│
│ 2 │ 🧑 │李四  │未绑定      │oDEF****UVW│禁用│06-14 09:│详情 解封│
│                                                            重置│
│                                                            删除│
└────────────────────────────────────────────────────────────────┘
                              [分页]
```

列定义：

| 列 | 字段 | 说明 |
|----|------|------|
| 头像 | avatar | el-avatar 32×32，无头像显示默认 icon |
| 昵称 | nickname | - |
| 手机号 | phone | 脱敏 `138****0000`；`phoneVerified=0` 显示"未验证"灰标 |
| OpenId | openId | 脱敏 前 4 后 4 |
| 状态 | status | el-tag：正常绿/禁用红 |
| 最后登录 | lastLoginTime | 相对时间 |
| 操作 | - | 详情 / 封禁-解封 / 重置 / 删除（按权限） |

**脱敏统一走后端**：列表和详情接口返回的 phone/openId 已脱敏；后台"重置资料"时不回显真实手机号（管理员看脱敏值即可，重置需求通常是清空）。

### 详情抽屉 `views/system/app-user/detail.vue`

```
┌─ App 用户详情 ─────────────────────────────┐
│  [🧑 头像大图]                              │
│                                            │
│  基本信息                                   │
│  ID: 123                                   │
│  昵称: 张三                                │
│  手机号: 138****0000 (已验证)              │
│  OpenId: oABC****XYZ                       │
│  UnionId: -                                │
│  状态: [正常] [封禁]                        │
│                                            │
│  时间信息                                   │
│  最后登录: 2026-06-15 14:30:00             │
│  注册时间: 2026-06-10 08:00:00             │
│  更新时间: 2026-06-15 14:30:00             │
│  注销时间: -  (若有则显示，红字)            │
└────────────────────────────────────────────┘
```

详情只读，所有写操作走列表的操作列按钮触发的对话框。

### 操作对话框

**封禁/解封** - el-message-box 二次确认："确定封禁用户 '张三'？该用户将立即下线。"

**重置资料** - el-dialog 表单：

```
┌─ 重置资料 ─────────────────┐
│ 昵称  [张三_______]         │
│ 头像URL [https://...] [上传]│ ← 走 admin-api/attachment/upload
│                            │
│      [取消]  [确定]         │
└────────────────────────────┘
```

**删除** - el-message-box 强提示："此操作将软删除用户，原 OpenId 将被改写以允许重注册，但历史数据保留。确定？"

### 路由与权限

后端菜单驱动，新增菜单 SQL 写到迁移脚本。前端 `v-permission` 控制按钮显隐：

```vue
<el-button v-permission="'system:app-user:detail'" @click="handleDetail">详情</el-button>
<el-button v-permission="'system:app-user:update'" @click="handleToggleStatus">封禁</el-button>
<el-button v-permission="'system:app-user:update'" @click="handleResetProfile">重置</el-button>
<el-button v-permission="'system:app-user:delete'" @click="handleDelete">删除</el-button>
```

## 错误处理与边界

### 业务异常码

新增枚举值（加到 `BusinessExceptionEnum`）：

| code | 常量 | 含义 |
|------|------|------|
| 40003 | PARAM_INVALID | 参数校验失败 |
| 40301 | USER_DEACTIVATED | 用户已注销（AOP 拦截） |
| 40302 | USER_DISABLED | 用户已被封禁 |
| 40901 | SMS_COOLDOWN | 验证码冷却中（60s） |
| 40902 | SMS_DAILY_EXCEEDED | 当日发送次数耗尽 |
| 40903 | PHONE_ALREADY_BOUND | 手机号已被其他用户占用 |
| 41001 | SMS_CODE_NOT_FOUND | 验证码不存在或已过期 |
| 41002 | SMS_CODE_ERROR | 验证码错误（带剩余次数提示） |
| 41003 | SMS_CODE_LOCKED | 错误次数耗尽，需重新发送 |
| 41004 | ATTACHMENT_TYPE_INVALID | 文件类型不允许 |
| 41005 | ATTACHMENT_SIZE_EXCEEDED | 文件大小超限 |
| 42900 | BIND_PROCESSING | 绑定处理中（分布式锁未抢到） |

### 并发与一致性

**场景 1：同手机号并发绑定**
- service 层 `bindPhone` 加分布式锁 `app:lock:bind-phone:{phone}`（5 秒 TTL）
- 抢不到锁返回 `BIND_PROCESSING`；拿到锁后二次校验 phone 未被占用

**场景 2：注销时正在调写操作**
- AOP `@AssertAppUserActive` 是最后一道防线，注销后所有写操作必失败

**场景 3：封禁时正在长连接（WebSocket）**
- 当前 app 端暂未用 WebSocket，本设计不涉及
- 但 `app_user_sessions:{userId}` 失效后，下一次 access_token 过期换 refresh 时会被拒

### 安全边界

**验证码相关**
- 服务端生成 6 位数字（`SecureRandom`），不用用户输入拼字符串
- Mock 实现的 `log.info` 在生产用 INFO 级别 + 占位符，避免日志注入
- 同手机号冷却 60s + 日 5 次 + 错 5 次锁码，三层防刷
- 双保险：`@RateLimiter(keyType = RequestAttribute, key = "appUserId", time = 60, count = 1)`

**头像上传**
- 文件类型白名单（`Content-Type` + magic number 双校验，不只信扩展名）
- 大小硬限 2 MB（Spring 配置 + controller 层 `MultipartFile.getSize()` 二次校验）
- 返回的 URL 不暴露内部存储路径（走 `SysAttachment` 现有 url 生成逻辑）

**手机号脱敏**
- 后端响应统一脱敏（自定义 `@JsonSerialize` serializer），避免遗漏
- 后台"重置资料"时不回显真实手机号

**OpenId 改写防注入**
- 改写格式硬编码 `<原值>#del_<epoch_second>`，不接受用户输入
- 若原 open_id 长度 + 后缀超过 64 字符（表定义上限），截断原值前缀（极小概率，加日志告警）

### 兼容性边界

**与现有 admin 端 `SysUser` 完全隔离**
- `app_user` 与 `sys_user` 是两张表，无外键关联
- 后台 admin 用户管理 app 用户是"管理"关系，不是"同账号"关系
- 不做 `app_user.id` ↔ `sys_user.id` 映射

**与现有 `SysAttachment` 复用边界**
- `sys_attachment.biz_type` 新增枚举 `APP_AVATAR`（与现有 `USER_AVATAR` 区分）
- `AppAttachmentController`（app）走 `/app-api/attachment`，`SysAttachmentController`（admin）走 `/admin-api/system/attachment`，端点物理分离
- `AppAttachmentController.upload` 内部调 `sysAttachmentService.upload(file, BizType.APP_AVATAR, appUserId)`，复用 service

**与现有 JWT 鉴权链**
- App 端 access_token 不入黑名单：注销/封禁靠 Redis session 失效
- 风险窗口：access_token 自然过期前（最长 2 小时），恶意持有者仍能调只读接口（如 `/profile` GET），但无法调写接口（被 AOP 拦）
- 这是有意识的权衡

### 国际化与日志

- 所有错误码走现有 i18n 体系
- 操作日志：后台 `封禁/解封/重置/删除` 走 `@OperationLog(title="App用户管理", businessType=...)`
- app 端 `绑定手机号/注销` 也走 `@OperationLog`，记录 `appUserId`、phone 脱敏值
- 短信验证码日志 INFO 级别（含 phone 脱敏），失败/超限 WARN 级别

## uni-app 工程结构

### monorepo 集成

```
forge-admin/
├── apps/
│   ├── forge-server/         (现有)
│   ├── forge-web/            (现有 admin 前端，pkg: forge-admin-frontend)
│   └── forge-miniapp/        (新增，pkg: forge-miniapp)
├── pnpm-workspace.yaml       (新增)
└── package.json              (扩展 scripts)
```

新增 `pnpm-workspace.yaml`：

```yaml
packages:
  - 'apps/*'
```

根 `package.json` scripts 扩展：

```json
{
  "scripts": {
    "dev:miniapp": "pnpm --filter forge-miniapp dev:mp-weixin",
    "build:miniapp": "pnpm --filter forge-miniapp build:mp-weixin",
    "dev:miniapp:h5": "pnpm --filter forge-miniapp dev:h5",
    "build:miniapp:h5": "pnpm --filter forge-miniapp build:h5"
  }
}
```

### `apps/forge-miniapp/` 目录结构

```
apps/forge-miniapp/
├── package.json
├── tsconfig.json
├── vite.config.ts
├── uni.config.ts             ← uni-app 编译配置
├── env.d.ts
├── .env                      ← VITE_API_BASE_URL 等
├── .env.development
├── .env.production
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── manifest.json         ← uni-app 配置（appid、权限等）
│   ├── pages.json            ← 页面路由、tabBar
│   ├── api/
│   │   ├── request.ts        ← 基于 uni.request 的封装（自动 token 刷新）
│   │   ├── auth.ts           ← wx-login / refresh / logout
│   │   ├── user.ts           ← profile / sms-code / bind-phone / deactivate
│   │   └── attachment.ts     ← uploadAvatar (uni.uploadFile)
│   ├── stores/
│   │   ├── index.ts          ← Pinia 安装
│   │   └── user.ts           ← userInfo / tokens（持久化到 uni.storage）
│   ├── pages/
│   │   ├── login/index.vue
│   │   ├── profile/index.vue
│   │   ├── profile/edit.vue
│   │   ├── profile/phone.vue
│   │   └── profile/deactivate.vue
│   ├── components/
│   │   ├── PhoneInput.vue
│   │   ├── SmsCodeInput.vue  ← 倒计时按钮
│   │   └── AvatarUploader.vue ← 选择图片 + 上传
│   ├── composables/
│   │   ├── useSmsCode.ts
│   │   └── useAuth.ts
│   ├── utils/
│   │   ├── storage.ts
│   │   └── phone.ts
│   └── types/
│       └── api.ts            ← 与后端 DTO 对齐
└── README.md
```

### 技术栈

| 维度 | 选择 | 理由 |
|------|------|------|
| Vue | 3.4 + `<script setup>` + TS | 与 admin 一致 |
| 构建 | Vite + `@dcloudio/vite-plugin-uni` | uni-app 官方 Vue 3 推荐 |
| 状态 | Pinia + `pinia-plugin-persistedstate` | 与 admin 一致 |
| 请求 | 自封装 `uni.request` | 小程序无 axios，必须用 `uni.request`/`uni.uploadFile` |
| 路由 | uni-app `pages.json` | 体系限制，无 vue-router |
| UI | uview-plus | 社区成熟、组件丰富 |
| 目标平台 | 微信小程序优先 + H5 兼容 | `wx.login` 仅小程序有 |

### 请求库设计要点

```typescript
// api/request.ts（伪代码）
const BASE_URL = import.meta.env.VITE_API_BASE_URL  // http://localhost:8181/app-api

export function request<T>({ url, method, data, header }) {
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: { ...authHeader(), ...header },
      success: async (res) => {
        if (res.statusCode === 401) {
          const ok = await tryRefreshToken()
          if (ok) return resolve(request({ url, method, data, header }))
          return reject(rejectAndRedirect())
        }
        const body = res.data as ApiResult<T>
        if (body.code === 200) return resolve(body.data)
        uni.showToast({ title: body.message, icon: 'none' })
        reject(new Error(body.message))
      },
      fail: reject
    })
  })
}
```

## 小程序端页面与交互

### 启动与登录态判断

```
App.onLaunch
   ├── 读 uni.storage 的 accessToken
   ├── 调 GET /user/profile 验证有效性
   │      ├── 200 → 进入 profile/index
   │      ├── 401 → 尝试 refresh
   │      │          ├── 成功 → 重发请求 → profile/index
   │      │          └── 失败 → 清 storage → 跳 pages/login
   │      └── 网络错误 → 显示重试页
   │
   └── pages/login/index（首次/未登录）
          ├── wx.login() 拿 code
          ├── POST /auth/wx-login { code }
          ├── 成功 → 存 token/userInfo → 跳 profile/index
          └── 失败 → 显示错误 + 重试按钮
```

`login` 页不放进 tabBar，未登录用户通过 `uni.reLaunch('/pages/login/index')` 进入。

### tabBar 结构

```json
{
  "tabBar": {
    "list": [
      { "pagePath": "pages/profile/index", "text": "我的" }
    ]
  }
}
```

本 spec 范围内只产出"我的"tab。其他业务 tab 由后续特性扩展。

### 页面 1：登录页 `pages/login/index.vue`

```
┌─────────────────────────────┐
│      [Logo / 应用名]         │
│      欢迎使用 XXX            │
│                             │
│   ┌─────────────────────┐   │
│   │  微信一键登录        │   │
│   └─────────────────────┘   │
│                             │
│   登录即代表同意《用户协议》  │
│   和《隐私政策》             │
└─────────────────────────────┘
```

交互：
- 点"微信一键登录"调 `wx.login()` 拿 code → POST `/auth/wx-login`
- 失败 toast + 留在登录页
- 成功 → `pinia.user.setTokens()` + `pinia.user.setUserInfo()` → `uni.reLaunch('/pages/profile/index')`

### 页面 2：个人中心 `pages/profile/index.vue`（tabBar）

```
┌─────────────────────────────┐
│ ┌─┐                  ⚙ 设置 │
│ │🧑│ 张三                   │
│ └─┘ 138****0000 (已验证✓)   │
├─────────────────────────────┤
│ 📝 编辑资料              >  │
│ 📱 绑定/换绑手机号       >  │
│ ⚠ 注销账号              >  │
├─────────────────────────────┤
│ 🚪 退出登录                 │
└─────────────────────────────┘
```

说明：
- 头像 + 昵称从 store 取，store 从 `/user/profile` 拉取
- 手机号脱敏显示；`phoneVerified=0` 时显示"未验证"灰字
- "编辑资料"跳 `pages/profile/edit`
- "绑定/换绑"跳 `pages/profile/phone`（如已绑定，按钮文案显示当前手机号）
- "注销账号"跳 `pages/profile/deactivate`（红字）
- "退出登录"调 `POST /auth/logout` → 清 storage → 跳 login

### 页面 3：编辑资料 `pages/profile/edit.vue`

```
┌─────────────────────────────┐
│ ← 编辑资料                  │
├─────────────────────────────┤
│ 头像                         │
│   ┌────┐                    │
│   │ 🧑 │ [修改]             │
│   └────┘                    │
│ 昵称                         │
│   [张三____________]         │
│                             │
│          [保存]              │
└─────────────────────────────┘
```

**头像修改流程：**
```
点 [修改]
  ├── uni.chooseMedia({ count:1, mediaType:['image'], sizeType:['compressed'] })
  │   （微信小程序原生 API，会触发授权）
  ├── 校验：uni.getFileInfo → ≤ 2MB
  ├── uni.uploadFile({
  │     url: BASE_URL + '/attachment/upload',
  │     filePath,
  │     name: 'file',
  │     formData: { bizType: 'APP_AVATAR' },
  │     header: { Authorization: 'Bearer ' + token }
  │   })
  ├── 拿到 { url, attachmentId }
  ├── PUT /user/profile { avatar: url }
  └── 更新本地 store，回到 edit 页显示新头像
```

**头像选择走原生 `uni.chooseMedia` + `button open-type="chooseAvatar"`**：拿得到微信头像原图；uview-plus 的 `u-upload` 走通用 chooseImage 拿不到微信头像。

### 页面 4：绑定/换绑手机号 `pages/profile/phone.vue`

```
┌─────────────────────────────┐
│ ← 绑定手机号                │
├─────────────────────────────┤
│ 手机号                       │
│ [+86 ▼] [138 0013 8000_]    │
│                             │
│ 验证码                       │
│ [______] [获取验证码] (60s) │
│                             │
│          [提交]              │
└─────────────────────────────┘
```

倒计时组件 `SmsCodeInput.vue`：
- `useSmsCode` composable 管理 `countdown` 状态
- 点"获取验证码" → POST `/user/sms-code` → 成功后 `setInterval` 倒计时 60s
- 倒计时中按钮置灰，显示 `XXs 后重发`

已绑定时进入此页：
- 标题改"换绑手机号"
- 顶部显示"当前手机号：138****0000，换绑后将无法用于原账号登录"
- 同样走"新手机号 + 验证码"流程，调 POST `/user/bind-phone`

### 页面 5：注销确认 `pages/profile/deactivate.vue`

```
┌─────────────────────────────┐
│ ← 注销账号                  │
├─────────────────────────────┤
│ ⚠ 注销前请注意               │
│                             │
│ • 注销后您的账号将无法登录    │
│ • 历史数据将保留但与您脱钩    │
│ • 同一微信可重新注册新账号    │
│ • 此操作不可撤销              │
│                             │
│ 我已知晓风险，确认注销：      │
│ [我同意上述提示 □]            │
│                             │
│ [二次输入"注销我的账号"]      │
│ [_____________________]     │
│                             │
│      [取消]  [确认注销]      │
└─────────────────────────────┘
```

**双重防误操作：**
1. 勾选"我同意上述提示"复选框
2. 文本框输入"注销我的账号"（与提示文案完全匹配）
3. 两项都满足后"确认注销"按钮才可点

**点确认注销：**
- DELETE `/user/deactivate` { confirm: true }
- 成功 → 清本地 storage → `uni.reLaunch('/pages/login/index')`
- 失败 → toast 错误信息，留在页面

### 小程序端错误处理

| 场景 | 处理 |
|------|------|
| 网络断开 | `uni.showToast` "网络异常，请稍后重试" |
| 401 + refresh 失败 | 清 storage → `uni.reLaunch('/pages/login/index')` |
| 业务错误码（如 SMS_CODE_ERROR） | 后端 message 直接 toast |
| 验证码错误次数耗尽 | toast + 重置倒计时按钮 |
| 上传失败 | toast + 保留原头像 |
| 注销失败 | toast + 留在页面（不动 storage） |

### 类型共享

`apps/forge-miniapp/src/types/api.ts` 与后端 DTO 一一对应，**手动维护**（不做跨工程类型导出，避免编译复杂度）。后续若加 monorepo 内部包依赖（如 `@forge/api-types`）再重构。

## 测试策略

### 后端测试（必做）

**1. Service 层集成测试（@SpringBootTest + 嵌入式 Redis）**

| 测试类 | 覆盖点 |
|--------|--------|
| `AppUserServiceImplTest` | bindPhone 成功/手机号已绑/验证码错；deactivate 软删除 + open_id 改写 + Redis session 清理 |
| `SmsCodeManagerTest` | 冷却命中/日限命中/错误次数自增到 5 后失效 |
| `MockSmsServiceImplTest` | 接口契约：send 调用后 Redis 有 code |
| `AppAuthServiceImplTest` | 注销后再登录创建新记录（验证 open_id 改写生效） |

**2. Controller 层 MockMvc 测试（关键路径）**

| 端点 | 必测 |
|------|------|
| `POST /app-api/user/sms-code` | 未登录 401 / 冷却中 40901 / 成功 200 |
| `POST /app-api/user/bind-phone` | 验证码错 41002 / 重复绑定 40903 / 成功 |
| `DELETE /app-api/user/deactivate` | confirm=false 400 / 成功后 open_id 改写 |
| `PUT /admin-api/system/app-user/{id}/status` | 权限缺失 403 / 封禁后 session 失效 |
| `POST /app-api/attachment/upload` | 文件超大 41005 / 类型错 41004 |

**3. AOP 切面测试**

`@AssertAppUserActive` 切面：
- 已注销用户调写接口 → 40301
- 正常用户调写接口 → 通过
- 只读接口（如 GET /profile）不应用注解 → 通过

### 后端测试（可选）

- 并发场景：`CompletableFuture.allOf` 模拟 2 个 bind-phone 并发，验证分布式锁
- Redis Key TTL：用 `RedisCommands` 直接查 TTL 验证

### 前端测试（必做）

**1. API 调用测试**（沿用 `auth.test.ts` 模式）

- `api/system/app-user.test.ts`：`list` / `updateStatus` / `resetProfile` 调用断言
- `api/attachment.test.ts`：`upload` 调用断言 FormData 含 file

**2. 组件测试**（关键交互）

| 组件 | 必测 |
|------|------|
| `app-user/index.vue` | 列表渲染、搜索触发 list、操作按钮按 `v-permission` 显隐 |
| `app-user/detail.vue` | 已验证/未验证手机号 tag 显示、注销时间显示 |
| 重置资料对话框 | 提交触发 resetProfile |

### 集成验证（手测清单）

```
[ ] app 端发送验证码，Mock 日志打印验证码
[ ] app 端绑定手机号成功，profile.phone_verified=1
[ ] app 端 60s 内重复发码返回 40901
[ ] app 端注销后立即调写接口返回 40301
[ ] app 端注销后用同微信重新登录创建新记录（不同 userId）
[ ] app 端上传 jpg 头像成功，PUT profile 后 avatar 字段更新
[ ] app 端上传 5MB 文件返回 41005
[ ] admin 端列表能查到 app 用户，可筛选手机号/状态
[ ] admin 端封禁后 app 端 access_token 还能用 2 小时（只读），但写操作 40301
[ ] admin 端重置资料后 app 端 GET profile 看到新值
[ ] admin 端删除（软删）后，原微信可重注册
[ ] 权限缺失时按钮不可见，强制调接口 403
[ ] 小程序端 wx.login + 后端 wx-login 闭环，未登录拦截到 login 页
[ ] 小程序端 401 后自动 refresh 一次，失败再跳 login
[ ] 小程序端选头像走 chooseAvatar，能拿到微信原图
[ ] 小程序端发码倒计时显示正确，60s 后恢复
[ ] 小程序端注销双重确认（复选框 + 输入"注销我的账号"）才放行
```

### 测试覆盖率目标

不强求指标。重点：service 关键路径 + AOP 切面 + Controller 错误码全覆盖；前端 API 调用全测，组件测关键交互。

## 验收标准

实现完成时需满足：

1. 上述手测清单全部通过
2. 后端必测类全部通过
3. 前端 API 调用测试全部通过
4. 数据库迁移脚本在干净环境执行无误
5. `application.yml` 新增配置项有默认值，未配置不报错
6. `MockSmsServiceImpl` 在控制台能清楚看到验证码（方便联调）
7. 小程序端在微信开发者工具中跑通完整流程（登录→绑定手机→改头像→注销→重注册）

## 风险与未决事项

| 风险 | 缓解措施 |
|------|----------|
| `wx.login` 在 H5 端不存在 | H5 端登录走模拟接口或后续接入；本 spec 仅保证微信小程序闭环 |
| uview-plus 与 uni-app 最新版兼容性 | 锁定版本号；遇到组件兼容问题降级到原生组件 |
| `open_id` 改写后超 64 字符 | 极小概率，截断原值前缀 + 日志告警 |
| 微信头像授权政策变化 | `chooseAvatar` 是当前推荐方式，必要时切换为通用 chooseImage |
| Mock 短信在生产误用 | `@ConditionalOnMissingBean` 默认激活；接入真实短信服务时新增 `@Service` 实现自动覆盖，无需改代码 |
