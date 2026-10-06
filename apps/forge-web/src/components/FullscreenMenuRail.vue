<template>
  <nav class="fsm-rail" aria-label="菜单分类">
    <button
      type="button"
      class="rail-item"
      :class="{ active: active === 'all' }"
      @click="$emit('select', 'all')"
    >
      <el-icon :size="16"><Grid /></el-icon>
      <span class="rail-title">全部</span>
      <span class="rail-badge">{{ totalCount }}</span>
    </button>
    <button
      type="button"
      class="rail-item"
      :class="{ active: active === 'home' }"
      @click="$emit('select', 'home')"
    >
      <el-icon :size="16"><HomeFilled /></el-icon>
      <span class="rail-title">首页</span>
    </button>
    <div class="rail-divider" />
    <el-scrollbar class="rail-scroll">
      <button
        v-for="cat in categories"
        :key="cat.id"
        type="button"
        class="rail-item"
        :class="{ active: active === cat.id }"
        @click="$emit('select', cat.id)"
      >
        <IconPreview v-if="cat.icon" :icon="cat.icon" :size="16" />
        <el-icon v-else :size="16"><FolderOpened /></el-icon>
        <span class="rail-title">{{ cat.title }}</span>
        <span class="rail-badge">{{ cat.count }}</span>
      </button>
    </el-scrollbar>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Grid, HomeFilled, FolderOpened } from '@element-plus/icons-vue'
import IconPreview from '@/components/IconPreview.vue'
import type { FullscreenMenuCategory } from '@/composables/useFullscreenMenuData'

const props = defineProps<{
  categories: FullscreenMenuCategory[]
  active: string | number
}>()

defineEmits<{
  (e: 'select', id: 'all' | 'home' | number): void
}>()

const totalCount = computed(() =>
  props.categories.reduce((n, c) => n + c.count, 0)
)
</script>

<style scoped lang="scss">
.fsm-rail {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 8px;
  background: var(--app-fsm-rail-bg, var(--el-fill-color-light));
  border-right: 1px solid var(--app-fsm-border, var(--el-border-color-lighter));
}

.rail-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: var(--app-radius-base, 6px);
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;

  &:hover {
    background: var(--app-fsm-hover-bg, var(--el-fill-color-light));
  }

  &.active {
    background: var(--app-fsm-rail-item-active-bg, var(--el-color-primary-light-9));
    color: var(--app-fsm-rail-item-active-text, var(--app-color-primary));
  }
}

.rail-title {
  flex: 1;
  min-width: 0;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-badge {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 18px;
  padding: 0 8px;
  border-radius: 9px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
}

.rail-divider {
  height: 1px;
  margin: 8px 4px;
  background: var(--app-fsm-border, var(--el-border-color-lighter));
}

.rail-scroll {
  flex: 1;
  min-height: 0;
}
</style>
