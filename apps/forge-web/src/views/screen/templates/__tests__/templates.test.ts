import { describe, it, expect } from 'vitest'
import { presetTemplates, getTemplate } from '../index'
import { SCREEN_DEFAULT_THEME } from '@/constants/screen'

describe('presetTemplates', () => {
  it('含 6 个模板', () => {
    expect(presetTemplates.length).toBe(6)
    expect(presetTemplates.map(t => t.code)).toEqual([
      'blank', 'hero-3', 'quad', 'top-bottom', 'triple', 'presentation'
    ])
  })

  it('每个模板的 config 都是合法 ScreenConfig', () => {
    for (const t of presetTemplates) {
      expect(t.config.version).toBe(1)
      expect(t.config.theme).toBe(SCREEN_DEFAULT_THEME)
      expect(Array.isArray(t.config.cards)).toBe(true)
      t.config.cards.forEach(c => {
        expect(c.id).toBeTruthy()
        expect(c.type).toBeTruthy()
        expect(c.w).toBeGreaterThan(0)
        expect(c.h).toBeGreaterThan(0)
      })
    }
  })

  it('getTemplate(blank) 返回空白模板', () => {
    const t = getTemplate('blank')
    expect(t.code).toBe('blank')
    expect(t.config.cards.length).toBe(0)
  })

  it('getTemplate(unknown) 回退到空白', () => {
    const t = getTemplate('non-existent')
    expect(t.code).toBe('blank')
  })

  it('所有卡片 id 唯一', () => {
    const allIds = presetTemplates.flatMap(t => t.config.cards.map(c => c.id))
    expect(new Set(allIds).size).toBe(allIds.length)
  })
})
