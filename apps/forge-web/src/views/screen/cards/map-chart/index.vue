<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { MapChart } from 'echarts/charts'
import { GeoComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([MapChart, GeoComponent, TooltipComponent, VisualMapComponent, CanvasRenderer])

const props = defineProps<{ data: unknown; options: Record<string, unknown> }>()
const containerRef = ref<HTMLDivElement | null>(null)
const chartRef = shallowRef<echarts.ECharts | null>(null)

const buildOption = (): echarts.EChartsOption => {
  const list = Array.isArray(props.data) ? props.data : []
  const nameField = (props.options.nameField as string) ?? 'name'
  const valueField = (props.options.valueField as string) ?? 'value'
  return {
    tooltip: { trigger: 'item' },
    visualMap: { min: 0, max: 1000, calculable: true,
      inRange: { color: ['#1e3a5f', '#1e88e5', '#7c4dff'] },
      textStyle: { color: '#8a96a8' } },
    series: [{ name: 'value', type: 'map', map: (props.options.mapName as string) ?? 'china',
      label: { show: false }, roam: false,
      data: list.map((r: any) => ({ name: r[nameField], value: r[valueField] })) }]
  }
}

const loadMap = async () => {
  const mapName = (props.options.mapName as string) ?? 'china'
  if (echarts.getMap(mapName)) return
  try {
    const res = await fetch(`/maps/${mapName}.json`)
    const geo = await res.json()
    echarts.registerMap(mapName, geo as any)
  } catch { /* 地图缺失时图表为空 */ }
}

onMounted(async () => {
  await loadMap()
  if (containerRef.value) {
    chartRef.value = echarts.init(containerRef.value)
    chartRef.value.setOption(buildOption())
  }
})
watch(() => props.data, () => chartRef.value?.setOption(buildOption(), true), { deep: true })
onUnmounted(() => { chartRef.value?.dispose(); chartRef.value = null })
</script>

<template><div ref="containerRef" class="map-chart" /></template>
<style scoped>.map-chart { width: 100%; height: 100%; }</style>