import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect } from 'vitest'
import { usePageConfigStore } from '@/stores/pageConfig'

describe('pageConfig store - 套餐切换', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.className = ''
    document.documentElement.removeAttribute('data-palette')
    document.documentElement.removeAttribute('data-layout')
    document.documentElement.removeAttribute('data-style')
    document.documentElement.removeAttribute('data-theme')
  })

  it('默认 preset 为 default', () => {
    const store = usePageConfigStore()
    expect(store.config.preset).toBe('default')
  })

  it('applyPreset 设置三个 data 属性', () => {
    const store = usePageConfigStore()
    store.applyPreset('geek')
    const html = document.documentElement
    expect(html.getAttribute('data-palette')).toBe('purple')
    expect(html.getAttribute('data-layout')).toBe('top')
    expect(html.getAttribute('data-style')).toBe('glass')
  })

  it('applyPreset 不影响 theme（明暗独立）', () => {
    const store = usePageConfigStore()
    store.applyTheme('dark')
    store.applyPreset('business')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('changePreset 同时更新 config 和 data 属性', () => {
    const store = usePageConfigStore()
    store.changePreset('dark-pro')
    expect(store.config.preset).toBe('dark-pro')
    expect(document.documentElement.getAttribute('data-palette')).toBe('crimson')
  })

  it('未知 presetId 回落到 default', () => {
    const store = usePageConfigStore()
    store.applyPreset('unknown-id')
    expect(store.config.preset).toBe('default')
    expect(document.documentElement.getAttribute('data-palette')).toBe('blue')
  })

  it('localStorage 缺失 preset 字段时回落 default', () => {
    localStorage.setItem('forge_admin-page-config', JSON.stringify({ theme: 'dark' }))
    const store = usePageConfigStore()
    store.loadConfig()
    expect(store.config.preset).toBe('default')
  })

  it('localStorage 损坏时不抛错', () => {
    localStorage.setItem('forge_admin-page-config', '{not json')
    expect(() => {
      const store = usePageConfigStore()
      store.loadConfig()
    }).not.toThrow()
  })

  it('resetConfig 把 preset 重置为 default 并应用到 DOM', () => {
    const store = usePageConfigStore()
    store.applyPreset('geek')
    store.resetConfig()
    expect(store.config.preset).toBe('default')
    // store 自洽：resetConfig 内部应触发 applyPreset 与 applyTheme
    expect(document.documentElement.getAttribute('data-palette')).toBe('blue')
    expect(document.documentElement.getAttribute('data-layout')).toBe('sidebar')
    expect(document.documentElement.getAttribute('data-style')).toBe('flat')
  })

  it('resetConfig 把 theme 重置为 light 并应用到 DOM', () => {
    const store = usePageConfigStore()
    store.applyTheme('dark')
    store.resetConfig()
    expect(store.config.theme).toBe('light')
    expect(document.documentElement.classList.contains('light')).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
})
