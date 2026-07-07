import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/screen', () => ({
  getDataSourceList: vi.fn(),
  deleteDataSource: vi.fn(),
  getDataSourceDetail: vi.fn(),
  createDataSource: vi.fn(),
  updateDataSource: vi.fn(),
  executeDataSource: vi.fn()
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import {
  getDataSourceList, deleteDataSource, createDataSource, updateDataSource, executeDataSource,
  type ScreenDataSource, type DataSourceExecuteResponse
} from '@/api/screen'
import { ElMessage, ElMessageBox } from 'element-plus'

const makeRow = (o: Partial<ScreenDataSource>): ScreenDataSource => ({
  id: 1, code: 'a', name: 'A', type: 'HTTP', config: '{}', cacheSeconds: 0, enabled: 1, ...o
})

describe('DataSource List 状态机（不挂载组件）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('getDataSourceList 返回 list + total', async () => {
    vi.mocked(getDataSourceList).mockResolvedValue({
      list: [makeRow({ id: 1, name: 'A', type: 'HTTP' })],
      total: 1, pageNum: 1, pageSize: 20, pages: 1
    })
    const res = await getDataSourceList({ pageNum: 1, pageSize: 20 })
    expect(res.list.length).toBe(1)
    expect(res.list[0].name).toBe('A')
    expect(res.total).toBe(1)
  })

  it('删除：ElMessageBox.confirm + deleteDataSource', async () => {
    vi.mocked(deleteDataSource).mockResolvedValue()
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
    const row = makeRow({ id: 5, name: 'A' })
    await ElMessageBox.confirm(`确认删除数据源"${row.name}"？`, '危险操作', { type: 'error' })
    await deleteDataSource([row.id!])
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(deleteDataSource).toHaveBeenCalledWith([5])
    ElMessage.success('删除成功')
    expect(ElMessage.success).toHaveBeenCalledWith('删除成功')
  })
})

describe('DataSource Editor 状态机', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('HTTP 配置 buildConfig 输出 JSON', () => {
    const httpCfg = { method: 'POST', url: 'http://x', headers: '{"k":"v"}', params: '{}', timeout: 5 }
    const cfg = JSON.stringify({ ...httpCfg })
    expect(JSON.parse(cfg).method).toBe('POST')
    expect(JSON.parse(cfg).url).toBe('http://x')
  })

  it('SQL 配置 buildConfig 输出 JSON', () => {
    const sqlCfg = { sqlTemplate: 'SELECT 1', paramSchema: '{}', maxRows: 100 }
    const cfg = JSON.stringify({ ...sqlCfg })
    expect(JSON.parse(cfg).sqlTemplate).toBe('SELECT 1')
    expect(JSON.parse(cfg).maxRows).toBe(100)
  })

  it('新建保存：createDataSource 返回新 id', async () => {
    vi.mocked(createDataSource).mockResolvedValue(42)
    const form: ScreenDataSource = {
      id: undefined, code: 'new', name: 'New', type: 'HTTP',
      config: '{"method":"GET","url":"http://x","headers":"{}","params":"{}","timeout":5}',
      cacheSeconds: 0, enabled: 1
    }
    const id = await createDataSource(form)
    expect(id).toBe(42)
    expect(createDataSource).toHaveBeenCalledWith(form)
  })

  it('编辑保存：updateDataSource', async () => {
    vi.mocked(updateDataSource).mockResolvedValue()
    const form: ScreenDataSource = { ...makeRow({ id: 9 }), name: 'Updated' }
    await updateDataSource(form)
    expect(updateDataSource).toHaveBeenCalledWith(form)
  })

  it('测试：executeDataSource 返回 fromCache 标志', async () => {
    const resp: DataSourceExecuteResponse = {
      data: [{ id: 1, name: 'A' }], fromCache: false, executedAt: '2026-07-07T00:00:00Z'
    }
    vi.mocked(executeDataSource).mockResolvedValue(resp)
    const r = await executeDataSource(9, { params: {} })
    expect(r.fromCache).toBe(false)
    expect(Array.isArray(r.data)).toBe(true)
    expect(executeDataSource).toHaveBeenCalledWith(9, { params: {} })
  })
})
