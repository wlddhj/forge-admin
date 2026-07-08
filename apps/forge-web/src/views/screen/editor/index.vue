<template>
  <iframe
    v-if="goViewUrl"
    :src="goViewUrl"
    class="screen-iframe"
    allow="fullscreen"
    @error="handleError"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const userStore = useUserStore()

const goViewUrl = computed(() => {
  const id = route.params.code
  const token = userStore.token
  // 开发: localhost:8001；生产: /screen-app（nginx 代理）
  const base = import.meta.env.DEV
    ? 'http://localhost:8001'
    : '/screen-app'
  return `${base}/#/chart?id=${id}&token=${token}`
})

const handleError = () => {
  ElMessage.error('goView 编辑器加载失败')
}
</script>

<style scoped>
.screen-iframe { width: 100vw; height: 100vh; border: none; display: block; }
</style>
