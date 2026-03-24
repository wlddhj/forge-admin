# 前端路由配置指南

## 概述

前端路由采用动态路由 + 静态路由的混合模式，支持基于后端菜单数据的动态路由生成。

---

## 路由配置文件结构

```
src/router/
├── index.ts          # 路由主配置、路由守卫
├── constants.ts      # 路由常量和工具函数（新增）
└── modules/          # 路由模块（可选）
```

---

## 核心文件说明

### 1. router/index.ts

**功能**：主路由配置、路由守卫、白名单管理

**关键配置**：
- 静态路由（不需要认证）
- 路由守卫（认证检查）
- 动态路由添加
- 路由重置

### 2. router/constants.ts（新增）

**功能**：路由常量和工具函数

**主要导出**：
- `WHITE_LIST` - 白名单路由数组
- `CONSTANT_ROUTES` - 静态路由配置
- `COMPONENT_MAP` - 组件路径映射表
- `loadComponent()` - 动态组件加载函数
- `generateRouteName()` - 路由名称生成函数
- `normalizeRoutePath()` - 路由路径标准化函数
- `isValidMenuType()` - 菜单类型验证函数
- `MenuType` 枚举

### 3. stores/permission.ts

**功能**：权限 store，管理动态路由

**核心方法**：
- `setRoutes(menus)` - 根据后端菜单生成路由
- `generateRoutes(menus)` - 递归生成路由树
- `generateSingleRoute(menu)` - 生成单个路由
- `resetRoutes()` - 重置路由

---

## 路由生成流程

### 流程图

```
┌─────────────────┐
│  用户登录       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  获取用户信息   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  获取菜单数据   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  生成动态路由   │──┬──▶ 添加静态路由（Dashboard、Profile）
│                 │──┬──▶ 处理后端菜单（Layout 子路由）
│                 │──┴──▶ 检查重复路由
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  注册到 Router  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  添加 404 路由   │
└─────────────────┘
```

---

## 组件映射表

### 已注册的页面组件

| 路由路径 | 组件文件 | 说明 |
|---------|---------|------|
| /dashboard | dashboard/index.vue | 首页 |
| /profile | profile/index.vue | 个人中心 |
| /system/user | system/user/index.vue | 用户管理 |
| /system/role | system/role/index.vue | 角色管理 |
| /system/menu | system/menu/index.vue | 菜单管理 |
| /system/dept | system/dept/index.vue | 部门管理 |
| /system/position | system/position/index.vue | 岗位管理 |
| /system/dict-type | system/dict-type/index.vue | 字典类型管理 |
| /system/config | system/config/index.vue | 系统配置 |
| /system/operation-log | system/operation-log/index.vue | 操作日志 |
| /system/login-log | system/login-log/index.vue | 登录日志 |
| /system/online-user | system/online-user/index.vue | 在线用户 |
| /system/attachment | system/attachment/index.vue | 附件管理 |
| /system/job | system/job/index.vue | 定时任务 |
| /system/job-log | system/job-log/index.vue | 任务日志 |
| /system/notice | system/notice/index.vue | 通知公告 |

---

## 路由 Meta 字段

### 支持的 Meta 属性

```typescript
{
  title: string        // 路由标题（用于面包屑、标签页）
  icon?: string         // 路由图标（用于菜单显示）
  hidden?: boolean      // 是否在菜单中隐藏
  affix?: boolean       // 是否固定在标签页
  keepAlive?: boolean   // 是否缓存页面
  menuId?: number       // 菜单ID
  menuType?: number     // 菜单类型（0:目录 1:菜单 2:按钮）
  noAuth?: boolean      // 是否不需要认证
}
```

---

## 路由守卫逻辑

### 认证流程

```
1. 检查 Token
   ├─ 有 Token ──▶ 检查路由是否已加载
   │              ├─ 未加载 ──▶ 获取用户信息 ──▶ 获取菜单 ──▶ 生成路由 ──▶ 添加路由
   │              └─ 已加载 ──▶ 直接通过
   └─ 无 Token ──▶ 检查白名单 ──▶ 在白名单直接通过，否则跳转登录
```

### 特殊处理

1. **登录后跳转**：
   - 从 `/login` 跳转到 `/dashboard`
   - 从根路径 `/` 跳转到 `/dashboard`

2. **路由重定向**：
   - Layout 组件自动重定向到第一个子路由或 dashboard
   - 未匹配的路由重定向到 404

3. **动态路由**：
   - 只有 `componentPath = 'Layout'` 的顶级菜单的子菜单才会被添加为路由
   - 按钮类型（menuType = 2）的菜单不会生成路由

---

## 新增页面路由

### 步骤

1. **创建页面组件**
   ```bash
   # 在 views/system/ 下创建新页面
   touch src/views/system/new-page/index.vue
   ```

2. **在 componentMap 中注册**
   ```typescript
   // 编辑 router/constants.ts
   export const COMPONENT_MAP: Record<string, () => Promise<any>> = {
     // ...
     '/views/system/new-page/index.vue': () => import('@/views/system/new-page/index.vue'),
   }
   ```

3. **后端添加菜单配置**
   ```sql
   INSERT INTO sys_menu (menu_name, menu_code, route_path, component_path, icon, menu_type)
   VALUES ('新页面', 'system:new:page', '/system/new-page', '/views/system/new-page/index.vue', 'Document', 1);
   ```

4. **刷新页面或重新登录即可生效**

---

## 常见问题

### 1. 页面显示 404

**原因**：组件路径未在 `componentMap` 中注册

**解决**：在 `router/constants.ts` 中添加组件映射

### 2. 路由重复警告

**原因**：后端菜单配置了重复的路由路径

**解决**：`setRoutes` 方法中已有去重逻辑，检查后端菜单配置

### 3. 路由守卫死循环

**原因**：路由守卫中调用了需要触发路由守卫的操作

**解决**：使用 `replace: true` 参数避免重复跳转

### 4. 刷新页面后菜单丢失

**原因**：动态路由未正确添加或 pinia store 数据丢失

**解决**：
- 检查 `isRoutesLoaded` 状态
- 检查路由是否正确添加到 router
- 检查 pinia 持久化配置

---

## 开发调试

### 启用调试日志

```typescript
// 在 permission.ts 中已有调试日志
if (import.meta.env.DEV) {
  console.log('[路由] 已加载路由:', childrenRoutes.map(r => ({ path: r.path, name: r.name })))
}
```

### 查看当前路由

```javascript
// 在浏览器控制台
console.log(router.getRoutes())
```

### 手动测试路由

```javascript
// 导航到特定路由
router.push('/system/user')
```

---

## 优化点

### 已优化

✅ 提取常量到独立文件
✅ 统一组件加载逻辑
✅ 添加路由去重
✅ 添加开发调试日志
✅ 优化路由守卫逻辑
✅ 添加 Meta 字段扩展

### 后续优化方向

- [ ] 添加路由缓存机制
- [ ] 支持路由懒加载优化
- [ ] 添加路由过渡动画
- [ ] 完善路由权限控制
- [ ] 添加路由错误边界处理

---

## 相关文件

- `router/index.ts` - 主路由配置
- `router/constants.ts` - 路由常量和工具函数
- `stores/permission.ts` - 权限 store
- `stores/user.ts` - 用户 store
- `layouts/BasicLayout.vue` - 基础布局组件

---

**更新日期**: 2026-03-03
