import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { DictData } from '@/types/system'

const { getDictDataByType } = vi.hoisted(() => ({ getDictDataByType: vi.fn() }))
vi.mock('@/api/system', () => ({ getDictDataByType }))

const dict = (dictValue: string, dictLabel: string, listClass = ''): DictData => ({
  id: 0,
  dictType: '',
  dictLabel,
  dictValue,
  dictSort: 0,
  cssClass: '',
  listClass,
  status: 0,
  remark: '',
  createTime: ''
})

// 可手动控制完成时机的请求
const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(r => { resolve = r })
  return { promise, resolve }
}

// 等待请求的 then/catch/finally 全部结算
const settle = () => new Promise(resolve => setTimeout(resolve, 0))

// 每个用例重新加载模块，隔离模块级缓存
const loadModule = async () => {
  vi.resetModules()
  const [{ useDict, reloadDictCache }, { computed }] = await Promise.all([
    import('../useDict'),
    import('vue')
  ])
  return { useDict, reloadDictCache, computed }
}

describe('useDict', () => {
  beforeEach(() => {
    getDictDataByType.mockReset()
  })

  it('同一类型并发调用只请求一次，所有实例共享结果并响应式更新', async () => {
    const req = deferred<DictData[]>()
    getDictDataByType.mockReturnValue(req.promise)
    const { useDict, computed } = await loadModule()

    // 模拟表格 20 行 <dict-value> 在同一渲染周期内 setup
    const instances = Array.from({ length: 20 }, () => useDict('wf_urgent'))
    const label = computed(() => instances[0].getDictLabel(1))
    expect(getDictDataByType).toHaveBeenCalledTimes(1)
    expect(instances[0].loading.value).toBe(true)
    // 加载完成前回退显示原值
    expect(label.value).toBe('1')

    req.resolve([dict('0', '普通'), dict('1', '紧急', 'danger')])
    await settle()

    expect(getDictDataByType).toHaveBeenCalledTimes(1)
    expect(label.value).toBe('紧急')
    instances.forEach(inst => {
      expect(inst.loading.value).toBe(false)
      expect(inst.dictData.value).toHaveLength(2)
      expect(inst.getDictLabel('1')).toBe('紧急')
      expect(inst.getTagType(1)).toBe('danger')
    })
  })

  it('已缓存的类型不再请求，结果同步可用', async () => {
    getDictDataByType.mockResolvedValue([dict('0', '普通')])
    const { useDict } = await loadModule()
    useDict('wf_urgent')
    await settle()

    const later = useDict('wf_urgent')
    expect(getDictDataByType).toHaveBeenCalledTimes(1)
    expect(later.loading.value).toBe(false)
    expect(later.getDictLabel(0)).toBe('普通')
  })

  it('不同类型各请求一次，互不影响', async () => {
    getDictDataByType.mockImplementation(async (type: string) =>
      type === 'wf_urgent' ? [dict('1', '紧急')] : [dict('1', '同意')])
    const { useDict } = await loadModule()
    const urgent = [useDict('wf_urgent'), useDict('wf_urgent')]
    const action = [useDict('wf_action_type'), useDict('wf_action_type')]
    await settle()

    expect(getDictDataByType).toHaveBeenCalledTimes(2)
    expect(urgent[1].getDictLabel(1)).toBe('紧急')
    expect(action[1].getDictLabel(1)).toBe('同意')
  })

  it('请求失败不写缓存：并发失败只请求一次，后续调用重新请求', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    getDictDataByType
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce([dict('1', '紧急')])
    const { useDict } = await loadModule()

    const failed = Array.from({ length: 5 }, () => useDict('wf_urgent'))
    await settle()
    expect(getDictDataByType).toHaveBeenCalledTimes(1)
    expect(failed[0].loading.value).toBe(false)
    expect(failed[0].getDictLabel(1)).toBe('1')

    useDict('wf_urgent')
    await settle()
    expect(getDictDataByType).toHaveBeenCalledTimes(2)
    // 先前的实例随共享缓存一并恢复
    expect(failed[0].getDictLabel(1)).toBe('紧急')
    errorSpy.mockRestore()
  })

  it('reloadDictCache 指定类型：重新请求并原地更新，失败保留旧数据，未缓存的类型跳过', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    getDictDataByType
      .mockResolvedValueOnce([dict('1', '紧急')])
      .mockResolvedValueOnce([dict('1', '特急')])
      .mockRejectedValueOnce(new Error('network'))
    const { useDict, reloadDictCache, computed } = await loadModule()
    const inst = useDict('wf_urgent')
    const label = computed(() => inst.getDictLabel(1))
    await settle()
    expect(label.value).toBe('紧急')

    await reloadDictCache('wf_urgent')
    expect(getDictDataByType).toHaveBeenCalledTimes(2)
    expect(label.value).toBe('特急')

    await reloadDictCache('wf_urgent')
    expect(getDictDataByType).toHaveBeenCalledTimes(3)
    expect(label.value).toBe('特急')

    await reloadDictCache('never_used')
    expect(getDictDataByType).toHaveBeenCalledTimes(3)
    errorSpy.mockRestore()
  })

  it('reloadDictCache 不传类型：重载全部已缓存类型', async () => {
    getDictDataByType.mockResolvedValue([])
    const { useDict, reloadDictCache } = await loadModule()
    useDict('wf_urgent')
    useDict('wf_action_type')
    await settle()
    getDictDataByType.mockClear()

    await reloadDictCache()
    expect(getDictDataByType).toHaveBeenCalledTimes(2)
    expect(getDictDataByType).toHaveBeenCalledWith('wf_urgent')
    expect(getDictDataByType).toHaveBeenCalledWith('wf_action_type')
  })
})
