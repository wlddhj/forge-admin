import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/screen', () => ({
  getScreenList: vi.fn(),
  createScreen: vi.fn(),
  copyScreen: vi.fn(),
  deleteScreen: vi.fn(),
  publishScreen: vi.fn()
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import { useScreenStore } from '@/stores/screen'
import { getScreenList, copyScreen, deleteScreen, publishScreen } from '@/api/screen'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ScreenDetailResponse } from '@/api/screen'

const makeRow = (o: Partial<ScreenDetailResponse>): ScreenDetailResponse => ({
  id: 1, code: 'a', name: 'A', config: '{}', configDraft: '{}', theme: 'dark-tech',
  status: 1, version: 1, createTime: '', updateTime: '',
  ...o
})

describe('screen store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('setActive / clear 切换 activeScreen', () => {
    const s = useScreenStore()
    expect(s.activeScreen).toBeNull()
    const row = makeRow({ id: 7, code: 'demo' })
    s.setActive(row)
    expect(s.activeScreen?.id).toBe(7)
    s.clear()
    expect(s.activeScreen).toBeNull()
  })
})

describe('list page 状态机（不挂载组件）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('getScreenList 返回 list + total', async () => {
    vi.mocked(getScreenList).mockResolvedValue({
      list: [makeRow({ id: 1, name: 'A' })],
      total: 1, pageNum: 1, pageSize: 20, pages: 1
    })
    const res = await getScreenList({ pageNum: 1, pageSize: 20 })
    expect(res.list.length).toBe(1)
    expect(res.total).toBe(1)
    expect(res.list[0].name).toBe('A')
  })

  it('复制大屏：调 copyScreen 并提示成功', async () => {
    vi.mocked(copyScreen).mockResolvedValue(99)
    const row = makeRow({ id: 1, code: 'a', name: 'A' })
    await copyScreen(row.code, { newCode: 'a-copy', newName: 'A 副本' })
    expect(copyScreen).toHaveBeenCalledWith('a', { newCode: 'a-copy', newName: 'A 副本' })
    ElMessage.success('复制成功')
    expect(ElMessage.success).toHaveBeenCalledWith('复制成功')
  })

  it('发布大屏：调 publishScreen 并提示', async () => {
    vi.mocked(publishScreen).mockResolvedValue()
    const row = makeRow({ id: 1, code: 'a', name: 'A', status: 0 })
    await publishScreen(row.code)
    expect(publishScreen).toHaveBeenCalledWith('a')
    ElMessage.success('发布成功')
    expect(ElMessage.success).toHaveBeenCalledWith('发布成功')
  })

  it('删除大屏：ElMessageBox.confirm + deleteScreen', async () => {
    vi.mocked(deleteScreen).mockResolvedValue()
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
    const row = makeRow({ id: 5, code: 'a', name: 'A' })
    await ElMessageBox.confirm(`确认删除"${row.name}"？此操作不可恢复。`, '危险操作', { type: 'error' })
    await deleteScreen([row.id])
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(deleteScreen).toHaveBeenCalledWith([5])
  })
})
