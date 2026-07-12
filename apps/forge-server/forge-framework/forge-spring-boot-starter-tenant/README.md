# forge-spring-boot-starter-tenant

多租户框架 starter。详细设计见 `docs/superpowers/specs/2026-07-11-multi-tenant-design.md`。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-spring-boot-starter-tenant</artifactId>
</dependency>
```

### 2. 开启/关闭

```yaml
forge:
  tenant:
    enable: true  # false 时所有多租户能力关闭
```

### 3. 业务实体继承 TenantBaseDO

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class SysNotice extends TenantBaseDO {
    @TableId private Long id;
    private String title;
    // ...
}
```

### 4. 跳过租户过滤

```java
@TenantIgnore
public List<SysTenant> listAllTenants() { ... }
```

## 组件

- `TenantContextHolder` — ThreadLocal，存储当前请求的 tenantId
- `TenantDatabaseInterceptor` — MyBatis Plus TenantLineHandler
- `TenantContextWebFilter` — 解析请求头
- `TenantSecurityWebFilter` — 越权与合法性校验
- `TenantIgnoreAspect` — @TenantIgnore 注解支持
- `TenantRedisCacheManager` — 缓存 key 加 tenantId 前缀
