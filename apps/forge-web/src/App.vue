<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useTenantConfig } from '@/composables/useTenantConfig'

// 启动时拉取后端多租户配置,决定是否显示租户相关 UI
onMounted(async () => {
  const { loadIfNeeded } = useTenantConfig()
  await loadIfNeeded()
})
</script>

<style>
#app {
  width: 100%;
  height: 100%;
}
</style>
