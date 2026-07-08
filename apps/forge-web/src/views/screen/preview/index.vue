<template>
  <div class="screen-preview">
    <div v-if="loading" class="loading">加载中...</div>
    <el-empty v-else-if="error" :description="error" />
    <template v-else>
      <div class="preview-toolbar">
        <el-tag type="warning">预览模式（草稿）</el-tag>
        <el-button size="small" @click="$router.back()">返回编辑</el-button>
      </div>
      <ScreenRenderer :config="config!" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getScreenByCode, type ScreenDetailResponse } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import ScreenRenderer from '@/views/screen/components/ScreenRenderer.vue'
import type { ScreenConfig } from '@/types/screen'

const route = useRoute()
const loading = ref(true)
const error = ref<string | null>(null)
const config = ref<ScreenConfig | null>(null)

onMounted(async () => {
  try {
    const code = String(route.params.code)
    const detail: ScreenDetailResponse = await getScreenByCode(code)
    const raw = detail.configDraft || detail.config
    if (!raw) { error.value = '大屏无草稿配置'; return }
    config.value = JSON.parse(raw)
    applyScreenTheme(config.value.theme)
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.screen-preview {
  position: fixed; inset: 0; background: var(--screen-bg, #000);
  display: flex; align-items: center; justify-content: center;
}
.preview-toolbar {
  position: absolute; top: 16px; left: 16px; display: flex; gap: 12px; z-index: 10;
}
.loading { color: var(--screen-text-primary, #e0e6f1); }
</style>
