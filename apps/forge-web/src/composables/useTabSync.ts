import { watch } from 'vue'
import type { ComputedRef } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { useTabsStore } from '@/stores/tabs'
import type { PageConfig } from '@/stores/pageConfig'

type TabsStoreInstance = ReturnType<typeof useTabsStore>

interface UseTabSyncOptions {
  route: RouteLocationNormalizedLoaded
  tabsStore: TabsStoreInstance
  shouldShowTabs: ComputedRef<boolean>
  config: PageConfig
}

/**
 * 路由变化同步标签页（含 maxTabsCount 淘汰），供无侧栏布局使用。
 * 逻辑与 LayoutTop/LayoutSidebar 内联实现保持一致。
 */
export function useTabSync({ route, tabsStore, shouldShowTabs, config }: UseTabSyncOptions) {
  watch(
    () => route.path,
    (path) => {
      if (path && route.meta?.title && shouldShowTabs.value) {
        if (tabsStore.tabs.length >= config.maxTabsCount) {
          const closableTab = tabsStore.tabs.find(t => t.closable)
          if (closableTab) tabsStore.removeTab(closableTab.path)
        }
        tabsStore.addTab({
          path,
          title: route.meta.title as string,
          icon: route.meta.icon as string,
          closable: path !== '/dashboard',
          routeName: route.name as string
        })
      }
    },
    { immediate: true }
  )
}
