<template>
  <div class="fsm-content">
    <div v-if="isFlatView && displayCategories.length" class="fsm-chips">
      <button
        v-for="cat in displayCategories"
        :key="cat.id"
        type="button"
        class="fsm-chip"
        @click="scrollToCategory(cat.id)"
      >
        {{ cat.title }}
      </button>
    </div>

    <el-scrollbar ref="scrollbarRef" class="fsm-scroll">
      <template v-if="displayCategories.length">
        <section
          v-for="cat in displayCategories"
          :id="sectionId(cat.id)"
          :key="cat.id"
          class="fsm-section"
        >
          <header class="fsm-section-header">
            <IconPreview v-if="cat.icon" :icon="cat.icon" :size="18" />
            <h3>{{ cat.title }}</h3>
            <span class="fsm-section-count">{{ cat.count }} 项</span>
          </header>

          <template v-for="seg in segmentsOf(cat.groups)" :key="seg.key">
            <div v-if="seg.type === 'group'" class="fsm-group">
              <div class="fsm-group-title">
                <IconPreview v-if="seg.group!.icon" :icon="seg.group!.icon" :size="14" />
                <span>{{ seg.group!.title }}</span>
              </div>
              <div class="fsm-items">
                <button
                  v-for="item in seg.group!.items"
                  :key="item.id"
                  type="button"
                  class="fsm-item"
                  @click="$emit('navigate', item)"
                >
                  <IconPreview v-if="item.icon" :icon="item.icon" :size="15" />
                  <span>{{ item.title }}</span>
                </button>
              </div>
            </div>
            <div v-else class="fsm-items">
              <button
                v-for="item in seg.items!"
                :key="item.id"
                type="button"
                class="fsm-item"
                @click="$emit('navigate', item)"
              >
                <IconPreview v-if="item.icon" :icon="item.icon" :size="15" />
                <span>{{ item.title }}</span>
              </button>
            </div>
          </template>
        </section>
      </template>
      <el-empty v-else :description="keyword.trim() ? '未找到匹配菜单' : '暂无菜单'" class="fsm-empty" />
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import IconPreview from '@/components/IconPreview.vue'
import type {
  FullscreenMenuCategory,
  FullscreenMenuGroup,
  FullscreenMenuItem
} from '@/composables/useFullscreenMenuData'

const props = defineProps<{
  categories: FullscreenMenuCategory[]
  active: string | number
  keyword: string
}>()

defineEmits<{
  (e: 'navigate', item: FullscreenMenuItem): void
}>()

const scrollbarRef = ref<{ setScrollTop: (top: number) => void } | null>(null)

interface Segment {
  type: 'grid' | 'group'
  key: string
  items?: FullscreenMenuItem[]
  group?: FullscreenMenuGroup
}

/** 连续单项组（仅两级菜单）合并为无标题网格段，多项组（三级）独立成块，保持菜单顺序 */
function segmentsOf(groups: FullscreenMenuGroup[]): Segment[] {
  const segments: Segment[] = []
  let pending: FullscreenMenuItem[] = []
  const flush = () => {
    if (pending.length) {
      segments.push({ type: 'grid', key: `n-${pending.map(it => it.id).join('-')}`, items: pending })
      pending = []
    }
  }
  for (const g of groups) {
    if (g.items.length === 1) {
      pending.push(g.items[0])
    } else {
      flush()
      segments.push({ type: 'group', key: `g-${g.id}`, group: g })
    }
  }
  flush()
  return segments
}

const isFlatView = computed(() => !!props.keyword.trim() || props.active === 'all')

const displayCategories = computed(() => {
  if (isFlatView.value) return props.categories
  return props.categories.filter(c => c.id === props.active)
})

const sectionId = (id: number) => `fsm-cat-${id}`

const scrollToCategory = (id: number) => {
  document.getElementById(sectionId(id))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch([() => props.active, () => props.keyword], () => {
  nextTick(() => scrollbarRef.value?.setScrollTop(0))
})
</script>

<style scoped lang="scss">
.fsm-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.fsm-chips {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
  padding: 12px 24px 0;
  overflow-x: auto;
}

.fsm-chip {
  flex-shrink: 0;
  padding: 4px 14px;
  border: 1px solid var(--app-fsm-border, var(--el-border-color-lighter));
  border-radius: 16px;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 13px;
  cursor: pointer;

  &:hover {
    color: var(--app-fsm-link-hover, var(--app-color-primary));
    border-color: var(--app-fsm-link-hover, var(--app-color-primary));
  }
}

.fsm-scroll {
  flex: 1;
  min-height: 0;
}

.fsm-section {
  padding: 0 24px 20px;

  & + .fsm-section {
    border-top: 1px solid var(--app-fsm-border, var(--el-border-color-lighter));
  }
}

.fsm-section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 0 6px;

  h3 {
    margin: 0;
    font-size: 16px;
    color: var(--el-text-color-primary);
  }
}

.fsm-section-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.fsm-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 0 4px;
  font-size: 13px;
  color: var(--app-fsm-group-title, var(--el-text-color-secondary));
}

.fsm-items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 2px 16px;
  padding: 4px 0;
}

.fsm-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: var(--app-radius-base, 6px);
  background: transparent;
  color: var(--app-fsm-link, var(--el-text-color-regular));
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &:hover {
    background: var(--app-fsm-hover-bg, var(--el-fill-color-light));
    color: var(--app-fsm-link-hover, var(--app-color-primary));
  }
}

.fsm-empty {
  margin-top: 80px;
}
</style>
