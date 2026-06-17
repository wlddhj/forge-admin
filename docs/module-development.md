# 新模块开发规范

本文件总结了项目中实际使用的开发模式和规范，用于开发新模块时参考。

---

## 一、后端（Java / Spring Boot）

### 1.1 模块结构

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

### 1.3 Maven 配置

#### API 模块 pom.xml

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

#### BIZ 模块 pom.xml

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

#### 父模块 pom.xml

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

#### 其他配置步骤

1. **根 pom.xml** 在 `apps/forge-server/pom.xml` 的 `<modules>` 中添加：
```xml
<module>forge-module-{module}</module>
```

2. **forge-dependencies** 在 `forge-dependencies/pom.xml` 的 `<dependencyManagement>` 中添加：
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

3. **forge-server** 在 `forge-server/pom.xml` 的 `<dependencies>` 中添加：
```xml
<dependency>
    <groupId>com.forge</groupId>
    <artifactId>forge-module-{module}-biz</artifactId>
</dependency>
```

### 1.4 Entity 层

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

### 1.5 Mapper 层

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

### 1.6 DTO 层

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

### 1.7 Service 层

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

### 1.8 Controller 层

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

### 1.9 横切关注点

#### 操作日志

```java
@OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.INSERT)
```

BusinessType 类型：`INSERT`、`UPDATE`、`DELETE`、`EXPORT`、`IMPORT`、`OTHER`

#### 数据权限

```java
@DataPermission(deptAlias = "d", userAlias = "u")
```

适用于需要按部门/用户数据范围过滤的查询。

#### 接口限流

```java
@RateLimiter(keyType = KeyType.USER, time = 60, count = 10)
```

#### 缓存

```java
@Cacheable(value = "xxx", key = "#id")
@CacheEvict(value = "xxx", key = "#id")
```

缓存名：`dictData`、`dictType`、`sysConfig`、`userInfo`、`menu`、`dept`

---

## 二、前端（Vue 3 + TypeScript + vxe-table）

### 2.1 文件结构

```
src/
├── api/{module}/           API 接口文件
│   └── {entity}.ts         接口定义 + 类型 + 请求函数
├── views/{module}/         页面组件
│   └── {entity}/           页面目录
│       └── index.vue       CRUD 页面
├── constants/dict.ts       字典类型常量
├── components/             公共组件
│   ├── DictValue.vue       字典标签渲染
│   ├── MobileSearchDrawer.vue  移动端搜索抽屉
│   ├── MobileSearchButton.vue  移动端搜索按钮
│   └── MobileBottomActions.vue 移动端底部操作栏
└── composables/
    ├── useDict.ts          字典数据 composable
    ├── useResponsive.ts    响应式布局 composable
    ├── useTableHeight.ts   表格高度自适应
    ├── useTableSeq.ts      序号计算
    └── useTableSort.ts     排序处理
```

### 2.2 API 文件

```typescript
// src/api/{module}/{entity}.ts
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

// 查询参数接口
export interface XxxQuery {
  pageNum: number
  pageSize: number
  name?: string
  status?: number
}

// 实体接口
export interface XxxEntity {
  id: number
  name: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}

// API 函数
export const xxxApi = {
  page: (params: XxxQuery) => request.get<PageResult<XxxEntity>>('/{module}/{entity}/list', { params }),
  get: (id: number) => request.get<XxxEntity>(`/{module}/{entity}/${id}`),
  add: (data: Partial<XxxEntity>) => request.post('/{module}/{entity}', data),
  update: (data: Partial<XxxEntity>) => request.put('/{module}/{entity}', data),
  delete: (ids: number[]) => request.delete('/{module}/{entity}', { data: ids })
}
```

**要点：**
- 查询用 `GET + params`，提交用 `POST/PUT + data`
- 删除用 `DELETE + body`（`{ data: ids }`）
- 所有接口字段可选（`?`）
- 接口返回值自动被 `request.ts` 拦截器解析为 `Result<T>`

### 2.3 页面组件（vxe-table 模板）

页面结构标准模板：

```vue
<template>
  <div class="app-container">
    <!-- 1. 搜索栏（桌面端表单 + 移动端按钮） -->
    <el-card shadow="never" class="search-card">
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <div v-else class="mobile-search-actions">
        <span class="title">{页面标题}</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount"
            @click="searchDrawerVisible = true" />
        </div>
      </div>
    </el-card>

    <!-- 2. 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams"
      @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="名称" style="width: 100%">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item label="状态" style="width: 100%">
          <el-select v-model="queryParams.status" clearable style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 3. 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'{module}:{entity}:add'" type="primary" @click="handleAdd">新增</el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-refresh" style="margin-right: 10px" @click="handleQuery" />
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="{entity}Table"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="name" title="名称" min-width="150" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column v-if="!isMobile" title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'{module}:{entity}:query'" type="primary" link
              @click.stop="handleView(row)">详情</el-button>
            <el-button v-permission="'{module}:{entity}:edit'" type="warning" link
              @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'{module}:{entity}:delete'" type="danger" link
              @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="getList" />
    </el-card>

    <!-- 4. 移动端底部操作栏 -->
    <MobileBottomActions :show="!!selectedRow" :item="selectedRow"
      :item-title="selectedRow?.name" @cancel="cancelSelection">
      <template #actions="{ item }">
        <el-button v-permission="'{module}:{entity}:query'" size="small"
          @click.stop="handleView(item)">详情</el-button>
        <el-button v-permission="'{module}:{entity}:edit'" size="small" type="warning"
          @click.stop="handleEdit(item)">编辑</el-button>
        <el-button v-permission="'{module}:{entity}:delete'" size="small" type="danger"
          @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 5. 新增/编辑抽屉 -->
    <el-drawer v-model="dialogVisible" :title="dialogTitle" size="700px" direction="rtl">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-drawer>

    <!-- 6. 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="详情" size="700px" direction="rtl">
      <el-descriptions :column="isMobile ? 1 : 2" border>
        <el-descriptions-item label="名称">{{ detailData.name }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailData.status === 1 ? 'success' : 'danger'">
            {{ detailData.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData.remark }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>
```

### 2.4 Script 标准模板

```vue
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { xxxApi } from '@/api/{module}/{entity}'
import type { XxxEntity, XxxQuery } from '@/api/{module}/{entity}'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const { isMobile } = useResponsive()
const { tableHeight } = useTableHeight()

// 表格引用
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 查询参数
const queryParams = reactive<XxxQuery>({
  pageNum: 1, pageSize: 20,
  name: '', status: undefined
})

// 表格数据
const loading = ref(false)
const tableData = ref<XxxEntity[]>([])
const total = ref(0)

// 移动端
const searchDrawerVisible = ref(false)
const selectedRow = ref<XxxEntity | null>(null)
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.name) count++
  if (queryParams.status !== undefined) count++
  return count
})

// 详情
const detailVisible = ref(false)
const detailData = ref<XxxEntity>({} as XxxEntity)

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const formData = ref<Partial<XxxEntity>>({})
const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await xxxApi.page(queryParams)
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.name = ''; queryParams.status = undefined; handleQuery() }

// 移动端搜索
const handleSearchFromDrawer = () => { handleQuery(); searchDrawerVisible.value = false }
const handleResetFromDrawer = () => { handleReset(); searchDrawerVisible.value = false }

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: XxxEntity | null }) => {
  if (isMobile.value) {
    selectedRow.value = row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
  if (tableRef.value) {
    tableRef.value.clearCurrentRow()
  }
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增'
  formData.value = {}
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: XxxEntity) => {
  dialogTitle.value = '编辑'
  const res = await xxxApi.get(row.id)
  formData.value = res.data || {}
  dialogVisible.value = true
}

// 详情
const handleView = async (row: XxxEntity) => {
  const res = await xxxApi.get(row.id)
  detailData.value = res.data || {}
  detailVisible.value = true
}

// 删除
const handleDelete = async (row: XxxEntity) => {
  try {
    await ElMessageBox.confirm('确认删除吗？', '提示', { type: 'warning' })
    await xxxApi.delete([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    if (formData.value.id) {
      await xxxApi.update(formData.value)
      ElMessage.success('更新成功')
    } else {
      await xxxApi.add(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch (error) {
    console.error('保存失败:', error)
  }
}

// 初始化
onMounted(() => {
  getList()
  // 关联工具栏与表格
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})
</script>
```

### 2.5 vxe-table 属性对照

**表格属性：**

| 属性 | 说明 |
|------|------|
| `id="{entity}Table"` | 唯一标识（必填，用于列配置持久化） |
| `:custom-config="{mode: 'modal'}"` | 自定义列弹窗模式 |
| `:height="tableHeight"` | 自适应高度（使用 useTableHeight） |
| `:loading="loading"` | 加载状态 |
| `:row-config="{ isCurrent: true, isHover: true }"` | 行配置 |
| `:column-config="{ resizable: true }"` | 列宽可调整 |
| `border="none"` | 边框样式（全局配置已设置） |
| `stripe` | 斑马纹 |
| `show-overflow="tooltip"` | 内容溢出提示 |
| `show-header-overflow="tooltip"` | 表头溢出提示 |
| `@current-change="handleCurrentChange"` | 行选中变化（移动端） |

**列属性：**

| 属性 | 说明 |
|------|------|
| `field` | 字段名 |
| `title` | 列标题 |
| `type="seq"` | 序号列（配合 :seq-method） |
| `width` | 固定宽度 |
| `min-width` | 最小宽度 |
| `fixed="right"` | 固定列 |
| `align="center"` / `align="right"` | 对齐方式 |

**工具栏属性：**

| 属性 | 说明 |
|------|------|
| `custom` | 启用自定义列功能 |
| `#buttons` | 左侧按钮插槽 |
| `#tools` | 右侧工具插槽 |

### 2.6 树形表格配置

```vue
<vxe-table
  :tree-config="{
    expandAll: !isMobile,
    indent: 20,
    transform: true,      <!-- 关键：启用 transform 模式 -->
    rowField: 'id',       <!-- 行唯一标识字段 -->
    parentField: 'parentId'  <!-- 父节点字段 -->
  }"
  :row-config="{ keyField: 'id', isCurrent: true, isHover: true }"
  ...
>
```

**展开/折叠控制：**

```typescript
const isAllExpand = ref(true)

const toggleExpandAll = () => {
  if (tableRef.value) {
    if (isAllExpand.value) {
      tableRef.value.clearTreeExpand()
    } else {
      tableRef.value.setAllTreeExpand(true)
    }
    isAllExpand.value = !isAllExpand.value
  }
}

// 高度配置（树形表格无分页）
const { tableHeight } = useTableHeight({ hasPagination: false })
```

### 2.7 多选功能（批量操作）

```vue
<vxe-table
  :checkbox-config="{ reserve: true }"
  @checkbox-change="handleCheckboxChange"
  @checkbox-all="handleCheckboxChange"
  @checkbox-range-end="handleCheckboxChange"
>
  <vxe-column type="checkbox" width="50" />
  ...
</vxe-table>
```

**checkbox-config 配置：**

| 属性 | 说明 |
|------|------|
| `reserve: true` | 跨页保留选中状态 |
| `checkField: 'checked'` | 指定选中状态的字段名 |
| `highlight: true` | 选中行高亮显示 |

**处理批量选择：**

```typescript
const selectedIds = ref<number[]>([])

const handleCheckboxChange = () => {
  if (tableRef.value) {
    const selectRecords = tableRef.value.getCheckboxRecords()
    selectedIds.value = selectRecords.map((item: any) => item.id)
  }
}

const cancelSelection = () => {
  selectedRow.value = null
  selectedIds.value = []
  if (tableRef.value) {
    tableRef.value.clearCheckboxRow()
    tableRef.value.clearCurrentRow()
  }
}
```

### 2.8 标准样式

页面样式**不需要**重复定义基础布局，只需定义特有的业务样式：

```scss
<style scoped lang="scss">
// 只定义页面特有的样式
.weight-warning {
  color: #F56C6C;
  font-weight: bold;
}

.mobile-search-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  .title { font-size: 18px; font-weight: bold; }
  .actions { display: flex; gap: 8px; }
}
</style>
```

**禁止使用：**
- ❌ `<el-card><template #header>` 包裹标题
- ❌ 自定义容器类名（如 `xxx-container`）
- ❌ 在页面中重复定义 `.app-container` / `.search-card` / `.table-card` 样式

### 2.9 数据字典使用指南

数据字典用于管理枚举类型数据，实现下拉选项、状态标签等的统一管理和复用。

#### 核心组件

| 组件/文件 | 说明 |
|-----------|------|
| `constants/dict.ts` | 字典类型常量定义 |
| `composables/useDict.ts` | 字典数据加载 Composable |
| `components/DictValue.vue` | 字典标签渲染组件 |

#### 前端使用流程

**步骤 1：定义字典常量**

在 `constants/dict.ts` 中添加：

```typescript
/** {描述} */
{MODULE}_{ENTITY}_{ATTRIBUTE}: '{module}_{entity}_{attribute}',
```

命名规范：`UPPER_SNAKE_CASE`，格式为 `模块_实体_属性`。

**步骤 2：导入并使用字典**

```typescript
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import DictValue from '@/components/DictValue.vue'

// 获取字典数据（用于下拉选项）
const { dictData: xxxOptions } = useDict(DICT_TYPE.XXX)

// 获取字典标签（用于单独转换）
const { getDictLabel, getTagType } = useDict(DICT_TYPE.XXX)
```

**步骤 3：下拉选项绑定**

```vue
<el-select v-model="queryParams.status" clearable>
  <el-option v-for="item in statusOptions" :key="item.dictValue"
    :label="item.dictLabel" :value="item.dictValue" />
</el-select>
```

**步骤 4：表格列字典渲染**

```vue
<vxe-column field="status" title="状态" width="100">
  <template #default="{ row }">
    <dict-value :dict-type="DICT_TYPE.XXX" :value="row.status" />
  </template>
</vxe-column>
```

#### useDict Composable API

```typescript
const {
  dictData,       // DictData[] - 字典数据数组
  loading,        // boolean - 加载状态
  loadDictData,   // () => Promise<void> - 手动刷新
  getDictLabel,   // (value: string | number) => string - 获取标签文本
  getTagType,     // (value: string | number) => string - 获取标签类型
  getCssClass     // (value: string | number) => string - 获取自定义样式
} = useDict(dictType)
```

#### 后端 SQL 模板

```sql
-- 字典类型
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `is_system`, `create_time`, `update_time`, `deleted`) VALUES
('{字典名称}', '{module}_{entity}_{attr}', 1, 0, NOW(), NOW(), 0);

-- 字典数据（listClass 用于指定标签样式）
INSERT INTO `sys_dict_data` (`dict_type`, `dict_label`, `dict_value`, `dict_sort`, `list_class`, `status`, `create_time`, `update_time`, `deleted`) VALUES
('{dict_type}', '启用', '1', 1, 'success', 1, NOW(), NOW(), 0),
('{dict_type}', '禁用', '0', 2, 'danger', 1, NOW(), NOW(), 0);
```

**list_class 常用值：**
- `success`（绿色）→ 正常/启用/成功状态
- `danger`（红色）→ 异常/禁用/失败状态
- `warning`（橙色）→ 警告/待处理状态
- `primary`（蓝色）→ 默认/重要状态
- `info`（灰色）→ 次要状态

---

## 三、数据库迁移

### 3.1 迁移脚本位置

```
forge-server/src/main/resources/db/migration/
```

### 3.2 命名规范

```
V{YYYYMMDD}{seq}__{description}.sql

示例：
V2024061701__create_xxx_table.sql
V2024061702__add_xxx_status_column.sql
```

### 3.3 表结构模板

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

---

## 四、菜单与权限 SQL

### 4.1 菜单数据 SQL 模板

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

**菜单类型说明：**
- `type=0`：目录
- `type=1`：菜单
- `type=2`：按钮

---

## 五、关键约定速查

| 项目 | 规范 |
|------|------|
| 权限注解 | `@PreAuthorize("hasAuthority('xxx')")` — **不是** `@ss.hasPerm` |
| 权限格式 | `{module}:{entity}:{action}`，action: list/query/add/edit/delete/import |
| 前端权限 | `v-permission="'module:entity:action'"` 或脚本 `hasPermission()` |
| 分页响应 | `PageResult.of(page)` 或 `PageResult.of(records, total, pageNum, pageSize)` |
| 实体转换 | `BeanUtil.copyProperties(source, Target.class)` 或 `page.convert()` |
| 异常抛出 | `throw new BusinessException("xxx不存在")` |
| 下拉选项 | 全部用数据字典，通过 `useDict` + `DICT_TYPE` 获取 |
| 列表枚举显示 | 用 `<dict-value :dict-type="DICT_TYPE.XXX" :value="row.xxx" />` |
| API 请求 | `import request from '@/utils/request'`，GET 用 params，POST/PUT 用 data |
| 移动端适配 | `useResponsive()` → `isMobile`，搜索抽屉 + 底部操作栏 |
| 表格组件 | 使用 vxe-table（不是 el-table） |
| 分页组件 | `<TablePagination v-model:page-num v-model:page-size :total @change />`（不是 el-pagination），默认 `pageSize: 20` |
| 表格高度 | `useTableHeight()` / `useTableHeight({ hasPagination: false })` |
| 序号列 | `<vxe-column type="seq" :seq-method="seqMethod" />` + `useTableSeq` |
| 工具栏 | `<vxe-toolbar custom>` + `tableRef.value.connect(toolbarRef.value)` |
| 刷新按钮 | `<vxe-button circle icon="vxe-icon-refresh" style="margin-right: 10px" />` |
| 行选中 | `@current-change="handleCurrentChange"` + `tableRef.value.clearCurrentRow()` |
| 菜单类型 | 0=目录, 1=菜单, 2=按钮 |
| 菜单 component | 目录='Layout', 菜单='/views/xxx/yyy/index', 按钮='' |
| Controller 路径 | `/{module}/{entity}/{action}` |
| 数据库表名 | `snake_case`，前缀 `sys_`/`wf_`/`ai_` 等 |
| Java 类名 | `PascalCase` |
| 包名 | `com.forge.modules.{module}` |

---

## 六、vxe-table 开发检查清单

### 6.1 必须检查项

- [ ] 添加 `id="xxxTable"` 唯一标识
- [ ] 添加 `:custom-config="{mode: 'modal'}"`
- [ ] 添加 `:row-config="{ isCurrent: true, isHover: true }"`
- [ ] 添加 `:column-config="{ resizable: true }"`
- [ ] 添加 `show-header-overflow="tooltip"`
- [ ] 添加序号列 `<vxe-column type="seq" ... :seq-method="seqMethod" />`
- [ ] 添加工具栏 `<vxe-toolbar>` 并关联表格
- [ ] 导入 `VxeTableInstance` / `VxeToolbarInstance` 类型
- [ ] 导入并使用 `useTableHeight` / `useTableSeq`
- [ ] 添加 `@current-change` 处理移动端选中

### 6.2 树形表格额外检查项

- [ ] 添加 `:tree-config="{ transform: true, ... }"`
- [ ] 添加 `:row-config="{ keyField: 'id', ... }"`
- [ ] 配置 `useTableHeight({ hasPagination: false })`
- [ ] 添加展开/折叠按钮和逻辑

### 6.3 多选表格额外检查项

- [ ] 添加 `<vxe-column type="checkbox" />`
- [ ] 添加 `:checkbox-config="{ reserve: true }"`
- [ ] 处理 `@checkbox-change` 事件
- [ ] 处理 `@checkbox-all` 事件
- [ ] 处理 `@checkbox-range-end` 事件（拖选范围选择）
- [ ] 添加 `clearCheckboxRow()` 取消选择逻辑

---

## 七、新模块开发检查清单

创建新模块时，确保完成以下步骤：

1. ✅ 创建模块目录结构（api + biz）
2. ✅ 配置 Maven pom.xml（父模块、api、biz、dependencies、forge-server）
3. ✅ 创建实体类（api 模块）
4. ✅ 创建请求/响应 DTO（api 模块）
5. ✅ 创建 Mapper 接口（biz 模块）
6. ✅ 创建 Service 接口和实现（biz 模块）
7. ✅ 创建 Controller（biz 模块，放在 `controller.admin` 或 `controller.app` 包下）
8. ✅ 编写数据库迁移脚本
9. ✅ 插入菜单和权限数据
10. ✅ 运行 `mvn clean compile` 验证编译
11. ✅ 创建前端 API 定义
12. ✅ 创建前端页面组件
13. ✅ 配置前端路由（通常由菜单自动生成）

---

## 八、常见问题

### Q1: 工具栏自定义列按钮不显示？

确保 `vxe-toolbar` 设置了 `custom` 属性，且表格有 `id` 和 `custom-config`。

### Q2: 序号不连续？

检查 `useTableSeq` 的 `currentPage` 和 `pageSize` 是否正确绑定到查询参数。

### Q3: 树形表格展开/折叠不生效？

检查 `tree-config` 中 `rowField` 和 `parentField` 是否匹配数据结构，`row-config` 中是否设置了 `keyField`。

### Q4: 列宽无法调整？

检查 `column-config="{ resizable: true }"` 是否设置，且列未设置 `fixed`（固定列不可调整宽度）。

### Q5: 移动端行选中不触发？

检查表格是否添加了 `@current-change="handleCurrentChange"`，且处理函数中判断了 `isMobile.value`。

---

*文档版本：v3.0*
*最后更新：2026-06-17*