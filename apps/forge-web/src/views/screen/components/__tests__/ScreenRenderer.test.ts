import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ScreenRenderer from '../ScreenRenderer.vue'
import { registerBuiltinCards, cardRegistry } from '@/views/screen/cards/registry'
import type { ScreenConfig } from '@/types/screen'

describe('ScreenRenderer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    try { registerBuiltinCards() } catch { /* idempotent */ }
  })

  it('空 config 渲染不抛错', () => {
    const wrapper = mount(ScreenRenderer, {
      props: { config: { version: 1, theme: 'dark-tech', cards: [] } as ScreenConfig }
    })
    expect(wrapper.find('.screen-renderer').exists()).toBe(true)
  })

  it('渲染每张卡片（用 stub 替换 component）', () => {
    const stub = { template: '<div class="stubbed-card">STUB</div>' }
    const original = cardRegistry.get('digital-number')
    if (original) (original as any).component = stub
    const wrapper = mount(ScreenRenderer, {
      props: {
        config: {
          version: 1, theme: 'dark-tech',
          cards: [
            { id: '1', type: 'digital-number', x: 0, y: 0, w: 6, h: 4, options: {} },
            { id: '2', type: 'digital-number', x: 6, y: 0, w: 6, h: 4, options: {} }
          ]
        } as ScreenConfig
      }
    })
    expect(wrapper.findAll('.screen-card').length).toBe(2)
  })

  it('未知卡片 type 显示占位', () => {
    const wrapper = mount(ScreenRenderer, {
      props: {
        config: {
          version: 1, theme: 'dark-tech',
          cards: [{ id: '1', type: 'unknown-type-xxx', x: 0, y: 0, w: 6, h: 4, options: {} }]
        } as ScreenConfig
      }
    })
    expect(wrapper.text()).toContain('未注册的卡片类型')
  })
})
