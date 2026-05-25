<template>
  <div ref="containerRef" class="bpmn-designer-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import '@logicflow/core/dist/index.css'
import '@logicflow/extension/lib/style/index.css'
import { useBpmnDesigner } from '@/composables/useBpmnDesigner'

const containerRef = ref<HTMLElement | null>(null)
const { init, destroy, ...designer } = useBpmnDesigner(containerRef)

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
.bpmn-designer-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
}
</style>
