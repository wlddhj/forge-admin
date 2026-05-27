<template>
  <div ref="containerRef" class="bpmn-preview-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import BpmnViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

const props = defineProps<{
  xml: string
}>()

const containerRef = ref<HTMLElement | null>(null)
const viewer = ref<BpmnViewer | null>(null)

const initViewer = async () => {
  if (!containerRef.value) return

  const instance = new BpmnViewer({
    container: containerRef.value,
  })

  viewer.value = instance

  if (props.xml) {
    await renderXml(props.xml)
  }
}

const renderXml = async (xml: string) => {
  if (!viewer.value || !xml) return

  try {
    await viewer.value.importXML(xml)
    const canvas = viewer.value.get('canvas')
    canvas.zoom('fit-viewport')
  } catch (err) {
    console.error('渲染 BPMN XML 失败:', err)
  }
}

watch(() => props.xml, (newXml) => {
  if (newXml && viewer.value) {
    renderXml(newXml)
  }
})

onMounted(() => {
  initViewer()
})

onBeforeUnmount(() => {
  if (viewer.value) {
    viewer.value.destroy()
  }
})
</script>

<style scoped>
.bpmn-preview-container {
  width: 100%;
  height: 100%;
  min-height: 300px;
  background: #f8f8f8;
}
</style>