<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()

const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)

const buildOption = (): echarts.EChartsOption => {
  const list = Array.isArray(props.data) ? props.data : []
  const xField = (props.options.xField as string) ?? 'x'
  const yField = (props.options.yField as string) ?? 'y'
  const seriesField = (props.options.seriesField as string) ?? null
  const smooth = Boolean(props.options.smooth ?? true)
  const baseAxis = { axisLine: { lineStyle: { color: '#1e3a5f' } } }
  const splitLine = { lineStyle: { color: 'rgba(30,58,95,0.3)' } }

  if (seriesField) {
    const groups = new Map<string, Record<string, unknown>[]>()
    list.forEach((row: any) => {
      const k = String(row[seriesField] ?? 'default')
      if (!groups.has(k)) groups.set(k, [])
      groups.get(k)!.push(row)
    })
    const xSet = new Set(list.map((r: any) => r[xField]))
    return {
      grid: { left: 40, right: 16, top: 32, bottom: 24 },
      tooltip: { trigger: 'axis' },
      legend: { textStyle: { color: '#8a96a8' } },
      xAxis: { type: 'category', data: Array.from(xSet), ...baseAxis },
      yAxis: { type: 'value', ...baseAxis, splitLine },
      series: Array.from(groups.entries()).map(([name, rows]) => ({
        name, type: 'line', smooth, data: rows.map(r => r[yField])
      }))
    }
  }
  return {
    grid: { left: 40, right: 16, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: list.map((r: any) => r[xField]), ...baseAxis },
    yAxis: { type: 'value', ...baseAxis, splitLine },
    series: [{ type: 'line', smooth, data: list.map((r: any) => r[yField]) }]
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

<template><div ref="containerRef" class="line-chart" /></template>
<style scoped>.line-chart { width: 100%; height: 100%; }</style>