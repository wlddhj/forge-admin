<script setup lang="ts">
withDefaults(defineProps<{
  position?: 'tl' | 'tr' | 'bl' | 'br'
  size?: number
}>(), { position: 'tl', size: 32 })
</script>

<template>
  <svg :class="['deco-corner', `deco-corner--${position}`]"
       :width="size" :height="size" viewBox="0 0 32 32" fill="none">
    <path d="M2 2 L2 14 M2 2 L14 2 M2 8 L8 8 L8 2"
          stroke="currentColor" stroke-width="1.5" />
  </svg>
</template>

<style scoped>
.deco-corner { position: absolute; color: var(--screen-accent, #1e88e5); }
.deco-corner--tl { top: 0; left: 0; }
.deco-corner--tr { top: 0; right: 0; transform: scaleX(-1); }
.deco-corner--bl { bottom: 0; left: 0; transform: scaleY(-1); }
.deco-corner--br { bottom: 0; right: 0; transform: scale(-1, -1); }
</style>