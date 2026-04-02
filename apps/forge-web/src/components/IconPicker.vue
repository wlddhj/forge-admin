<template>
  <el-popover
    :visible="popoverVisible"
    placement="bottom-start"
    :width="450"
    @update:visible="(val: boolean) => popoverVisible = val"
  >
    <template #reference>
      <el-input
        :model-value="modelValue"
        placeholder="请选择图标"
        readonly
        style="cursor: pointer"
        @click="popoverVisible = !popoverVisible"
      >
        <template #prefix>
          <IconPreview v-if="modelValue" :icon="modelValue" />
        </template>
        <template #suffix>
          <el-icon v-if="modelValue" class="clear-icon" @click.stop="handleClear">
            <CircleClose />
          </el-icon>
        </template>
      </el-input>
    </template>

    <div class="icon-picker">
      <el-input
        v-model="searchText"
        placeholder="搜索图标..."
        clearable
        size="small"
        style="margin-bottom: 10px"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-tabs v-model="activeType" class="icon-tabs" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="tab in tabsList"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        />
      </el-tabs>

      <el-scrollbar height="220px">
        <div class="icon-grid">
          <div
            v-for="name in pagedIcons"
            :key="name"
            class="icon-item"
            :class="{ active: modelValue === activeType + name }"
            :title="activeType + name"
            @click="handleSelect(name)"
          >
            <IconPreview :icon="activeType + name" :size="20" />
            <span class="icon-name">{{ name }}</span>
          </div>
        </div>
        <div v-if="filteredIcons.length === 0" class="no-result">未找到图标</div>
      </el-scrollbar>

      <div class="icon-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredIcons.length"
          background
          layout="prev, pager, next"
          small
        />
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Search, CircleClose } from '@element-plus/icons-vue'
import { iconData } from './IconPickerData'
import IconPreview from './IconPreview.vue'

defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const tabsList = [
  { label: 'Element Plus', name: 'ep:' },
  { label: 'Font Awesome 4', name: 'fa:' },
  { label: 'Font Awesome 5 Solid', name: 'fa-solid:' }
]

const popoverVisible = ref(false)
const searchText = ref('')
const activeType = ref('ep:')
const currentPage = ref(1)
const pageSize = 96

// 搜索过滤
const filteredIcons = computed(() => {
  const list = iconData[activeType.value] || []
  if (!searchText.value) return list
  const keyword = searchText.value.toLowerCase()
  return list.filter(name => name.toLowerCase().includes(keyword))
})

// 分页
const pagedIcons = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredIcons.value.slice(start, start + pageSize)
})

// 切换标签页
const handleTabChange = () => {
  currentPage.value = 1
  searchText.value = ''
}

// 选择图标
const handleSelect = (name: string) => {
  emit('update:modelValue', activeType.value + name)
  popoverVisible.value = false
  searchText.value = ''
}

// 清空
const handleClear = () => {
  emit('update:modelValue', '')
}

// 搜索时重置页码
watch(searchText, () => {
  currentPage.value = 1
})

// 根据已有值自动切换到对应标签页
watch(() => popoverVisible, (visible) => {
  if (visible) {
    // popoverVisible 是 ref，需要用 .value
  }
})
</script>

<style scoped lang="scss">
.icon-picker {
  .icon-tabs {
    margin-bottom: 8px;

    :deep(.el-tabs__header) {
      margin-bottom: 0;
    }

    :deep(.el-tabs__item) {
      height: 30px;
      font-size: 12px;
      line-height: 30px;
    }

    :deep(.el-tabs__nav-wrap::after) {
      height: 1px;
    }
  }

  .icon-grid {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    gap: 4px;
  }

  .icon-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 6px 2px;
    border-radius: 4px;
    cursor: pointer;
    border: 1px solid transparent;
    transition: all 0.2s;

    &:hover {
      background-color: var(--el-color-primary-light-9);
      border-color: var(--el-color-primary-light-5);
    }

    &.active {
      background-color: var(--el-color-primary-light-7);
      border-color: var(--el-color-primary);
      color: var(--el-color-primary);
    }

    .icon-name {
      font-size: 10px;
      margin-top: 2px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      width: 100%;
      text-align: center;
      line-height: 1.2;
    }
  }

  .no-result {
    text-align: center;
    color: var(--el-text-color-secondary);
    padding: 20px 0;
  }

  .icon-pagination {
    margin-top: 8px;
    display: flex;
    justify-content: center;

    :deep(.el-pagination) {
      .btn-prev,
      .btn-next,
      .el-pager li {
        min-width: 24px;
        height: 24px;
        line-height: 24px;
      }
    }
  }
}

.clear-icon {
  cursor: pointer;
  color: var(--el-text-color-placeholder);

  &:hover {
    color: var(--el-text-color-secondary);
  }
}
</style>
