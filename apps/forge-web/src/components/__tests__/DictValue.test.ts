import { describe, it, expect, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import DictValue from '../DictValue.vue'

const { getDictDataByType } = vi.hoisted(() => ({ getDictDataByType: vi.fn() }))
vi.mock('@/api/system', () => ({ getDictDataByType }))

describe('DictValue', () => {
  it('表格多行同时渲染同一字典类型时只请求一次', async () => {
    getDictDataByType.mockResolvedValue([
      { dictValue: '0', dictLabel: '普通', listClass: 'info', cssClass: '' },
      { dictValue: '1', dictLabel: '紧急', listClass: 'danger', cssClass: '' }
    ])
    const rows = Array.from({ length: 20 }, (_, i) => ({ id: i, urgent: i % 2 }))
    const Table = defineComponent({
      render: () => h('div', rows.map(row =>
        h(DictValue, { key: row.id, dictType: 'wf_urgent', value: row.urgent })
      ))
    })

    const wrapper = mount(Table, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(getDictDataByType).toHaveBeenCalledTimes(1)
    expect(getDictDataByType).toHaveBeenCalledWith('wf_urgent')
    const tags = wrapper.findAll('.el-tag')
    expect(tags).toHaveLength(20)
    expect(tags[0].text()).toBe('普通')
    expect(tags[1].text()).toBe('紧急')
    expect(tags[1].classes()).toContain('el-tag--danger')
  })
})
