import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ScrollNumber from '../ScrollNumber.vue'

const wait = (ms: number) => new Promise(r => setTimeout(r, ms))

describe('ScrollNumber', () => {
  it('从 0 滚动到 100', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 100, duration: 100 } })
    expect(wrapper.text()).toBe('0')
    await wait(50)
    expect(Number(wrapper.text())).toBeGreaterThan(0)
    expect(Number(wrapper.text())).toBeLessThan(100)
    await wait(60)
    expect(wrapper.text()).toBe('100')
  })

  it('value 变化时重新触发动画', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 50, duration: 100 } })
    await wait(110)
    expect(wrapper.text()).toBe('50')
    await wrapper.setProps({ value: 200 })
    expect(wrapper.text()).toBe('50')
    await wait(110)
    expect(wrapper.text()).toBe('200')
  })

  it('传 decimals 显示小数', async () => {
    const wrapper = mount(ScrollNumber, { props: { value: 3.14, duration: 60, decimals: 2 } })
    await wait(70)
    expect(wrapper.text()).toBe('3.14')
  })
})
