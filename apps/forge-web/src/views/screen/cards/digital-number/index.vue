<script setup lang="ts">
import { computed } from 'vue'
import ScrollNumber from './ScrollNumber.vue'

const props = defineProps<{
  data: unknown
  options: Record<string, unknown>
}>()

const value = computed(() => {
  const v = props.data
  if (typeof v === 'number') return v
  if (Array.isArray(v) && v.length > 0 && typeof v[0] === 'object') {
    const field = (props.options.valueField as string) ?? 'value'
    return Number((v[0] as Record<string, unknown>)[field] ?? 0)
  }
  return Number(v ?? 0)
})

const title = computed(() => (props.options.title as string) ?? '指标')
const unit = computed(() => (props.options.unit as string) ?? '')
const decimals = computed(() => Number(props.options.decimals ?? 0))
const duration = computed(() => Number(props.options.duration ?? 1500))
const color = computed(() => (props.options.color as string) ?? 'var(--screen-accent)')
</script>

<template>
  <div class="digital-number-card">
    <div class="dnc-title">{{ title }}</div>
    <div class="dnc-value" :style="{ color }">
      <ScrollNumber :value="value" :duration="duration" :decimals="decimals" />
      <span v-if="unit" class="dnc-unit">{{ unit }}</span>
    </div>
  </div>
</template>

<style scoped>
.digital-number-card {
  display: flex; flex-direction: column; justify-content: center; align-items: center;
  padding: 16px; height: 100%; width: 100%;
  background: var(--screen-card-bg, rgba(8, 22, 40, 0.85));
  border: 1px solid var(--screen-border, #1e3a5f);
  color: var(--screen-text-primary, #e0e6f1);
  border-radius: 4px;
}
.dnc-title { font-size: 16px; margin-bottom: 8px; color: var(--screen-text-secondary, #8a96a8); }
.dnc-value {
  font-family: 'DIN Alternate', 'Orbitron', 'Courier New', monospace;
  font-size: 56px; font-weight: 700; line-height: 1;
  display: flex; align-items: baseline;
  text-shadow: 0 0 12px currentColor;
}
.dnc-unit { font-size: 18px; margin-left: 6px; opacity: 0.7; }
</style>