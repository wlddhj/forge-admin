import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect } from 'vitest'
import { usePageConfigStore } from '@/stores/pageConfig'

describe('pageConfig store - 三维度独立切换', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.className = ''
    document.documentElement.removeAttribute('data-palette')
    document.documentElement.removeAttribute('data-layout')
    document.documentElement.removeAttribute('data-style')
    document.documentElement.removeAttribute('data-theme')
    // 清理 custom 调色板可能写入的 inline style
    document.documentElement.style.cssText = ''
  })

  describe('默认值', () => {
    it('默认三维度为 blue/sidebar/flat', () => {
      const store = usePageConfigStore()
      expect(store.config.palette).toBe('blue')
      expect(store.config.layout).toBe('sidebar')
      expect(store.config.style).toBe('flat')
    })
  })

  describe('applyPalette', () => {
    it('设置 data-palette 属性', () => {
      const store = usePageConfigStore()
      store.applyPalette('purple')
      expect(store.config.palette).toBe('purple')
      expect(document.documentElement.getAttribute('data-palette')).toBe('purple')
    })

    it('不影响 layout 与 style', () => {
      const store = usePageConfigStore()
      store.applyLayout('top')
      store.applyStyle('glass')
      store.applyPalette('green')
      expect(store.config.layout).toBe('top')
      expect(store.config.style).toBe('glass')
    })

    it('不影响 theme（明暗独立）', () => {
      const store = usePageConfigStore()
      store.applyTheme('dark')
      store.applyPalette('crimson')
      expect(document.documentElement.classList.contains('dark')).toBe(true)
    })
  })

  describe('applyLayout', () => {
    it('设置 data-layout 属性', () => {
      const store = usePageConfigStore()
      store.applyLayout('top')
      expect(store.config.layout).toBe('top')
      expect(document.documentElement.getAttribute('data-layout')).toBe('top')
    })
  })

  describe('applyStyle', () => {
    it('设置 data-style 属性', () => {
      const store = usePageConfigStore()
      store.applyStyle('compact')
      expect(store.config.style).toBe('compact')
      expect(document.documentElement.getAttribute('data-style')).toBe('compact')
    })
  })

  describe('changePreset（套餐快捷切换）', () => {
    it('一次性设置三维度', () => {
      const store = usePageConfigStore()
      store.changePreset('geek')
      expect(store.config.palette).toBe('purple')
      expect(store.config.layout).toBe('top')
      expect(store.config.style).toBe('glass')
    })

    it('未知 presetId 回落到 default', () => {
      const store = usePageConfigStore()
      store.changePreset('unknown-id')
      expect(store.config.palette).toBe('blue')
      expect(store.config.layout).toBe('sidebar')
      expect(store.config.style).toBe('flat')
    })
  })

  describe('loadConfig 老数据迁移', () => {
    it('有 preset 没三维度 → 派生三维度', () => {
      localStorage.setItem('forge_admin-page-config', JSON.stringify({
        theme: 'dark',
        preset: 'geek'
      }))
      const store = usePageConfigStore()
      store.loadConfig()
      expect(store.config.palette).toBe('purple')
      expect(store.config.layout).toBe('top')
      expect(store.config.style).toBe('glass')
    })

    it('三维度已被显式设置 → 优先使用三维度', () => {
      localStorage.setItem('forge_admin-page-config', JSON.stringify({
        theme: 'light',
        preset: 'geek',
        palette: 'green',
        layout: 'sidebar',
        style: 'card'
      }))
      const store = usePageConfigStore()
      store.loadConfig()
      expect(store.config.palette).toBe('green')
      expect(store.config.layout).toBe('sidebar')
      expect(store.config.style).toBe('card')
    })

    it('localStorage 损坏时不抛错', () => {
      localStorage.setItem('forge_admin-page-config', '{not json')
      expect(() => {
        const store = usePageConfigStore()
        store.loadConfig()
      }).not.toThrow()
    })

    it('三维度被篡改为非法值 → 回落默认', () => {
      localStorage.setItem('forge_admin-page-config', JSON.stringify({
        palette: 'unknown',
        layout: 'invalid',
        style: 'wrong'
      }))
      const store = usePageConfigStore()
      store.loadConfig()
      expect(store.config.palette).toBe('blue')
      expect(store.config.layout).toBe('sidebar')
      expect(store.config.style).toBe('flat')
    })
  })

  describe('resetConfig store 自洽', () => {
    it('重置后三维度回到默认并应用到 DOM', () => {
      const store = usePageConfigStore()
      store.changePreset('geek')
      store.resetConfig()
      expect(store.config.palette).toBe('blue')
      expect(store.config.layout).toBe('sidebar')
      expect(store.config.style).toBe('flat')
      expect(document.documentElement.getAttribute('data-palette')).toBe('blue')
      expect(document.documentElement.getAttribute('data-layout')).toBe('sidebar')
      expect(document.documentElement.getAttribute('data-style')).toBe('flat')
    })

    it('重置后 theme 回到 light 并应用到 DOM', () => {
      const store = usePageConfigStore()
      store.applyTheme('dark')
      store.resetConfig()
      expect(store.config.theme).toBe('light')
      expect(document.documentElement.classList.contains('light')).toBe(true)
      expect(document.documentElement.classList.contains('dark')).toBe(false)
    })
  })

  describe('custom 调色板', () => {
    it('applyPalette("custom") 使用 customPrimary 写入 inline style', () => {
      const store = usePageConfigStore()
      store.changeCustomPrimary('#13C2C2')
      store.applyPalette('custom')
      const root = document.documentElement
      expect(root.style.getPropertyValue('--el-color-primary')).toBe('#13C2C2')
      expect(root.style.getPropertyValue('--el-color-primary-light-3')).toBeTruthy()
      expect(root.style.getPropertyValue('--app-color-primary')).toBe('#13C2C2')
    })

    it('changeCustomPrimary 在 custom 模式下即时应用', () => {
      const store = usePageConfigStore()
      store.applyPalette('custom')
      store.changeCustomPrimary('#722ED1')
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('#722ED1')
      expect(store.config.customPrimary).toBe('#722ED1')
    })

    it('changeCustomPrimary 在非 custom 模式下只暂存不应用', () => {
      const store = usePageConfigStore()
      store.applyPalette('blue')
      store.changeCustomPrimary('#FF7A45')
      expect(store.config.customPrimary).toBe('#FF7A45')
      // 不应写入 inline style（blue 模式应由 SCSS 接管）
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('')
    })

    it('applyCustomPalette 非法主色回落 DEFAULT_CUSTOM_PRIMARY', () => {
      const store = usePageConfigStore()
      store.applyPalette('custom')
      store.changeCustomPrimary('invalid-color')
      expect(store.config.customPrimary).toBe('#409EFF')  // DEFAULT_CUSTOM_PRIMARY
    })

    it('切回预设调色板时清理 inline style', () => {
      const store = usePageConfigStore()
      store.applyPalette('custom')
      store.changeCustomPrimary('#13C2C2')
      // 验证 inline style 已写入
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBeTruthy()
      // 切回 blue
      store.applyPalette('blue')
      // inline style 应被清理
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('')
      expect(document.documentElement.style.getPropertyValue('--app-color-primary')).toBe('')
    })

    it('loadConfig 校验 customPrimary 合法性', () => {
      localStorage.setItem('forge_admin-page-config', JSON.stringify({
        palette: 'custom',
        customPrimary: 'invalid-color',
        layout: 'sidebar',
        style: 'flat'
      }))
      const store = usePageConfigStore()
      store.loadConfig()
      expect(store.config.customPrimary).toBe('#409EFF')  // 非法回落默认
    })

    it('loadConfig 接受合法的 custom 配置', () => {
      localStorage.setItem('forge_admin-page-config', JSON.stringify({
        palette: 'custom',
        customPrimary: '#13C2C2',
        layout: 'sidebar',
        style: 'flat'
      }))
      const store = usePageConfigStore()
      store.loadConfig()
      expect(store.config.palette).toBe('custom')
      expect(store.config.customPrimary).toBe('#13C2C2')
    })

    it('resetConfig 重置 palette 为 blue 且清理 custom inline style', () => {
      const store = usePageConfigStore()
      store.applyPalette('custom')
      store.changeCustomPrimary('#13C2C2')
      store.resetConfig()
      expect(store.config.palette).toBe('blue')
      expect(store.config.customPrimary).toBe('#409EFF')  // DEFAULT_CUSTOM_PRIMARY
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('')
    })
  })

  describe('系统默认主题（首次访问）', () => {
    it('首次访问 firstVisit=true 且不落盘', () => {
      const store = usePageConfigStore()
      expect(store.firstVisit).toBe(true)
      expect(localStorage.getItem('forge_admin-page-config')).toBeNull()
    })

    it('已有本地配置 firstVisit=false', () => {
      localStorage.setItem('forge_admin-page-config', JSON.stringify({ palette: 'green' }))
      const store = usePageConfigStore()
      expect(store.firstVisit).toBe(false)
    })

    it('applySystemDefault 应用三维度并落盘定稿', () => {
      const store = usePageConfigStore()
      store.applySystemDefault({ palette: 'teal', layout: 'top', style: 'glass', mode: 'dark' })
      expect(store.config.palette).toBe('teal')
      expect(store.config.layout).toBe('top')
      expect(store.config.style).toBe('glass')
      expect(store.config.theme).toBe('dark')
      expect(document.documentElement.getAttribute('data-palette')).toBe('teal')
      expect(store.firstVisit).toBe(false)
      expect(localStorage.getItem('forge_admin-page-config')).toContain('"palette":"teal"')
    })

    it('applySystemDefault mode=auto 跟随浏览器（happy-dom 默认 light）', () => {
      const store = usePageConfigStore()
      store.applySystemDefault({ palette: 'cyan', mode: 'auto' })
      expect(store.config.theme).toBe('light')
      expect(store.firstVisit).toBe(false)
    })

    it('applySystemDefault 非法/缺失字段逐项回退默认', () => {
      const store = usePageConfigStore()
      store.applySystemDefault({ palette: 'custom', layout: 'invalid' as any, style: undefined, mode: 'wrong' as any })
      expect(store.config.palette).toBe('blue')
      expect(store.config.layout).toBe('sidebar')
      expect(store.config.style).toBe('flat')
      expect(store.config.theme).toBe('light')  // mode 回退 auto → prefers light
    })

    it('applySystemDefault(null) 全量回退默认值', () => {
      const store = usePageConfigStore()
      store.applySystemDefault(null)
      expect(store.config.palette).toBe('blue')
      expect(store.firstVisit).toBe(false)
      expect(localStorage.getItem('forge_admin-page-config')).toBeTruthy()
    })

    it('finalizeFirstVisit 落盘并结束首次访问态', () => {
      const store = usePageConfigStore()
      store.finalizeFirstVisit()
      expect(store.firstVisit).toBe(false)
      expect(localStorage.getItem('forge_admin-page-config')).toBeTruthy()
      // happy-dom prefers-color-scheme 默认 light
      expect(store.config.theme).toBe('light')
    })
  })

  describe('跟随系统默认主题（followSystemTheme）', () => {
    it('默认开启', () => {
      const store = usePageConfigStore()
      expect(store.config.followSystemTheme).toBe(true)
    })

    it('存量老数据（无 follow 字段）合并后为 true', () => {
      localStorage.setItem('forge_admin-page-config', JSON.stringify({ palette: 'green' }))
      const store = usePageConfigStore()
      store.loadConfig()
      expect(store.config.followSystemTheme).toBe(true)
    })

    it('手动切换套餐后自动关闭', () => {
      const store = usePageConfigStore()
      store.changePreset('geek')
      expect(store.config.followSystemTheme).toBe(false)
    })

    it('手动切换调色板/布局/风格后自动关闭', () => {
      const store = usePageConfigStore()
      store.changePalette('orange')
      expect(store.config.followSystemTheme).toBe(false)
      const store2 = usePageConfigStore()
      store2.changeLayout('top')
      expect(store2.config.followSystemTheme).toBe(false)
    })

    it('updateConfig 更新主题维度后自动关闭', () => {
      const store = usePageConfigStore()
      store.updateConfig('theme', 'dark')
      expect(store.config.followSystemTheme).toBe(false)
    })

    it('toggleTheme 后自动关闭', () => {
      const store = usePageConfigStore()
      store.toggleTheme()
      expect(store.config.followSystemTheme).toBe(false)
    })

    it('resetConfig（用户显式动作）后关闭', () => {
      const store = usePageConfigStore()
      store.resetConfig()
      expect(store.config.followSystemTheme).toBe(false)
    })

    it('applySystemDefault（系统应用）不影响跟随状态', () => {
      const store = usePageConfigStore()
      store.applySystemDefault({ palette: 'teal', mode: 'dark' })
      expect(store.config.followSystemTheme).toBe(true)
    })

    it('updateConfig 非主题键不影响跟随状态', () => {
      const store = usePageConfigStore()
      store.updateConfig('showTabs', false)
      expect(store.config.followSystemTheme).toBe(true)
    })

    it('手动关闭后可重新开启跟随（设置面板开关路径）', () => {
      const store = usePageConfigStore()
      store.changePalette('orange')
      expect(store.config.followSystemTheme).toBe(false)
      store.updateConfig('followSystemTheme', true)
      store.applySystemDefault({ palette: 'teal', layout: 'sidebar', style: 'compact', mode: 'dark' })
      expect(store.config.followSystemTheme).toBe(true)
      expect(store.config.palette).toBe('teal')
    })
  })
})
