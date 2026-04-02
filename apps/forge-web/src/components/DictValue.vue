<template>
  <span class="dict-value-container">
    <el-tag
      v-for="(item, index) in displayItems"
      :key="index"
      :type="item.tagType"
      :class="item.cssClass || undefined"
      size="small"
      style="margin-right: 4px;"
    >
      {{ item.label }}
    </el-tag>
    <span v-if="displayItems.length === 0" style="color: var(--el-text-color-secondary);">-</span>
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

const { getDictLabel, getTagType, getCssClass } = useDict(props.dictType)

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
    const strVal = String(val)
    return {
      label: getDictLabel(strVal),
      tagType: getTagType(strVal),
      cssClass: getCssClass(strVal)
    }
  })
})
</script>

<style scoped lang="scss">
.dict-value-container {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;

  // ========== 基础色 ==========
  :deep(.el-tag.primary) {
    --el-tag-bg-color: var(--el-color-primary-light-9);
    --el-tag-border-color: var(--el-color-primary-light-7);
    --el-tag-text-color: var(--el-color-primary);
  }

  :deep(.el-tag.success) {
    --el-tag-bg-color: var(--el-color-success-light-9);
    --el-tag-border-color: var(--el-color-success-light-7);
    --el-tag-text-color: var(--el-color-success);
  }

  :deep(.el-tag.warning) {
    --el-tag-bg-color: var(--el-color-warning-light-9);
    --el-tag-border-color: var(--el-color-warning-light-7);
    --el-tag-text-color: var(--el-color-warning);
  }

  :deep(.el-tag.danger) {
    --el-tag-bg-color: var(--el-color-danger-light-9);
    --el-tag-border-color: var(--el-color-danger-light-7);
    --el-tag-text-color: var(--el-color-danger);
  }

  :deep(.el-tag.info) {
    --el-tag-bg-color: var(--el-color-info-light-9);
    --el-tag-border-color: var(--el-color-info-light-7);
    --el-tag-text-color: var(--el-color-info);
  }

  :deep(.el-tag.default) {
    --el-tag-bg-color: var(--el-fill-color-light);
    --el-tag-border-color: var(--el-border-color-light);
    --el-tag-text-color: var(--el-text-color-regular);
  }

  // ========== 圆角样式 ==========
  @each $color in (primary, success, warning, danger) {
    :deep(.el-tag.#{$color}-round) {
      --el-tag-bg-color: var(--el-color-#{$color}-light-9);
      --el-tag-border-color: var(--el-color-#{$color}-light-5);
      --el-tag-text-color: var(--el-color-#{$color});
      border-radius: 999px !important;
    }
  }

  // ========== 纯文本色（无背景边框） ==========
  @each $color in (primary, success, warning, danger) {
    :deep(.el-tag.text-#{$color}) {
      --el-tag-bg-color: transparent;
      --el-tag-border-color: transparent;
      --el-tag-text-color: var(--el-color-#{$color});
      font-weight: 500;
    }
  }

  // ========== 粗体 ==========
  @each $color in (primary, success, warning, danger) {
    :deep(.el-tag.bold-#{$color}) {
      --el-tag-bg-color: var(--el-color-#{$color}-light-9);
      --el-tag-border-color: var(--el-color-#{$color}-light-5);
      --el-tag-text-color: var(--el-color-#{$color});
      font-weight: 600;
    }
  }

  // ========== 大号 ==========
  @each $color in (primary, success, warning, danger) {
    :deep(.el-tag.large-#{$color}) {
      --el-tag-bg-color: var(--el-color-#{$color}-light-9);
      --el-tag-border-color: var(--el-color-#{$color}-light-5);
      --el-tag-text-color: var(--el-color-#{$color});
      font-size: 14px;
      padding: 4px 12px;
      height: auto;
    }
  }
}
</style>
