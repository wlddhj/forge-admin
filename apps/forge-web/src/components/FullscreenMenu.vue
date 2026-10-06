<template>
  <Teleport to="body">
    <Transition name="fsm" @after-enter="focusSearch">
      <div v-if="modelValue" class="fsm-overlay" role="dialog" aria-modal="true" aria-label="全屏菜单">
        <div class="fsm-topbar">
          <span class="fsm-topbar-title">全部菜单</span>
          <el-input
            ref="searchInputRef"
            v-model="keyword"
            class="fsm-search"
            placeholder="搜索菜单…"
            clearable
            :prefix-icon="Search"
          />
          <el-button circle aria-label="关闭菜单" @click="close">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="fsm-body">
          <FullscreenMenuRail :categories="filteredCategories" :active="activeCategory" @select="handleSelect" />
          <FullscreenMenuContent
            :categories="filteredCategories"
            :active="activeCategory"
            :keyword="keyword"
            @navigate="navigate"
          />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Close } from '@element-plus/icons-vue'
import FullscreenMenuRail from '@/components/FullscreenMenuRail.vue'
import FullscreenMenuContent from '@/components/FullscreenMenuContent.vue'
import { useFullscreenMenuData } from '@/composables/useFullscreenMenuData'
import type { FullscreenMenuItem } from '@/composables/useFullscreenMenuData'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const router = useRouter()
const { keyword, filteredCategories } = useFullscreenMenuData()

const activeCategory = ref<string | number>('all')
const searchInputRef = ref<{ focus: () => void } | null>(null)

const close = () => emit('update:modelValue', false)

const navigate = (item: FullscreenMenuItem) => {
  close()
  if (item.isExternal) {
    window.open(item.fullPath, '_blank', 'noopener')
  } else {
    router.push(item.fullPath)
  }
}

const handleSelect = (id: 'all' | 'home' | number) => {
  if (id === 'home') {
    navigate({ id: 0, title: '首页', icon: '', fullPath: '/dashboard', isExternal: false })
    return
  }
  activeCategory.value = id
}

const focusSearch = () => searchInputRef.value?.focus()

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') close()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      keyword.value = ''
      activeCategory.value = 'all'
      window.addEventListener('keydown', onKeydown, true)
      document.documentElement.style.overflow = 'hidden'
    } else {
      window.removeEventListener('keydown', onKeydown, true)
      document.documentElement.style.overflow = ''
    }
  }
)

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown, true)
  document.documentElement.style.overflow = ''
})
</script>

<style scoped lang="scss">
.fsm-overlay {
  position: fixed;
  inset: 0;
  z-index: calc(var(--el-index-popper, 2000) + 10);
  display: flex;
  flex-direction: column;
  background: var(--app-fsm-bg, var(--el-bg-color));
}

.fsm-topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid var(--app-fsm-border, var(--el-border-color-lighter));
}

.fsm-topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.fsm-search {
  flex: 1;
  max-width: 360px;
}

.fsm-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

.fsm-enter-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fsm-leave-active {
  transition: opacity 0.12s ease;
}

.fsm-enter-from,
.fsm-leave-to {
  opacity: 0;
}

.fsm-enter-from {
  transform: translateY(-10px);
}
</style>
