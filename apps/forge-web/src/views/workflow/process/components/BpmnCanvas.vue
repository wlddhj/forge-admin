<template>
  <div ref="containerRef" class="bpmn-canvas-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useBpmnJsDesigner } from '@/composables/useBpmnJsDesigner'

const containerRef = ref<HTMLElement | null>(null)
const { init, destroy, ...designer } = useBpmnJsDesigner(containerRef)

const emit = defineEmits<{
  (e: 'ready'): void
}>()

onMounted(() => {
  init()
  emit('ready')
})

onBeforeUnmount(() => {
  destroy()
})

defineExpose(designer)
</script>

<style scoped>
.bpmn-canvas-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
  background: #f8f8f8;
}
</style>