import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DigitalNumber from '../index.vue'

const wait = (ms: number) => new Promise(r => setTimeout(r, ms))

describe('DigitalNumber', () => {
  it('直接传 number 类型渲染', async () => {
    const wrapper = mount(DigitalNumber, { props: { data: 1234, options: { duration: 100 } } })
    await wait(120)
    expect(wrapper.text()).toContain('1234')
  })

  it('数组 + valueField 提取', async () => {
    const wrapper = mount(DigitalNumber, {
      props: { data: [{ count: 42 }], options: { valueField: 'count', duration: 100 } }
    })
    await wait(120)
    expect(wrapper.text()).toContain('42')
  })

  it('title/unit 透传', () => {
    const wrapper = mount(DigitalNumber, {
      props: { data: 100, options: { title: '在线用户', unit: '人' } }
    })
    expect(wrapper.text()).toContain('在线用户')
  })
})
