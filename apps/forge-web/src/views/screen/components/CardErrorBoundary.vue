<script setup lang="ts">
import { onErrorCaptured, onUnmounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  retryAfterMs?: number
  onRetry?: () => void | Promise<void>
}>(), {
  retryAfterMs: 5000,
  onRetry: undefined
})

const error = ref<Error | null>(null)
let retryTimer: ReturnType<typeof setTimeout> | null = null

const handleError = (err: unknown) => {
  error.value = err instanceof Error ? err : new Error(String(err))
  if (props.onRetry) {
    retryTimer = setTimeout(() => { void props.onRetry?.() }, props.retryAfterMs)
  }
}

const clearTimer = () => {
  if (retryTimer) { clearTimeout(retryTimer); retryTimer = null }
}

const handleRetry = () => {
  clearTimer()
  error.value = null
  if (props.onRetry) void props.onRetry()
}

onErrorCaptured((err) => { handleError(err); return false })
watch(() => props.retryAfterMs, clearTimer)
onUnmounted(clearTimer)
</script>

<template>
  <div class="card-error-boundary">
    <template v-if="!error"><slot /></template>
    <div v-else class="card-error-fallback">
      <div class="card-error-icon">!</div>
      <div class="card-error-text">数据加载失败，{{ Math.round(retryAfterMs / 1000) }}s 后重试</div>
      <button class="card-error-retry" type="button" @click="handleRetry">立即重试</button>
    </div>
  </div>
</template>

<style scoped>
.card-error-boundary { width: 100%; height: 100%; }
.card-error-fallback {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  width: 100%; height: 100%;
  color: var(--screen-text-secondary, #8a96a8); font-size: 14px;
}
.card-error-icon {
  width: 32px; height: 32px; line-height: 32px; text-align: center;
  border: 1px solid var(--screen-accent, #1e88e5); border-radius: 50%;
  margin-bottom: 8px; color: var(--screen-accent, #1e88e5);
}
.card-error-retry {
  margin-top: 12px; padding: 4px 12px; cursor: pointer;
  background: transparent; color: var(--screen-accent, #1e88e5);
  border: 1px solid var(--screen-accent, #1e88e5); border-radius: 4px;
}
</style>
