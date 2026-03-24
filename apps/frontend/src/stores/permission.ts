import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { MenuTree } from '@/types/system'

// 组件映射
// 支持动态导入所有系统管理模块的页面
const componentMap: Record<string, () => Promise<any>> = {
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
  '/views/system/file-config/index.vue': () => import('@/views/system/file-config/index.vue'),

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
 */
const loadComponent = (componentPath: string) => {
  // 尝试多种路径格式
  const pathVariants = [
    componentPath,
    componentPath.endsWith('.vue') ? componentPath : componentPath + '.vue',
    componentPath.startsWith('/views/') ? componentPath : '/views/' + componentPath,
    componentPath.startsWith('/views/') ? componentPath : '/views/' + (componentPath.endsWith('.vue') ? componentPath : componentPath + '.vue')
  ]

  for (const path of pathVariants) {
    if (componentMap[path]) {
      return componentMap[path]
    }
  }

  // 如果组件不存在，返回 404 页面
  console.warn(`组件未找到: ${componentPath}，尝试的路径:`, pathVariants)
  return () => import('@/views/error/404.vue')
}

export const usePermissionStore = defineStore('permission', () => {
  const routes = ref<RouteRecordRaw[]>([])
  const isRoutesLoaded = ref(false)

  // 根据菜单生成路由
  const generateRoutes = (menus: MenuTree[]): RouteRecordRaw[] => {
    const result: RouteRecordRaw[] = []

    for (const menu of menus) {
      // 只处理目录和菜单类型（不处理按钮）
      if (menu.menuType === 2) continue

      const route: RouteRecordRaw = {
        path: menu.routePath || '',
        name: menu.menuCode || `menu_${menu.id}`,
        meta: {
          title: menu.menuName,
          icon: menu.icon,
          hidden: menu.visible !== 1
        },
        children: []
      }

      // 设置组件
      if (menu.componentPath) {
        if (menu.componentPath === 'Layout') {
          route.component = componentMap['Layout']
          route.redirect = menu.redirectPath || undefined
        } else {
          route.component = componentMap[menu.componentPath]
        }
      }

      // 递归处理子菜单
      if (menu.children && menu.children.length > 0) {
        const childRoutes = generateChildRoutes(menu.children, menu.routePath || '')
        if (childRoutes.length > 0) {
          route.children = childRoutes
        }
      }

      result.push(route)
    }

    return result
  }

  // 生成子路由
  const generateChildRoutes = (menus: MenuTree[], parentPath: string): RouteRecordRaw[] => {
    const result: RouteRecordRaw[] = []

    for (const menu of menus) {
      if (menu.menuType === 2) continue

      const route: RouteRecordRaw = {
        path: menu.routePath || '',
        name: menu.menuCode || `menu_${menu.id}`,
        meta: {
          title: menu.menuName,
          icon: menu.icon,
          hidden: menu.visible !== 1
        },
        children: []
      }

      if (menu.componentPath && componentMap[menu.componentPath]) {
        route.component = componentMap[menu.componentPath]
      }

      if (menu.children && menu.children.length > 0) {
        const childRoutes = generateChildRoutes(menu.children, menu.routePath || '')
        if (childRoutes.length > 0) {
          route.children = childRoutes
        }
      }

      result.push(route)
    }

    return result
  }

  // 设置路由
  const setRoutes = (menus: MenuTree[]) => {
    const childrenRoutes: RouteRecordRaw[] = []

    // 1. 添加首页路由
    childrenRoutes.push({
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/dashboard/index.vue'),
      meta: { title: '首页', icon: 'HomeFilled', affix: true }
    } as RouteRecordRaw)

    // 2. 添加个人中心路由
    childrenRoutes.push({
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/profile/index.vue'),
      meta: { title: '个人中心', icon: 'User', hidden: true }
    } as RouteRecordRaw)

    // 3. 处理后端菜单，递归提取所有子路由
    const extractChildRoutes = (menus: MenuTree[]) => {
      for (const menu of menus) {
        if (menu.menuType === 2) continue // 跳过按钮

        if (import.meta.env.DEV) {
          console.log(`[路由] 处理菜单: ${menu.menuName} (${menu.routePath}), 组件: ${menu.componentPath}`)
        }

        // 处理有组件路径的菜单（页面）
        const childRoute = generateSingleRoute(menu, '')
        if (childRoute) {
          // 检查路由是否已存在，避免重复
          const exists = childrenRoutes.some(r => r.path === childRoute.path)
          if (!exists) {
            childrenRoutes.push(childRoute)
          } else if (import.meta.env.DEV) {
            console.log(`[路由] 路由已存在，跳过: ${childRoute.path}`)
          }
        }

        // 递归处理子菜单
        if (menu.children?.length) {
          extractChildRoutes(menu.children)
        }
      }
    }

    for (const menu of menus) {
      if (menu.menuType === 2) continue // 跳过按钮
      if (menu.children?.length) {
        extractChildRoutes(menu.children)
      }
    }

    // 4. 主布局路由
    routes.value = [
      {
        path: '/',
        component: componentMap['Layout'],
        redirect: '/dashboard',
        children: childrenRoutes
      }
    ]

    isRoutesLoaded.value = true

    // 5. 打印路由信息（开发调试用）
    if (import.meta.env.DEV) {
      console.log('[路由] 已加载路由:', childrenRoutes.map(r => ({ path: r.path, name: r.name })))
    }
  }

  // 生成单个路由
  const generateSingleRoute = (menu: MenuTree, parentPath: string): RouteRecordRaw | null => {
    if (menu.menuType === 2) return null // 跳过按钮

    let routePath = menu.routePath || ''
    if (!routePath) {
      console.warn(`[路由] 菜单 ${menu.menuName} (id=${menu.id}) 没有路由路径`)
      return null
    }

    const route: any = {
      path: routePath,
      name: menu.menuCode || `menu_${menu.id}`,
      meta: {
        title: menu.menuName,
        icon: menu.icon || '',
        hidden: menu.visible !== 1,
        menuId: menu.id,
        menuType: menu.menuType,
        keepAlive: menu.isCached === 1
      }
    }

    // 动态加载组件
    if (menu.componentPath) {
      route.component = loadComponent(menu.componentPath)
      if (import.meta.env.DEV) {
        console.log(`[路由] 加载组件: ${menu.menuName} -> ${menu.componentPath}`)
      }
    } else {
      console.warn(`[路由] 菜单 ${menu.menuName} (id=${menu.id}) 没有组件路径`)
    }

    return route
  }

  // 重置路由
  const resetRoutes = () => {
    routes.value = []
    isRoutesLoaded.value = false
  }

  return {
    routes,
    isRoutesLoaded,
    generateRoutes,
    setRoutes,
    resetRoutes
  }
})
