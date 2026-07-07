<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { GaugeChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([GaugeChart, TooltipComponent, CanvasRenderer])

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()
const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)

const valueOf = (): number => {
  const v = props.data
  if (typeof v === 'number') return v
  if (Array.isArray(v) && v.length > 0 && typeof v[0] === 'object') {
    const field = (props.options.valueField as string) ?? 'value'
    return Number((v[0] as Record<string, unknown>)[field] ?? 0)
  }
  return Number(v ?? 0)
}

const buildOption = (): echarts.EChartsOption => ({
  series: [{
    type: 'gauge',
    min: Number(props.options.min ?? 0),
    max: Number(props.options.max ?? 100),
    progress: { show: true, width: 12 },
    axisLine: { lineStyle: { width: 12, color: [[1, 'var(--screen-accent)']] } },
    axisTick: { show: false }, splitLine: { length: 8 },
    pointer: { width: 4 },
    detail: { fontSize: 24, color: 'var(--screen-text-primary)', formatter: '{value}%' },
    data: [{ value: valueOf() }]
  }]
})

onMounted(() => {
  if (containerRef.value) {
    chartRef.value = echarts.init(containerRef.value)
    chartRef.value.setOption(buildOption())
  }
})
watch(() => props.data, () => chartRef.value?.setOption(buildOption(), true))
onUnmounted(() => { chartRef.value?.dispose(); chartRef.value = null })
</script>

<template><div ref="containerRef" class="gauge-chart" /></template>
<style scoped>.gauge-chart { width: 100%; height: 100%; }</style>