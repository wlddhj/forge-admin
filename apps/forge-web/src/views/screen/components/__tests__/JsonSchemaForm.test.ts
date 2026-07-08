import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('element-plus', () => ({}))

import { useScreenEditorStore } from '@/stores/screenEditor'
import { registerBuiltinCards } from '@/views/screen/cards/registry'
import type { JSONSchema7 } from 'json-schema'

describe('screenEditor store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    try { registerBuiltinCards() } catch { /* 幂等 */ }
  })

  it('初始状态：空 config，无 activeCard', () => {
    const s = useScreenEditorStore()
    expect(s.config.cards.length).toBe(0)
    expect(s.activeCard).toBeNull()
    expect(s.isDirty).toBe(false)
    expect(s.canUndo).toBe(false)
    expect(s.canRedo).toBe(false)
  })

  it('addCard 增加卡片并设 activeCardId', () => {
    const s = useScreenEditorStore()
    s.addCard('digital-number', { x: 2, y: 3 })
    expect(s.config.cards.length).toBe(1)
    expect(s.config.cards[0].type).toBe('digital-number')
    expect(s.config.cards[0].x).toBe(2)
    expect(s.activeCard?.type).toBe('digital-number')
    expect(s.isDirty).toBe(true)
  })

  it('removeCard 删除卡片并清除 activeCardId', () => {
    const s = useScreenEditorStore()
    s.addCard('digital-number', { x: 0, y: 0 })
    const id = s.activeCardId!
    s.removeCard(id)
    expect(s.config.cards.length).toBe(0)
    expect(s.activeCardId).toBeNull()
  })

  it('updateCard 修改卡片属性', () => {
    const s = useScreenEditorStore()
    s.addCard('line-chart', { x: 0, y: 0 })
    s.updateCard(s.activeCardId!, { title: 'New Title', refresh: 60 })
    expect(s.activeCard?.title).toBe('New Title')
    expect(s.activeCard?.refresh).toBe(60)
  })

  it('undo / redo 切换状态', () => {
    const s = useScreenEditorStore()
    s.addCard('pie-chart', { x: 0, y: 0 })
    expect(s.canUndo).toBe(true)
    s.undo()
    expect(s.config.cards.length).toBe(0)
    expect(s.canRedo).toBe(true)
    s.redo()
    expect(s.config.cards.length).toBe(1)
  })

  it('markClean 清除 isDirty', () => {
    const s = useScreenEditorStore()
    s.addCard('bar-chart', { x: 0, y: 0 })
    expect(s.isDirty).toBe(true)
    s.markClean()
    expect(s.isDirty).toBe(false)
  })

  it('reset 回到初始状态', () => {
    const s = useScreenEditorStore()
    s.addCard('bar-chart', { x: 0, y: 0 })
    s.reset()
    expect(s.config.cards.length).toBe(0)
    expect(s.isDirty).toBe(false)
    expect(s.canUndo).toBe(false)
  })

  it('undo stack 超过 MAX_HISTORY 时丢弃最旧的', () => {
    const s = useScreenEditorStore()
    // Push 51 changes
    for (let i = 0; i < 51; i++) {
      s.addCard('digital-number', { x: i, y: 0 })
    }
    // undo 应该最多回到第 2 个 card
    for (let i = 0; i < 50; i++) {
      if (s.canUndo) s.undo()
    }
    // 还剩 1 张 card（最早加的那张）
    expect(s.config.cards.length).toBe(1)
  })

  it('redo 在 applyChange 后被清空', () => {
    const s = useScreenEditorStore()
    s.addCard('pie-chart', { x: 0, y: 0 })
    s.undo()
    expect(s.canRedo).toBe(true)
    // applyChange 清空 redo
    s.updateCard(s.activeCardId!, { title: 'X' })
    expect(s.canRedo).toBe(false)
  })
})
