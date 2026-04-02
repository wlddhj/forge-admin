/**
 * 数据字典 Composable
 */
import { ref } from 'vue'
import { getDictDataByType } from '@/api/system'
import type { DictData } from '@/types/system'

// 字典数据缓存
const dictCache = new Map<string, DictData[]>()

/**
 * 使用字典数据
 */
export function useDict(dictType: string) {
  const dictData = ref<DictData[]>([])
  const loading = ref(false)

  const loadDictData = async () => {
    if (dictCache.has(dictType)) {
      dictData.value = dictCache.get(dictType)!
      return
    }

    loading.value = true
    try {
      const data = await getDictDataByType(dictType)
      dictData.value = data
      dictCache.set(dictType, data)
    } catch (error) {
      console.error(`加载字典数据失败: ${dictType}`, error)
      dictData.value = []
    } finally {
      loading.value = false
    }
  }

  // 自动加载数据
  loadDictData()

  const getDictLabel = (value: string | number): string => {
    const item = dictData.value.find(d => d.dictValue === String(value))
    return item?.dictLabel || String(value)
  }

  const getTagType = (value: string | number): string => {
    const item = dictData.value.find(d => d.dictValue === String(value))
    const listClass = item?.listClass || ''
    // 根据 listClass 映射到 el-tag 的 type
    const typeMap: Record<string, string> = {
      'primary': 'primary',
      'success': 'success',
      'warning': 'warning',
      'danger': 'danger',
      'info': 'info'
    }
    return typeMap[listClass] || 'info'
  }

  const getCssClass = (value: string | number): string => {
    const item = dictData.value.find(d => d.dictValue === String(value))
    return item?.cssClass || ''
  }

  return {
    dictData,
    loading,
    loadDictData,
    getDictLabel,
    getTagType,
    getCssClass
  }
}
