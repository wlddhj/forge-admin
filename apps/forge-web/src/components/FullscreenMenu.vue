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
const { keyword, categories, filteredCategories } = useFullscreenMenuData()

const FSM_CATEGORY_KEY = 'forge-admin-fsm-category'

const activeCategory = ref<string | number>('all')
const searchInputRef = ref<{ focus: () => void } | null>(null)

/** 恢复上次选中的分类；菜单权限变化导致 id 失效时回退 'all'。
 *  按 String 比较并以运行时 id 原样返回——后端 Long 序列化为字符串，
 *  与类型标注的 number 不符，数字字面量严格比较会失配 */
const restoreCategory = (): string | number => {
  try {
    const saved = localStorage.getItem(FSM_CATEGORY_KEY)
    if (saved === 'all') return 'all'
    const matched = categories.value.find(c => String(c.id) === saved)
    if (matched) return matched.id
  } catch {
    // localStorage 不可用时静默回退默认
  }
  return 'all'
}

const persistCategory = (id: string | number) => {
  try {
    localStorage.setItem(FSM_CATEGORY_KEY, String(id))
  } catch {
    // 存储失败不影响本次使用
  }
}

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
  persistCategory(id)
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
        activeCategory.value = restoreCategory()
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
