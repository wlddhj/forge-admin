import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { MenuTree } from '@/types/system'
import { loadComponent, LAYOUT_COMPONENT } from '@/router/constants'

export const usePermissionStore = defineStore('permission', () => {
  const routes = ref<RouteRecordRaw[]>([])
  const isRoutesLoaded = ref(false)

  // 设置路由
  const setRoutes = (menus: MenuTree[]) => {
    const childrenRoutes: RouteRecordRaw[] = []

    // 1. 添加首页路由
    childrenRoutes.push({
      path: '/dashboard',
      name: 'Dashboard',
      component: loadComponent('/views/dashboard/index', 'Dashboard'),
      meta: { title: '首页', icon: 'HomeFilled', affix: true }
    } as RouteRecordRaw)

    // 2. 添加个人中心路由
    childrenRoutes.push({
      path: '/profile',
      name: 'Profile',
      component: loadComponent('/views/profile/index', 'Profile'),
      meta: { title: '个人中心', icon: 'User', hidden: true }
    } as RouteRecordRaw)

    // 3. 添加隐藏路由（没有对应菜单的页面）
    // 模型设计器
    childrenRoutes.push({
      path: '/workflow/model/designer',
      name: 'ModelDesigner',
      component: loadComponent('/views/workflow/model/ModelDesigner', 'ModelDesigner'),
      meta: { title: '模型设计', hidden: true }
    } as RouteRecordRaw)

    // 流程设计器
    childrenRoutes.push({
      path: '/workflow/process/designer',
      name: 'ProcessDesigner',
      component: loadComponent('/views/workflow/process/ProcessDesigner', 'ProcessDesigner'),
      meta: { title: '流程设计', hidden: true }
    } as RouteRecordRaw)

    // 4. 递归提取所有有组件路径的菜单，生成平级路由
    const extractRoutes = (menus: MenuTree[]) => {
      for (const menu of menus) {
        if (menu.menuType === 2) continue // 跳过按钮

        // 只处理有组件路径的菜单（排除 Layout 类型）
        if (menu.componentPath && menu.componentPath !== 'Layout' && menu.componentPath !== 'Layouts') {
          const route: RouteRecordRaw = {
            path: menu.routePath || '',
            name: menu.menuCode || `menu_${menu.id}`,
            meta: {
              title: menu.menuName,
              icon: menu.icon || '',
              hidden: menu.visible !== 1,
              menuId: menu.id,
              menuType: menu.menuType,
              keepAlive: menu.isCached === 1
            },
            component: loadComponent(menu.componentPath, menu.menuCode || `menu_${menu.id}`)
          }

          if (import.meta.env.DEV) {
            console.log(`[路由] 添加路由: ${menu.menuName} -> ${menu.routePath}`)
          }

          // 检查路由是否已存在，避免重复
          const exists = childrenRoutes.some(r => r.path === route.path)
          if (!exists) {
            childrenRoutes.push(route)
          }
        }

        // 递归处理子菜单
        if (menu.children?.length) {
          extractRoutes(menu.children)
        }
      }
    }

    extractRoutes(menus)

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

  // 重置路由
  const resetRoutes = () => {
    routes.value = []
    isRoutesLoaded.value = false
  }

  return {
    routes,
    isRoutesLoaded,
    setRoutes,
    resetRoutes
  }
})
