# 二级等保技术改造合规文档

> 本文档记录 forge-admin 项目为满足 GB/T 22239-2019 二级等保要求而实施的技术改造措施。

## 1. 改造概述

### 1.1 项目信息
- **项目名称**: forge-admin 企业级后台管理系统
- **等保级别**: 二级
- **标准依据**: GB/T 22239-2019《信息安全技术 网络安全等级保护基本要求》
- **改造范围**: 技术改造 + 技术文档（不含管理文档）

### 1.2 改造周期
- **开始日期**: 2026-06-19
- **完成日期**: 2026-06-19
- **改造阶段**: Phase 1（核心安全功能）

## 2. 安全层面覆盖

### 2.1 安全物理环境（物理层面由部署环境保障）
- 服务器机房物理访问控制（由云服务商保障）
- 电力供应与防火防水（由云服务商保障）

### 2.2 安全通信网络 ✅
| 控制点 | 实现措施 | 状态 |
|--------|----------|------|
| 通信传输加密 | MySQL SSL 连接、HTTPS（反向代理） | ✅ |
| 网络边界防护 | 防火墙规则、端口限制（部署配置） | 待配置 |

### 2.3 安全区域边界 ✅
| 控制点 | 实现措施 | 状态 |
|--------|----------|------|
| 边界访问控制 | CORS 白名单、API 白名单 | ✅ |
| 入侵防范 | XSS 过滤器、文件上传校验 | ✅ |

### 2.4 安全计算环境 ✅
| 控制点 | 实现措施 | 状态 |
|--------|----------|------|
| 身份鉴别 | 用户名密码 + 验证码 + JWT Token | ✅ |
| 密码复杂度 | 8-32位、大小写+数字+特殊字符 | ✅ |
| 密码有效期 | 90天强制更换 | ✅ |
| 登录失败处理 | 5次失败锁定15分钟 | ✅ |
| 访问控制 | RBAC + 数据权限 | ✅ |
| 安全审计 | 登录日志 + 操作日志（敏感字段脱敏） | ✅ |
| 敏感数据加密 | AES-256-GCM 加密存储 | ✅ |
| 配置文件加密 | jasypt ENC() 加密 | ✅ |

### 2.5 安全管理中心 ✅
| 控制点 | 实现措施 | 状态 |
|--------|----------|------|
| 系统管理 | 用户管理、角色管理、菜单管理 | ✅ |
| 审计管理 | 登录日志查询、操作日志查询 | ✅ |

## 3. 技术措施详细说明

### 3.1 身份鉴别增强

#### 3.1.1 密码安全
```yaml
forge.security.password:
  min-length: 8              # 最小长度
  max-length: 32             # 最大长度
  require-uppercase: true    # 大写字母
  require-lowercase: true    # 小写字母
  require-digit: true        # 数字
  require-special: true      # 特殊字符
  history-size: 5            # 历史密码不可重复
  expire-days: 90            # 有效期90天
  bcrypt-strength: 12        # BCrypt强度
```

#### 3.1.2 登录安全
```yaml
forge.security.login:
  max-fail-count: 5          # 失败阈值
  lock-minutes: 15           # 锁定时长
  single-session: true       # 单点登录
```

#### 3.1.3 验证码
```yaml
forge.security.captcha:
  enabled: true              # 强制启用
  length: 4                  # 4位字符
  expire-seconds: 300        # 5分钟有效
```

### 3.2 数据安全

#### 3.2.1 敏感字段加密
- **加密算法**: AES-256-GCM
- **密文格式**: `ENCv1:{base64(iv || ciphertext || tag)}`
- **加密字段**: phone, email

#### 3.2.2 配置文件加密
- **工具**: jasypt-spring-boot-starter
- **格式**: `ENC(加密内容)`
- **敏感配置**: DB_PASSWORD, JWT_SECRET, APP_AES_KEY

### 3.3 应用安全

#### 3.3.1 XSS 防护
- XssFilter: 全局 XSS 过滤
- @XssIgnore: 白名单注解

#### 3.3.2 安全响应头
| 响应头 | 值 |
|--------|-----|
| X-Frame-Options | SAMEORIGIN |
| X-Content-Type-Options | nosniff |
| X-XSS-Protection | 1; mode=block |
| Content-Security-Policy | default-src 'self' |
| Strict-Transport-Security | max-age=31536000 |

#### 3.3.3 文件上传安全
- 文件大小限制: 10MB
- 扩展名白名单校验
- Magic Number 文件类型校验

### 3.4 安全审计

#### 3.4.1 登录日志
- 记录登录成功/失败
- 包含 IP、用户名、时间、结果

#### 3.4.2 操作日志
- 记录关键操作
- 敏感字段脱敏处理

## 4. 数据库变更

### 4.1 sys_user 扩展字段
| 字段 | 类型 | 说明 |
|------|------|------|
| password_update_time | datetime | 密码最后修改时间 |
| first_login | int | 首次登录标记（需强制改密） |
| password_error_count | int | 连续登录失败次数 |
| lock_time | datetime | 账户锁定截止时间 |
| phone_suffix | varchar(4) | 手机号后4位（明文） |

### 4.2 sys_user_password_history 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| user_id | bigint | 用户ID |
| password | varchar(255) | 密码哈希 |
| create_time | datetime | 记录时间 |

## 5. 环境配置要求

### 5.1 必须配置的环境变量
```bash
# 数据库
DB_HOST=数据库地址
DB_PORT=3306
DB_NAME=forge_admin
DB_USERNAME=应用账户
DB_PASSWORD=加密或环境变量

# Redis
REDIS_HOST=Redis地址
REDIS_PORT=6379
REDIS_PASSWORD=Redis密码

# 安全密钥
APP_AES_KEY=32字节AES密钥
JWT_SECRET=256位JWT密钥
JASYPT_PASSWORD=jasypt解密密钥
```

### 5.2 生产环境启动命令
```bash
java -jar forge-server.jar \
  -Dspring.profiles.active=prod \
  -Djasypt.encryptor.password=${JASYPT_PASSWORD} \
  -DAPP_AES_KEY=${AES_KEY} \
  -DJWT_SECRET=${JWT_SECRET}
```

## 6. 测评配合说明

### 6.1 需提供的材料
1. 本技术改造文档
2. 部署检查清单（DEPLOYMENT-CHECKLIST.md）
3. 应用架构说明文档
4. 数据库表结构说明
5. API 安全机制说明

### 6.2 测评现场演示要点
1. 密码复杂度校验演示
2. 登录失败锁定演示
3. 验证码功能演示
4. 敏感字段加密验证
5. 安全审计日志查看
6. XSS 过滤验证
7. 文件上传安全校验验证

---

**文档版本**: v1.0
**编写日期**: 2026-06-19
**编写人**: Claude Code