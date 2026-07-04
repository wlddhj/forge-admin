<template>
  <component :is="currentLayout" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import { getPreset } from '@/themes'
import LayoutSidebar from '@/layouts/LayoutSidebar.vue'

const { config } = usePageConfigStore()
const { isMobile } = useResponsive()

const currentLayout = computed(() => {
  // 移动端强制侧栏布局（无论套餐预设）
  if (isMobile.value) return LayoutSidebar
  // 桌面端按套餐预设选择（LayoutTop 在 Task 9 加入）
  return getPreset(config.preset).layout === 'top' ? LayoutSidebar : LayoutSidebar
})
</script>
