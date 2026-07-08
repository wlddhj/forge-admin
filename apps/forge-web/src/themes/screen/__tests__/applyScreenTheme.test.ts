import { describe, it, expect, beforeEach } from 'vitest'

vi.mock('@/stores/pageConfig', () => ({
  usePageConfigStore: () => ({
    config: { value: { palette: 'blue' } }
  })
}))

import { applyScreenTheme, getCurrentScreenTheme } from '../index'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'

describe('applyScreenTheme', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-screen-theme')
    document.documentElement.style.removeProperty('--screen-accent')
  })

  it('写入 data-screen-theme 属性', () => {
    applyScreenTheme('dark-tech')
    expect(document.documentElement.getAttribute('data-screen-theme')).toBe('dark-tech')
  })

  it('从 pageConfig.palette 派生 --screen-accent', () => {
    applyScreenTheme('blue-deep')
    const accent = document.documentElement.style.getPropertyValue('--screen-accent')
    expect(accent).toBeTruthy()
  })

  it('getCurrentScreenTheme 默认返回 SCREEN_DEFAULT_THEME', () => {
    expect(getCurrentScreenTheme()).toBe(SCREEN_DEFAULT_THEME)
  })

  it('getCurrentScreenTheme 反映已设置的主题', () => {
    applyScreenTheme('black-gold')
    expect(getCurrentScreenTheme()).toBe('black-gold')
  })
})