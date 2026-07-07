import { describe, it, expect, beforeEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { createRegistry, cardRegistry, registerBuiltinCards } from '../registry'
import type { ScreenCardComponent } from '../types'

const StubComp = defineComponent({ name: 'Stub', setup: () => () => h('div', 'stub') })

const makeEntry = (type: string): ScreenCardComponent => ({
  type,
  component: StubComp,
  meta: {
    title: type, icon: 'Histogram', defaultProps: {},
    configSchema: { type: 'object', properties: {} },
    dataShape: { fields: [{ name: 'v', type: 'number' }], sample: { v: 0 } },
    minWidth: 4, minHeight: 3
  }
})

describe('cardRegistry', () => {
  beforeEach(() => {
    // 每个测试前用独立 registry
    ;(cardRegistry as any).__test_reset?.()
  })

  it('register / get 双向一致', () => {
    const reg = createRegistry<ScreenCardComponent>()
    reg.register(makeEntry('line-chart'))
    expect(reg.get('line-chart')?.type).toBe('line-chart')
  })

  it('get 不存在的 type 返回 undefined', () => {
    const reg = createRegistry<ScreenCardComponent>()
    expect(reg.get('nope')).toBeUndefined()
  })

  it('list 返回所有已注册条目（按注册顺序）', () => {
    const reg = createRegistry<ScreenCardComponent>()
    reg.register(makeEntry('a'))
    reg.register(makeEntry('b'))
    expect(reg.list().map(e => e.type)).toEqual(['a', 'b'])
  })

  it('重复注册同 type 抛错', () => {
    const reg = createRegistry<ScreenCardComponent>()
    reg.register(makeEntry('x'))
    expect(() => reg.register(makeEntry('x'))).toThrow(/already registered/i)
  })

  it('registerBuiltinCards 不抛错（具体注册数量由 Task 5/6 决定）', () => {
    expect(() => registerBuiltinCards()).not.toThrow()
  })

  it('registerBuiltinCards 注册 6 个核心卡片', () => {
    registerBuiltinCards()
    expect(cardRegistry.list().length).toBe(6)
    expect(cardRegistry.get('digital-number')).toBeDefined()
    expect(cardRegistry.get('line-chart')).toBeDefined()
    expect(cardRegistry.get('bar-chart')).toBeDefined()
    expect(cardRegistry.get('pie-chart')).toBeDefined()
    expect(cardRegistry.get('scroll-table')).toBeDefined()
    expect(cardRegistry.get('text-board')).toBeDefined()
  })
})
