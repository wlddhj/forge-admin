import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { MenuTree } from '@/types/system'
import { loadComponent, LAYOUT_COMPONENT } from '@/router/constants'

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
        name: `menu_${menu.id}`,
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
          route.component = LAYOUT_COMPONENT
          route.redirect = menu.redirectPath || undefined
        } else {
          route.component = loadComponent(menu.componentPath)
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
        name: `menu_${menu.id}`,
        meta: {
          title: menu.menuName,
          icon: menu.icon,
          hidden: menu.visible !== 1
        },
        children: []
      }

      if (menu.componentPath) {
        route.component = loadComponent(menu.componentPath)
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
      component: loadComponent('/views/dashboard/index'),
      meta: { title: '首页', icon: 'HomeFilled', affix: true }
    } as RouteRecordRaw)

    // 2. 添加个人中心路由
    childrenRoutes.push({
      path: '/profile',
      name: 'Profile',
      component: loadComponent('/views/profile/index'),
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
        component: LAYOUT_COMPONENT,
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
