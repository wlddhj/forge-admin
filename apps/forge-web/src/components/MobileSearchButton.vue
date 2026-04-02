<template>
  <div class="mobile-search-button" @click="handleClick">
    <el-icon class="search-icon"><Search /></el-icon>
    <el-badge v-if="badgeCount > 0" :value="badgeCount" class="search-badge" />
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

interface Props {
  badgeCount?: number
}

interface Emits {
  (e: 'click'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const handleClick = () => {
  emit('click')
}
</script>

<style scoped lang="scss">
.mobile-search-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;

  &:hover {
    background-color: var(--el-bg-color-page);
  }

  &:active {
    background-color: #e6e6e6;
  }

  .search-icon {
    font-size: 18px;
    color: var(--el-text-color-regular);
  }

  .search-badge {
    position: absolute;
    top: -4px;
    right: -4px;

    :deep(.el-badge__content) {
      background-color: var(--el-color-danger);
      border: 2px solid #fff;
    }
  }
}
</style>
