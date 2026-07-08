<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()
const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)
const baseAxis = { axisLine: { lineStyle: { color: '#1e3a5f' } } }
const splitLine = { lineStyle: { color: 'rgba(30,58,95,0.3)' } }

const buildOption = () => {
  const list = Array.isArray(props.data) ? props.data : []
  const xField = (props.options.xField as string) ?? 'name'
  const yField = (props.options.yField as string) ?? 'value'
  return {
    grid: { left: 40, right: 16, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: list.map((r: any) => r[xField]), ...baseAxis },
    yAxis: { type: 'value', ...baseAxis, splitLine },
    series: [{ type: 'bar', data: list.map((r: any) => r[yField]) }]
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

<template><div ref="containerRef" class="bar-chart" /></template>
<style scoped>.bar-chart { width: 100%; height: 100%; }</style>