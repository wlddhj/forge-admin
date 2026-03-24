<template>
  <span class="dict-value-container">
    <el-tag
      v-for="(item, index) in displayItems"
      :key="index"
      :type="item.tagType"
      size="small"
      style="margin-right: 4px;"
    >
      {{ item.label }}
    </el-tag>
    <span v-if="displayItems.length === 0" style="color: #909399;">-</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useDict } from '@/composables/useDict'

interface Props {
  dictType: string
  value?: string | number | number[] | string[] | null | undefined
  separator?: string
}

const props = withDefaults(defineProps<Props>(), {
  separator: ','
})

const { getDictLabel, getTagType } = useDict(props.dictType)

// 解析值数组
const values = computed(() => {
  if (props.value === null || props.value === undefined) return []

  // 如果是数组，直接返回
  if (Array.isArray(props.value)) {
    return props.value
  }

  const strValue = String(props.value).trim()
  if (!strValue) return []

  // 尝试解析 JSON 数组格式 "[4,7,8]"
  if (strValue.startsWith('[') && strValue.endsWith(']')) {
    try {
      const parsed = JSON.parse(strValue)
      if (Array.isArray(parsed)) return parsed
    } catch {
      // 解析失败，使用分隔符处理
    }
  }

  // 使用分隔符分割 "4,7,8"
  const sep = props.separator
  return strValue.split(sep).map(v => v.trim()).filter(v => v !== '')
})

// 获取显示项列表
const displayItems = computed(() => {
  return values.value.map(val => {
    const label = getDictLabel(String(val))
    return {
      label,
      tagType: getTagType(String(val))
    }
  })
})
</script>

<style scoped lang="scss">
.dict-value-container {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
}
</style>
