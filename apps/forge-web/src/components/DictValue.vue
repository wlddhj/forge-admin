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
// 扩展色定义
$ext-colors: (
  'cyan':    (#e6fffb, #87e8de, #13c2c2),
  'purple':  (#f9f0ff, #d3adf7, #722ed1),
  'orange':  (#fff7e6, #ffd591, #fa8c16),
  'pink':    (#fff0f6, #ffadd2, #eb2f96),
  'indigo':  (#f0f5ff, #adc6ff, #2f54eb),
  'brown':   (#fdf2e9, #d9b38c, #8b572a),
  'grey':    (#f5f5f5, #d9d9d9, #8c8c8c),
  'lime':    (#fcffe6, #d3f5a0, #73d13d),
);

// 暗黑模式扩展色
$ext-colors-dark: (
  'cyan':    (#0d2b2a, #1a5c5a, #36cfc9),
  'purple':  (#1a0d2e, #3d2066, #b37feb),
  'orange':  (#2b1d0d, #6b3e11, #ffa940),
  'pink':    (#2b0d1a, #7a1f4e, #f759ab),
  'indigo':  (#0d1530, #1d3a8f, #85a5ff),
  'brown':   (#2b1e0d, #6b4226, #c4884a),
  'grey':    (#1f1f1f, #434343, #bfbfbf),
  'lime':    (#1a2b0d, #3d6b11, #95de64),
);

.dict-value-container {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;

  // ========== 基础色（Element Plus 内置） ==========
  @each $color in (primary, success, warning, danger, info) {
    :deep(.el-tag.#{$color}) {
      --el-tag-bg-color: var(--el-color-#{$color}-light-9);
      --el-tag-border-color: var(--el-color-#{$color}-light-7);
      --el-tag-text-color: var(--el-color-#{$color});
    }
  }

  :deep(.el-tag.default) {
    --el-tag-bg-color: var(--el-fill-color-light);
    --el-tag-border-color: var(--el-border-color-light);
    --el-tag-text-color: var(--el-text-color-regular);
  }

  // ========== 扩展色（基础） ==========
  @each $name, $colors in $ext-colors {
    $bg: nth($colors, 1);
    $border: nth($colors, 2);
    $text: nth($colors, 3);
    :deep(.el-tag.#{$name}) {
      --el-tag-bg-color: #{$bg};
      --el-tag-border-color: #{$border};
      --el-tag-text-color: #{$text};
    }
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

  @each $name, $colors in $ext-colors {
    $bg: nth($colors, 1);
    $border: nth($colors, 2);
    $text: nth($colors, 3);
    :deep(.el-tag.#{$name}-round) {
      --el-tag-bg-color: #{$bg};
      --el-tag-border-color: #{$border};
      --el-tag-text-color: #{$text};
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

  @each $name, $colors in $ext-colors {
    $text: nth($colors, 3);
    :deep(.el-tag.text-#{$name}) {
      --el-tag-bg-color: transparent;
      --el-tag-border-color: transparent;
      --el-tag-text-color: #{$text};
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

  @each $name, $colors in $ext-colors {
    $bg: nth($colors, 1);
    $border: nth($colors, 2);
    $text: nth($colors, 3);
    :deep(.el-tag.bold-#{$name}) {
      --el-tag-bg-color: #{$bg};
      --el-tag-border-color: #{$border};
      --el-tag-text-color: #{$text};
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

  @each $name, $colors in $ext-colors {
    $bg: nth($colors, 1);
    $border: nth($colors, 2);
    $text: nth($colors, 3);
    :deep(.el-tag.large-#{$name}) {
      --el-tag-bg-color: #{$bg};
      --el-tag-border-color: #{$border};
      --el-tag-text-color: #{$text};
      font-size: 14px;
      padding: 4px 12px;
      height: auto;
    }
  }
}

// ========== 暗黑模式扩展色覆盖 ==========
html.dark .dict-value-container {
  @each $name, $colors in $ext-colors-dark {
    $bg: nth($colors, 1);
    $border: nth($colors, 2);
    $text: nth($colors, 3);
    :deep(.el-tag.#{$name}),
    :deep(.el-tag.#{$name}-round),
    :deep(.el-tag.bold-#{$name}),
    :deep(.el-tag.large-#{$name}) {
      --el-tag-bg-color: #{$bg};
      --el-tag-border-color: #{$border};
      --el-tag-text-color: #{$text};
    }
    :deep(.el-tag.text-#{$name}) {
      --el-tag-text-color: #{$text};
    }
  }
}
</style>
