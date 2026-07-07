<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  duration?: number
  decimals?: number
}>(), { duration: 1000, decimals: 0 })

const display = ref(0)
let raf: number | null = null
let startTime = 0
let from = 0
let to = 0

const format = (n: number) => Number(n).toFixed(props.decimals)

const animate = (now: number) => {
  const elapsed = now - startTime
  const progress = Math.min(elapsed / props.duration, 1)
  const eased = 1 - Math.pow(1 - progress, 3)
  display.value = from + (to - from) * eased
  if (progress < 1) {
    raf = requestAnimationFrame(animate)
  } else {
    display.value = to
    raf = null
  }
}

const start = (newVal: number) => {
  if (raf) cancelAnimationFrame(raf)
  from = display.value
  to = newVal
  startTime = performance.now()
  raf = requestAnimationFrame(animate)
}

watch(() => props.value, (v) => start(v), { immediate: true })
onUnmounted(() => { if (raf) cancelAnimationFrame(raf) })
</script>

<template>
  <span class="scroll-number">{{ format(display) }}</span>
</template>

<style scoped>
.scroll-number {
  font-family: 'DIN Alternate', 'Orbitron', 'Courier New', monospace;
  font-variant-numeric: tabular-nums;
}
</style>