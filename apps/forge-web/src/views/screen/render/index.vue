<template>
  <div class="screen-render">
    <div v-if="loading" class="loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
    <el-empty v-else-if="error" :description="error" />
    <ScreenRenderer v-else-if="config" :config="config" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getScreenByCode, type ScreenDetailResponse } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import ScreenRenderer from '@/views/screen/components/ScreenRenderer.vue'
import { Loading } from '@element-plus/icons-vue'
import type { ScreenConfig } from '@/types/screen'

const route = useRoute()
const loading = ref(true)
const error = ref<string | null>(null)
const config = ref<ScreenConfig | null>(null)

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const code = String(route.params.code)
    const detail: ScreenDetailResponse = await getScreenByCode(code)
    if (!detail.config) {
      error.value = '大屏未配置'
      return
    }
    const parsed: ScreenConfig = JSON.parse(detail.config)
    config.value = parsed
    applyScreenTheme(parsed.theme)
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.code, load)
</script>

<style scoped>
.screen-render {
  position: fixed; inset: 0; overflow: hidden;
  background: var(--screen-bg, #000);
  display: flex; align-items: center; justify-content: center;
}
.loading { color: var(--screen-text-primary, #e0e6f1); display: flex; gap: 8px; align-items: center; }
</style>
