import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTenantConfig } from '@/composables/useTenantConfig'
import { CONSTANT_ROUTES, WHITE_LIST } from './constants'

// 静态路由（不需要权限）
export const constantRoutes: RouteRecordRaw[] = CONSTANT_ROUTES

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes
})

// 白名单（从常量导入）
const whiteList = WHITE_LIST

// 动态添加路由
export const addDynamicRoutes = (routes: RouteRecordRaw[]) => {
  // 添加动态路由
  routes.forEach(route => {
    router.addRoute(route)
  })

  // 添加 404 路由（必须最后添加）
  router.addRoute({
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    meta: { hidden: true }
  })
}

// 移除动态路由
export const resetRouter = () => {
  const newRouter = createRouter({
    history: createWebHistory(),
    routes: constantRoutes
  })
  ;(router as any).matcher = (newRouter as any).matcher
}

// 路由守卫
router.beforeEach(async (to, from, next) => {
  NProgress.start()

  const userStore = useUserStore()
  const permissionStore = usePermissionStore()
  const token = userStore.token

  // 等待多租户配置加载完成,避免登录页/头部切换器在 enabled 未确定时渲染
  const { loadIfNeeded } = useTenantConfig()
  await loadIfNeeded()

  if (token) {
    if (to.path === '/login') {
      // 已登录，跳转到首页
      next({ path: '/dashboard' })
      NProgress.done()
    } else {
      // 检查是否已加载路由
      if (!permissionStore.isRoutesLoaded) {
        try {
          // 获取用户信息
          await userStore.getUserInfoAction()
          // 获取用户菜单
          const menus = await userStore.getMenusAction()
          // 生成动态路由
          permissionStore.setRoutes(menus)
          // 添加动态路由
          addDynamicRoutes(permissionStore.routes)
          // 如果目标是根路径，重定向到 dashboard
          if (to.path === '/') {
            next({ path: '/dashboard', replace: true })
          } else {
            next({ ...to, replace: true })
          }
        } catch (error) {
          // 获取用户信息失败，清除token并跳转登录
          const permissionStore2 = usePermissionStore()
          userStore.logoutAction().finally(() => {
            permissionStore2.resetRoutes()
          })
          next(`/login?redirect=${to.path}`)
          NProgress.done()
        }
      } else {
        // 已加载路由，处理根路径
        if (to.path === '/') {
          next({ path: '/dashboard' })
        } else {
          next()
        }
      }
    }
  } else {
    if (whiteList.includes(to.path) || to.meta?.noAuth) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
