/**
 * 页面配置状态管理
 */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeType = 'light' | 'dark'

export interface PageConfig {
  // 标签页设置
  showTabs: boolean
  maxTabsCount: number
  autoHideTabsOnMobile: boolean // 移动端自动隐藏标签页

  // 主题设置
  theme: ThemeType

  // 侧边栏设置
  sidebarCollapsed: boolean

  // 其他设置
  showBreadcrumb: boolean
  showPageTransition: boolean
  keepAlive: boolean // 页面缓存（切换标签页不刷新）
}

const LOCAL_STORAGE_KEY = 'forge_admin-page-config'

// 默认配置
const defaultConfig: PageConfig = {
  showTabs: true,
  maxTabsCount: 20,
  autoHideTabsOnMobile: true, // 默认移动端隐藏标签页
  theme: 'light',
  sidebarCollapsed: false,
  showBreadcrumb: true,
  showPageTransition: true,
  keepAlive: true
}

export const usePageConfigStore = defineStore('pageConfig', () => {
  // 配置对象
  const config = ref<PageConfig>({ ...defaultConfig })

  // 设置对话框显示状态
  const settingsVisible = ref(false)

  // 从 localStorage 加载配置
  const loadConfig = () => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY)
      if (saved) {
        const parsed = JSON.parse(saved)
        config.value = { ...defaultConfig, ...parsed }
      }
    } catch (error) {
      console.error('加载页面配置失败:', error)
    }
  }

  // 保存配置到 localStorage
  const saveConfig = () => {
    try {
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(config.value))
    } catch (error) {
      console.error('保存页面配置失败:', error)
    }
  }

  // 更新配置
  const updateConfig = (key: keyof PageConfig, value: any) => {
    (config.value as any)[key] = value
  }

  // 批量更新配置
  const updateMultipleConfig = (updates: Partial<PageConfig>) => {
    Object.assign(config.value, updates)
  }

  // 重置配置
  const resetConfig = () => {
    config.value = { ...defaultConfig }
  }

  // 打开设置面板
  const openSettings = () => {
    settingsVisible.value = true
  }

  // 关闭设置面板
  const closeSettings = () => {
    settingsVisible.value = false
  }

  // 应用主题
  const applyTheme = (theme: ThemeType) => {
    document.documentElement.setAttribute('data-theme', theme)
    if (theme === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  // 切换主题
  const toggleTheme = () => {
    const newTheme = config.value.theme === 'light' ? 'dark' : 'light'
    config.value.theme = newTheme
    applyTheme(newTheme)
  }

  // 监听配置变化，自动保存
  watch(
    () => config.value,
    () => {
      saveConfig()
    },
    { deep: true }
  )

  // 初始化时加载配置并应用主题
  loadConfig()
  applyTheme(config.value.theme)

  return {
    config,
    settingsVisible,
    updateConfig,
    updateMultipleConfig,
    resetConfig,
    openSettings,
    closeSettings,
    loadConfig,
    saveConfig,
    applyTheme,
    toggleTheme
  }
})
