# 后端开发规范

> 适用范围：`apps/forge-server`（Java 21 / Spring Boot 3.4 / MyBatis Plus）。
>
> 配套文档：新模块端到端搭建流程见 [新模块开发向导](./module-development.md)；数据库设计与迁移脚本细节见 [数据库设计规范](./database-design-specification.md)；前端对接见 [前端开发规范](./frontend-development-specification.md)。

---

## 1. 模块结构

### 1.1 api + biz 双模块结构

每个业务模块采用 **api + biz** 双模块结构：

```
forge-module-{module}/
├── forge-module-{module}-api/     # API层：实体、DTO、枚举
│   ├── pom.xml
│   └── src/main/java/com/forge/modules/{module}/
│       ├── entity/                # 实体类（对应数据库表）
│       ├── dto/
│       │   ├── request/           # 请求 DTO
│       │   └── response/          # 响应 DTO
│       └── enums/                 # 模块枚举（如有）
│
└── forge-module-{module}-biz/     # 业务层：Controller、Service、Mapper
    ├── pom.xml
    └── src/main/java/com/forge/modules/{module}/
        ├── controller/
        │   ├── admin/             # 后台管理接口（自动添加 /admin-api 前缀）
        │   └── app/               # 移动端/小程序接口（自动添加 /app-api 前缀）
        ├── service/
        │   ├── XxxService.java    # 服务接口
        │   └── impl/
        │       └── XxxServiceImpl.java  # 服务实现
        └── mapper/
            └── XxxMapper.java     # MyBatis Mapper
    └── src/main/resources/
        └── mapper/
            └── XxxMapper.xml      # MyBatis XML（如有复杂SQL）
```

### 1.2 路径前缀规则

| 包路径 | API 前缀 | 用途 |
|--------|----------|------|
| `**.controller.admin.**` | `/admin-api` | 后台管理系统接口 |
| `**.controller.app.**` | `/app-api` | 移动端/小程序接口 |

**示例：**
```java
// Controller 位于 com.forge.modules.order.controller.admin.OrderController
@RequestMapping("/order")  // 实际路径：/admin-api/order

// Controller 位于 com.forge.modules.order.controller.app.OrderController
@RequestMapping("/order")  // 实际路径：/app-api/order
```

**注意：** Controller 类的 `@RequestMapping` 不要再写 `/admin-api` 或 `/app-api` 前缀，前缀由框架按包名自动注入。

---

## 2. Maven 配置

### 2.1 API 模块 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>forge-module-{module}</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>forge-module-{module}-api</artifactId>
    <name>forge-module-{module}-api</name>
    <description>{模块描述} API层</description>

    <dependencies>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-common</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 2.2 BIZ 模块 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>forge-module-{module}</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>forge-module-{module}-biz</artifactId>
    <name>forge-module-{module}-biz</name>
    <description>{模块描述} 业务层</description>

    <dependencies>
        <!-- 本模块 API -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-module-{module}-api</artifactId>
        </dependency>

        <!-- 框架依赖 -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 如需依赖其他模块 -->
        <dependency>
            <groupId>com.forge</groupId>
            <artifactId>forge-module-system-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 2.3 父模块 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.forge</groupId>
        <artifactId>forge</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>forge-module-{module}</artifactId>
    <name>forge-module-{module}</name>
    <description>{模块描述}</description>
    <packaging>pom</packaging>

    <modules>
        <module>forge-module-{module}-api</module>
        <module>forge-module-{module}-biz</module>
    </modules>
</project>
```

### 2.4 模块注册步骤

新模块需完成以下注册（缺一不可）：

1. **根聚合 POM**：在 `apps/forge-server/pom.xml` 的 `<modules>` 中添加：
```xml
<module>forge-module-{module}</module>
```

2. **forge-dependencies**：在 `forge-dependencies/pom.xml` 的 `<dependencyManagement>` 中添加：
```xml
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-module-{module}-api</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-module-{module}-biz</artifactId>
    <version>${project.version}</version>
</dependency>
```

3. **forge-server**：在 `forge-server/pom.xml` 的 `<dependencies>` 中添加：
```xml
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-module-{module}-biz</artifactId>
</dependency>
```

4. **验证编译**：运行 `mvn clean compile` 确认模块接入成功。

---

## 3. 分层代码规范

### 3.1 Entity 层

```java
package com.forge.modules.{module}.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * {实体名称}实体
 */
@Data
@TableName("sys_{entity}")
public class Sys{Entity} {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 逻辑删除：0未删除 1已删除 */
    @TableLogic
    private Integer deleted;
}
```

**要点：**
- `@Data` + `@TableName` + `@TableId(type = IdType.AUTO)`
- `createTime` 用 `FieldFill.INSERT`，`updateTime` 用 `FieldFill.INSERT_UPDATE`
- JSON 字段用 `@TableField(typeHandler = JacksonTypeHandler.class)` + `Map<String, Object>` 类型
- 金额用 `BigDecimal`，日期用 `LocalDate`，时间用 `LocalDateTime`
- 软删除字段：`@TableLogic private Integer deleted`
- 表名前缀：`sys_`（系统模块）、`wf_`（工作流模块）、`ai_`（AI模块）等

### 3.2 Mapper 层

```java
package com.forge.modules.{module}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.{module}.entity.Sys{Entity};
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface Sys{Entity}Mapper extends BaseMapper<Sys{Entity}> {

    // 如有复杂 SQL，可在 XML 中定义
}
```

对应的 XML 放在 `resources/mapper/` 下（如需自定义 SQL）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.forge.modules.{module}.mapper.Sys{Entity}Mapper">

    <resultMap id="BaseResultMap" type="com.forge.modules.{module}.entity.Sys{Entity}">
        <id column="id" property="id"/>
        <result column="name" property="name"/>
        <result column="status" property="status"/>
        <result column="remark" property="remark"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
        <result column="create_by" property="createBy"/>
        <result column="update_by" property="updateBy"/>
        <result column="deleted" property="deleted"/>
    </resultMap>

</mapper>
```

**注意：** 手写 SQL 的 `<where>` 块首行必须显式 `AND xxx.deleted = 0`，`@TableLogic` 不会自动注入到手写 SQL。

### 3.3 DTO 层

#### Request（新增/更新）

```java
package com.forge.modules.{module}.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * {实体}新增/修改请求
 */
@Data
public class XxxRequest {

    /** ID（修改时必填） */
    private Long id;

    /** 名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "名称长度不能超过50")
    private String name;

    /** 状态 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
```

#### QueryRequest（分页查询）

```java
package com.forge.modules.{module}.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * {实体}查询请求
 */
@Data
public class XxxQueryRequest {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(100)
    private Integer pageSize = 10;

    /** 名称（模糊查询） */
    private String name;

    /** 状态 */
    private Integer status;
}
```

#### Response（响应）

```java
package com.forge.modules.{module}.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {实体}响应
 */
@Data
public class XxxResponse {

    private Long id;
    private String name;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

**要点：**
- Response 中包含外键关联名称（如 `enterpriseName`）
- QueryRequest 无验证注解，Request 用 `@NotNull` / `@NotBlank` / `@Size` / `@Min` / `@Max`
- 验证消息格式：`"{字段名}不能为空"`

### 3.4 Service 层

#### 接口

```java
package com.forge.modules.{module}.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.modules.{module}.dto.request.*;
import com.forge.modules.{module}.dto.response.*;
import com.forge.modules.{module}.entity.SysXxx;

import java.util.List;

public interface SysXxxService extends IService<SysXxx> {

    Page<XxxResponse> pageXxx(XxxQueryRequest request);

    XxxResponse getXxx(Long id);

    void addXxx(XxxRequest request);

    void updateXxx(XxxRequest request);

    void deleteXxx(List<Long> ids);
}
```

#### 实现

```java
package com.forge.modules.{module}.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.modules.{module}.dto.request.*;
import com.forge.modules.{module}.dto.response.*;
import com.forge.modules.{module}.entity.SysXxx;
import com.forge.modules.{module}.mapper.SysXxxMapper;
import com.forge.modules.{module}.service.SysXxxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysXxxServiceImpl extends ServiceImpl<SysXxxMapper, SysXxx> implements SysXxxService {

    @Override
    public Page<XxxResponse> pageXxx(XxxQueryRequest request) {
        LambdaQueryWrapper<SysXxx> wrapper = new LambdaQueryWrapper<>();

        // 模糊查询
        wrapper.like(request.getName() != null, SysXxx::getName, request.getName());
        // 精确匹配
        wrapper.eq(request.getStatus() != null, SysXxx::getStatus, request.getStatus());
        // 排序
        wrapper.orderByDesc(SysXxx::getCreateTime);

        Page<SysXxx> page = page(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

        // 转换为响应 DTO
        return page.convert(this::toResponse);
    }

    @Override
    public XxxResponse getXxx(Long id) {
        SysXxx entity = getById(id);
        if (entity == null) {
            throw new BusinessException("数据不存在");
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addXxx(XxxRequest request) {
        SysXxx entity = new SysXxx();
        entity.setName(request.getName());
        entity.setStatus(request.getStatus());
        entity.setRemark(request.getRemark());
        save(entity);
        log.info("[{Module}] 新增{实体}: id={}", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateXxx(XxxRequest request) {
        SysXxx entity = getById(request.getId());
        if (entity == null) {
            throw new BusinessException("数据不存在");
        }
        entity.setName(request.getName());
        entity.setStatus(request.getStatus());
        entity.setRemark(request.getRemark());
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteXxx(List<Long> ids) {
        removeByIds(ids);
    }

    private XxxResponse toResponse(SysXxx entity) {
        XxxResponse response = new XxxResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setStatus(entity.getStatus());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }
}
```

**要点：**
- `@Slf4j` + `@Service` + `@RequiredArgsConstructor`
- 构造器注入：`private final XxxMapper xxxMapper`
- 分页列表：使用 `page.convert()` 转换实体
- 批量查询外键名称（`selectBatchIds` + Map），避免 N+1
- 详情：单条 `selectById` 查外键名称
- 实体转换：可使用 `BeanUtil.copyProperties(entity, Response.class)`
- 异常：`throw new BusinessException("xxx不存在")`
- 日志：`log.info("[{Module}] 操作: key={}", value)`
- 写操作加 `@Transactional(rollbackFor = Exception.class)`

### 3.5 Controller 层

```java
package com.forge.modules.{module}.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.{module}.dto.request.*;
import com.forge.modules.{module}.dto.response.*;
import com.forge.modules.{module}.service.SysXxxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "{模块}管理")
@RestController
@RequestMapping("/{module}/{entity}")
@RequiredArgsConstructor
public class SysXxxController {

    private final SysXxxService sysXxxService;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('{module}:{entity}:list')")
    public Result<PageResult<XxxResponse>> list(XxxQueryRequest request) {
        Page<XxxResponse> page = sysXxxService.pageXxx(request);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "获取详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('{module}:{entity}:query')")
    public Result<XxxResponse> get(@PathVariable Long id) {
        XxxResponse response = sysXxxService.getXxx(id);
        return Result.success(response);
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('{module}:{entity}:add')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody XxxRequest request) {
        sysXxxService.addXxx(request);
        return Result.success();
    }

    @Operation(summary = "修改")
    @PutMapping
    @PreAuthorize("hasAuthority('{module}:{entity}:edit')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody XxxRequest request) {
        sysXxxService.updateXxx(request);
        return Result.success();
    }

    @Operation(summary = "删除")
    @DeleteMapping
    @PreAuthorize("hasAuthority('{module}:{entity}:delete')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        sysXxxService.deleteXxx(ids);
        return Result.success();
    }
}
```

**要点：**
- 权限注解：`@PreAuthorize("hasAuthority('{module}:{entity}:{action}')")` — 注意是 `hasAuthority`
- 权限格式：`{module}:{entity}:{list|query|add|edit|delete|import}`
- 分页响应：`Result<PageResult<Response>>`，使用 `PageResult.of(page)`
- 写操作加 `@OperationLog`
- 删除支持批量：`@RequestBody List<Long> ids`

---

## 4. 横切关注点

### 4.1 操作日志

```java
@OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.INSERT)
```

BusinessType 类型：`INSERT`、`UPDATE`、`DELETE`、`EXPORT`、`IMPORT`、`OTHER`

### 4.2 数据权限

```java
@DataPermission(deptAlias = "d", userAlias = "u")
```

适用于需要按部门/用户数据范围过滤的查询。

### 4.3 接口限流

```java
@RateLimiter(keyType = KeyType.USER, time = 60, count = 10)
```

### 4.4 缓存

```java
@Cacheable(value = "xxx", key = "#id")
@CacheEvict(value = "xxx", key = "#id")
```

缓存名：`dictData`、`dictType`、`sysConfig`、`userInfo`、`menu`、`dept`

---

## 5. 数据库迁移

> 迁移脚本的字段/索引/命名设计细节见 [数据库设计规范](./database-design-specification.md) 第 9 章「迁移脚本规范」，本节只保留开发视角的操作速查。

### 5.1 脚本位置

每个模块**各自的** `db/migration/` 目录下：

```
forge-module-{module}-biz/src/main/resources/db/migration/
forge-server/src/main/resources/db/migration/
```

**注意：** 项目未引入 Flyway 依赖，迁移 SQL 需手工按版本号顺序执行（首次部署用 `sql/init.sql` + 各 `init-*.sql`；脚本不含 CREATE DATABASE/USE 语句，手工导入需显式指定库名）。

### 5.2 命名规范

```
V{YYYYMMDD}{seq}__{description}.sql

示例：
V2024061701__create_xxx_table.sql
V2024061702__add_xxx_status_column.sql
```

### 5.3 表结构模板

```sql
-- V2024061701__create_xxx_table.sql

CREATE TABLE `sys_xxx` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{实体}表';
```

字段类型选择、索引设计、软删除等规范细节见 [数据库设计规范](./database-design-specification.md) 第 5-8 章。

---

## 6. 菜单与权限 SQL

> 菜单表字段、字典数据的完整规范见 [数据库设计规范](./database-design-specification.md) 第 10-11 章，本节只保留开发视角的 SQL 模板。

### 6.1 菜单数据 SQL 模板

```sql
-- {模块名称}（目录）
INSERT INTO `sys_menu` (parent_id, name, path, component, perms, type, sort, visible, status, create_time, update_time, deleted)
VALUES (0, '{模块}管理', '/{module}', 'Layout', NULL, 0, 1, 1, 1, NOW(), NOW(), 0);

-- {实体}管理（菜单）
INSERT INTO `sys_menu` (parent_id, name, path, component, perms, type, sort, visible, status, create_time, update_time, deleted)
VALUES (目录ID, '{实体}管理', '/{module}/{entity}', '/views/{module}/{entity}/index', NULL, 1, 1, 1, 1, NOW(), NOW(), 0);

-- 权限按钮
INSERT INTO `sys_menu` (parent_id, name, path, component, perms, type, sort, visible, status, create_time, update_time, deleted)
VALUES (菜单ID, '查询', NULL, NULL, '{module}:{entity}:query', 2, 1, 1, 1, NOW(), NOW(), 0);
INSERT INTO `sys_menu` (parent_id, name, path, component, perms, type, sort, visible, status, create_time, update_time, deleted)
VALUES (菜单ID, '新增', NULL, NULL, '{module}:{entity}:add', 2, 2, 1, 1, NOW(), NOW(), 0);
INSERT INTO `sys_menu` (parent_id, name, path, component, perms, type, sort, visible, status, create_time, update_time, deleted)
VALUES (菜单ID, '修改', NULL, NULL, '{module}:{entity}:edit', 2, 3, 1, 1, NOW(), NOW(), 0);
INSERT INTO `sys_menu` (parent_id, name, path, component, perms, type, sort, visible, status, create_time, update_time, deleted)
VALUES (菜单ID, '删除', NULL, NULL, '{module}:{entity}:delete', 2, 4, 1, 1, NOW(), NOW(), 0);

-- 给管理员角色分配权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 目录ID), (1, 菜单ID), (1, 按钮ID1), (1, 按钮ID2), ...;
```

### 6.2 菜单类型说明

| 类型 | 值 | component 取值 |
|------|-----|----------------|
| 目录 | `0` | `Layout` |
| 菜单 | `1` | `/views/{module}/{entity}/index` |
| 按钮 | `2` | 空（NULL） |

---

## 7. 关键约定速查

| 项目 | 规范 |
|------|------|
| 权限注解 | `@PreAuthorize("hasAuthority('xxx')")` — **不是** `@ss.hasPerm` |
| 权限格式 | `{module}:{entity}:{action}`，action: list/query/add/edit/delete/import |
| Controller 路径 | `/{module}/{entity}/{action}`，前缀由包名自动注入 |
| 分页响应 | `PageResult.of(page)` 或 `PageResult.of(records, total, pageNum, pageSize)` |
| 实体转换 | `BeanUtil.copyProperties(source, Target.class)` 或 `page.convert()` |
| 异常抛出 | `throw new BusinessException("xxx不存在")` |
| 数据库表名 | `snake_case`，前缀 `sys_`/`wf_`/`ai_` 等 |
| Java 类名 | `PascalCase` |
| 包名 | `com.forge.modules.{module}` |
| 手写 SQL | `<where>` 首行显式 `AND xxx.deleted = 0` |

---

## 8. 检查清单

后端功能提交前自查：

- [ ] 实体类位于 api 模块，公共字段与自动填充注解齐全（3.1）
- [ ] Mapper 接口加 `@Mapper`，手写 SQL 显式过滤 `deleted = 0`（3.2）
- [ ] Request/QueryRequest/Response 齐全，验证注解与消息格式正确（3.3）
- [ ] Service 写操作加 `@Transactional(rollbackFor = Exception.class)`（3.4）
- [ ] Controller 位于正确的 `admin`/`app` 子包，`@RequestMapping` 无重复前缀（3.5）
- [ ] 权限注解使用 `hasAuthority` 且权限码格式正确（3.5）
- [ ] 写操作加 `@OperationLog`（4.1）
- [ ] 迁移脚本命名 `V{YYYYMMDD}{seq}__{description}.sql`，放在模块自己的 `db/migration/`（5.1-5.2）
- [ ] 菜单/权限 SQL 覆盖目录-菜单-按钮三层并分配管理员角色（6.1）
- [ ] `mvn clean compile` 编译通过

---

*文档版本：v1.0*
*最后更新：2026-10-12*
