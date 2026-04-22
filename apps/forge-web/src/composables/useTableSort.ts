import { ref } from 'vue'

/**
 * 排序参数接口
 */
export interface SortParams {
  field: string
  order: 'asc' | 'desc' | null
}

/**
 * 排序 Hook
 * 处理表格排序逻辑
 */
export interface UseTableSortOptions {
  /** 默认排序字段 */
  defaultSort?: SortParams
  /** 排序变化回调 */
  onSortChange?: (params: SortParams) => void
}

export interface UseTableSortReturn {
  sortParams: ReturnType<typeof ref<SortParams>>
  handleSortChange: (params: { property: string; order: 'asc' | 'desc' | null }) => void
  buildSortingField: () => string | undefined
  clearSort: () => void
}

export function useTableSort(options: UseTableSortOptions = {}): UseTableSortReturn {
  const { defaultSort, onSortChange } = options

  const sortParams = ref<SortParams>(defaultSort || { field: '', order: null })

  /**
   * 处理排序变化
   */
  const handleSortChange = (params: { property: string; order: 'asc' | 'desc' | null }) => {
    sortParams.value = {
      field: params.property,
      order: params.order
    }
    if (onSortChange) {
      onSortChange(sortParams.value)
    }
  }

  /**
   * 构建排序字段字符串（用于 API 请求）
   * 格式：fieldName,asc 或 fieldName,desc
   */
  const buildSortingField = (): string | undefined => {
    if (!sortParams.value.field || !sortParams.value.order) {
      return undefined
    }
    return `${sortParams.value.field},${sortParams.value.order}`
  }

  /**
   * 清除排序
   */
  const clearSort = () => {
    sortParams.value = { field: '', order: null }
  }

  return {
    sortParams,
    handleSortChange,
    buildSortingField,
    clearSort
  }
}