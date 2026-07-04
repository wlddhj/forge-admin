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
})
