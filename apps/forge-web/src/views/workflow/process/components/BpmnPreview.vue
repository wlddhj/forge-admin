<template>
  <div ref="containerRef" class="bpmn-preview-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/index.css'
import '@logicflow/extension/lib/style/index.css'
import { BPMNElements, BPMNAdapter } from '@logicflow/extension'

const props = defineProps<{
  xml: string
}>()

const containerRef = ref<HTMLElement | null>(null)
let lfInstance: LogicFlow | null = null

const renderDiagram = () => {
  if (!containerRef.value || !props.xml) return

  if (!lfInstance) {
    lfInstance = new LogicFlow({
      container: containerRef.value,
      plugins: [BPMNElements, BPMNAdapter],
      isSilentMode: true,
      stopZoomGraph: true,
      stopScrollGraph: true,
      stopMoveGraph: true,
    })
  }

  (lfInstance as any).render(props.xml)
}

watch(() => props.xml, () => renderDiagram())

onMounted(() => renderDiagram())

onBeforeUnmount(() => {
  lfInstance?.destroy()
  lfInstance = null
})
</script>

<style scoped>
.bpmn-preview-container {
  width: 100%;
  height: 100%;
  min-height: 300px;
}
</style>
