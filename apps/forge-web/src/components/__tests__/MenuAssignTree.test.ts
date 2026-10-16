import { describe, it, expect } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import MenuAssignTree from '../MenuAssignTree.vue'
import type { MenuTree } from '@/types/system'

const mockTree: MenuTree[] = [
  {
    id: 1,
    menuName: '系统管理',
    parentId: 0,
    menuType: 0,
    children: [
      {
        id: 2,
        menuName: '用户管理',
        parentId: 1,
        menuType: 1,
        children: [
          { id: 21, menuName: '新增', parentId: 2, menuType: 2 },
          { id: 22, menuName: '编辑', parentId: 2, menuType: 2 },
          { id: 23, menuName: '删除', parentId: 2, menuType: 2 }
        ]
      },
      { id: 3, menuName: '角色管理', parentId: 1, menuType: 1 }
    ]
  }
] as MenuTree[]

const mountTree = async (modelValue: number[], onupdate: (ids: number[]) => void = () => {}) => {
  const wrapper = mount(MenuAssignTree, {
    props: { modelValue, data: mockTree, 'onUpdate:modelValue': onupdate },
    global: { plugins: [ElementPlus], stubs: { 'el-collapse-transition': false, transition: false } }
  })
  await flushPromises()
  // 展开根目录使子节点渲染
  await (wrapper.findAll('.el-tree-node__expand-icon')[0] as any).trigger('click')
  await flushPromises()
  return wrapper
}

const textOf = (b: any) => (b.element?.textContent || '').trim()

const buttonState = (wrapper: Awaited<ReturnType<typeof mountTree>>) => {
  const map: Record<string, boolean> = {}
  wrapper.findAll('.menu-node-button').forEach((b: any) => {
    map[textOf(b)] = b.find('input[type=checkbox]').element.checked
  })
  return map
}

const findButton = (wrapper: Awaited<ReturnType<typeof mountTree>>, label: string) =>
  wrapper.findAll('.menu-node-button').find((b: any) => textOf(b) === label)!

describe('MenuAssignTree', () => {
  it('回显：按钮勾选与菜单回推勾选正确', async () => {
    const wrapper = await mountTree([1, 2, 21, 22])
    expect(buttonState(wrapper)).toEqual({ 新增: true, 编辑: true, 删除: false })
  })

  it('勾选单个按钮不影响同级按钮勾选', async () => {
    const emitted: number[][] = []
    const wrapper = await mountTree([1, 2, 21, 22], (ids) => emitted.push([...ids]))

    await findButton(wrapper, '删除').find('input[type=checkbox]').setValue(true)
    await flushPromises()

    expect(buttonState(wrapper)).toEqual({ 新增: true, 编辑: true, 删除: true })
    expect(emitted[emitted.length - 1]).toContain(23)
    expect(emitted[emitted.length - 1]).toContain(21)
    expect(emitted[emitted.length - 1]).toContain(22)
  })

  it('取消单个按钮不影响同级按钮勾选', async () => {
    const wrapper = await mountTree([1, 2, 21, 22])
    await findButton(wrapper, '新增').find('input[type=checkbox]').setValue(false)
    await flushPromises()

    expect(buttonState(wrapper)).toEqual({ 新增: false, 编辑: true, 删除: false })
  })

it('真实形态：菜单下按钮+子菜单混合，回显与按钮交互', async () => {
    // 116 待办任务 = 6 按钮(117-122) + 子菜单 126；库里 dfe5c4b 语义只有按钮+126，无 116
    const tree = [
      { id: 110, menuName: '工作流', parentId: 0, menuType: 0, children: [
        { id: 116, menuName: '待办任务', parentId: 110, menuType: 1, children: [
          { id: 117, menuName: '任务查询', parentId: 116, menuType: 2 },
          { id: 118, menuName: '任务认领', parentId: 116, menuType: 2 },
          { id: 126, menuName: '任务详情', parentId: 116, menuType: 1 }
        ] }
      ] }
    ] as any as MenuTree[]

    const emitted: number[][] = []
    const wrapper = mount(MenuAssignTree, {
      props: {
        modelValue: [117, 118, 126],
        data: tree,
        'onUpdate:modelValue': (ids: number[]) => { emitted.push([...ids]); wrapper.setProps({ modelValue: ids }) }
      },
      global: { plugins: [ElementPlus], stubs: { 'el-collapse-transition': false, transition: false } }
    })
    await flushPromises()
    await (wrapper.findAll('.el-tree-node__expand-icon')[0] as any).trigger('click')
    await flushPromises()

    // 初始：按钮 117/118 勾选
    expect(buttonState(wrapper)).toEqual({ 任务查询: true, 任务认领: true })

    // 取消单个按钮：不影响其他按钮勾选；菜单因仍有按钮勾选保持勾选
    const q = wrapper.findAll('.menu-node-button').find((b: any) => textOf(b) === '任务查询')!
    await q.find('input[type=checkbox]').setValue(false)
    await flushPromises()
    expect(buttonState(wrapper)).toEqual({ 任务查询: false, 任务认领: true })
    expect(emitted[emitted.length - 1]).toContain(118)
    expect(emitted[emitted.length - 1]).not.toContain(117)
    expect(emitted[emitted.length - 1]).toContain(116)
  })

  it('v-model 回写不引发按钮勾选丢失', async () => {
    // 模拟父组件 v-model：把 emit 的数组写回 props
    const wrapper = mount(MenuAssignTree, {
      props: {
        modelValue: [1, 2, 21, 22],
        data: mockTree,
        'onUpdate:modelValue': (ids: number[]) => {
          wrapper.setProps({ modelValue: ids })
        }
      },
      global: { plugins: [ElementPlus], stubs: { 'el-collapse-transition': false, transition: false } }
    })
    await flushPromises()
    await (wrapper.findAll('.el-tree-node__expand-icon')[0] as any).trigger('click')
    await flushPromises()

    await findButton(wrapper, '删除').find('input[type=checkbox]').setValue(true)
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(buttonState(wrapper)).toEqual({ 新增: true, 编辑: true, 删除: true })
  })
})
