<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { executeDataSource, type DataSourceExecuteResponse } from '@/api/screen'
import { useScreenEditorStore } from '@/stores/screenEditor'
import type { ScreenCard, CardDataShape } from '@/types/screen'

const props = defineProps<{ card: ScreenCard; dataShape: CardDataShape }>()

const store = useScreenEditorStore()
const sampleData = ref<any>(null)
const suggestions = ref<Record<string, string>>({})

const inferType = (val: unknown): 'string' | 'number' | 'date' | 'boolean' => {
  if (typeof val === 'number') return 'number'
  if (typeof val === 'boolean') return 'boolean'
  if (val instanceof Date || (typeof val === 'string' && /^\d{4}-\d{2}-\d{2}/.test(val))) return 'date'
  return 'string'
}

const suggest = async () => {
  if (!props.card.dataSourceId) return
  try {
    const res: DataSourceExecuteResponse = await executeDataSource(props.card.dataSourceId, { params: {} })
    const data = res.data
    if (!Array.isArray(data) || data.length === 0 || typeof data[0] !== 'object') return
    sampleData.value = data[0]
    const fieldNames = Object.keys(data[0])
    const shapes = props.dataShape.fields
    const newSuggestions: Record<string, string> = {}
    for (const f of shapes) {
      const candidates = fieldNames.filter(fn => {
        const v = data[0][fn]
        return f.type === inferType(v)
      })
      newSuggestions[f.name] = candidates[0] ?? ''
    }
    suggestions.value = newSuggestions
  } catch { /* ignore */ }
}

const apply = () => {
  const opts = props.card.options || {}
  store.updateCard(props.card.id, { options: { ...opts, ...suggestions.value } })
}

watch(() => props.card.dataSourceId, suggest, { immediate: true })
onMounted(suggest)
</script>

<template>
  <div class="field-mapping">
    <el-button size="small" @click="suggest">重新分析</el-button>
    <el-table :data="dataShape.fields" size="small" border>
      <el-table-column prop="name" label="逻辑字段" width="100" />
      <el-table-column prop="type" label="类型" width="80" />
      <el-table-column label="映射">
        <template #default="{ row }">
          <el-select v-model="suggestions[row.name]" size="small" clearable>
            <el-option v-for="fn in Object.keys(sampleData || {})" :key="fn" :label="fn" :value="fn" />
          </el-select>
        </template>
      </el-table-column>
    </el-table>
    <el-button size="small" type="primary" @click="apply" :disabled="Object.keys(suggestions).length === 0">应用</el-button>
  </div>
</template>
