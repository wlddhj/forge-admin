<template>
  <el-drawer
    v-model="drawerVisible"
    title="筛选"
    direction="btt"
    :size="'70%'"
    class="mobile-search-drawer"
  >
    <el-form :model="formData" label-width="80px" class="search-form-drawer">
      <slot name="form-items"></slot>
    </el-form>
    <template #footer>
      <div class="search-drawer-footer">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  modelValue: boolean
  formData: Record<string, any>
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'search'): void
  (e: 'reset'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const handleSearch = () => {
  emit('search')
  drawerVisible.value = false
}

const handleReset = () => {
  emit('reset')
  drawerVisible.value = false
}
</script>

<style scoped lang="scss">
.mobile-search-drawer {
  .search-form-drawer {
    padding: 0 15px;

    :deep(.el-form-item) {
      margin-bottom: 15px;
    }

    :deep(.el-form-item__label) {
      display: block;
      text-align: left;
      line-height: 1.5;
    }

    :deep(.el-select),
    :deep(.el-cascader),
    :deep(.el-date-editor) {
      width: 100%;
    }
  }

  .search-drawer-footer {
    display: flex;
    gap: 10px;
    padding: 10px 15px;
    border-top: 1px solid var(--el-border-color-lighter);

    .el-button {
      flex: 1;
      height: 44px;
      font-size: 14px;
    }
  }
}

:deep(.el-drawer__header) {
  padding: 15px 20px;
  margin-bottom: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.el-drawer__body) {
  padding: 15px 0;
  overflow-y: auto;
}
</style>
