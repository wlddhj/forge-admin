<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()
const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)

const buildOption = () => {
  const list = Array.isArray(props.data) ? props.data : []
  const nameField = (props.options.nameField as string) ?? 'name'
  const valueField = (props.options.valueField as string) ?? 'value'
  return {
    tooltip: { trigger: 'item' },
    legend: { textStyle: { color: '#8a96a8' }, bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: list.map((r: any) => ({ name: r[nameField], value: r[valueField] }))
    }]
  }
}

onMounted(() => {
  if (containerRef.value) {
    chartRef.value = echarts.init(containerRef.value)
    chartRef.value.setOption(buildOption())
  }
})
watch(() => props.data, () => chartRef.value?.setOption(buildOption(), true), { deep: true })
onUnmounted(() => { chartRef.value?.dispose(); chartRef.value = null })
</script>

<template><div ref="containerRef" class="pie-chart" /></template>
<style scoped>.pie-chart { width: 100%; height: 100%; }</style>