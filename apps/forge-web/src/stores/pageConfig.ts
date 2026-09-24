/**
 * 页面配置状态管理
 */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import {CACHE_KEY, useCache} from "@/hooks/web/useCache.ts";
import {VxeUI} from "vxe-pc-ui";
import { getPreset, type Palette, type LayoutKind, type StyleKind, type ThemeMode, type DefaultThemeConfig, DEFAULT_CUSTOM_PRIMARY, DEFAULT_THEME, isValidPrimary } from '@/themes'
import { lightStep, darkStep } from '@/themes/color-utils'
const { wsCache } = useCache()
export type ThemeType = 'light' | 'dark'

export interface PageConfig {
  // 标签页设置
  showTabs: boolean
  maxTabsCount: number
  autoHideTabsOnMobile: boolean // 移动端自动隐藏标签页

  // 主题设置
  theme: ThemeType
  palette: Palette
  customPrimary: string // custom 调色板的主色（HEX）
  layout: LayoutKind
  style: StyleKind
  /** 跟随系统默认主题：true 时每次加载应用管理员配置的默认主题；用户手动改动任一主题项后自动置 false */
  followSystemTheme: boolean

  // 侧边栏设置
  sidebarCollapsed: boolean

  // 其他设置
  showBreadcrumb: boolean
  showPageTransition: boolean
  keepAlive: boolean // 页面缓存（切换标签页不刷新）
}

const LOCAL_STORAGE_KEY = 'forge_admin-page-config'

// 三维度合法性枚举（防止 localStorage 被篡改为未知值）
const VALID_PALETTES: Palette[] = ['blue', 'purple', 'green', 'crimson', 'orange', 'cyan', 'teal', 'custom']
// 系统默认主题可用调色板（不含 custom：后端无自定义主色配置入口）
const VALID_SYSTEM_PALETTES: Palette[] = ['blue', 'purple', 'green', 'crimson', 'orange', 'cyan', 'teal']
const VALID_LAYOUTS: LayoutKind[] = ['sidebar', 'top']
const VALID_STYLES: StyleKind[] = ['flat', 'glass', 'card', 'compact']
const VALID_THEME_MODES: ThemeMode[] = ['light', 'dark', 'auto']
// 用户手动变更的主题维度键（updateConfig 据此退出跟随系统默认）
const USER_THEME_KEYS: (keyof PageConfig)[] = ['theme', 'palette', 'layout', 'style', 'customPrimary']

// 默认配置
const defaultConfig: PageConfig = {
  showTabs: true,
  maxTabsCount: 20,
  autoHideTabsOnMobile: true, // 默认移动端隐藏标签页
  theme: 'light',
  palette: 'blue',
  customPrimary: DEFAULT_CUSTOM_PRIMARY,
  layout: 'sidebar',
  style: 'flat',
  followSystemTheme: true,
  sidebarCollapsed: false,
  showBreadcrumb: true,
  showPageTransition: true,
  keepAlive: true
}

export const usePageConfigStore = defineStore('pageConfig', () => {
  // 配置对象
  const config = ref<PageConfig>({ ...defaultConfig })

  // 首次访问标记（localStorage 无记录；待品牌接口返回系统默认主题后定稿落盘）
  const firstVisit = ref(false)

  // 设置对话框显示状态
  const settingsVisible = ref(false)

  // 从 localStorage 加载配置
  const loadConfig = () => {
    try {
      const saved = localStorage.getItem(LOCAL_STORAGE_KEY)
      if (saved) {
        const parsed = JSON.parse(saved)
        config.value = { ...defaultConfig, ...parsed }

        // 老数据迁移：如有 preset 但缺少三维度，从 preset 派生
        if (parsed?.preset && (!parsed.palette || !parsed.layout || !parsed.style)) {
          const preset = getPreset(parsed.preset)
          config.value.palette = preset.palette
          config.value.layout = preset.layout
          config.value.style = preset.style
        }

        // 校验三维度合法性（防止篡改导致 unknown 值）
        if (!VALID_PALETTES.includes(config.value.palette)) config.value.palette = 'blue'
        if (!VALID_LAYOUTS.includes(config.value.layout)) config.value.layout = 'sidebar'
        if (!VALID_STYLES.includes(config.value.style)) config.value.style = 'flat'

        // 校验 customPrimary 合法性
        if (!isValidPrimary(config.value.customPrimary)) {
          config.value.customPrimary = DEFAULT_CUSTOM_PRIMARY
        }

        // 校验 followSystemTheme 类型（默认跟随）
        if (typeof config.value.followSystemTheme !== 'boolean') {
          config.value.followSystemTheme = true
        }
      } else {
        // 首次访问：暂不定稿，待品牌接口返回系统默认主题后应用（见 applySystemDefault）
        firstVisit.value = true
      }
    } catch (error) {
      console.error('加载页面配置失败:', error)
      config.value = { ...defaultConfig }
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

  // 用户手动变更主题任一维度 → 退出跟随，固定为个人选择
  const markUserChoice = () => {
    config.value.followSystemTheme = false
  }

  // 更新配置
  const updateConfig = (key: keyof PageConfig, value: any) => {
    if (USER_THEME_KEYS.includes(key)) {
      markUserChoice()
    }
    (config.value as any)[key] = value
  }

  // 批量更新配置
  const updateMultipleConfig = (updates: Partial<PageConfig>) => {
    Object.assign(config.value, updates)
  }

  // 重置配置（用户显式动作：回到内置默认并退出跟随）
  const resetConfig = () => {
    config.value = { ...defaultConfig }
    config.value.followSystemTheme = false
    applyTheme(config.value.theme)
    applyPalette(config.value.palette)
    applyLayout(config.value.layout)
    applyStyle(config.value.style)
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
      document.documentElement.classList.remove('light')
    } else {
      document.documentElement.classList.add('light')
      document.documentElement.classList.remove('dark')
    }
    wsCache.set(CACHE_KEY.IS_DARK, 'dark' === theme)
    VxeUI.setTheme(theme)
  }

  // 应用调色板
  const applyPalette = (palette: Palette) => {
    config.value.palette = palette
    if (palette === 'custom') {
      applyCustomPalette(config.value.customPrimary)
    } else {
      document.documentElement.setAttribute('data-palette', palette)
      // 切回预设时清理 custom 写入的 inline style，避免残留覆盖预设
      clearCustomPaletteInlineStyle()
    }
  }

  // 应用 custom 调色板：根据主色派生 EP 颜色阶梯并写到 inline style
  const applyCustomPalette = (primary: string) => {
    const safe = isValidPrimary(primary) ? primary : DEFAULT_CUSTOM_PRIMARY
    config.value.customPrimary = safe
    document.documentElement.setAttribute('data-palette', 'custom')
    // inline style 直接覆盖 EP 变量（特异性 1,0,0,0 高于任何 SCSS 选择器）
    const root = document.documentElement
    root.style.setProperty('--app-color-primary', safe)
    root.style.setProperty('--el-color-primary', safe)
    root.style.setProperty('--el-color-primary-light-3', lightStep(safe, 30))
    root.style.setProperty('--el-color-primary-light-5', lightStep(safe, 50))
    root.style.setProperty('--el-color-primary-light-7', lightStep(safe, 70))
    root.style.setProperty('--el-color-primary-light-9', lightStep(safe, 90))
    root.style.setProperty('--el-color-primary-dark-2', darkStep(safe, 20))
    root.style.setProperty('--vxe-ui-primary-color', safe)
  }

  // 清理 custom 调色板写入的 inline style
  const clearCustomPaletteInlineStyle = () => {
    const root = document.documentElement
    ;[
      '--app-color-primary',
      '--el-color-primary',
      '--el-color-primary-light-3',
      '--el-color-primary-light-5',
      '--el-color-primary-light-7',
      '--el-color-primary-light-9',
      '--el-color-primary-dark-2',
      '--vxe-ui-primary-color'
    ].forEach(prop => root.style.removeProperty(prop))
  }

  /** 用户在颜色选择器中改 custom 主色 */
  const changeCustomPrimary = (primary: string) => {
    markUserChoice()
    if (config.value.palette === 'custom') {
      applyCustomPalette(primary)
    } else {
      // 暂存到 config，但不应用（等切到 custom 时再 apply）
      config.value.customPrimary = isValidPrimary(primary) ? primary : DEFAULT_CUSTOM_PRIMARY
    }
  }

  // 应用布局
  const applyLayout = (layout: LayoutKind) => {
    config.value.layout = layout
    document.documentElement.setAttribute('data-layout', layout)
  }

  // 应用样式
  const applyStyle = (style: StyleKind) => {
    config.value.style = style
    document.documentElement.setAttribute('data-style', style)
  }

  // 切换套餐（一次性设置三维度）
  const changePreset = (presetId: string) => {
    markUserChoice()
    const preset = getPreset(presetId)
    applyPalette(preset.palette)
    applyLayout(preset.layout)
    applyStyle(preset.style)
  }

  // 三维度独立切换
  const changePalette = (palette: Palette) => {
    markUserChoice()
    applyPalette(palette)
  }
  const changeLayout = (layout: LayoutKind) => {
    markUserChoice()
    applyLayout(layout)
  }
  const changeStyle = (style: StyleKind) => {
    markUserChoice()
    applyStyle(style)
  }

  // 切换主题
  const toggleTheme = () => {
    markUserChoice()
    const newTheme = config.value.theme === 'light' ? 'dark' : 'light'
    config.value.theme = newTheme
    applyTheme(newTheme)
  }

  // 首次访问应用系统默认主题（品牌公开接口返回；非法/缺失字段逐项回退默认）
  const applySystemDefault = (theme: Partial<DefaultThemeConfig> | null | undefined) => {
    const t = theme ?? {}
    const palette = VALID_SYSTEM_PALETTES.includes(t.palette as Palette) ? t.palette as Palette : DEFAULT_THEME.palette
    const layout = VALID_LAYOUTS.includes(t.layout as LayoutKind) ? t.layout as LayoutKind : DEFAULT_THEME.layout
    const style = VALID_STYLES.includes(t.style as StyleKind) ? t.style as StyleKind : DEFAULT_THEME.style
    const mode = VALID_THEME_MODES.includes(t.mode as ThemeMode) ? t.mode as ThemeMode : DEFAULT_THEME.mode
    const resolved: ThemeType = mode === 'auto'
      ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
      : mode

    config.value.palette = palette
    config.value.layout = layout
    config.value.style = style
    config.value.theme = resolved
    applyTheme(resolved)
    applyPalette(palette)
    applyLayout(layout)
    applyStyle(style)
    saveConfig()
    firstVisit.value = false
  }

  // 首次访问兜底：品牌接口不可用时维持既有行为（跟随浏览器 + 默认套餐）并落盘定稿
  const finalizeFirstVisit = () => {
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
      config.value.theme = 'dark'
      applyTheme('dark')
    }
    saveConfig()
    firstVisit.value = false
  }

  // 监听配置变化，自动保存
  watch(
    () => config.value,
    () => {
      saveConfig()
    },
    { deep: true }
  )

  // 初始化时加载配置并应用
  loadConfig()
  applyTheme(config.value.theme)
  applyPalette(config.value.palette)
  applyLayout(config.value.layout)
  applyStyle(config.value.style)

  return {
    config,
    firstVisit,
    settingsVisible,
    updateConfig,
    updateMultipleConfig,
    resetConfig,
    openSettings,
    closeSettings,
    loadConfig,
    saveConfig,
    applyTheme,
    applyPalette,
    applyLayout,
    applyStyle,
    changePreset,
    changePalette,
    changeLayout,
    changeStyle,
    changeCustomPrimary,
    toggleTheme,
    applySystemDefault,
    finalizeFirstVisit
  }
})
