import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import DigitalNumber from '../index.vue'

describe('DigitalNumber', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('直接传 number 类型渲染', async () => {
    const wrapper = mount(DigitalNumber, { props: { data: 1234, options: {} } })
    vi.advanceTimersByTime(2000)
    expect(wrapper.text()).toContain('1234')
  })

  it('数组 + valueField 提取', async () => {
    const wrapper = mount(DigitalNumber, {
      props: { data: [{ count: 42 }], options: { valueField: 'count' } }
    })
    vi.advanceTimersByTime(2000)
    expect(wrapper.text()).toContain('42')
  })

  it('title/unit 透传', () => {
    const wrapper = mount(DigitalNumber, {
      props: { data: 100, options: { title: '在线用户', unit: '人' } }
    })
    expect(wrapper.text()).toContain('在线用户')
  })
})