<template>
  <iframe
    v-if="goViewUrl"
    :src="goViewUrl"
    class="screen-iframe"
    allow="fullscreen"
  />
  <div v-else class="screen-render">
    <span class="loading">加载中...</span>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getScreenByCode } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const ready = ref(false)

const goViewUrl = computed(() => {
  const code = route.params.code
  const token = userStore.token
  // goView preview 路由用 code 而非 id 定位
  const base = import.meta.env.DEV
    ? 'http://localhost:8001'
    : '/screen-app'
  return `${base}/#/chart/preview/forge?code=${code}&token=${token}&runtime=1`
})

const load = async () => {
  ready.value = false
  try {
    const code = String(route.params.code)
    const detail = await getScreenByCode(code)
    if (detail.theme) applyScreenTheme(detail.theme as any)
    ready.value = true
  } catch (e: any) {
    if (e?.response?.status === 401 || e?.response?.status === 403) {
      ElMessage.error('无权访问该大屏')
    } else if (e?.response?.status === 404) {
      ElMessage.error('大屏不存在')
    } else {
      ElMessage.error('大屏加载失败')
    }
  }
}

onMounted(load)
watch(() => route.params.code, load)
</script>

<style scoped>
.screen-iframe { width: 100vw; height: 100vh; border: none; display: block; }
.screen-render { width: 100vw; height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--screen-bg, #000); }
.loading { color: var(--screen-text-primary, #e0e6f1); }
</style>
