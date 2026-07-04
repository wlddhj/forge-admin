<template>
  <component :is="currentLayout" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import { getPreset } from '@/themes'
import LayoutSidebar from '@/layouts/LayoutSidebar.vue'
import LayoutTop from '@/layouts/LayoutTop.vue'

const { config } = usePageConfigStore()
const { isMobile } = useResponsive()

const currentLayout = computed(() => {
  if (isMobile.value) return LayoutSidebar
  return getPreset(config.preset).layout === 'top' ? LayoutTop : LayoutSidebar
})
</script>
