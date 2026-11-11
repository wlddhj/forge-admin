/**
 * Tabs 标签页状态管理
 */
import { defineStore } from 'pinia'
import { ref, watch, computed } from 'vue'

export interface TabItem {
  path: string
  title: string
  icon?: string
  closable: boolean
  routeName?: string // 路由名称，用于 keep-alive :include 匹配
}

const LOCAL_STORAGE_KEY = 'standadmin-tabs'

// 首页固定路径（与 permission.ts 动态路由 / dashboard 对齐）
const HOME_PATH = '/dashboard'

// 首页固定标签（title/icon/routeName 与 permission.ts 首页路由 meta 保持一致）
const createHomeTab = (): TabItem => ({
  path: HOME_PATH,
  title: '首页',
  icon: 'HomeFilled',
  closable: false,
  routeName: 'Dashboard'
})

export const useTabsStore = defineStore('tabs', () => {
  // 标签页列表
  const tabs = ref<TabItem[]>([])

  // 当前激活的标签页
  const activeTab = ref('')

  // 当前缓存的路由名称列表（用于 keep-alive :include）
  const cachedViews = computed(() => {
    return tabs.value
      .map(t => t.routeName)
      .filter((name): name is string => !!name)
  })

  // 首页固定：始终存在于第一位且不可关闭（登录失效重登/刷新/恢复后自动补齐）
  const ensureHomeTab = () => {
    const index = tabs.value.findIndex(t => t.path === HOME_PATH)
    if (index === -1) {
      tabs.value.unshift(createHomeTab())
    } else if (index > 0 || tabs.value[index].closable) {
      // 已存在但位置/属性异常（历史数据可能被写成可关闭），校正为固定首页
      const [existing] = tabs.value.splice(index, 1)
      tabs.value.unshift({ ...existing, ...createHomeTab() })
    }
  }

  // 从 localStorage 加载标签页（刷新时仅保留当前激活标签）
  const loadTabs = () => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY)
      if (saved) {
        const { tabs: savedTabs, activeTab: savedActive } = JSON.parse(saved)
        const active = savedActive || ''
        const activeTabItem = (savedTabs || []).find((t: TabItem) => t.path === active)
        if (activeTabItem) {
          tabs.value = [activeTabItem]
        }
        activeTab.value = active
      }
    } catch (error) {
      console.error('加载标签页失败:', error)
    }
    ensureHomeTab()
  }

  // 保存标签页到 localStorage
  const saveTabs = () => {
    try {
      localStorage.setItem(
        LOCAL_STORAGE_KEY,
        JSON.stringify({
          tabs: tabs.value,
          activeTab: activeTab.value
        })
      )
    } catch (error) {
      console.error('保存标签页失败:', error)
    }
  }

  // 添加标签页
  const addTab = (tab: TabItem) => {
    // 添加非首页标签时自动补齐固定首页（覆盖登出清空后重新登录、store 未重建的场景）
    if (tab.path !== HOME_PATH) {
      ensureHomeTab()
    }

    // 检查是否已存在
    const existIndex = tabs.value.findIndex(t => t.path === tab.path)
    if (existIndex === -1) {
      tabs.value.push(tab)
    } else {
      // 如果已存在，更新标题和路由名称（可能动态变化，或从 localStorage 恢复时缺少 routeName）
      tabs.value[existIndex].title = tab.title
      if (tab.routeName) {
        tabs.value[existIndex].routeName = tab.routeName
      }
    }
    activeTab.value = tab.path
  }

  // 移除标签页
  const removeTab = (path: string) => {
    const index = tabs.value.findIndex(t => t.path === path)
    if (index > -1) {
      tabs.value.splice(index, 1)

      // 如果关闭的是当前激活的标签页，需要激活另一个
      if (activeTab.value === path && tabs.value.length > 0) {
        // 激活右侧标签页，如果没有则激活左侧
        const nextTab = tabs.value[index] || tabs.value[index - 1]
        activeTab.value = nextTab?.path || ''
      }
    }
  }

  // 关闭其他标签页
  const closeOtherTabs = (path: string) => {
    const currentTab = tabs.value.find(t => t.path === path)
    const homeTab = tabs.value.find(t => t.path === HOME_PATH)

    if (currentTab) {
      // 如果当前不是首页，需要同时保留首页
      if (homeTab && path !== HOME_PATH) {
        tabs.value = [homeTab, currentTab]
      } else {
        tabs.value = [currentTab]
      }
      activeTab.value = currentTab.path
    }
  }

  // 清空所有标签页（退出登录时使用）
  const clearAllTabs = () => {
    tabs.value = []
    activeTab.value = ''
    saveTabs()
  }

  // 关闭所有标签页
  const closeAllTabs = () => {
    // 保留首页
    const homeTab = tabs.value.find(t => t.path === HOME_PATH)
    if (homeTab) {
      tabs.value = [homeTab]
      activeTab.value = HOME_PATH
    } else {
      tabs.value = []
      activeTab.value = ''
    }
  }

  // 关闭左侧标签页
  const closeLeftTabs = (path: string) => {
    const index = tabs.value.findIndex(t => t.path === path)
    if (index > 0) {
      // 确保首页不被关闭
      const homeIndex = tabs.value.findIndex(t => t.path === HOME_PATH)
      if (homeIndex >= 0 && homeIndex < index) {
        tabs.value = [tabs.value[homeIndex], ...tabs.value.slice(index)]
      } else {
        tabs.value = tabs.value.slice(index)
      }
    }
  }

  // 关闭右侧标签页
  const closeRightTabs = (path: string) => {
    const index = tabs.value.findIndex(t => t.path === path)
    if (index > -1 && index < tabs.value.length - 1) {
      tabs.value = tabs.value.slice(0, index + 1)
    }
  }

  // 设置激活的标签页
  const setActiveTab = (path: string) => {
    activeTab.value = path
  }

  // 监听变化，自动保存
  watch(
    () => [tabs.value, activeTab.value],
    () => {
      saveTabs()
    },
    { deep: true }
  )

  // 初始化时加载
  loadTabs()

  return {
    tabs,
    activeTab,
    cachedViews,
    addTab,
    removeTab,
    closeOtherTabs,
    closeAllTabs,
    clearAllTabs,
    closeLeftTabs,
    closeRightTabs,
    setActiveTab,
    loadTabs,
    saveTabs
  }
})
