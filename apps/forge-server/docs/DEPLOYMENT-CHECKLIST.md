# 二级等保部署检查清单

> 本清单用于生产环境部署前的安全检查，确保系统符合 GB/T 22239-2019 二级等保要求。

## 1. 配置安全

### 1.1 敏感配置加密 ✅
- [ ] `APP_AES_KEY` - AES-256 加密密钥（32字节，通过环境变量或启动参数注入）
- [ ] `JWT_SECRET` - JWT 签名密钥（至少256位）
- [ ] `DB_PASSWORD` - 数据库密码（使用 ENC() 加密或环境变量）
- [ ] `REDIS_PASSWORD` - Redis 密码（如有）
- [ ] `JASYPT_PASSWORD` - jasypt 配置解密密钥

### 1.2 环境变量注入方式
```bash
# 推荐方式：启动参数注入
java -jar forge-server.jar \
  -Dspring.profiles.active=prod \
  -Djasypt.encryptor.password=${JASYPT_PASSWORD} \
  -DAPP_AES_KEY=${AES_KEY} \
  -DJWT_SECRET=${JWT_SECRET}
```

## 2. 数据库安全

### 2.1 连接安全 ✅
- [ ] MySQL 启用 SSL 连接（`useSSL=true`）
- [ ] 数据库账户使用最小权限原则
- [ ] 敏感字段已启用加密存储（phone, email）

### 2.2 密码历史表
```sql
-- 确认密码历史表已创建
SELECT COUNT(*) FROM sys_user_password_history;
```

## 3. Redis 安全

### 3.1 访问控制 ✅
- [ ] Redis 启用密码认证
- [ ] Redis 绑定内网 IP，禁止公网访问
- [ ] Redis 禁用危险命令（FLUSHALL, FLUSHDB, KEYS）

## 4. 应用安全

### 4.1 密码策略 ✅
- [ ] 密码长度 8-32 位
- [ ] 必须包含：大写、小写、数字、特殊字符
- [ ] 密码历史 5 条不可重复
- [ ] 密码有效期 90 天
- [ ] 首次登录强制改密

### 4.2 登录安全 ✅
- [ ] 登录失败 5 次锁定 15 分钟
- [ ] 验证码强制启用
- [ ] 单点登录模式（踢掉旧会话）

### 4.3 会话安全 ✅
- [ ] JWT Token 有效期 2 小时
- [ ] Refresh Token 有效期 7 天
- [ ] Token 存储在 HttpOnly Cookie

## 5. 文件上传安全

### 5.1 文件校验 ✅
- [ ] 文件大小限制 10MB
- [ ] 扩展名白名单校验
- [ ] Magic Number 文件类型校验

## 6. 安全响应头

### 6.1 HTTP 安全头（需在反向代理或应用层配置）
```nginx
# Nginx 配置示例
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Content-Security-Policy "default-src 'self'" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

## 7. 日志审计

### 7.1 登录日志 ✅
- [ ] 登录成功/失败日志记录
- [ ] 包含 IP、用户名、时间、结果

### 7.2 操作日志 ✅
- [ ] 关键操作审计日志
- [ ] 敏感字段脱敏处理

## 8. 网络安全

### 8.1 端口暴露
- [ ] 仅开放必要端口（应用端口、管理端口）
- [ ] 数据库、Redis 禁止公网访问

### 8.2 防火墙规则
- [ ] 配置白名单访问策略
- [ ] 禁止非授权 IP 访问管理接口

## 9. 运维安全

### 9.1 定期检查
- [ ] 每 90 天检查密码有效期
- [ ] 每季度审计登录日志异常
- [ ] 每年进行渗透测试

### 9.2 备份策略
- [ ] 数据库定期备份
- [ ] 备份数据加密存储
- [ ] 异地备份

## 10. 检查确认

| 检查项 | 状态 | 检查人 | 检查日期 |
|--------|------|--------|----------|
| 配置加密 | | | |
| 数据库 SSL | | | |
| Redis 认证 | | |
| 密码策略 | | | |
| 登录锁定 | | | |
| 验证码启用 | | | |
| 文件校验 | | | |
| 安全响应头 | | | |
| 日志审计 | | | |

---

**注意：** 本清单为技术检查项，不包含管理制度文档。管理制度文档需由公司层面另行准备。