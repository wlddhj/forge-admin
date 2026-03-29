# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

forge-admin is an enterprise-level admin management system with RBAC (Role-Based Access Control). It's a monorepo with separate frontend and backend applications.

**Tech Stack:**
- Frontend: Vue 3.4 + TypeScript + Element Plus + Pinia + Vite 5
- Backend: Spring Boot 3.2.0 + MyBatis Plus + MySQL + Redis
- Auth: JWT Token (access 2h, refresh 7d)
- API Docs: Knife4j at `/api/doc.html`

## Key Configuration

| Service | Port | Path |
|---------|------|------|
| Frontend Dev | 3003 | `apps/forge-web` |
| Backend API | 8181 | `apps/forge-server` |
| Context Path | - | `/api` |
| API Docs | 8181 | `/api/doc.html` |

**Database:** MySQL `forge_admin` on localhost:3306
**Redis:** localhost:6379 (no password)
**Java:** 21 | **Node:** 22.9.0 | **pnpm:** 8.15.4

## Development Commands

### Frontend (from `apps/forge-web`)
```bash
pnpm install    # Install dependencies
pnpm dev        # Start dev server (port 3003)
pnpm build      # Build for production (includes type check)
pnpm preview    # Preview production build
pnpm lint       # Run ESLint
pnpm test       # Run vitest unit tests
```

### Backend (from `apps/forge-server`)
```bash
mvn spring-boot:run              # Start dev server
mvn clean compile                # Compile only
mvn clean package -DskipTests    # Build JAR (skip tests)
mvn test                         # Run all tests
mvn test -Dtest=ClassName       # Run single test class
mvn test -Dtest=ClassName#methodName  # Run single test method
```

## Architecture

### Backend Module Pattern (`apps/forge-server/src/main/java/com/forge/admin/`)

Each module follows a layered pattern: `controller/` → `service/` (interface + impl) → `mapper/` → `entity/`, with `dto/` for request/response objects.

```
common/
├── annotation/    # @OperationLog, @DataPermission, @RateLimiter
├── aspect/        # AOP aspects for logging, rate limiting, data scope
├── config/        # Security, Redis, JWT, MyBatis Plus, WebSocket, Cache, Quartz
├── exception/     # GlobalExceptionHandler → Result<Void> with appropriate codes
├── permission/    # Data permission rules and MyBatis interceptor
├── response/      # Result<T>, PageResult<T>, ResultCode enum
└── utils/         # SecurityUtils, ExcelUtils (EasyExcel)

modules/
├── auth/          # Login, token refresh, JWT filter, session management
│   └── security/  # JwtTokenProvider, JwtAuthenticationFilter
└── system/        # User, Role, Menu, Dept, Dict, Config, Notice, File, Job, Log
```

**Cross-cutting concerns:**
- `@OperationLog(title, businessType)` — AOP-based audit logging
- `@DataPermission(deptAlias, userAlias)` — SQL-level data scope filtering (5 scope types)
- `@RateLimiter(keyType, time, count)` — Redis-based token bucket rate limiting
- `@Cacheable/@CacheEvict` — Redis caching (caches: dictData, dictType, sysConfig, userInfo, menu, dept)
- `@Valid @RequestBody` — Jakarta validation on DTOs

### Frontend Structure (`apps/forge-web/src/`)

```
api/           # API modules with typed request/response interfaces
composables/   # Vue composables (useWebSocket, useDict, useResponsive)
directives/    # v-permission, v-role (remove DOM element if no access)
layouts/       # BasicLayout with sidebar, header, tabs, notifications
router/        # Dynamic route generation from backend menu tree
stores/        # Pinia stores (user, permission, tabs, pageConfig)
utils/         # request.ts (Axios with auto token refresh)
views/         # Page components (auto-discovered via import.meta.glob)
```

**Auto-imports (no manual imports needed):**
- Vue APIs (`ref`, `computed`, `watch`, etc.), vue-router, pinia — via `unplugin-auto-import`
- Element Plus components — via `unplugin-vue-components`

**Dynamic routing:** Backend returns menu tree → `permissionStore.setRoutes()` converts to Vue Router config → components resolved via `import.meta.glob('/src/views/**/*.vue')` registry → routes added with `router.addRoute()`, 404 last.

## Important Patterns

### API Response Format
```json
{ "code": 200, "message": "success", "data": {}, "timestamp": 1709635800000 }
```
Frontend `request.ts` auto-handles 401 with token refresh queue — concurrent requests wait and replay.

### Permission Format
- Authority: `system:user:list`, `system:user:add`, `system:user:edit`, `system:user:delete`
- Controller: `@PreAuthorize("hasAuthority('system:user:list')")`
- Frontend template: `v-permission="'system:user:add'"` or `v-permission="['perm1', 'perm2']"` (any match)
- Frontend script: `hasPermission('system:user:add')`, `hasAllPermissions([...])`

### Database Conventions
- Prefix: `sys_` (e.g., `sys_user`, `sys_role`, `sys_menu`)
- Common fields: `id` (auto), `create_time`, `update_time`, `create_by`, `update_by`, `deleted` (logical), `status`, `remark`
- MyBatis Plus auto-fills `create_time`/`update_time`, handles logical delete and optimistic locking

### Database Migrations
Location: `apps/forge-server/src/main/resources/db/migration/`
Naming: `V{YYYYMMDD}{seq}__{description}.sql` (e.g., `V2026030501__file_config.sql`)

### Excel Export
Use `ExcelUtils.export(response, fileName, sheetName, DtoClass.class, dataList)` with EasyExcel `@ExcelProperty` annotated DTOs.

### WebSocket (STOMP over SockJS)
- Endpoint: `/ws` (SockJS fallback)
- Broadcast: `/topic/notifications`
- User-specific: `/user/{userId}/queue/notifications`
- Frontend: `useWebSocket()` composable in `composables/useWebSocket.ts`

### Data Permission System
SQL-level filtering via `@DataPermission` annotation + MyBatis interceptor:
- Type 1: All data | Type 2: Custom depts | Type 3: Own dept | Type 4: Own dept + children | Type 5: Own data only

## Git Commit Convention

**Format:** `<type>(<scope>): <subject>`

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `revert`

**IMPORTANT:** Do NOT add `Co-Authored-By` in commit messages.

## Project Initialization

Use `scripts/init-project.js` to create new projects from this template:
```bash
pnpm run init <project-name> "<description>" <java-package>
# Example: pnpm run init my-admin "我的管理系统" com.mycompany
```
Renames packages, directories, classes, and updates all references.

## Key Files

| Purpose | Path |
|---------|------|
| Backend Config | `apps/forge-server/src/main/resources/application.yml` |
| API Definitions | `apps/forge-server/src/main/java/com/forge/admin/modules/*/controller/` |
| Frontend Request (Axios + token refresh) | `apps/forge-web/src/utils/request.ts` |
| Route Guards & Dynamic Routes | `apps/forge-web/src/router/index.ts` |
| Permission Directives | `apps/forge-web/src/directives/permission.ts` |
| Pinia Stores | `apps/forge-web/src/stores/` |

## Naming Conventions

### Backend (Java)
| 类型 | 约定 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.forge.admin.modules.system` |
| 类名 | PascalCase | `SysUserController` |
| 方法名 | camelCase | `getUserById` |
| 常量名 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 表名 | `sys_` 前缀 + 小写下划线 | `sys_user`, `sys_role` |
| 字段名 | 小写下划线 | `create_time`, `user_name` |

### Frontend (TypeScript/Vue)
| 类型 | 约定 | 示例 |
|------|------|------|
| 文件名 | kebab-case | `user-profile.vue` |
| 组件名 | PascalCase | `UserProfile` |
| 接口/类型 | PascalCase | `UserInfo` |
| 变量/函数 | camelCase | `getUserInfo` |
| 常量 | UPPER_SNAKE_CASE | `API_BASE_URL` |

### API Path Convention
```
/api/{module}/{entity}/{action}
```
Examples: `/api/auth/login`, `/api/system/user/list`, `/api/system/menu/tree`

### Business Modules
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

## Code Templates

### Backend Controller

```java
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
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
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

### Backend Service

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SysXxxServiceImpl extends ServiceImpl<SysXxxMapper, SysXxx> implements SysXxxService {

    @Override
    public Page<XxxResponse> pageXxx(XxxQueryRequest request) {
        Page<SysXxx> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysXxx> wrapper = new LambdaQueryWrapper<>();
        // wrapper conditions...
        Page<SysXxx> result = page(page, wrapper);
        // convert and return
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addXxx(XxxRequest request) {
        SysXxx entity = new SysXxx();
        BeanUtil.copyProperties(request, entity);
        save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateXxx(XxxRequest request) {
        SysXxx entity = getById(request.getId());
        BeanUtil.copyProperties(request, entity);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteXxx(List<Long> ids) {
        removeByIds(ids);
    }
}
```

### Frontend API Module

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

### Frontend Vue Page (CRUD)

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
      <template #header>
        <div class="card-header">
          <span>列表</span>
          <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon>新增</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
// ... 组件实现
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .search-card { margin-bottom: 15px; }
  .table-card {
    .card-header { display: flex; justify-content: space-between; align-items: center; }
    .el-pagination { margin-top: 15px; justify-content: flex-end; }
  }
}
</style>
```
