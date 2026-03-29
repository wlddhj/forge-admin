/**
 * 路由配置工具类
 * 提供路由相关常量和辅助方法
 */

/**
 * 白名单路由（不需要认证）
 */
export const WHITE_LIST = [
  '/login',
  '/404',
  '/register' // 如果有注册页面
]

/**
 * 基础静态路由
 * 注意：通配路由 /:pathMatch(.*)* 不在这里添加，
 * 而是在动态路由加载完成后添加，避免在路由加载前匹配所有路径
 */
export const CONSTANT_ROUTES = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      hidden: true,
      noAuth: true
    }
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/views/error/404.vue'),
    meta: {
      title: '404',
      hidden: true,
      noAuth: true
    }
  }
]

/**
 * 组件路径映射
 * 支持动态导入所有系统管理模块的页面
 */
export const COMPONENT_MAP: Record<string, () => Promise<any>> = {
  // 基础页面
  '/views/dashboard/index.vue': () => import('@/views/dashboard/index.vue'),
  '/views/profile/index.vue': () => import('@/views/profile/index.vue'),

  // 系统管理模块 - 用户相关
  '/views/system/user/index.vue': () => import('@/views/system/user/index.vue'),

  // 系统管理模块 - 权限相关
  '/views/system/role/index.vue': () => import('@/views/system/role/index.vue'),
  '/views/system/menu/index.vue': () => import('@/views/system/menu/index.vue'),

  // 系统管理模块 - 组织架构
  '/views/system/dept/index.vue': () => import('@/views/system/dept/index.vue'),
  '/views/system/position/index.vue': () => import('@/views/system/position/index.vue'),

  // 系统管理模块 - 字典配置
  '/views/system/dict-type/index.vue': () => import('@/views/system/dict-type/index.vue'),

  // 系统管理模块 - 系统配置
  '/views/system/config/index.vue': () => import('@/views/system/config/index.vue'),

  // 系统管理模块 - 日志监控
  '/views/system/operation-log/index.vue': () => import('@/views/system/operation-log/index.vue'),
  '/views/system/login-log/index.vue': () => import('@/views/system/login-log/index.vue'),
  '/views/system/online-user/index.vue': () => import('@/views/system/online-user/index.vue'),

  // 系统管理模块 - 文件管理
  '/views/system/attachment/index.vue': () => import('@/views/system/attachment/index.vue'),

  // 系统管理模块 - 定时任务
  '/views/system/job/index.vue': () => import('@/views/system/job/index.vue'),
  '/views/system/job-log/index.vue': () => import('@/views/system/job-log/index.vue'),

  // 系统管理模块 - 通知公告
  '/views/system/notice/index.vue': () => import('@/views/system/notice/index.vue'),

  // 布局组件
  'Layout': () => import('@/layouts/BasicLayout.vue')
}

/**
 * 动态加载组件
 * 支持懒加载和错误处理
 *
 * @param componentPath 组件路径
 * @returns 组件加载函数
 */
export function loadComponent(componentPath: string): () => Promise<any> {
  // 如果是 Layout，直接返回
  if (componentPath === 'Layout') {
    return COMPONENT_MAP['Layout']
  }

  // 尝试多种路径格式
  const pathVariants = [
    componentPath,
    componentPath.endsWith('.vue') ? componentPath : componentPath + '.vue',
    componentPath.startsWith('/views/') ? componentPath : '/views/' + componentPath,
    componentPath.startsWith('/views/') ? componentPath : '/views/' + (componentPath.endsWith('.vue') ? componentPath : componentPath + '.vue')
  ]

  for (const path of pathVariants) {
    if (COMPONENT_MAP[path]) {
      return COMPONENT_MAP[path]
    }
  }

  // 如果组件不存在，返回 404 页面
  console.warn(`[路由] 组件未找到: ${componentPath}，尝试的路径:`, pathVariants)
  return () => import('@/views/error/404.vue')
}

/**
 * 检查路由是否在白名单中
 *
 * @param path 路由路径
 * @returns 是否在白名单中
 */
export function isWhiteListed(path: string): boolean {
  return WHITE_LIST.some(item => path === item || path.startsWith(item))
}

/**
 * 生成路由名称
 *
 * @param menuCode 菜单编码
 * @param menuId 菜单ID
 * @returns 路由名称
 */
export function generateRouteName(menuCode: string | null, menuId: number): string {
  return menuCode || `menu_${menuId}`
}

/**
 * 处理路由路径
 * 如果是绝对路径，转换为相对路径
 *
 * @param routePath 原始路由路径
 * @returns 处理后的路由路径
 */
export function normalizeRoutePath(routePath: string): string {
  if (!routePath) return ''

  // 如果已经是相对路径（不以 / 开头），直接返回
  if (!routePath.startsWith('/')) {
    return routePath
  }

  // 移除开头的 /，使其成为相对路径
  return routePath.substring(1)
}

/**
 * 菜单类型枚举
 */
export enum MenuType {
  DIRECTORY = 0,  // 目录
  MENU = 1,       // 菜单
  BUTTON = 2      // 按钮
}

/**
 * 判断是否为有效的菜单类型（可生成路由）
 *
 * @param menuType 菜单类型
 * @returns 是否有效
 */
export function isValidMenuType(menuType: number): boolean {
  return menuType === MenuType.DIRECTORY || menuType === MenuType.MENU
}
