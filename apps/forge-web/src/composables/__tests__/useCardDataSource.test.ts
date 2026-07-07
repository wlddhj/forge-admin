import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api/screen', () => ({ executeDataSource: vi.fn() }))

import { executeDataSource } from '@/api/screen'
import { useCardDataSource } from '../useCardDataSource'
import type { ScreenCard } from '@/types/screen'

const makeCard = (overrides: Partial<ScreenCard> = {}): ScreenCard => ({
  id: 'c1', type: 'line-chart', x: 0, y: 0, w: 12, h: 6,
  dataSourceId: 1, refresh: 0, options: {}, ...overrides
})

describe('useCardDataSource', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('dataSourceId 为 null 时 data 始终 null，不调 API', async () => {
    const card = ref<ScreenCard>(makeCard({ dataSourceId: null }))
    const { data, load } = useCardDataSource(card)
    await load()
    expect(data.value).toBeNull()
    expect(executeDataSource).not.toHaveBeenCalled()
  })

  it('load 拉取数据并填入 data', async () => {
    vi.mocked(executeDataSource).mockResolvedValue({ data: [{ v: 1 }], fromCache: false, executedAt: '' })
    const card = ref<ScreenCard>(makeCard({ dataSourceId: 1 }))
    const { data, load } = useCardDataSource(card)
    await load()
    expect(data.value).toEqual([{ v: 1 }])
    expect(executeDataSource).toHaveBeenCalledWith(1, { params: {} })
  })

  it('options.params 透传到请求', async () => {
    vi.mocked(executeDataSource).mockResolvedValue({ data: [], fromCache: false, executedAt: '' })
    const card = ref<ScreenCard>(makeCard({ dataSourceId: 1, options: { params: { id: 5 } } }))
    const { load } = useCardDataSource(card)
    await load()
    expect(executeDataSource).toHaveBeenCalledWith(1, { params: { id: 5 } })
  })

  it('API 抛错时 error 被填充，data 保持上次值', async () => {
    vi.mocked(executeDataSource).mockResolvedValueOnce({ data: [{ v: 1 }], fromCache: false, executedAt: '' })
    const card = ref<ScreenCard>(makeCard({ dataSourceId: 1 }))
    const { data, load, error } = useCardDataSource(card)
    await load()
    vi.mocked(executeDataSource).mockRejectedValueOnce(new Error('boom'))
    await load()
    expect(error.value?.message).toBe('boom')
    expect(data.value).toEqual([{ v: 1 }])
  })
})