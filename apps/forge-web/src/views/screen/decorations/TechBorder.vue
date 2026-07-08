<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'thick' | 'dotted' | 'double' | 'solid' | 'glow'
}>(), { variant: 'default' })
</script>

<template>
  <div :class="['tech-border', `tech-border--${variant}`]">
    <span class="corner tl" /><span class="corner tr" />
    <span class="corner bl" /><span class="corner br" />
    <div class="tech-border__content"><slot /></div>
  </div>
</template>

<style scoped>
.tech-border { position: relative; width: 100%; height: 100%;
  border: 1px solid var(--screen-border, #1e3a5f);
  background: var(--screen-card-bg, rgba(8, 22, 40, 0.85)); }
.tech-border--thick { border-width: 2px; }
.tech-border--dotted { border-style: dotted; }
.tech-border--double { border-style: double; }
.tech-border--solid { border-style: solid; border-color: var(--screen-accent, #1e88e5); }
.tech-border--glow { box-shadow: 0 0 20px var(--screen-accent, #1e88e5); }
.corner { position: absolute; width: 12px; height: 12px;
  border: 2px solid var(--screen-accent, #1e88e5); }
.corner.tl { top: -2px; left: -2px; border-right: none; border-bottom: none; }
.corner.tr { top: -2px; right: -2px; border-left: none; border-bottom: none; }
.corner.bl { bottom: -2px; left: -2px; border-right: none; border-top: none; }
.corner.br { bottom: -2px; right: -2px; border-left: none; border-top: none; }
.tech-border__content { width: 100%; height: 100%; }
</style>