<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { cardRegistry, registerBuiltinCards } from '@/views/screen/cards/registry'
import type { ScreenCardComponent } from '@/views/screen/cards/types'

const emit = defineEmits<{ 'add-card': [type: string] }>()

const items = ref<ScreenCardComponent[]>([])

onMounted(() => {
  try { registerBuiltinCards() } catch { /* 已注册 */ }
  items.value = cardRegistry.list()
})

const handleDragStart = (e: DragEvent, type: string) => {
  e.dataTransfer?.setData('text/plain', type)
  emit('add-card', type)
}
</script>

<template>
  <div class="card-panel">
    <h3>组件库</h3>
    <div class="card-list">
      <div
        v-for="entry in items" :key="entry.type"
        class="card-item" draggable="true"
        @dragstart="e => handleDragStart(e, entry.type)"
        @click="emit('add-card', entry.type)"
      >
        <el-icon><component :is="entry.meta.icon" /></el-icon>
        <span class="card-item-name">{{ entry.meta.title }}</span>
        <span class="card-item-type">{{ entry.type }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card-panel { width: 200px; background: rgba(8,22,40,0.6); padding: 12px; border-right: 1px solid #1e3a5f; overflow-y: auto; }
.card-list { display: flex; flex-direction: column; gap: 6px; }
.card-item {
  padding: 8px 10px; background: rgba(30,58,95,0.4); border-radius: 4px; cursor: grab;
  display: flex; align-items: center; gap: 8px; color: #e0e6f1; font-size: 13px;
}
.card-item:hover { background: rgba(30,58,95,0.7); }
.card-item-name { flex: 1; }
.card-item-type { font-size: 10px; color: #8a96a8; font-family: monospace; }
</style>
