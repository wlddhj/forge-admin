/**
 * 数据字典 Composable
 *
 * 缓存为模块级、全应用共享：
 * - dictCache：已加载的字典（响应式），写入或替换后所有使用方自动刷新
 * - pendingRequests：进行中的请求，同一类型的并发调用共享同一个请求
 *
 * 表格中 N 行 <dict-value> 会在同一渲染周期内依次 setup，若只缓存请求结果，
 * 首个请求返回前每个实例都判定"未缓存"而各自请求，造成 N 次重复请求。
 */
import { computed, shallowReactive } from 'vue'
import { getDictDataByType } from '@/api/system'
import type { DictData } from '@/types/system'

interface DictCacheEntry {
  list: DictData[]
  /** dictValue → 字典项 */
  index: Map<string, DictData>
}

const dictCache = shallowReactive(new Map<string, DictCacheEntry>())
const pendingRequests = shallowReactive(new Map<string, Promise<void>>())

// listClass 映射到 el-tag 的 type
const TAG_TYPE_MAP: Record<string, string> = {
  primary: 'primary',
  success: 'success',
  warning: 'warning',
  danger: 'danger',
  info: 'info'
}

const buildEntry = (list: DictData[]): DictCacheEntry => {
  const index = new Map<string, DictData>()
  list.forEach(item => {
    const key = String(item.dictValue)
    // 值重复时保留首个，与 Array.find 语义一致
    if (!index.has(key)) index.set(key, item)
  })
  return { list, index }
}

/**
 * 加载字典：已缓存直接返回，加载中复用进行中的请求，force 时忽略缓存重新请求。
 * 失败不写缓存，后续调用会重新请求。
 */
const loadDict = (dictType: string, force = false): Promise<void> => {
  if (!force && dictCache.has(dictType)) return Promise.resolve()

  const pending = pendingRequests.get(dictType)
  if (pending) return pending

  const request = getDictDataByType(dictType)
    .then(list => {
      dictCache.set(dictType, buildEntry(list || []))
    })
    .catch(error => {
      console.error(`加载字典数据失败: ${dictType}`, error)
    })
    .finally(() => {
      pendingRequests.delete(dictType)
    })
  pendingRequests.set(dictType, request)
  return request
}

/**
 * 重新加载字典缓存（字典数据变更后调用）
 * @param dictType 指定类型；不传则重载当前已缓存的全部类型
 *
 * 未缓存的类型跳过（下次使用时自然取到最新数据）；新数据返回前旧数据保持可用，返回后原地替换。
 */
export function reloadDictCache(dictType?: string): Promise<void> {
  const types = dictType ? [dictType] : Array.from(dictCache.keys())
  return Promise.all(
    types.filter(type => dictCache.has(type)).map(type => loadDict(type, true))
  ).then(() => undefined)
}

/**
 * 使用字典数据
 */
export function useDict(dictType: string) {
  // 自动加载数据（已缓存或加载中时不会重复请求）
  loadDict(dictType)

  const dictData = computed<DictData[]>(() => dictCache.get(dictType)?.list ?? [])
  const loading = computed(() => pendingRequests.has(dictType))

  const findItem = (value: string | number) => dictCache.get(dictType)?.index.get(String(value))

  const getDictLabel = (value: string | number): string => {
    return findItem(value)?.dictLabel || String(value)
  }

  const getTagType = (value: string | number): string => {
    return TAG_TYPE_MAP[findItem(value)?.listClass || ''] || 'info'
  }

  const getCssClass = (value: string | number): string => {
    return findItem(value)?.cssClass || ''
  }

  return {
    dictData,
    loading,
    loadDictData: () => loadDict(dictType),
    getDictLabel,
    getTagType,
    getCssClass
  }
}
