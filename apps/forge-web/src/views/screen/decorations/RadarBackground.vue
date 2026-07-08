<template>
  <div class="radar-bg" aria-hidden="true">
    <div class="radar-bg__line" />
    <div class="radar-bg__line" style="--delay: 2s" />
    <div class="radar-bg__line" style="--delay: 4s" />
  </div>
</template>

<style scoped>
.radar-bg { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.radar-bg__line {
  position: absolute; left: 50%; top: 50%;
  width: 200vmax; height: 2px;
  background: linear-gradient(to right, transparent 0%, var(--screen-accent, #1e88e5) 50%, transparent 100%);
  opacity: 0.4;
  transform-origin: 0 50%;
  animation: radar-sweep 8s linear infinite;
  animation-delay: var(--delay, 0s);
}
@keyframes radar-sweep { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>