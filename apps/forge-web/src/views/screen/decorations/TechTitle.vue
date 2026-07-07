<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  subtitle?: string
  align?: 'left' | 'center' | 'right'
}>(), { subtitle: '', align: 'left' })
</script>

<template>
  <div :class="['tech-title', `tech-title--${align}`]">
    <span class="tech-title__bar" />
    <div class="tech-title__text">
      <div class="tech-title__main">{{ title }}</div>
      <div v-if="subtitle" class="tech-title__sub">{{ subtitle }}</div>
    </div>
    <span class="tech-title__bar" />
  </div>
</template>

<style scoped>
.tech-title { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.tech-title__bar { flex: 1; height: 1px;
  background: linear-gradient(to right, var(--screen-accent, #1e88e5), transparent); }
.tech-title--right .tech-title__bar:first-child {
  background: linear-gradient(to left, var(--screen-accent, #1e88e5), transparent); }
.tech-title--center .tech-title__bar:first-child {
  background: linear-gradient(to right, transparent, var(--screen-accent, #1e88e5)); }
.tech-title--center .tech-title__bar:last-child {
  background: linear-gradient(to left, transparent, var(--screen-accent, #1e88e5)); }
.tech-title__main { font-size: 18px; font-weight: 600;
  color: var(--screen-text-primary, #e0e6f1); letter-spacing: 2px; }
.tech-title__sub { font-size: 12px; color: var(--screen-text-secondary, #8a96a8); margin-top: 2px; }
</style>