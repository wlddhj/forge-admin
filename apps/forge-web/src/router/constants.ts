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
  '/403',
  '/500',
  '/503',
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
  },
  {
    path: '/403',
    name: '403',
    component: () => import('@/views/error/403.vue'),
    meta: {
      title: '403',
      hidden: true,
      noAuth: true
    }
  },
  {
    path: '/500',
    name: '500',
    component: () => import('@/views/error/500.vue'),
    meta: {
      title: '500',
      hidden: true,
      noAuth: true
    }
  },
  {
    path: '/503',
    name: '503',
    component: () => import('@/views/error/503.vue'),
    meta: {
      title: '503',
      hidden: true,
      noAuth: true
    }
  }
]

// 自动扫描 views 目录下所有 Vue 组件
// glob 返回键格式: '/src/views/system/user/index.vue'
const viewModules = import.meta.glob<{ default: any }>('/src/views/**/*.vue')

// 布局组件（单独处理，不在 views 目录下）
export const LAYOUT_COMPONENT = () => import('@/layouts/BasicLayout.vue')

// 将 glob 键转为与后端 componentPath 兼容的格式
// '/src/views/system/user/index.vue' → '/views/system/user/index'
function normalizeGlobKey(key: string): string {
  return key.replace(/^\/src/, '').replace(/\.vue$/, '')
}

// 构建组件注册表
const COMPONENT_REGISTRY: Record<string, () => Promise<any>> = {}
for (const [globKey, loader] of Object.entries(viewModules)) {
  COMPONENT_REGISTRY[normalizeGlobKey(globKey)] = loader
}

/**
 * 动态加载组件
 * 支持懒加载和错误处理
 *
 * @param componentPath 组件路径
 * @param routeName 路由名称（用于 keep-alive :include 匹配）
 * @returns 组件加载函数
 */
export function loadComponent(componentPath: string, routeName?: string): () => Promise<any> {
  if (componentPath === 'Layout') return LAYOUT_COMPONENT

  const normalizedPath = componentPath.replace(/\.vue$/, '')
  const loader = COMPONENT_REGISTRY[normalizedPath] || COMPONENT_REGISTRY[normalizedPath.startsWith('/') ? normalizedPath : '/views/' + normalizedPath]

  if (!loader) {
    console.warn(`[路由] 组件未找到: ${componentPath}`)
    return () => import('@/views/error/404.vue')
  }

  if (!routeName) return loader

  // 设置组件 name 以匹配 keep-alive :include
  return async () => {
    const module = await loader()
    return { ...module.default, name: routeName }
  }
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
