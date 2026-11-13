# 后端开发规范

> 适用范围：`apps/forge-server`（Java 21 / Spring Boot 4 / MyBatis Plus）。
>
> 配套文档：新模块端到端搭建流程见 [新模块开发向导](./module-development.md)；数据库设计与迁移脚本细节见 [数据库设计规范](./database-design-specification.md)；前端对接见 [前端开发规范](./frontend-development-specification.md)。

## 0. 代码风格总则

- **禁止在代码正文使用类全限定名**（如 `java.util.concurrent.CompletableFuture`、`javax.net.ssl.SSLSocket`、`org.springframework.web.servlet.mvc.method.annotation.SseEmitter`），一律在文件头部 `import` 后使用简名。正文全限定名仅在两种场景可接受：
  1. 同文件存在**同名类冲突**（如 `tools.jackson` 与 `com.fasterxml` 的 `ObjectMapper` 并存），且冲突处不超过个别行——此时优先考虑换局部变量命名或方法封装规避，仍无法规避才用全限定名并加注释说明；
  2. 单行一次性工具调用且无碍可读性（少用）。
- import 分组习惯：第三方/框架 → `java.*`/`javax.*`，组内按包名字典序；禁止 `import xxx.*` 通配（Entity 的 MyBatis-Plus 注解等既有惯例除外）。

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
        └── mapper/{module}/
            └── XxxMapper.xml      # MyBatis XML（落位规则见 §3.2）
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
- JSON 字段用 `@TableField(typeHandler = Jackson3TypeHandler.class)` + `Map<String, Object>` 类型（Jackson 3 线；旧 `JacksonTypeHandler` 基于 Jackson 2 禁止新用）
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

    // 自定义 SQL 落位见下方「SQL 落位规则」
}
```

对应的 XML 放在 `resources/mapper/{module}/` 下（如需自定义 SQL）：

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

#### SQL 落位规则（ArchUnit 守卫）

能用 MyBatis Plus 的 `BaseMapper` / `Wrapper` 表达的查询不手写 SQL；需要手写时按下表落位：

| 场景 | 写法 |
|------|------|
| 单行简单 SQL：单段、≤100 字符、无 `<script>` | 可用 `@Select` / `@Insert` / `@Update` / `@Delete` 注解 |
| 多行、`<if>` / `<foreach>` 等动态 SQL、超过 100 字符 | 一律写 `src/main/resources/mapper/{module}/XxxMapper.xml`，`namespace` 为接口全限定名 |
| mapper 已有 XML | 该 mapper 的全部 SQL 写进 XML，不再混用注解（同一 mapper 的 SQL 只放一处） |

- mapper 声明的每个抽象方法都必须绑定 SQL（注解或 XML 中同 id 语句），禁止留无绑定的声明——调用时才会抛 `BindingException`
- XML 中 `<select>` 显式写 `resultType`（全限定名，与方法返回的元素类型一致，`Map` 返回写 `java.util.Map`）；`insert` / `update` / `delete` 省略即按方法返回类型推断
- 需要在 SQL 里引用 Java 常量时，用 `<sql>` 片段 + OGNL 静态引用保持单一来源，如 `<sql id="batchLimit">LIMIT ${@com.forge.modules.ai.agent.lifecycle.AiDataCleanupMapper@BATCH_LIMIT}</sql>`，使用处 `<include refid="batchLimit"/>`；常量改名不会编译报错，需配测试守护（参考 `AiMapperXmlGuardTest`）
- 机器守卫：`forge-server` 的 `ArchitectureTest`「五、Mapper SQL 落位」三条规则（方法必须绑定 SQL / 不混放 / 注解只许单行简单 SQL），随 CI `mvn test` 执行；100 字符阈值即 `MapperSqlConditions.MAX_ANNOTATION_SQL_LENGTH`

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

#### 外部调用约束

- `RestTemplate`/`RestClient` **必须显式 connect+read 超时**（3-5s 量级）且**单例复用**（Bean 或静态常量），禁每请求 `new RestTemplate()`（无超时、无连接复用）。
- 配置注入的超时**必须验证真实生效**——警惕无参 `Xxx.create()` 覆盖带参工厂的死分支（screen `http-timeout-ms` 配置失效教训），配套测试断言 factory 超时值。
- 外部调用超时/重试参数**必须有服务端钳制上限**（`@Max` + 服务端 `Math.min`），不信任客户端传值。

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

### 4.5 资源归属与防泄露

- 按 id 直查的资源（会话/文档/运行记录/任务等），操作前必须走 **`requireOwnedXxx(id)` 归属校验**：不存在或（非管理员且非本人）→ **404**（不泄露存在性）；列表查询非管理员强制追加 userId 条件。管理员豁免统一 `SecurityHelper.isAdmin()` / `workflow_admin` 角色。
- 错误响应**不回传 `e.getMessage()`/内部异常细节**——固定友好文案 + 服务端日志留详情。
- 敏感字段出参走**类型级脱敏**（如 `hasSecret` 布尔替代明文，参照 `ImChannelConfigResponse`），禁在 Controller 散点 mask。
- 引擎/框架内部键（`__` 前缀变量、节流标记等）不得随接口出参。

事故出处：AI 会话/文档/运行记录横向越权（任意用户枚举读全部对话）、错误帧回传内部异常细节。

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

## 9. 并发、事务与逻辑删除卫生

> 来源：2026-11-12 后端全量代码评审（175 条）中的四处 P1 级并发事故模式。逐条含真实事故出处。

### 9.1 逻辑删除表 + 含 deleted 列的唯一索引（墓碑惯用法）

当逻辑删除表的唯一索引包含 `deleted` 列时（如 `uk(tenant_id, channel, deleted)`）：

- **墓碑值必须每行唯一**：删除时 `deleted` 置**本行 id**（或 `NULL`，MySQL 唯一索引不约束 NULL）；**禁止置 0/1**——同 `(业务键)` 第二块墓碑必撞唯一索引，"删除-重建-再删除"直接失败。
- **`@TableLogic` 必须显式 `delval`**（如 `delval = "id"`），**禁用裸注解**：裸注解默认 delval=1，将来任何人调 MP 自带 `deleteById` 都会生成墓碑=1 撞索引。无此唯一索引约束的表，裸注解即可，不强制全仓显式化。
- **逻辑删除字段类型用 `BIGINT/Long`**（容纳行 id 墓碑值）；H2 schema 与生产 DDL **列型逐列一致**（TINYINT 上限 127 会静默吞掉大 id 测试覆盖）。
- 删除入口优先**显式 `LambdaUpdateWrapper.set(删除列, 行id)`**，不走 MP 自带 `deleteById`（绕开 delval 机制歧义）。`@TableLogic` 仍保留：查询/更新自动追加 `deleted=0` 过滤。

参照实现：`WfImChannelConfig` + `ImChannelConfigService.deleteConfig` + `sql/upgrade-20261112-im-unique-tenant-channel.sql`。

事故出处：MP 默认 `SET deleted=1` 与墓碑=id 惯用法冲突（89e8416e 加固前，"删除-重建-再删除"必撞 `uk_tenant_channel`）。

### 9.2 状态变迁必须 CAS

任何「读状态 → 判断 → 写状态」的竞态点，写入必须防并发双穿：

- **简单状态翻转**：条件更新 `UPDATE ... SET status=新 WHERE id=? AND status=旧`，判受影响行数——0 行即已被并发处理（抛"已被处理"或静默跳过，视语义）。禁 `updateById` 无条件覆盖竞态字段。
- **读-算-写多字段**（如 JSON 列读改写）：`SELECT ... FOR UPDATE` 行锁串行化（H2/MySQL 均支持）。
- **保护用户输入**：自动写入可能覆盖用户手工数据时，条件带旧值（`WHERE title=旧占位值`），0 行放弃。
- **check-then-insert 并发双插**：唯一约束兜底 + insert 捕获 `DuplicateKeyException` 重查转更新。先查后插不能作为唯一防线。

事故出处：催办节流并发双穿透（WfTaskServiceUrge FOR UPDATE 行锁修复）、Agent 待办双审批双恢复（PENDING 条件更新抢占）、标题生成覆盖用户手动改名（乐观锁）、IM 配置并发双行瘫痪管理页（唯一索引）。

### 9.3 事务内禁止同步外呼

`@Transactional` 方法内**禁止同步调用**：WebSocket 推送、HTTP 外呼、IM 渠道、LLM 等——慢发送拉长事务持锁；回滚后外呼不可撤回（幽灵通知）。

事务内要发通知/触发外呼，走事件化：

1. 事务内组装并 `publishEvent`（事件为 **record 不可变快照**，内容发布时刻烘焙）；
2. 监听器固定组合：`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` + `@Async("workflowExecutor")`；
3. 组装依赖引擎/库内数据的部分**留在事务内**，只把投递事件化。

参照实现：`TaskNotificationEvent` + `TaskNotifyEventHandler`（催办/待办通知共用）。

事故出处：催办通知事务内直发——回滚后用户收到"催办"但库里无痕迹；慢发送拉长事务。

### 9.4 异步执行约束

- `@Async` **必须显式指定池名**（如 `@Async("workflowExecutor")`）——缺省回退 `SimpleAsyncTaskExecutor`（每任务新建线程、无界）。
- `CompletableFuture.supplyAsync` **必须显式 executor，禁用 commonPool**——`cancel(true)` 对 CompletableFuture 不中断底层线程，超时后 commonPool 被白烧线程耗尽（AI 决策 45s 超时事故根因）。
- 长耗时并行块 `join()` **必须带 `orTimeout`**（超时走降级路径），禁裸 `join()` 无限等待。
- 跨线程任务提交前**快照租户上下文**（`TenantContextHolder.getTenantId()` → 子线程 set → finally 恢复/clear），或线程池统一 `TtlRunnable::get` TaskDecorator 包装；注意 `UserContext` 是普通 ThreadLocal **不会自动传递**，必须参数显式传。

事故出处：agentExecutor 单池互等死锁、决策竞速 commonPool 白烧、候选人策略异步链 NPE。

### 9.5 安全闸 fail-closed

- 涉租户/归属/白名单的查询，**缺上下文或解析失败 → 返回空/拒绝 + 告警，禁止猜默认值兜底**（兜底"默认租户 1" = 跨租户串发/串读）。
- fail-closed 的判别条件是**闸态**（`TenantProperties.enable`）：关闸单租户部署可按历史语义兜底默认租户；开闸严格拒绝。
- 表达式求值（SpEL 等）**禁 `StandardEvaluationContext`**，一律 `SimpleEvaluationContext`；`Class.forName` 动态实例化禁用。

事故出处：IM/回调租户来源塌陷、候选人策略 UserContext 异步 NPE、触发器 SpEL RCE 面。

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
- [ ] 状态变迁写入用条件更新（`WHERE status=旧值`）或行锁，判受影响行数（9.2）
- [ ] 事务内无同步外呼（WebSocket/HTTP/IM/LLM），通知走事件 + AFTER_COMMIT 监听器（9.3）
- [ ] `@Async` 显式指定池名；`supplyAsync` 显式 executor；并行 `join()` 带 `orTimeout`（9.4）
- [ ] 跨线程任务已快照/恢复租户上下文（9.4）
- [ ] 逻辑删除表含 deleted 唯一索引时：墓碑置行 id、`@TableLogic(delval="id")`、字段 BIGINT、H2 与生产列型一致（9.1）
- [ ] 按 id 操作资源已做归属校验（requireOwnedXxx，404 语义）（4.5）
- [ ] 外部调用带显式超时且配置真实生效（3.4）
- [ ] `mvn clean compile` 编译通过

---

*文档版本：v1.1*
*最后更新：2026-11-13*

**v1.1 变更记录（2026-11-13）**：新增第 9 章「并发、事务与逻辑删除卫生」（逻辑删除墓碑惯用法/状态 CAS/事务内禁外呼与事件化/异步执行约束/安全闸 fail-closed）；4.5 新增「资源归属与防泄露」；3.4 新增「外部调用约束」；检查清单增补 8 项。来源：2026-11-12 后端全量代码评审（175 条，docs/code-review/2026-11-12-backend-review.md）五批修复沉淀，条款均含真实事故出处。
