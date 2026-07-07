import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ScrollNumber from '../ScrollNumber.vue'

describe('ScrollNumber', () => {
  beforeEach(() => { vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('从 0 滚动到 100', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 100, duration: 1000 } })
    expect(wrapper.text()).toBe('0')
    vi.advanceTimersByTime(500)
    expect(Number(wrapper.text())).toBeGreaterThan(0)
    expect(Number(wrapper.text())).toBeLessThan(100)
    vi.advanceTimersByTime(600)
    expect(wrapper.text()).toBe('100')
  })

  it('value 变化时重新触发动画', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 50, duration: 500 } })
    vi.advanceTimersByTime(600)
    expect(wrapper.text()).toBe('50')
    await wrapper.setProps({ value: 200 })
    expect(wrapper.text()).toBe('50')
    vi.advanceTimersByTime(500)
    expect(wrapper.text()).toBe('200')
  })

  it('传 decimals 显示小数', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 3.14, duration: 100, decimals: 2 } })
    vi.advanceTimersByTime(200)
    expect(wrapper.text()).toBe('3.14')
  })
})