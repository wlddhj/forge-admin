<template>
  <component :is="currentLayout" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import LayoutSidebar from '@/layouts/LayoutSidebar.vue'
import LayoutTop from '@/layouts/LayoutTop.vue'
import LayoutFullscreen from '@/layouts/LayoutFullscreen.vue'

const { config } = usePageConfigStore()
const { isMobile } = useResponsive()

const currentLayout = computed(() => {
  if (isMobile.value) return LayoutSidebar
  if (config.layout === 'top') return LayoutTop
  if (config.layout === 'fullscreen') return LayoutFullscreen
  return LayoutSidebar
})
</script>
