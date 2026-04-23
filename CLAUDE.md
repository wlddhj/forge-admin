# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指导。

## 项目概述

forge-admin 是一个基于 RBAC（基于角色的访问控制）的企业级后台管理系统，采用 monorepo 结构，前后端分离。

**技术栈：**
- 前端：Vue 3.4 + TypeScript + Element Plus + vxe-table + Pinia + Vite 5
- 后端：Spring Boot 3.2.0 + MyBatis Plus + MySQL + Redis
- 认证：JWT Token（访问令牌 2 小时，刷新令牌 7 天）
- API 文档：Knife4j，地址 `/api/doc.html`

## 关键配置

| 服务 | 端口 | 路径 |
|------|------|------|
| 前端开发 | 3003 | `apps/forge-web` |
| 后端 API | 8181 | `apps/forge-server` |
| 上下文路径 | - | `/api` |
| API 文档 | 8181 | `/api/doc.html` |

**数据库：** MySQL `forge_admin`，地址 localhost:3306
**Redis：** localhost:6379（无密码）
**Java：** 21 | **Node：** 22.9.0 | **pnpm：** 8.15.4

## 开发命令

### 前端（在 `apps/forge-web` 目录下）
```bash
pnpm install    # 安装依赖
pnpm dev        # 启动开发服务器（端口 3003）
pnpm build      # 生产构建（含类型检查）
pnpm preview    # 预览生产构建
pnpm lint       # 运行 ESLint
pnpm test       # 运行 vitest 单元测试
```

### 后端（在 `apps/forge-server` 目录下）
```bash
mvn spring-boot:run              # 启动开发服务器
mvn clean compile                # 仅编译
mvn clean package -DskipTests    # 打包 JAR（跳过测试）
mvn test                         # 运行所有测试
mvn test -Dtest=ClassName       # 运行单个测试类
mvn test -Dtest=ClassName#methodName  # 运行单个测试方法
```

## 架构

### 后端模块模式（`apps/forge-server/src/main/java/com/forge/admin/`）

每个模块遵循分层模式：`controller/` → `service/`（接口 + 实现）→ `mapper/` → `entity/`，`dto/` 存放请求/响应对象。

```
common/
├── annotation/    # @OperationLog, @DataPermission, @RateLimiter
├── aspect/        # AOP 切面：日志、限流、数据范围
├── config/        # 配置类：Security, Redis, JWT, MyBatis Plus, WebSocket, Cache, Quartz
├── exception/     # GlobalExceptionHandler → 返回 Result<Void> 及对应错误码
├── permission/    # 数据权限规则和 MyBatis 拦截器
├── response/      # Result<T>, PageResult<T>, ResultCode 枚举
└── utils/         # 工具类：SecurityUtils, ExcelUtils (EasyExcel)

modules/
├── auth/          # 登录、令牌刷新、JWT 过滤器、会话管理
│   └── security/  # JwtTokenProvider, JwtAuthenticationFilter
└── system/        # 用户、角色、菜单、部门、字典、配置、通知、文件、任务、日志
```

**横切关注点：**
- `@OperationLog(title, businessType)` — 基于 AOP 的审计日志
- `@DataPermission(deptAlias, userAlias)` — SQL 级数据范围过滤（5 种范围类型）
- `@RateLimiter(keyType, time, count)` — 基于 Redis 令牌桶的限流
- `@Cacheable/@CacheEvict` — Redis 缓存（缓存名：dictData, dictType, sysConfig, userInfo, menu, dept）
- `@Valid @RequestBody` — Jakarta DTO 参数校验

### 前端结构（`apps/forge-web/src/`）

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

**自动导入（无需手动 import）：**
- Vue API（`ref`, `computed`, `watch` 等）、vue-router、pinia — 通过 `unplugin-auto-import`
- Element Plus 组件 — 通过 `unplugin-vue-components`

**动态路由：** 后端返回菜单树 → `permissionStore.setRoutes()` 转换为 Vue Router 配置 → 通过 `import.meta.glob('/src/views/**/*.vue')` 注册表解析组件 → `router.addRoute()` 添加路由，404 最后添加。

### vxe-table 表格组件

所有列表页面统一使用 vxe-table 替代 el-table，提供更强的表格功能（列自定义、导出、打印等）。

**全局配置：** `src/plugins/vxe/vxe-table-config.ts`
**插件注册：** `src/plugins/vxe/index.ts`

**表格命名约定：**
| 模块 | 表格 id |
|------|---------|
| 用户管理 | sysUserTable |
| 角色管理 | sysRoleTable |
| 菜单管理（树形） | sysMenuTable |
| 部门管理（树形） | sysDeptTable |
| 岗位管理 | sysPositionTable |
| 字典类型管理 | sysDictTypeTable |
| 字典数据列表 | sysDictDataTable |
| 系统配置管理 | sysConfigTable |
| 文件配置管理 | sysFileConfigTable |
| 附件管理 | sysAttachmentTable |
| 通知公告管理 | sysNoticeTable |
| 在线用户管理 | sysOnlineUserTable |
| 定时任务管理 | sysJobTable |
| 任务日志管理 | sysJobLogTable |
| 登录日志管理 | loginLogTable |
| 操作日志管理 | sysOperationLogTable |

**表格 composables：**
- `useTableHeight` — 表格高度自适应，铺满屏幕剩余区域
- `useTableSeq` — 分页序号计算，支持全局序号
- `useTableSort` — 远程排序处理（可选）

**树形表格配置：**
```typescript
:tree-config="{ childrenField: 'children', expandAll: !isMobile, indent: 20 }"
```
树形列添加 `tree-node` 属性，展开/折叠使用 `setAllTreeExpand()` 和 `clearTreeExpand()`。

## 重要模式

### API 响应格式
```json
{ "code": 200, "message": "success", "data": {}, "timestamp": 1709635800000 }
```
前端 `request.ts` 自动处理 401，通过令牌刷新队列实现并发请求等待和重放。

### 权限格式
- 权限标识：`system:user:list`、`system:user:add`、`system:user:edit`、`system:user:delete`
- Controller：`@PreAuthorize("hasAuthority('system:user:list')")`
- 前端模板：`v-permission="'system:user:add'"` 或 `v-permission="['perm1', 'perm2']"`（任一匹配）
- 前端脚本：`hasPermission('system:user:add')`、`hasAllPermissions([...])`

### 数据库约定
- 表名前缀：`sys_`（如 `sys_user`、`sys_role`、`sys_menu`）
- 公共字段：`id`（自增）、`create_time`、`update_time`、`create_by`、`update_by`、`deleted`（逻辑删除）、`status`、`remark`
- MyBatis Plus 自动填充 `create_time`/`update_time`，处理逻辑删除和乐观锁

### 数据库迁移
位置：`apps/forge-server/src/main/resources/db/migration/`
命名：`V{YYYYMMDD}{seq}__{description}.sql`（如 `V2026030501__file_config.sql`）

### Excel 导出
使用 `ExcelUtils.export(response, fileName, sheetName, DtoClass.class, dataList)`，配合 EasyExcel 的 `@ExcelProperty` 注解 DTO。

### WebSocket（STOMP over SockJS）
- 端点：`/ws`（SockJS 回退）
- 广播：`/topic/notifications`
- 用户专属：`/user/{userId}/queue/notifications`
- 前端：`composables/useWebSocket.ts` 中的 `useWebSocket()` 组合式函数

### 数据权限系统
通过 `@DataPermission` 注解 + MyBatis 拦截器实现 SQL 级数据过滤：
- 类型 1：全部数据 | 类型 2：自定义部门 | 类型 3：本部门 | 类型 4：本部门及子部门 | 类型 5：仅本人

## Git 提交规范

**格式：** `<type>(<scope>): <subject>`

类型：`feat`、`fix`、`docs`、`style`、`refactor`、`perf`、`test`、`chore`、`revert`

**重要：** 禁止在提交信息中添加 `Co-Authored-By`。

## 项目初始化

使用 `scripts/init-project.js` 基于此模板创建新项目：
```bash
pnpm run init <项目名称> "<项目描述>" <Java包名>
# 示例：pnpm run init my-admin "我的管理系统" com.mycompany
```
自动重命名包名、目录、类名，并更新所有引用。

## 关键文件

| 用途 | 路径 |
|------|------|
| 后端配置 | `apps/forge-server/src/main/resources/application.yml` |
| API 定义 | `apps/forge-server/src/main/java/com/forge/admin/modules/*/controller/` |
| 前端请求工具（Axios + 令牌刷新） | `apps/forge-web/src/utils/request.ts` |
| 路由守卫与动态路由 | `apps/forge-web/src/router/index.ts` |
| 权限指令 | `apps/forge-web/src/directives/permission.ts` |
| Pinia 状态管理 | `apps/forge-web/src/stores/` |
| vxe-table 全局配置 | `apps/forge-web/src/plugins/vxe/vxe-table-config.ts` |
| vxe-table 插件注册 | `apps/forge-web/src/plugins/vxe/index.ts` |
| 表格高度自适应 Hook | `apps/forge-web/src/composables/useTableHeight.ts` |
| 表格序号计算 Hook | `apps/forge-web/src/composables/useTableSeq.ts` |

## 命名约定

### 后端（Java）
| 类型 | 约定 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.forge.admin.modules.system` |
| 类名 | PascalCase | `SysUserController` |
| 方法名 | camelCase | `getUserById` |
| 常量名 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 表名 | `sys_` 前缀 + 小写下划线 | `sys_user`, `sys_role` |
| 字段名 | 小写下划线 | `create_time`, `user_name` |

### 前端（TypeScript/Vue）
| 类型 | 约定 | 示例 |
|------|------|------|
| 文件名 | kebab-case | `user-profile.vue` |
| 组件名 | PascalCase | `UserProfile` |
| 接口/类型 | PascalCase | `UserInfo` |
| 变量/函数 | camelCase | `getUserInfo` |
| 常量 | UPPER_SNAKE_CASE | `API_BASE_URL` |

### API 路径约定
```
/api/{module}/{entity}/{action}
```
示例：`/api/auth/login`、`/api/system/user/list`、`/api/system/menu/tree`

### 业务模块
| 模块 | 权限前缀 |
|------|----------|
| 用户管理 | `system:user` |
| 角色管理 | `system:role` |
| 菜单管理 | `system:menu` |
| 部门管理 | `system:dept` |
| 岗位管理 | `system:position` |
| 字典管理 | `system:dict` |
| 参数配置 | `system:config` |
| 文件配置 | `system:file-config` |
| 通知公告 | `system:notice` |
| 在线用户 | `monitor:online` |
| 登录日志 | `monitor:login-log` |
| 操作日志 | `monitor:operation-log` |
| 定时任务 | `monitor:job` |

## 代码模板

### 后端 Controller 模板

```java
package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.xxx.XxxQueryRequest;
import com.forge.admin.modules.system.dto.xxx.XxxRequest;
import com.forge.admin.modules.system.dto.xxx.XxxResponse;
import com.forge.admin.modules.system.service.SysXxxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
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
        PageResult<XxxResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "根据ID查询")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:{entity}:query')")
    public Result<XxxResponse> getById(@PathVariable Long id) {
        return Result.success(sysXxxService.getXxxDetail(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    @PreAuthorize("hasAuthority('system:{entity}:add')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody XxxRequest request) {
        sysXxxService.addXxx(request);
        return Result.success();
    }

    @Operation(summary = "更新")
    @PutMapping
    @PreAuthorize("hasAuthority('system:{entity}:edit')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody XxxRequest request) {
        sysXxxService.updateXxx(request);
        return Result.success();
    }

    @Operation(summary = "删除")
    @DeleteMapping
    @PreAuthorize("hasAuthority('system:{entity}:delete')")
    @OperationLog(title = "{模块}管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        sysXxxService.deleteXxx(ids);
        return Result.success();
    }
}
```

### 后端 Service 模板

```java
package com.forge.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.modules.system.entity.SysXxx;
import com.forge.admin.modules.system.mapper.SysXxxMapper;
import com.forge.admin.modules.system.service.SysXxxService;
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
        // 分页查询实现
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addXxx(XxxRequest request) {
        // 新增实现
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateXxx(XxxRequest request) {
        // 更新实现
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteXxx(List<Long> ids) {
        // 删除实现
    }
}
```

### 前端 API 模板

```typescript
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface XxxEntity {
  id: number
  name: string
  status: number
  createTime: string
}

export interface XxxQuery {
  pageNum: number
  pageSize: number
  name?: string
  status?: number
}

export const xxxApi = {
  page: (params: XxxQuery) => request.get<PageResult<XxxEntity>>('/system/xxx/list', { params }),
  get: (id: number) => request.get<XxxEntity>(`/system/xxx/${id}`),
  add: (data: Partial<XxxEntity>) => request.post('/system/xxx', data),
  update: (data: Partial<XxxEntity>) => request.put('/system/xxx', data),
  delete: (ids: number[]) => request.delete('/system/xxx', { data: ids })
}
```

### 前端 Vue 页面模板（CRUD + vxe-table）

```vue
<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏 -->
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon>新增</el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysXxxTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="name" title="名称" min-width="150" />
        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total" :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="getList" @current-change="getList"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

// ... 其他业务逻辑
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .search-card { margin-bottom: 15px; }
  .table-card {
    .el-pagination { margin-top: 15px; justify-content: flex-end; }
  }
}
</style>
```
