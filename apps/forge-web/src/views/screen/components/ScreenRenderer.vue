<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { cardRegistry, registerBuiltinCards } from '@/views/screen/cards/registry'
import CardErrorBoundary from './CardErrorBoundary.vue'
import TechBorder from '@/views/screen/decorations/TechBorder.vue'
import type { ScreenConfig, ScreenCard } from '@/types/screen'

const props = defineProps<{
  config: ScreenConfig
  editor?: boolean
}>()

onMounted(() => {
  try { registerBuiltinCards() } catch { /* 已注册则忽略 */ }
})

const cards = computed<ScreenCard[]>(() => props.config?.cards ?? [])

const cardPositionStyle = (card: ScreenCard) => {
  const colW = 80
  const rowH = 45
  return {
    position: 'absolute' as const,
    left: `${card.x * colW}px`,
    top: `${card.y * rowH}px`,
    width: `${card.w * colW}px`,
    height: `${card.h * rowH}px`
  }
}
</script>

<template>
  <div class="screen-renderer" :data-screen-theme="config.theme">
    <div
      v-for="card in cards"
      :key="card.id"
      class="screen-card"
      :style="cardPositionStyle(card)"
    >
      <TechBorder variant="default" class="card-border">
        <CardErrorBoundary>
          <component
            v-if="cardRegistry.get(card.type)"
            :is="cardRegistry.get(card.type)!.component"
            :data="null"
            :options="card.options || {}"
          />
          <div v-else class="unknown-card">未注册的卡片类型：{{ card.type }}</div>
        </CardErrorBoundary>
      </TechBorder>
    </div>
  </div>
</template>

<style scoped>
.screen-renderer { position: relative; width: 1920px; height: 1080px; }
.screen-card { position: absolute; }
.card-border { width: 100%; height: 100%; }
.unknown-card { color: var(--screen-text-secondary, #8a96a8); padding: 16px; }
</style>
