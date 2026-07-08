import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))

import request from '@/utils/request'
import {
  getScreenList, getScreenDetail, getScreenByCode,
  createScreen, updateScreen, deleteScreen, publishScreen, copyScreen,
  getDataSourceList, getDataSourceDetail,
  createDataSource, updateDataSource, deleteDataSource, executeDataSource
} from '../index'

describe('screen API', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getScreenList → GET /screen/list', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { list: [], total: 0 } })
    await getScreenList({ pageNum: 1, pageSize: 10, name: 'x' })
    expect(request.get).toHaveBeenCalledWith('/screen/list', { params: { pageNum: 1, pageSize: 10, name: 'x' } })
  })

  it('getScreenDetail → GET /screen/{id}', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { id: 1 } })
    await getScreenDetail(1)
    expect(request.get).toHaveBeenCalledWith('/screen/1')
  })

  it('getScreenByCode → GET /screen/code/{code}', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { id: 1 } })
    await getScreenByCode('operations')
    expect(request.get).toHaveBeenCalledWith('/screen/code/operations')
  })

  it('createScreen → POST /screen', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: 99 })
    await createScreen({ code: 'a', name: 'A' })
    expect(request.post).toHaveBeenCalledWith('/screen', { code: 'a', name: 'A' })
  })

  it('updateScreen → PUT /screen', async () => {
    vi.mocked(request.put).mockResolvedValue({})
    await updateScreen({ id: 1, name: 'A2' })
    expect(request.put).toHaveBeenCalledWith('/screen', { id: 1, name: 'A2' })
  })

  it('deleteScreen → DELETE /screen with ids array', async () => {
    vi.mocked(request.delete).mockResolvedValue({})
    await deleteScreen([1, 2])
    expect(request.delete).toHaveBeenCalledWith('/screen', { data: [1, 2] })
  })

  it('publishScreen → PUT /screen/publish/{code}', async () => {
    vi.mocked(request.put).mockResolvedValue({})
    await publishScreen('operations')
    expect(request.put).toHaveBeenCalledWith('/screen/publish/operations')
  })

  it('copyScreen → POST /screen/copy/{code}', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: 88 })
    await copyScreen('operations', { newCode: 'ops2', newName: '副本' })
    expect(request.post).toHaveBeenCalledWith('/screen/copy/operations', { newCode: 'ops2', newName: '副本' })
  })
})

describe('dataSource API', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('getDataSourceList → GET /screen/data-source/list', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { list: [], total: 0 } })
    await getDataSourceList({ pageNum: 1, pageSize: 10 })
    expect(request.get).toHaveBeenCalledWith('/screen/data-source/list', { params: { pageNum: 1, pageSize: 10 } })
  })

  it('getDataSourceDetail → GET /screen/data-source/{id}', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { id: 7 } })
    await getDataSourceDetail(7)
    expect(request.get).toHaveBeenCalledWith('/screen/data-source/7')
  })

  it('createDataSource → POST /screen/data-source', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: 1 })
    await createDataSource({ code: 'a', name: 'A', type: 'HTTP', config: '{}' })
    expect(request.post).toHaveBeenCalledWith('/screen/data-source', { code: 'a', name: 'A', type: 'HTTP', config: '{}' })
  })

  it('updateDataSource → PUT /screen/data-source', async () => {
    vi.mocked(request.put).mockResolvedValue({})
    await updateDataSource({ id: 1, name: 'A2' })
    expect(request.put).toHaveBeenCalledWith('/screen/data-source', { id: 1, name: 'A2' })
  })

  it('deleteDataSource → DELETE /screen/data-source with ids', async () => {
    vi.mocked(request.delete).mockResolvedValue({})
    await deleteDataSource([1, 2])
    expect(request.delete).toHaveBeenCalledWith('/screen/data-source', { data: [1, 2] })
  })

  it('executeDataSource → POST /screen/data-source/execute/{id}', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: { data: [], fromCache: false, executedAt: '' } })
    await executeDataSource(1, { params: { id: 1 } })
    expect(request.post).toHaveBeenCalledWith('/screen/data-source/execute/1', { params: { id: 1 } })
  })
})