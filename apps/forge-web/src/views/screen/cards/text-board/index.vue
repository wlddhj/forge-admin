<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()

const template = computed(() => (props.options.template as string) ?? '{{value}}')
const fontSize = computed(() => Number(props.options.fontSize ?? 32))

const rendered = computed(() => {
  const dataObj = (props.data && typeof props.data === 'object' && !Array.isArray(props.data))
    ? (props.data as Record<string, unknown>)
    : { value: props.data }
  return template.value.replace(/\{\{(\w+)\}\}/g, (_, key) => String(dataObj[key] ?? ''))
})
</script>

<template>
  <div class="text-board-card">
    <div class="tb-text" :style="{ fontSize: `${fontSize}px` }">{{ rendered }}</div>
  </div>
</template>

<style scoped>
.text-board-card { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  padding: 16px; background: var(--screen-card-bg, rgba(8,22,40,0.85));
  border: 1px solid var(--screen-border, #1e3a5f); color: var(--screen-text-primary, #e0e6f1); border-radius: 4px; }
.tb-text { font-weight: 600; text-align: center; word-break: break-all; }
</style>