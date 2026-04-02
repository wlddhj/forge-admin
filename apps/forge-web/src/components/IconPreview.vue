<template>
  <el-icon v-if="isEp" :size="size">
    <component :is="iconName" />
  </el-icon>
  <i v-else :class="faClass" :style="faStyle" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  icon: string
  size?: number
}>(), {
  size: 16
})

// 判断是否为 Element Plus 图标
const isEp = computed(() => props.icon.startsWith('ep:') || (!props.icon.includes(':')))

// 图标名称（去掉前缀）
const iconName = computed(() => {
  if (props.icon.startsWith('ep:')) return props.icon.slice(3)
  if (!props.icon.includes(':')) return props.icon
  return ''
})

// Font Awesome class
const faClass = computed(() => {
  if (props.icon.startsWith('fa-solid:')) {
    return `fas fa-${props.icon.slice(9)}`
  }
  if (props.icon.startsWith('fa:')) {
    return `fa fa-${props.icon.slice(3)}`
  }
  return ''
})

// FA 图标大小样式
const faStyle = computed(() => ({
  fontSize: `${props.size}px`,
  lineHeight: '1',
  verticalAlign: 'middle'
}))
</script>
