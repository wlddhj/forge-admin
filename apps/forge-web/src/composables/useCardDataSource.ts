import { ref, watch, getCurrentScope, onScopeDispose, type Ref } from 'vue'
import { useIntervalFn } from '@vueuse/core'
import { executeDataSource, type DataSourceExecuteResponse } from '@/api/screen'
import type { ScreenCard } from '@/types/screen'

export interface CardDataSourceReturn {
  data: Ref<unknown>
  loading: Ref<boolean>
  error: Ref<Error | null>
  load: () => Promise<void>
  refresh: () => Promise<void>
  cancel: () => void
}

export function useCardDataSource(card: Ref<ScreenCard>): CardDataSourceReturn {
  const data = ref<unknown>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  let token = 0
  let interval: ReturnType<typeof useIntervalFn> | null = null

  const load = async () => {
    const my = ++token
    const dsId = card.value.dataSourceId
    if (!dsId) { data.value = null; return }
    loading.value = true
    error.value = null
    try {
      const res: DataSourceExecuteResponse = await executeDataSource(dsId, {
        params: (card.value.options?.params as Record<string, unknown>) ?? {}
      })
      if (my === token) data.value = res.data
    } catch (e) {
      if (my === token) error.value = e instanceof Error ? e : new Error(String(e))
    } finally {
      if (my === token) loading.value = false
    }
  }

  const start = () => {
    interval?.pause()
    if (card.value.refresh && card.value.refresh > 0) {
      interval = useIntervalFn(load, card.value.refresh * 1000, { immediate: false })
    }
  }
  const stop = () => interval?.pause()

  watch(() => card.value.refresh, start, { immediate: true })
  watch(() => card.value.dataSourceId, load)

  const teardown = () => { stop(); token = -1 }
  if (getCurrentScope()) onScopeDispose(teardown)

  return { data, loading, error, load, refresh: load, cancel: stop }
}