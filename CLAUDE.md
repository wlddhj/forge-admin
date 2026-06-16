# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

forge-admin 是一个基于 RBAC 的企业级后台管理系统，采用 monorepo 结构，前后端分离。

**技术栈：**
- 前端：Vue 3.4 + TypeScript + Element Plus + vxe-table + Pinia + Vite 5
- 后端：Spring Boot 3.2.0 + MyBatis Plus 3.5.7 + MySQL + Redis + Flowable 7.0.1
- 认证：JWT Token（访问令牌 2 小时，刷新令牌 7 天）
- API 文档：Knife4j，地址 `/api/doc.html`

## 关键配置

| 服务 | 端口 | 路径 |
|------|------|------|
| 前端开发 | 3003 | `apps/forge-web` |
| 小程序开发 | - | `apps/forge-miniapp` |
| 后端 API | 8181 | `apps/forge-server` |
| 上下文路径 | - | `/api` |

**数据库：** MySQL `forge_admin`，localhost:3306 | **Redis：** localhost:6379（无密码） | **Java：** 21 | **Node：** 22.9.0 | **pnpm：** 8.15.4

## 开发命令

### 前端（在 `apps/forge-web` 目录下）
```bash
pnpm install    # 安装依赖
pnpm dev        # 启动开发服务器（端口 3003）
pnpm build      # 生产构建（含类型检查）
pnpm lint       # 运行 ESLint
pnpm test       # 运行 vitest 单元测试
```

### 小程序（在 `apps/forge-miniapp` 目录下）
```bash
pnpm install        # 安装依赖
pnpm dev:mp-weixin  # 微信小程序开发模式
pnpm build:mp-weixin # 微信小程序生产构建
```

### 后端（在 `apps/forge-server` 目录下）
```bash
mvn spring-boot:run -pl forge-server          # 启动开发服务器
mvn clean compile                              # 仅编译
mvn clean package -DskipTests                  # 打包 JAR（跳过测试）
mvn clean install -DskipTests                  # 安装到本地仓库
mvn test -pl forge-module-system/forge-module-system-biz  # 运行指定模块测试
mvn test -Dtest=ClassName -pl <module>         # 运行单个测试类
```

## 后端架构

### 多模块结构

后端是一个 14 模块的 Maven 项目，包名基类为 `com.forge`。

```
apps/forge-server/
├── pom.xml                          # 根聚合 POM（parent: spring-boot-starter-parent:3.2.0）
├── forge-dependencies/              # BOM 版本管理（纯 POM，无 Java 代码）
├── forge-framework/                 # 框架层
│   ├── forge-common/                # 纯公共：注解、枚举、异常、响应、JSON 序列化、工具类
│   ├── forge-spring-boot-starter-mybatis/   # MyBatis Plus 配置、数据权限框架
│   ├── forge-spring-boot-starter-redis/     # Redis 配置、缓存配置
│   ├── forge-spring-boot-starter-security/  # JWT、OAuth2、JustAuth 社交登录
│   └── forge-spring-boot-starter-web/       # Jackson、全局异常、WebSocket、Excel、限流
├── forge-module-system/             # 系统模块
│   ├── forge-module-system-api/     # 实体 + DTO（82 个文件）
│   └── forge-module-system-biz/     # Controller/Service/Mapper（含 auth、quartz）
├── forge-module-workflow/           # 工作流模块
│   ├── forge-module-workflow-api/   # 实体 + DTO（44 个文件）
│   └── forge-module-workflow-biz/   # Controller/Service/Mapper + Flowable 集成
└── forge-server/                    # Spring Boot 启动入口（ForgeAdminApplication.java）
```

**模块依赖关系：**
```
forge-server ← system-biz, workflow-biz
workflow-biz ← workflow-api, system-api, starters, flowable
system-biz ← system-api, starters, quartz, justauth
starter-security ← forge-common, system-api
starter-mybatis ← forge-common
starter-web ← forge-common
starter-redis ← forge-common
```

### 模块分层模式

每个业务模块遵循：`controller/` → `service/`（接口 + impl）→ `mapper/` → 实体在 `api/` 模块，`dto/` 在 `api/` 模块。

**包结构：**
- `com.forge.common` — 公共（annotation, enumeration, exception, json, response, utils）
- `com.forge.framework.mybatis` — MyBatis 配置、数据权限（permission/）
- `com.forge.framework.redis` — Redis 配置
- `com.forge.framework.security` — JWT、OAuth2 配置
- `com.forge.framework.web` — Web 配置、全局异常、WebSocket
- `com.forge.modules.system` — system + auth + quartz
- `com.forge.modules.workflow` — 工作流

### 横切关注点

- `@OperationLog(title, businessType)` — 审计日志（starter-web 中）
- `@DataPermission(deptAlias, userAlias)` — SQL 级数据范围过滤（starter-mybatis 中，5 种范围类型）
- `@RateLimiter(keyType, time, count)` — Redis 令牌桶限流（starter-web 中）
- `@Cacheable/@CacheEvict` — Redis 缓存（缓存名：dictData, dictType, sysConfig, userInfo, menu, dept）
- `@Valid @RequestBody` — Jakarta DTO 校验

### Spring Boot Starter 自动配置

每个 starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册。修改 starter 类时需同步更新此文件。

### 数据库迁移

位置：`apps/forge-server/forge-server/src/main/resources/db/migration/`
命名：`V{YYYYMMDD}{seq}__{description}.sql`

## 前端架构（`apps/forge-web/src/`）

```
api/           # API 模块，带类型的请求/响应接口
composables/   # Vue 组合式函数（useWebSocket, useDict, useResponsive, useTableHeight, useTableSeq）
directives/    # v-permission, v-role（无权限时移除 DOM 元素）
layouts/       # BasicLayout：侧边栏、头部、标签页、通知
plugins/       # vxe-table 全局配置和插件注册
router/        # 基于后端菜单树动态生成路由
stores/        # Pinia 状态（user, permission, tabs, pageConfig）
utils/         # request.ts（Axios + 自动令牌刷新）
views/         # 页面组件（通过 import.meta.glob 自动发现）
```

**自动导入：** Vue API、vue-router、pinia 通过 `unplugin-auto-import`；Element Plus 组件通过 `unplugin-vue-components`。

**动态路由：** 后端返回菜单树 → `permissionStore.setRoutes()` → `import.meta.glob('/src/views/**/*.vue')` 解析组件 → `router.addRoute()`，404 最后添加。

## 小程序架构（`apps/forge-miniapp/src/`）

```
api/           # API 接口定义（auth, user）
pages/         # 页面组件
  login/       # 微信授权登录页
  profile/     # 个人中心（编辑、手机绑定、注销）
stores/        # Pinia 状态管理（user）
static/        # 静态资源（logo.svg, default-avatar.png）
composables/   # 组合式函数
```

**微信登录流程：**
1. 小程序调用 `uni.login()` 获取 code
2. 后端 `/app-api/auth/wx-login` 用 code 换取 openId
3. 后端查找/创建 `app_user` 记录，返回 JWT Token
4. 小程序存储 Token，用于后续 API 请求

**环境变量配置：**
- `WX_MINI_APP_ID` - 微信小程序 AppID（必填，否则使用 Mock 模式）
- `WX_MINI_APP_SECRET` - 微信小程序 AppSecret

## 重要模式

### API 响应格式
```json
{ "code": 200, "message": "success", "data": {}, "timestamp": 1709635800000 }
```

### 权限格式
- Controller：`@PreAuthorize("hasAuthority('system:user:list')")`
- 前端模板：`v-permission="'system:user:add'"` 或 `v-permission="['perm1', 'perm2']"`（任一匹配）
- 前端脚本：`hasPermission('system:user:add')`

### 数据库约定
- 表名前缀 `sys_`，公共字段：`id`、`create_time`、`update_time`、`create_by`、`update_by`、`deleted`、`status`、`remark`
- MyBatis Plus 自动填充 `create_time`/`update_time`

### Excel 导出
`ExcelUtils.export(response, fileName, sheetName, DtoClass.class, dataList)`，配合 `@ExcelProperty` 注解。

### WebSocket（STOMP over SockJS）
端点 `/ws`，广播 `/topic/notifications`，用户专属 `/user/{userId}/queue/notifications`

## Git 提交规范

**格式：** `<type>(<scope>): <subject>`（中文）

类型：`feat`、`fix`、`docs`、`style`、`refactor`、`perf`、`test`、`chore`、`revert`

**重要：** 禁止在提交信息中添加 `Co-Authored-By`。

## 项目初始化

```bash
pnpm run init <项目名称> "<项目描述>" <Java包名>
# 示例：pnpm run init my-admin "我的管理系统" com.mycompany
```

## 模块管理

### 创建新模块

```bash
node scripts/create-module.js <模块名称> "<模块描述>"
# 示例：node scripts/create-module.js order "订单管理模块"
```

自动创建：
- 模块目录结构（api/biz）
- pom.xml 配置
- 基础类模板（Entity、DTO、Controller、Service、Mapper）
- 数据库迁移脚本模板

### 删除模块

```bash
./scripts/remove-module.sh <模块名称>
# 示例：./scripts/remove-module.sh workflow
```

自动处理：
- 从根 pom.xml 移除模块引用
- 从 forge-dependencies 移除依赖声明
- 从 forge-server 移除依赖引用
- 检查并移除其他模块的依赖
- 清理数据库迁移脚本（交互确认）
- 删除模块目录（交互确认）

**注意：** 删除模块后需运行 `mvn clean compile` 验证编译。

## 关键文件

| 用途 | 路径 |
|------|------|
| 后端配置 | `apps/forge-server/forge-server/src/main/resources/application.yml` |
| BOM 版本管理 | `apps/forge-server/forge-dependencies/pom.xml` |
| 系统模块 API 定义 | `apps/forge-server/forge-module-system/forge-module-system-api/` |
| 工作流模块 API | `apps/forge-server/forge-module-workflow/forge-module-workflow-api/` |
| 前端请求工具 | `apps/forge-web/src/utils/request.ts` |
| 路由守卫 | `apps/forge-web/src/router/index.ts` |
| vxe-table 全局配置 | `apps/forge-web/src/plugins/vxe/vxe-table-config.ts` |
| 小程序 API 定义 | `apps/forge-miniapp/src/api/` |
| 小程序登录页 | `apps/forge-miniapp/src/pages/login/index.vue` |
| 微信登录服务 | `apps/forge-server/forge-module-system-biz/.../service/app/AppAuthServiceImpl.java` |

## 命名约定

### 后端（Java）
| 类型 | 约定 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.forge.modules.system` |
| 类名 | PascalCase | `SysUserController` |
| 方法名 | camelCase | `getUserById` |
| 表名 | `sys_` 前缀 + 小写下划线 | `sys_user` |

### 前端（TypeScript/Vue）
| 类型 | 约定 | 示例 |
|------|------|------|
| 文件名 | kebab-case | `user-profile.vue` |
| 组件名 | PascalCase | `UserProfile` |
| 变量/函数 | camelCase | `getUserInfo` |

### 小程序（uni-app）
| 类型 | 约定 | 示例 |
|------|------|------|
| 页面目录 | kebab-case | `pages/profile/` |
| 静态资源 | kebab-case | `static/logo.svg` |
| API 模块 | camelCase | `authApi`、`userApi` |

### API 路径
`/api/{module}/{entity}/{action}`，如 `/api/auth/login`、`/api/system/user/list`

### 业务模块权限前缀

用户 `system:user`、角色 `system:role`、菜单 `system:menu`、部门 `system:dept`、岗位 `system:position`、字典 `system:dict`、参数配置 `system:config`、文件配置 `system:file-config`、通知公告 `system:notice`、在线用户 `monitor:online`、登录日志 `monitor:login-log`、操作日志 `monitor:operation-log`、定时任务 `monitor:job`

## 代码模板

### 后端 Controller

```java
package com.forge.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.system.dto.xxx.XxxQueryRequest;
import com.forge.modules.system.dto.xxx.XxxRequest;
import com.forge.modules.system.dto.xxx.XxxResponse;
import com.forge.modules.system.service.SysXxxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "{模块}管理")
@RestController
@RequestMapping("/system/{entity}")
@RequiredArgsConstructor
public class SysXxxController {

    private final SysXxxService sysXxxService;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:{entity}:list')")
    public Result<PageResult<XxxResponse>> list(XxxQueryRequest request) {
        Page<XxxResponse> page = sysXxxService.pageXxx(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('system:{entity}:add')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody XxxRequest request) {
        sysXxxService.addXxx(request);
        return Result.success();
    }
    // ... 其他方法同理
}
```

### 后端 Service

```java
package com.forge.modules.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.modules.system.entity.SysXxx;
import com.forge.modules.system.mapper.SysXxxMapper;
import com.forge.modules.system.service.SysXxxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysXxxServiceImpl extends ServiceImpl<SysXxxMapper, SysXxx> implements SysXxxService {
    // 分层：controller → service → mapper，实体在 api 模块
}
```

### 前端 API

```typescript
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export const xxxApi = {
  page: (params: XxxQuery) => request.get<PageResult<XxxEntity>>('/system/xxx/list', { params }),
  get: (id: number) => request.get<XxxEntity>(`/system/xxx/${id}`),
  add: (data: Partial<XxxEntity>) => request.post('/system/xxx', data),
  update: (data: Partial<XxxEntity>) => request.put('/system/xxx', data),
  delete: (ids: number[]) => request.delete('/system/xxx', { data: ids })
}
```

### 前端 Vue 页面（CRUD + vxe-table）

```vue
<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" @click="handleAdd">新增</el-button>
        </template>
      </vxe-toolbar>
      <vxe-table ref="tableRef" id="sysXxxTable" :data="tableData" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="name" title="名称" min-width="150" />
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @size-change="getList" @current-change="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

const { tableHeight } = useTableHeight()
const { seqMethod } = useTableSeq({ currentPage: computed(() => queryParams.pageNum), pageSize: computed(() => queryParams.pageSize) })

onMounted(() => { tableRef.value?.connect(toolbarRef.value!) })
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .search-card { margin-bottom: 15px; }
  .table-card .el-pagination { margin-top: 15px; justify-content: flex-end; }
}
</style>
```
