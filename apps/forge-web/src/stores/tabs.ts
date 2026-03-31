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

  // 从 localStorage 加载标签页
  const loadTabs = () => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY)
      if (saved) {
        const { tabs: savedTabs, activeTab: savedActive } = JSON.parse(saved)
        tabs.value = savedTabs || []
        activeTab.value = savedActive || ''
      }
    } catch (error) {
      console.error('加载标签页失败:', error)
    }
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
    const homeTab = tabs.value.find(t => t.path === '/dashboard')

    if (currentTab) {
      // 如果当前不是首页，需要同时保留首页
      if (homeTab && path !== '/dashboard') {
        tabs.value = [homeTab, currentTab]
      } else {
        tabs.value = [currentTab]
      }
      activeTab.value = currentTab.path
    }
  }

  // 关闭所有标签页
  const closeAllTabs = () => {
    // 保留首页
    const homeTab = tabs.value.find(t => t.path === '/dashboard')
    if (homeTab) {
      tabs.value = [homeTab]
      activeTab.value = '/dashboard'
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
      const homeIndex = tabs.value.findIndex(t => t.path === '/dashboard')
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
    closeLeftTabs,
    closeRightTabs,
    setActiveTab,
    loadTabs,
    saveTabs
  }
})
