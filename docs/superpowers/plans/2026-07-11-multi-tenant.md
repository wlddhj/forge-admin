# 多租户架构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 forge-admin 引入 B2B SaaS 多租户能力，包含租户 CRUD、套餐管理、平台超管、按租户隔离业务数据、配置开关可关闭。

**Architecture:** 基于 MyBatis Plus 官方 `TenantLineInnerInterceptor` 实现 SQL 层自动注入 `WHERE tenant_id = ?`；通过自定义 `TenantRedisCacheManager` 在缓存 key 前加 `tenantId:`；用两个 Web 过滤器（ContextFilter + SecurityFilter）实现请求头 → 上下文 → 越权/合法性校验。参考实现对齐 shi9-boot `shi9-spring-boot-starter-biz-tenant`。

**Tech Stack:** Spring Boot 3.2 + MyBatis Plus 3.5.7 + JSQLParser + TransmittableThreadLocal + Flyway（不开，手动 SQL） + Redis + JWT。

---

## 全局约束（每个任务都要遵守）

1. **Java 版本**：21（`<java.version>21</java.version>`）
2. **包基类**：`com.forge`
3. **新模块包名**：`com.forge.framework.tenant`
4. **Git 提交格式**：`<type>(<scope>): <subject>`（中文，不带 `Co-Authored-By`）
5. **数据库**：MySQL `forge_admin`，localhost:3306，root/password
6. **Flyway 状态**：本项目**未启用** Flyway，所有 SQL 必须手动执行
7. **不删除字段**：所有现有表加 `tenant_id` 列时保留原有结构，向后兼容
8. **配置开关**：`forge.tenant.enable=false` 时，所有多租户能力必须可关闭
9. **测试框架**：JUnit 5 + Mockito（项目已有），Service 层用 `@MockBean`
10. **不写 emoji**，除非用户明确要求

---

## 文件结构总览

### 新增

```
forge-framework/forge-spring-boot-starter-tenant/                  # 新模块
├── pom.xml
├── src/main/java/com/forge/framework/tenant/
│   ├── config/TenantProperties.java
│   ├── config/TenantAutoConfiguration.java
│   ├── core/context/TenantContextHolder.java
│   ├── core/web/TenantContextWebFilter.java
│   ├── core/web/TenantSecurityWebFilter.java
│   ├── core/db/TenantDatabaseInterceptor.java
│   ├── core/db/TenantBaseDO.java
│   ├── core/aop/TenantIgnore.java
│   ├── core/aop/TenantIgnoreAspect.java
│   ├── core/job/TenantJob.java
│   ├── core/job/TenantJobAspect.java
│   ├── core/redis/TenantRedisCacheManager.java
│   ├── core/api/TenantApi.java
│   ├── core/service/TenantFrameworkService.java
│   ├── core/service/TenantFrameworkServiceImpl.java
│   └── core/holder/TenantContextHolder.java                       # alias
└── src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 修改

```
forge-framework/forge-spring-boot-starter-redis/src/main/java/com/forge/framework/redis/config/CacheConfig.java
forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/SysUser.java
forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/auth/...
forge-web/src/views/login/index.vue
forge-web/src/utils/request.ts
forge-web/src/stores/user.ts                                       # 加 tenantId
apps/forge-server/pom.xml                                          # 注册新 starter
apps/forge-server/forge-framework/pom.xml                         # 注册新子模块
apps/forge-server/docs/manual-migrations/                          # 新增 SQL 目录
```

---

## Phase 1：Starter 骨架与配置

### Task 1：创建 forge-spring-boot-starter-tenant 模块骨架

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/pom.xml`
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/config/TenantProperties.java`
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/context/TenantContextHolder.java`
- Modify: `apps/forge-server/forge-framework/pom.xml`（添加 `<module>forge-spring-boot-starter-tenant</module>`）
- Modify: `apps/forge-server/forge-framework/forge-spring-boot-starter-mybatis/pom.xml`（参考依赖格式，无需改内容）

- [ ] **Step 1: 在 framework/pom.xml 中添加子模块**

编辑 `apps/forge-server/forge-framework/pom.xml`：

```xml
<modules>
    <module>forge-common</module>
    <module>forge-spring-boot-starter-mybatis</module>
    <module>forge-spring-boot-starter-redis</module>
    <module>forge-spring-boot-starter-security</module>
    <module>forge-spring-boot-starter-web</module>
    <module>forge-spring-boot-starter-tenant</module>     <!-- 新增 -->
</modules>
```

- [ ] **Step 2: 创建 starter pom.xml**

创建 `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>forge-framework</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>forge-spring-boot-starter-tenant</artifactId>
    <name>forge-spring-boot-starter-tenant</name>
    <description>多租户框架 - 基于 MyBatis Plus TenantLineInnerInterceptor</description>

    <dependencies>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>transmittable-thread-local</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
</project>
```

注意：transmittable-thread-local 版本在 forge-dependencies BOM 中确认；如未声明则改为 `${alibaba.transmittable-thread-local.version}` 占位由 BOM 提供。

- [ ] **Step 3: 创建 TenantProperties**

创建 `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/config/TenantProperties.java`：

```java
package com.forge.framework.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "forge.tenant")
public class TenantProperties {

    /**
     * 是否开启多租户（默认 true）
     */
    private Boolean enable = true;

    /**
     * 请求头名称（默认 X-Tenant-Id）
     */
    private String header = "X-Tenant-Id";

    /**
     * 忽略租户校验的 URL（白名单）
     */
    private Set<String> ignoreUrls = Collections.emptySet();

    /**
     * 跨租户共享的表（不注入 tenant_id）
     */
    private Set<String> ignoreTables = Collections.emptySet();

    /**
     * 跨租户共享的缓存（key 不加 tenantId 前缀）
     */
    private Set<String> ignoreCaches = Collections.emptySet();
}
```

- [ ] **Step 4: 创建 TenantContextHolder**

创建 `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/context/TenantContextHolder.java`：

```java
package com.forge.framework.tenant.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

public class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new TransmittableThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE = new TransmittableThreadLocal<>();

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static Long getRequiredTenantId() {
        Long t = TENANT_ID.get();
        if (t == null) {
            throw new NullPointerException("TenantContextHolder 不存在租户编号！");
        }
        return t;
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static boolean isIgnore() {
        return Boolean.TRUE.equals(IGNORE.get());
    }

    public static void setIgnore(Boolean ignore) {
        IGNORE.set(ignore);
    }

    public static void clear() {
        TENANT_ID.remove();
        IGNORE.remove();
    }
}
```

- [ ] **Step 5: 验证编译**

Run: `mvn clean compile -pl forge-framework/forge-spring-boot-starter-tenant -am`
Expected: BUILD SUCCESS，无编译错误。

- [ ] **Step 6: 提交**

```bash
git add apps/forge-server/forge-framework/pom.xml \
        apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/
git commit -m "feat(tenant): 创建 forge-spring-boot-starter-tenant 骨架模块"
```

---

### Task 2：注册 starter 到 forge-server 依赖

**Files:**
- Modify: `apps/forge-server/forge-server/pom.xml`（添加 starter 依赖）

- [ ] **Step 1: 在 forge-server/pom.xml 中添加依赖**

定位 `forge-server/pom.xml` 中其他 starter 依赖的位置（`forge-spring-boot-starter-mybatis`、`forge-spring-boot-starter-redis` 等），在其后添加：

```xml
<!-- 多租户 -->
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-spring-boot-starter-tenant</artifactId>
</dependency>
```

- [ ] **Step 2: 验证编译**

Run: `mvn clean compile -pl forge-server -am`
Expected: BUILD SUCCESS（注意此时 TenantContextHolder 还没有引用，不会报错；如果有依赖错误说明 pom 配置有误）

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-server/pom.xml
git commit -m "feat(tenant): forge-server 引入 tenant starter 依赖"
```

---

## Phase 2：SQL 拦截与 @TenantIgnore

### Task 3：实现 TenantDatabaseInterceptor

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/db/TenantDatabaseInterceptor.java`
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/db/TenantBaseDO.java`

- [ ] **Step 1: 创建 TenantBaseDO**

创建 `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/db/TenantBaseDO.java`：

```java
package com.forge.framework.tenant.core.db;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多租户业务实体基类
 *
 * 业务实体继承此类即可获得 tenantId 字段。
 * 注意：不要让 BaseDO 继承此类——因为 BaseDO 用于跨租户共享表（如菜单、字典）。
 * 业务实体应直接继承 TenantBaseDO。
 */
@Data
public abstract class TenantBaseDO {

    /**
     * 多租户编号
     */
    private Long tenantId;

    /**
     * 创建时间（与 BaseDO 对齐）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 更新人
     */
    private Long updateBy;

    /**
     * 逻辑删除标记
     */
    private Integer deleted;
}
```

- [ ] **Step 2: 创建 TenantDatabaseInterceptor**

创建 `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/db/TenantDatabaseInterceptor.java`：

```java
package com.forge.framework.tenant.core.db;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TenantDatabaseInterceptor implements TenantLineHandler {

    private static final String TENANT_ID_COLUMN = "tenant_id";

    private final Set<String> ignoreTables;

    public TenantDatabaseInterceptor(TenantProperties properties) {
        this.ignoreTables = new HashSet<>();
        properties.getIgnoreTables().forEach(t -> {
            ignoreTables.add(t.toLowerCase());
            ignoreTables.add(t.toUpperCase());
        });
        ignoreTables.add("DUAL");
    }

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContextHolder.getRequiredTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_ID_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return TenantContextHolder.isIgnore() || CollUtil.contains(ignoreTables, tableName);
    }

    @Override
    public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        // 平台超管调用 setIgnore(true) 时，跳过 INSERT 自动填 tenantId
        return TenantContextHolder.isIgnore();
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn clean compile -pl forge-framework/forge-spring-boot-starter-tenant -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/db/
git commit -m "feat(tenant): 实现 TenantDatabaseInterceptor 和 TenantBaseDO"
```

---

### Task 4：实现 @TenantIgnore 注解与 AOP

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/aop/TenantIgnore.java`
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/aop/TenantIgnoreAspect.java`

- [ ] **Step 1: 创建 TenantIgnore 注解**

```java
package com.forge.framework.tenant.core.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TenantIgnore {
}
```

- [ ] **Step 2: 创建 TenantIgnoreAspect**

```java
package com.forge.framework.tenant.core.aop;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Slf4j
@Aspect
public class TenantIgnoreAspect {

    @Around("@annotation(tenantIgnore) || @within(tenantIgnore)")
    public Object around(ProceedingJoinPoint joinPoint, TenantIgnore tenantIgnore) throws Throwable {
        Boolean previous = TenantContextHolder.isIgnore() ? Boolean.TRUE : null;
        TenantContextHolder.setIgnore(true);
        try {
            return joinPoint.proceed();
        } finally {
            if (previous == null) {
                TenantContextHolder.setIgnore(null);
            } else {
                TenantContextHolder.setIgnore(previous);
            }
        }
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl forge-framework/forge-spring-boot-starter-tenant -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/aop/
git commit -m "feat(tenant): 实现 @TenantIgnore 注解与 AOP 切面"
```

---

### Task 5：编写 TenantDatabaseInterceptor 单元测试

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/test/java/com/forge/framework/tenant/core/db/TenantDatabaseInterceptorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.forge.framework.tenant.core.db;

import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TenantDatabaseInterceptorTest {

    private TenantDatabaseInterceptor interceptor;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setIgnoreTables(Set.of("sys_menu", "sys_dict_type"));
        interceptor = new TenantDatabaseInterceptor(props);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getTenantId_returnsCurrentTenantId() {
        TenantContextHolder.setTenantId(42L);
        Expression expr = interceptor.getTenantId();
        assertNotNull(expr);
        assertEquals("42", expr.toString());
    }

    @Test
    void ignoreTable_returnsTrueForIgnoreTable() {
        assertTrue(interceptor.ignoreTable("sys_menu"));
        assertTrue(interceptor.ignoreTable("SYS_MENU"));  // 大小写不敏感
    }

    @Test
    void ignoreTable_returnsFalseForNormalTable() {
        assertFalse(interceptor.ignoreTable("sys_user"));
    }

    @Test
    void ignoreTable_returnsTrueWhenGlobalIgnore() {
        TenantContextHolder.setIgnore(true);
        assertTrue(interceptor.ignoreTable("sys_user"));
    }

    @Test
    void getRequiredTenantId_throwsWhenMissing() {
        assertThrows(NullPointerException.class, TenantContextHolder::getRequiredTenantId);
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn test -pl forge-framework/forge-spring-boot-starter-tenant`
Expected: 5 个测试全部通过。

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/test/
git commit -m "test(tenant): TenantDatabaseInterceptor 单元测试"
```

---

## Phase 3：Web 过滤器

### Task 6：实现 TenantContextWebFilter

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/web/TenantContextWebFilter.java`

- [ ] **Step 1: 创建过滤器**

```java
package com.forge.framework.tenant.core.web;

import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantContextWebFilter extends OncePerRequestFilter {

    private final String headerName;

    public TenantContextWebFilter(TenantProperties properties) {
        this.headerName = properties.getHeader();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(headerName);
        if (header != null && !header.isBlank()) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(header.trim()));
            } catch (NumberFormatException e) {
                // 不阻断请求，让后续 SecurityFilter 报错
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/web/TenantContextWebFilter.java
git commit -m "feat(tenant): 实现 TenantContextWebFilter 解析请求头"
```

---

### Task 7：实现 TenantApi 接口（system-biz 提供）

**Files:**
- Create: `apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/api/tenant/TenantApi.java`

- [ ] **Step 1: 在 system-api 中创建 RPC 接口**

```java
package com.forge.modules.system.api.tenant;

import java.util.List;

/**
 * 租户 RPC 接口（system-biz 实现，供其他模块调用）
 */
public interface TenantApi {

    /**
     * 校验租户是否合法
     *
     * @param tenantId 租户ID
     * @throws com.forge.common.exception.BusinessException 不合法时抛出
     */
    void validTenant(Long tenantId);

    /**
     * 获取租户名称
     */
    String getTenantName(Long tenantId);

    /**
     * 获取租户套餐的菜单ID列表
     */
    List<Long> getTenantPackageMenuIds(Long tenantId);
}
```

- [ ] **Step 2: 提交**

```bash
git add apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/api/tenant/
git commit -m "feat(tenant): system-api 新增 TenantApi 接口"
```

---

### Task 8：实现 TenantFrameworkService（starter 内）

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/service/TenantFrameworkService.java`
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/service/TenantFrameworkServiceImpl.java`

- [ ] **Step 1: 创建服务接口**

```java
package com.forge.framework.tenant.core.service;

public interface TenantFrameworkService {

    /**
     * 校验租户合法（存在 + 启用 + 未过期）
     *
     * @param tenantId 租户ID
     */
    void validTenant(Long tenantId);
}
```

- [ ] **Step 2: 创建服务实现（依赖 TenantApi，运行时由 system-biz 提供）**

```java
package com.forge.framework.tenant.core.service;

import com.forge.framework.tenant.core.api.TenantApi;

public class TenantFrameworkServiceImpl implements TenantFrameworkService {

    private final TenantApi tenantApi;

    public TenantFrameworkServiceImpl(TenantApi tenantApi) {
        this.tenantApi = tenantApi;
    }

    @Override
    public void validTenant(Long tenantId) {
        tenantApi.validTenant(tenantId);
    }
}
```

- [ ] **Step 3: 创建 TenantApi 占位接口（starter 内，避免循环依赖）**

`apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/api/TenantApi.java`：

```java
package com.forge.framework.tenant.core.api;

public interface TenantApi {
    void validTenant(Long tenantId);
}
```

注意：这是 starter 内部的小接口，system-api 中的 `TenantApi` 是同名的全功能接口。运行时通过 Spring Bean 名称区分，或合并。

**调整方案**：合并——直接用 system-api 的 `TenantApi`，从 starter 中删除 starter 内的占位接口。TenantFrameworkServiceImpl 依赖 `com.forge.modules.system.api.tenant.TenantApi`。

修改 starter 内 TenantFrameworkServiceImpl：

```java
package com.forge.framework.tenant.core.service;

import com.forge.modules.system.api.tenant.TenantApi;

public class TenantFrameworkServiceImpl implements TenantFrameworkService {

    private final TenantApi tenantApi;

    public TenantFrameworkServiceImpl(TenantApi tenantApi) {
        this.tenantApi = tenantApi;
    }

    @Override
    public void validTenant(Long tenantId) {
        tenantApi.validTenant(tenantId);
    }
}
```

starter pom.xml 添加 system-api 依赖：

```xml
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-module-system-api</artifactId>
</dependency>
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -pl forge-framework/forge-spring-boot-starter-tenant -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/service/ \
        apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/api/ \
        apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/pom.xml
git commit -m "feat(tenant): 实现 TenantFrameworkService 与 TenantApi 集成"
```

---

### Task 9：实现 TenantSecurityWebFilter

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/web/TenantSecurityWebFilter.java`

- [ ] **Step 1: 创建过滤器**

```java
package com.forge.framework.tenant.core.web;

import cn.hutool.core.collection.CollUtil;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.Result;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.UserContext;
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.framework.tenant.core.service.TenantFrameworkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class TenantSecurityWebFilter extends OncePerRequestFilter {

    private final TenantProperties tenantProperties;
    private final TenantFrameworkService tenantFrameworkService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantSecurityWebFilter(TenantProperties tenantProperties,
                                   TenantFrameworkService tenantFrameworkService) {
        this.tenantProperties = tenantProperties;
        this.tenantFrameworkService = tenantFrameworkService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long tenantId = TenantContextHolder.getTenantId();
        UserContext user = UserContext.get();

        // (a) 登录用户：校验越权
        if (user != null && !Boolean.TRUE.equals(user.getAccountType() != null && user.getAccountType() == 2)) {
            if (tenantId == null) {
                tenantId = user.getDeptId() == null ? null : extractTenantIdFromUser(user);
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                }
            } else if (!Objects.equals(extractTenantIdFromUser(user), tenantId)) {
                log.error("[tenant] 用户({}) 越权访问租户({}) URL({})",
                        user.getUserId(), tenantId, request.getRequestURI());
                writeError(response, ResultCode.FORBIDDEN.getCode(), "您无权访问该租户的数据");
                return;
            }
        }

        // (b) 非忽略 URL 必须有 tenantId
        boolean ignoreUrl = isIgnoreUrl(request);
        if (!ignoreUrl) {
            if (tenantId == null) {
                boolean isPlatformAdmin = user != null && user.getAccountType() != null && user.getAccountType() == 2;
                if (!isPlatformAdmin) {
                    log.error("[tenant] URL({}) 未传递租户编号", request.getRequestURI());
                    writeError(response, ResultCode.BAD_REQUEST.getCode(), "请求的租户标识未传递，请检查请求头 X-Tenant-Id");
                    return;
                }
            } else {
                try {
                    tenantFrameworkService.validTenant(tenantId);
                } catch (BusinessException e) {
                    writeError(response, ResultCode.FORBIDDEN.getCode(), e.getMessage());
                    return;
                }
            }
        } else {
            if (tenantId == null) {
                TenantContextHolder.setIgnore(true);
            }
        }

        chain.doFilter(request, response);
    }

    private Long extractTenantIdFromUser(UserContext user) {
        // LoginUser 需要新增 tenantId 字段；此处先用 placeholder
        // 实际实现依赖 UserContext 扩展
        return null;
    }

    private boolean isIgnoreUrl(HttpServletRequest request) {
        Set<String> ignoreUrls = tenantProperties.getIgnoreUrls();
        if (CollUtil.isEmpty(ignoreUrls)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (ignoreUrls.contains(uri)) {
            return true;
        }
        for (String pattern : ignoreUrls) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
```

注意：`extractTenantIdFromUser` 是占位实现。**后续任务 16** 会在 `UserContext` 中加 `tenantId` 字段并实现该方法。

- [ ] **Step 2: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/web/TenantSecurityWebFilter.java
git commit -m "feat(tenant): 实现 TenantSecurityWebFilter 越权与合法性校验"
```

---

### Task 10：编写 TenantSecurityWebFilter 单元测试

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/test/java/com/forge/framework/tenant/core/web/TenantSecurityWebFilterTest.java`

- [ ] **Step 1: 写测试**

```java
package com.forge.framework.tenant.core.web;

import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.framework.tenant.core.service.TenantFrameworkService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantSecurityWebFilterTest {

    private TenantSecurityWebFilter filter;
    private TenantFrameworkService frameworkService;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setIgnoreUrls(Set.of("/admin-api/auth/login"));
        frameworkService = mock(TenantFrameworkService.class);
        filter = new TenantSecurityWebFilter(props, frameworkService);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void ignoreUrl_withoutTenantId_callsChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin-api/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test
    void normalUrl_withoutTenantId_writes400() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin-api/system/user/list");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain, never()).doFilter(req, res);
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("租户标识未传递"));
    }

    @Test
    void normalUrl_withValidTenant_callsValidTenant() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin-api/system/user/list");
        req.addHeader("X-Tenant-Id", "1");
        TenantContextHolder.setTenantId(1L);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(frameworkService).validTenant(1L);
        verify(chain).doFilter(req, res);
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn test -pl forge-framework/forge-spring-boot-starter-tenant`
Expected: 3 个测试全部通过。

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/test/java/com/forge/framework/tenant/core/web/
git commit -m "test(tenant): TenantSecurityWebFilter 单元测试"
```

---

## Phase 4：自动装配

### Task 11：实现 TenantAutoConfiguration

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/config/TenantAutoConfiguration.java`
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建自动装配类**

```java
package com.forge.framework.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.forge.framework.tenant.core.aop.TenantIgnoreAspect;
import com.forge.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.forge.framework.tenant.core.service.TenantFrameworkService;
import com.forge.framework.tenant.core.service.TenantFrameworkServiceImpl;
import com.forge.framework.tenant.core.web.TenantContextWebFilter;
import com.forge.framework.tenant.core.web.TenantSecurityWebFilter;
import com.forge.modules.system.api.tenant.TenantApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "forge.tenant", value = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantAutoConfiguration {

    @Bean
    public TenantFrameworkService tenantFrameworkService(TenantApi tenantApi) {
        return new TenantFrameworkServiceImpl(tenantApi);
    }

    @Bean
    public TenantIgnoreAspect tenantIgnoreAspect() {
        return new TenantIgnoreAspect();
    }

    @Bean
    public TenantDatabaseInterceptor tenantDatabaseInterceptor(TenantProperties properties) {
        return new TenantDatabaseInterceptor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantDatabaseInterceptor handler,
                                                                  MybatisPlusInterceptor interceptor) {
        TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(handler);
        // 必须添加到第一个，确保在分页、乐观锁之前
        interceptor.addInnerInterceptor(inner);
        return inner;
    }

    @Bean
    public FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilter(TenantProperties properties) {
        FilterRegistrationBean<TenantContextWebFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TenantContextWebFilter(properties));
        bean.setOrder(0);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TenantSecurityWebFilter> tenantSecurityWebFilter(
            TenantProperties properties, TenantFrameworkService service) {
        FilterRegistrationBean<TenantSecurityWebFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TenantSecurityWebFilter(properties, service));
        bean.setOrder(1);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
```

- [ ] **Step 2: 创建 AutoConfiguration.imports**

`apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
com.forge.framework.tenant.config.TenantAutoConfiguration
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl forge-framework/forge-spring-boot-starter-tenant -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: 启动应用验证**

Run: `mvn spring-boot:run -pl forge-server -Dspring-boot.run.profiles=dev`
Expected: 应用启动成功，控制台无 Bean 装配错误。如果启动失败说明 starter 与现有框架冲突，需调整 filter 顺序或 MyBatis 拦截器装配。

- [ ] **Step 5: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/config/ \
        apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/resources/
git commit -m "feat(tenant): 实现 TenantAutoConfiguration 自动装配"
```

---

## Phase 5：手动 SQL 迁移

### Task 12：编写迁移脚本 — 租户表

**Files:**
- Create: `apps/forge-server/docs/manual-migrations/V2026071101__create_tenant_tables.sql`
- Create: `apps/forge-server/docs/manual-migrations/README.md`

- [ ] **Step 1: 创建迁移目录与 README**

`apps/forge-server/docs/manual-migrations/README.md`：

```markdown
# 手动 SQL 迁移

本项目未启用 Flyway，所有 schema 变更通过本目录下脚本手动执行。

## 执行顺序

```
1. V2026071101__create_tenant_tables.sql
2. V2026071102__add_tenant_id_to_sys_user.sql
3. V2026071103__add_tenant_id_to_business_tables.sql
4. V2026071104__backfill_tenant_data.sql
5. V2026071105__init_app_user_tenant.sql
```

## 执行方式

```bash
mysql -u root -p forge_admin < V2026071101__create_tenant_tables.sql
mysql -u root -p forge_admin < V2026071102__add_tenant_id_to_sys_user.sql
...
```

所有脚本使用 `IF NOT EXISTS` / `IGNORE` 关键字，幂等可重跑。
```

- [ ] **Step 2: 创建租户表脚本**

`apps/forge-server/docs/manual-migrations/V2026071101__create_tenant_tables.sql`：

```sql
-- 租户主表
CREATE TABLE IF NOT EXISTS sys_tenant (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64)   NOT NULL COMMENT '租户名称',
  code            VARCHAR(32)   NOT NULL UNIQUE COMMENT '租户标识（登录用）',
  contact_name    VARCHAR(32)   COMMENT '联系人',
  contact_phone   VARCHAR(32)   COMMENT '联系电话',
  status          TINYINT       NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  package_id      BIGINT        COMMENT '套餐ID',
  expire_time     DATETIME      COMMENT '到期时间（NULL=永久）',
  website         VARCHAR(255)  COMMENT '租户官网',
  remark          VARCHAR(500)  COMMENT '备注',
  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by       BIGINT,
  update_by       BIGINT,
  deleted         TINYINT       NOT NULL DEFAULT 0,
  INDEX idx_status (status),
  INDEX idx_package (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- 租户套餐
CREATE TABLE IF NOT EXISTS sys_tenant_package (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64)   NOT NULL,
  code            VARCHAR(32)   NOT NULL UNIQUE,
  status          TINYINT       NOT NULL DEFAULT 1,
  remark          VARCHAR(500),
  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by       BIGINT,
  update_by       BIGINT,
  deleted         TINYINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐';

-- 套餐-菜单
CREATE TABLE IF NOT EXISTS sys_tenant_package_menu (
  tenant_package_id  BIGINT NOT NULL,
  menu_id            BIGINT NOT NULL,
  PRIMARY KEY (tenant_package_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐-菜单关联';

-- 套餐-角色
CREATE TABLE IF NOT EXISTS sys_tenant_package_role (
  tenant_package_id BIGINT NOT NULL,
  role_id           BIGINT NOT NULL,
  PRIMARY KEY (tenant_package_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐-角色关联';
```

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/docs/manual-migrations/
git commit -m "feat(tenant): 编写租户表创建脚本"
```

---

### Task 13：sys_user 增加 tenant_id

**Files:**
- Create: `apps/forge-server/docs/manual-migrations/V2026071102__add_tenant_id_to_sys_user.sql`

- [ ] **Step 1: 编写脚本**

```sql
-- 给 sys_user 加 tenant_id 列
ALTER TABLE sys_user
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id;

-- 删除原 username 唯一索引
ALTER TABLE sys_user DROP INDEX uk_username;

-- 加联合唯一索引 (tenant_id, username)
ALTER TABLE sys_user ADD UNIQUE INDEX uk_tenant_username (tenant_id, username);

-- 加 tenant_id 索引（提升按租户查询性能）
CREATE INDEX idx_tenant ON sys_user (tenant_id);
```

注意：MySQL 8.0.29+ 才支持 `ADD COLUMN IF NOT EXISTS`，更早版本需先查 INFORMATION_SCHEMA 判断。本脚本默认 MySQL 8.0.29+。

如果项目 MySQL < 8.0.29，需要调整为：

```sql
-- 检查列是否存在
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sys_user'
                     AND COLUMN_NAME = 'tenant_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT "租户ID" AFTER id',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: 提交**

```bash
git add apps/forge-server/docs/manual-migrations/V2026071102__add_tenant_id_to_sys_user.sql
git commit -m "feat(tenant): sys_user 增加 tenant_id 列与联合唯一索引"
```

---

### Task 14：业务表加 tenant_id 列

**Files:**
- Create: `apps/forge-server/docs/manual-migrations/V2026071103__add_tenant_id_to_business_tables.sql`

- [ ] **Step 1: 扫描所有业务表**

通过以下命令查找需要加 tenant_id 的表。**重要：跨租户共享表不要加 tenant_id**。

```bash
grep -r "@TableName" apps/forge-server/forge-module-*/forge-module-*-api/src/main/java/com/forge/modules/*/entity/ | grep -v "BaseDO\|sys_menu\|sys_dict\|sys_config\|sys_file_config\|sys_job\|sys_tenant\|sys_user\|app_user" | head -50
```

**跨租户共享表（不加 tenant_id）**：
- `sys_role` - 角色定义由平台维护，套餐选取
- `sys_role_menu` - 角色权限定义共享
- `sys_role_dept` - 实际上需要 tenant_id（关联租户内的角色和部门）

**需要 tenant_id 的关联表**：
- `sys_user_role`、`sys_user_position`、`sys_role_dept` - 这些都关联租户内的用户/角色/部门

将扫描结果按"加 tenant_id"和"共享"两类整理成列表。

- [ ] **Step 2: 编写脚本**

```sql
-- 给所有业务表加 tenant_id 列（具体表名根据扫描结果填充）
-- 列表来源：grep @TableName，排除共享表

-- 示例（每张表都要写一段）
ALTER TABLE sys_notice
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_attachment
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_login_log
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

ALTER TABLE sys_operation_log
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

-- ...其他业务表按相同模式添加

-- 各业务模块表（screen、workflow、ai 等）：
-- ALTER TABLE <表名>
--   ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
--   ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);
```

> **实现说明**：实施者需要执行 Step 1 的扫描命令，根据结果把每张表填入此脚本。

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/docs/manual-migrations/V2026071103__add_tenant_id_to_business_tables.sql
git commit -m "feat(tenant): 业务表统一加 tenant_id 列与索引"
```

---

### Task 15：初始化默认租户 + 历史数据回填

**Files:**
- Create: `apps/forge-server/docs/manual-migrations/V2026071104__backfill_tenant_data.sql`
- Create: `apps/forge-server/docs/manual-migrations/V2026071105__init_app_user_tenant.sql`

- [ ] **Step 1: 默认租户 + 数据回填脚本**

`V2026071104__backfill_tenant_data.sql`：

```sql
-- 插入默认租户
INSERT IGNORE INTO sys_tenant (id, name, code, status, expire_time, remark)
VALUES (1, '默认租户', 'default', 1, NULL, '系统初始租户，用于容纳历史数据');

-- 插入默认套餐
INSERT IGNORE INTO sys_tenant_package (id, name, code, status, remark)
VALUES (1, '默认套餐', 'default', 1, '包含全部菜单');

-- 默认租户绑定默认套餐
UPDATE sys_tenant SET package_id = 1 WHERE id = 1 AND package_id IS NULL;

-- 把历史 sys_user 都归到默认租户
UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

-- 把历史业务数据都归到默认租户
UPDATE sys_notice SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_attachment SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_login_log SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
UPDATE sys_operation_log SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;

-- 其他业务模块的表同上模式
```

- [ ] **Step 2: app_user 加 tenant_id 脚本**

`V2026071105__init_app_user_tenant.sql`：

```sql
-- app_user 加 tenant_id 列
ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID' AFTER id,
  ADD INDEX IF NOT EXISTS idx_tenant (tenant_id);

-- 历史数据回填
UPDATE app_user SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0;
```

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/docs/manual-migrations/V2026071104__backfill_tenant_data.sql \
        apps/forge-server/docs/manual-migrations/V2026071105__init_app_user_tenant.sql
git commit -m "feat(tenant): 默认租户初始化与历史数据回填脚本"
```

---

## Phase 6：实体与登录流程

### Task 16：UserContext 扩展 tenantId 字段

**Files:**
- Modify: `apps/forge-server/forge-framework/forge-common/src/main/java/com/forge/common/utils/UserContext.java`

- [ ] **Step 1: 添加 tenantId 字段**

在 `UserContext` 类中添加：

```java
/**
 * 租户ID
 */
private Long tenantId;

/**
 * 账户类型（0:普通 1:管理员 2:平台超管）
 */
private Integer accountType;

/**
 * 是否为平台超管
 */
public boolean isPlatformAdmin() {
    return accountType != null && accountType == 2;
}

/**
 * 获取 tenantId，如果为 null 返回 null
 */
public Long getTenantId() {
    return tenantId;
}

public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
}
```

- [ ] **Step 2: 同步 TenantSecurityWebFilter 的 extractTenantIdFromUser**

修改 `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/web/TenantSecurityWebFilter.java`：

将 `extractTenantIdFromUser` 改为：

```java
private Long extractTenantIdFromUser(UserContext user) {
    return user.getTenantId();
}
```

并修复 isPlatformAdmin 判断：

```java
if (user != null && !user.isPlatformAdmin()) {
    ...
}
```

- [ ] **Step 3: 编译 + 测试**

Run: `mvn clean test -pl forge-framework/forge-spring-boot-starter-tenant,forge-framework/forge-common -am`
Expected: BUILD SUCCESS，所有单元测试通过

- [ ] **Step 4: 提交**

```bash
git add apps/forge-server/forge-framework/forge-common/src/main/java/com/forge/common/utils/UserContext.java \
        apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/web/TenantSecurityWebFilter.java
git commit -m "feat(tenant): UserContext 增加 tenantId 与 platformAdmin 判断"
```

---

### Task 17：SysUser 实体加 tenantId 字段

**Files:**
- Modify: `apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/SysUser.java`

- [ ] **Step 1: 添加字段**

在 `SysUser` 类中 `private Long deptId;` 之后添加：

```java
/**
 * 租户ID
 */
private Long tenantId;
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl forge-module-system -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/SysUser.java
git commit -m "feat(tenant): SysUser 实体增加 tenantId 字段"
```

---

### Task 18：SysUserService 查询增加 tenantId 维度

**Files:**
- Modify: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/mapper/SysUserMapper.java`
- Modify: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/SysUserService.java`
- Modify: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/impl/SysUserServiceImpl.java`

- [ ] **Step 1: 修改 Mapper 接口签名**

定位 `SysUserMapper.selectByUsernameSimple(String username)`，改为：

```java
SysUser selectByUsernameSimple(@Param("tenantId") Long tenantId, @Param("username") String username);
```

- [ ] **Step 2: 修改对应 XML / @Select**

定位该方法对应的 SQL（可能在 `SysUserMapper.xml` 或 `@Select` 注解中），改为：

```sql
SELECT * FROM sys_user
WHERE tenant_id = #{tenantId}
  AND username = #{username}
  AND deleted = 0
LIMIT 1
```

- [ ] **Step 3: 修改 Service 方法签名**

```java
SysUser getByUsername(Long tenantId, String username);
```

- [ ] **Step 4: 修改 ServiceImpl**

```java
@Override
public SysUser getByUsername(Long tenantId, String username) {
    SysUser user = sysUserMapper.selectByUsernameSimple(tenantId, username);
    if (user != null) {
        loadUserRolesWithDataScope(user);
    }
    return user;
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl forge-module-system -am`
Expected: BUILD SUCCESS，可能需要修改其他调用 `getByUsername` 的地方。

- [ ] **Step 6: 全局搜索调用点**

```bash
grep -rn "getByUsername\|selectByUsernameSimple" apps/forge-server/forge-module-system/ --include="*.java"
```

把所有调用点改为传入 tenantId。优先调用点：
- `AuthServiceImpl.login(...)` - 修改为按 tenantCode 查租户 → tenantId → 查 user
- 后续 Task 19 处理

- [ ] **Step 7: 提交**

```bash
git add apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/mapper/ \
        apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/
git commit -m "feat(tenant): SysUser 查询按 (tenantId, username) 联合过滤"
```

---

### Task 19：登录接口加 tenantCode

**Files:**
- Modify: `apps/forge-server/forge-module-system/forge-module-system-biz/.../dto/auth/LoginRequest.java`
- Modify: `apps/forge-server/forge-module-system/forge-module-system-biz/.../service/auth/AuthServiceImpl.java`

- [ ] **Step 1: LoginRequest 加 tenantCode 字段**

```java
@NotBlank(message = "租户标识不能为空")
@Schema(description = "租户标识")
private String tenantCode;
```

- [ ] **Step 2: AuthServiceImpl.login 修改流程**

在用户名密码校验前：

```java
// 1. 查租户
Long tenantId = tenantService.getIdByCode(loginRequest.getTenantCode());
if (tenantId == null) {
    throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);  // 新增错误码
}
TenantContextHolder.setTenantId(tenantId);

// 2. 按 (tenantId, username) 查用户
SysUser user = sysUserService.getByUsername(tenantId, loginRequest.getUsername());
```

- [ ] **Step 3: JWT 增加 tenantId claim**

定位 JWT 生成代码（`JwtTokenHelper` 或类似），在 claims 中加入 `tenantId`：

```java
claims.put("tenantId", user.getTenantId());
```

- [ ] **Step 4: 新增 ResultCode**

在 `apps/forge-server/forge-framework/forge-common/src/main/java/com/forge/common/response/ResultCode.java` 中新增：

```java
TENANT_NOT_EXISTS(1401, "租户不存在"),
TENANT_DISABLED(1402, "租户已被禁用"),
TENANT_EXPIRED(1403, "租户已过期");
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl forge-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add apps/forge-server/forge-module-system/forge-module-system-biz/ \
        apps/forge-server/forge-framework/forge-common/src/main/java/com/forge/common/response/ResultCode.java
git commit -m "feat(tenant): 登录接口加 tenantCode 入参与 JWT claim"
```

---

## Phase 7：Redis 缓存隔离

### Task 20：实现 TenantRedisCacheManager

**Files:**
- Modify: `apps/forge-server/forge-framework/forge-spring-boot-starter-redis/src/main/java/com/forge/framework/redis/config/CacheConfig.java`

- [ ] **Step 1: 在 starter-redis 中创建 TenantRedisCacheManager**

在 `CacheConfig.java` 同包下新建文件 `TenantRedisCacheManager.java`：

```java
package com.forge.framework.redis.config;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.lang.Nullable;

import java.util.Set;

public class TenantRedisCacheManager extends RedisCacheManager {

    private final Set<String> ignoreCaches;

    public TenantRedisCacheManager(RedisCacheWriter cacheWriter,
                                   RedisCacheConfiguration defaultConfig,
                                   Set<String> ignoreCaches) {
        super(cacheWriter, defaultConfig);
        this.ignoreCaches = ignoreCaches == null ? java.util.Collections.emptySet() : ignoreCaches;
    }

    @Override
    @Nullable
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        // 跨租户共享的缓存：使用原名
        if (ignoreCaches.contains(name)) {
            return super.createRedisCache(name, cacheConfig);
        }
        // 多租户缓存：加 tenantId 前缀
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || TenantContextHolder.isIgnore()) {
            return super.createRedisCache(name, cacheConfig);
        }
        String prefixedName = tenantId + ":" + name;
        return super.createRedisCache(prefixedName, cacheConfig);
    }
}
```

- [ ] **Step 2: 修改 CacheConfig 使用 TenantRedisCacheManager**

修改 `CacheConfig.cacheManager` 方法的返回类型为 `TenantRedisCacheManager`，并在最后一行替换 `RedisCacheManager.builder(...).build()`：

```java
return new TenantRedisCacheManager(
    RedisCacheWriter.lockingRedisCacheWriter(factory),
    defaultConfig,
    Set.of("dictData", "dictType", "sysConfig", "menu", "dept")  // ignoreCaches
);
```

注意：`TenantRedisCacheManager` 需要从 starter-tenant 获取 `TenantContextHolder`，因此 starter-redis 需要依赖 starter-tenant。在 `forge-spring-boot-starter-redis/pom.xml` 添加：

```xml
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-spring-boot-starter-tenant</artifactId>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl forge-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: 启动验证**

Run: `mvn spring-boot:run -pl forge-server -Dspring-boot.run.profiles=dev`
Expected: 应用启动成功，控制台无 Bean 装配错误。

- [ ] **Step 5: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-redis/ \
        apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/
git commit -m "feat(tenant): Redis 缓存 key 自动加 tenantId 前缀"
```

---

## Phase 8：Quartz 跨线程

### Task 21：实现 TenantJobAspect

**Files:**
- Create: `apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/job/TenantJobAspect.java`

- [ ] **Step 1: 创建 AOP**

```java
package com.forge.framework.tenant.core.job;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Slf4j
@Aspect
public class TenantJobAspect {

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) || @within(com.forge.framework.tenant.core.job.TenantJob)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("[tenant-job] 定时任务 {} 未指定 tenantId，将忽略租户过滤",
                    joinPoint.getSignature().toShortString());
            TenantContextHolder.setIgnore(true);
        }
        try {
            return joinPoint.proceed();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
```

- [ ] **Step 2: 在 TenantAutoConfiguration 注册**

```java
@Bean
public TenantJobAspect tenantJobAspect() {
    return new TenantJobAspect();
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl forge-framework/forge-spring-boot-starter-tenant -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/core/job/ \
        apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/src/main/java/com/forge/framework/tenant/config/
git commit -m "feat(tenant): 实现 TenantJobAspect 处理定时任务跨线程"
```

---

## Phase 9：租户管理后台

### Task 22：租户实体、Mapper、Service

**Files:**
- Create: `apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/SysTenant.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/SysTenantPackage.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/entity/SysTenantPackageMenu.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-api/src/main/java/com/forge/modules/system/dto/tenant/*`
- Create: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/mapper/SysTenantMapper.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/mapper/SysTenantPackageMapper.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/SysTenantService.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/impl/SysTenantServiceImpl.java`

> **实现说明**：本任务量较大但与现有 CRUD 模块结构完全一致。参照 `SysUserController` + `SysUserServiceImpl` + `SysUserMapper` 三层结构实现。提供：list / get / add / update / delete / changeStatus / resetPassword 等接口。

需要在 `TenantAutoConfiguration` 的 ignore-tables 加上 `sys_tenant`、`sys_tenant_package`、`sys_tenant_package_menu`、`sys_tenant_package_role`（在 application.yml 中配置）。

- [ ] **Step 1: 创建实体类**

参照 `SysUser.java`，定义 `SysTenant`、`SysTenantPackage`、`SysTenantPackageMenu` 三个实体。`SysTenant` 必须继承 `TenantBaseDO`（让 TenantLineInterceptor 自动注入 tenantId=1 用于平台超管）。

实际上：sys_tenant 跨租户共享（在 ignoreTables 中），所以继承普通 BaseDO 而不是 TenantBaseDO。

```java
// SysTenant.java（继承普通 BaseDO）
@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private String code;
    private String contactName;
    private String contactPhone;
    private Integer status;
    private Long packageId;
    private LocalDateTime expireTime;
    private String website;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    @TableLogic private Integer deleted;
}
```

- [ ] **Step 2: DTO 类**

`TenantRequest`、`TenantQueryRequest`、`TenantResponse`、`TenantPackageRequest`、`TenantPackageResponse` —— 与现有 DTO 风格一致（用 `@Schema` 注解、Knife4j 文档生成）。

- [ ] **Step 3: Mapper**

`SysTenantMapper` 继承 `BaseMapper<SysTenant>`，提供 `getIdByCode(String code)` 自定义方法（XML 或 @Select 注解）。

- [ ] **Step 4: Service**

`SysTenantServiceImpl` 实现 CRUD：
- list：分页 + 模糊查询
- add：唯一性校验（code 唯一）
- update：状态校验
- delete：逻辑删除
- changeStatus：状态切换
- validTenant(Long tenantId)：状态=1 且（expireTime=NULL 或 expireTime > now）

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl forge-module-system -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add apps/forge-server/forge-module-system/
git commit -m "feat(tenant): 实现 sys_tenant / sys_tenant_package 实体与 CRUD"
```

---

### Task 23：租户管理 Controller + TenantApiImpl

**Files:**
- Create: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/controller/SysTenantController.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/controller/SysTenantPackageController.java`
- Create: `apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/tenant/TenantApiImpl.java`

- [ ] **Step 1: SysTenantController**

```java
@Tag(name = "租户管理")
@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService tenantService;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:tenant:list')")
    public Result<PageResult<TenantResponse>> list(TenantQueryRequest request) {
        return Result.success(tenantService.pageTenant(request));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('system:tenant:add')")
    @OperationLog(title = "租户管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody TenantRequest request) {
        tenantService.addTenant(request);
        return Result.success();
    }

    // update / delete / get / changeStatus 同理
}
```

> **实现说明**：所有写操作的方法体第一行调用 `TenantContextHolder.setIgnore(true)`，让 TenantLineInterceptor 不注入 tenant_id 过滤（因为 sys_tenant 本身在 ignoreTables 中其实已被过滤，但显式调用 setIgnore 防御性更强）。

- [ ] **Step 2: TenantApiImpl**

实现 `com.forge.modules.system.api.tenant.TenantApi` 接口：

```java
@Service
@RequiredArgsConstructor
public class TenantApiImpl implements TenantApi {

    private final SysTenantService tenantService;
    private final SysTenantPackageService packageService;

    @Override
    public void validTenant(Long tenantId) {
        TenantContextHolder.setIgnore(true);
        try {
            SysTenant tenant = tenantService.getById(tenantId);
            if (tenant == null) {
                throw new BusinessException(ResultCode.TENANT_NOT_EXISTS);
            }
            if (tenant.getStatus() != 1) {
                throw new BusinessException(ResultCode.TENANT_DISABLED);
            }
            if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.TENANT_EXPIRED);
            }
        } finally {
            TenantContextHolder.setIgnore(null);
        }
    }

    @Override
    public String getTenantName(Long tenantId) {
        TenantContextHolder.setIgnore(true);
        try {
            SysTenant tenant = tenantService.getById(tenantId);
            return tenant != null ? tenant.getName() : null;
        } finally {
            TenantContextHolder.setIgnore(null);
        }
    }

    @Override
    public List<Long> getTenantPackageMenuIds(Long tenantId) {
        TenantContextHolder.setIgnore(true);
        try {
            SysTenant tenant = tenantService.getById(tenantId);
            if (tenant == null || tenant.getPackageId() == null) {
                return Collections.emptyList();
            }
            return packageService.getMenuIdsByPackageId(tenant.getPackageId());
        } finally {
            TenantContextHolder.setIgnore(null);
        }
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -pl forge-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: 启动 + 调用测试**

启动应用，使用 Knife4j 测试 `GET /admin-api/system/tenant/list`（需要 platformAdmin 权限）。

- [ ] **Step 5: 提交**

```bash
git add apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/controller/ \
        apps/forge-server/forge-module-system/forge-module-system-biz/src/main/java/com/forge/modules/system/service/tenant/
git commit -m "feat(tenant): 租户 Controller + TenantApiImpl"
```

---

## Phase 10：前端

### Task 24：登录页加 tenantCode 输入

**Files:**
- Modify: `apps/forge-web/src/views/login/index.vue`
- Modify: `apps/forge-web/src/api/auth.ts`

- [ ] **Step 1: API 调整**

```typescript
export interface LoginRequest {
  tenantCode: string
  username: string
  password: string
  captcha?: string
  captchaId?: string
}

export const authApi = {
  login: (data: LoginRequest) => request.post<LoginResult>('/auth/login', data),
  // ...
}
```

- [ ] **Step 2: 登录页表单**

在用户名输入框前加：

```vue
<el-form-item prop="tenantCode">
  <el-input v-model="loginForm.tenantCode" placeholder="租户标识" size="large" prefix-icon="OfficeBuilding" />
</el-form-item>
```

`loginForm` 初始化：

```typescript
const loginForm = reactive({
  tenantCode: '',
  username: '',
  password: '',
  captcha: '',
  captchaId: ''
})
```

- [ ] **Step 3: 提交**

```bash
git add apps/forge-web/src/views/login/index.vue \
        apps/forge-web/src/api/auth.ts
git commit -m "feat(tenant): 前端登录页加 tenantCode 输入"
```

---

### Task 25：Axios 自动注入 X-Tenant-Id

**Files:**
- Modify: `apps/forge-web/src/utils/request.ts`
- Modify: `apps/forge-web/src/stores/user.ts`

- [ ] **Step 1: user store 加 tenantId 字段**

```typescript
interface UserState {
  // 现有字段...
  tenantId: number | null
}
```

登录成功后保存 tenantId 到 store：

```typescript
setUserInfo(info: UserInfo) {
  this.tenantId = info.tenantId
  // 现有逻辑...
}
```

- [ ] **Step 2: request.ts 拦截器**

```typescript
request.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.tenantId && !config.url?.includes('/auth/login')) {
    config.headers['X-Tenant-Id'] = String(userStore.tenantId)
  }
  return config
})
```

- [ ] **Step 3: 提交**

```bash
git add apps/forge-web/src/utils/request.ts \
        apps/forge-web/src/stores/user.ts
git commit -m "feat(tenant): Axios 自动注入 X-Tenant-Id 请求头"
```

---

### Task 26：租户管理页面

**Files:**
- Create: `apps/forge-web/src/views/system/tenant/index.vue`
- Create: `apps/forge-web/src/views/system/tenant-package/index.vue`
- Create: `apps/forge-web/src/api/system/tenant.ts`
- Create: `apps/forge-web/src/api/system/tenant-package.ts`

- [ ] **Step 1: API 定义**

参照现有 `user.ts`：

```typescript
export const tenantApi = {
  page: (params: TenantQuery) => request.get<PageResult<TenantResponse>>('/system/tenant/list', { params }),
  get: (id: number) => request.get<TenantResponse>(`/system/tenant/${id}`),
  add: (data: TenantRequest) => request.post('/system/tenant', data),
  update: (data: TenantRequest) => request.put('/system/tenant', data),
  delete: (id: number) => request.delete(`/system/tenant/${id}`),
  changeStatus: (id: number, status: number) => request.put(`/system/tenant/change-status/${id}?status=${status}`)
}
```

- [ ] **Step 2: 列表页 CRUD**

参照 `apps/forge-web/src/views/system/user/index.vue`，实现租户列表 CRUD：
- 搜索：name、code、status
- 表格字段：id、name、code、contact、status、package、expireTime、createTime
- 操作：编辑、删除、改状态
- 新增/编辑对话框：name、code、contact、package（select）、expireTime、status、remark

- [ ] **Step 3: 路由注册**

在 `apps/forge-web/src/router/modules/system.ts` 中添加：

```typescript
{
  path: 'tenant',
  name: 'SystemTenant',
  component: () => import('@/views/system/tenant/index.vue'),
  meta: { title: '租户管理', icon: 'OfficeBuilding', permission: 'system:tenant:list' }
}
```

**重要**：菜单项**只在平台超管可见**。在 `permissionStore` 过滤逻辑中检查 `accountType === 2` 才显示。

- [ ] **Step 4: 提交**

```bash
git add apps/forge-web/src/views/system/tenant/ \
        apps/forge-web/src/views/system/tenant-package/ \
        apps/forge-web/src/api/system/tenant.ts \
        apps/forge-web/src/api/system/tenant-package.ts \
        apps/forge-web/src/router/modules/system.ts
git commit -m "feat(tenant): 租户管理前端页面（列表 + 套餐）"
```

---

## Phase 11：配置与文档

### Task 27：application.yml 加 forge.tenant 配置

**Files:**
- Modify: `apps/forge-server/forge-server/src/main/resources/application.yml`
- Modify: `apps/forge-server/forge-server/src/main/resources/application-dev.yml`（如有）

- [ ] **Step 1: 在 application.yml 添加配置段**

```yaml
forge:
  tenant:
    enable: true                  # 关闭多租户时改为 false
    header: X-Tenant-Id
    ignore-urls:
      - /admin-api/auth/login
      - /admin-api/auth/refresh
      - /admin-api/system/tenant/public/**
      - /app-api/auth/wx-login
    ignore-tables:
      - sys_menu
      - sys_role
      - sys_role_menu
      - sys_dict_type
      - sys_dict_data
      - sys_config
      - sys_file_config
      - sys_job
      - sys_tenant
      - sys_tenant_package
      - sys_tenant_package_menu
      - sys_tenant_package_role
    ignore-caches:
      - dictData
      - dictType
      - sysConfig
      - menu
      - dept
```

- [ ] **Step 2: 提交**

```bash
git add apps/forge-server/forge-server/src/main/resources/
git commit -m "feat(tenant): application.yml 加 forge.tenant 配置段"
```

---

### Task 28：编写 README 文档

**Files:**
- Create: `apps/forge-server/forge-spring-boot-starter-tenant/README.md`

- [ ] **Step 1: 编写文档**

```markdown
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
```

- [ ] **Step 2: 提交**

```bash
git add apps/forge-server/forge-framework/forge-spring-boot-starter-tenant/README.md
git commit -m "docs(tenant): starter README"
```

---

## Phase 12：端到端验证

### Task 29：手工验证清单

- [ ] **Step 1: 启动应用**

Run: `mvn spring-boot:run -pl forge-server -Dspring-boot.run.profiles=dev`
Expected: 启动成功

- [ ] **Step 2: 执行 SQL 迁移**

```bash
mysql -u root -p forge_admin < apps/forge-server/docs/manual-migrations/V2026071101__create_tenant_tables.sql
mysql -u root -p forge_admin < apps/forge-server/docs/manual-migrations/V2026071102__add_tenant_id_to_sys_user.sql
mysql -u root -p forge_admin < apps/forge-server/docs/manual-migrations/V2026071103__add_tenant_id_to_business_tables.sql
mysql -u root -p forge_admin < apps/forge-server/docs/manual-migrations/V2026071104__backfill_tenant_data.sql
mysql -u root -p forge_admin < apps/forge-server/docs/manual-migrations/V2026071105__init_app_user_tenant.sql
```

- [ ] **Step 3: 手工测试场景**

按 spec §7.3 清单逐项验证：

```
□ 创建租户 A → 自动创建管理员 a_admin / 默认密码
□ 创建租户 B → 重复
□ a_admin 登录 → CRUD 只能看到 A 的数据
□ 切换 X-Tenant-Id 头 → 越权 403
□ 平台超管 p1 登录 → 看到所有租户
□ forge.tenant.enable=false 重启 → 全部功能回归单租户
```

每个场景通过即勾选，全部通过后继续。

- [ ] **Step 4: 最终提交（如有遗留改动）**

```bash
git status
# 若有未提交改动
git add -A
git commit -m "feat(tenant): 端到端验证通过"
```

---

## 自检清单（实施者必查）

实施者开始前对照确认：

- [ ] 所有新文件路径已存在父目录
- [ ] MySQL 版本 ≥ 8.0.29（支持 IF NOT EXISTS）或使用了 fallback 写法
- [ ] `transmittable-thread-local` 版本在 forge-dependencies BOM 中已声明
- [ ] `forge-module-screen`、`forge-module-workflow`、`forge-module-ai` 业务表都已加入 `V2026071103` 脚本
- [ ] Redis 已清理旧缓存（避免缓存 key 格式冲突）
- [ ] 默认租户（id=1）的套餐菜单已配置
- [ ] 平台超管账号已创建（account_type=2）

---

## 实施完成标志

所有 Phase 任务完成 + Phase 12 手工验证清单全部勾选 = 实施完成。