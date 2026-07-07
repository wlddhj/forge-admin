<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()
const list = computed(() => Array.isArray(props.data) ? props.data : [])
const columns = computed(() => {
  const first = list.value[0]
  if (!first || typeof first !== 'object') return []
  return Object.keys(first as Record<string, unknown>)
})
const sortField = computed(() => (props.options.sortField as string) || '')
const sorted = computed(() => {
  if (!sortField.value) return list.value
  return [...list.value].sort((a: any, b: any) => {
    const av = a[sortField.value], bv = b[sortField.value]
    return av === bv ? 0 : av > bv ? 1 : -1
  })
})
</script>

<template>
  <div class="scroll-table-card">
    <div class="st-header">
      <span v-for="col in columns" :key="col" class="st-th">{{ col }}</span>
    </div>
    <div class="st-body">
      <div v-for="(row, i) in sorted" :key="i" class="st-row">
        <span v-for="col in columns" :key="col" class="st-td">{{ (row as any)[col] }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scroll-table-card { width: 100%; height: 100%; overflow: hidden; display: flex; flex-direction: column;
  background: var(--screen-card-bg, rgba(8,22,40,0.85));
  border: 1px solid var(--screen-border, #1e3a5f); color: var(--screen-text-primary, #e0e6f1); border-radius: 4px; }
.st-header { display: flex; padding: 8px 12px; background: rgba(30,58,95,0.3); font-weight: 600; }
.st-th { flex: 1; padding: 0 8px; }
.st-body { flex: 1; overflow-y: auto; }
.st-row { display: flex; padding: 6px 12px; border-bottom: 1px solid var(--screen-grid-line, rgba(30,58,95,0.3)); }
.st-td { flex: 1; padding: 0 8px; }
</style>