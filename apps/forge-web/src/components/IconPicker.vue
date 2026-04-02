<template>
  <el-popover
    :visible="popoverVisible"
    placement="bottom-start"
    :width="400"
    trigger="click"
  >
    <template #reference>
      <el-input
        :model-value="modelValue"
        placeholder="请选择图标"
        readonly
        style="cursor: pointer"
        @click="popoverVisible = true"
      >
        <template #prefix>
          <el-icon v-if="modelValue" style="vertical-align: middle">
            <component :is="modelValue" />
          </el-icon>
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
        prefix-icon="Search"
        size="small"
        style="margin-bottom: 10px"
      />
      <el-scrollbar height="280px">
        <div class="icon-grid">
          <div
            v-for="name in filteredIcons"
            :key="name"
            class="icon-item"
            :class="{ active: modelValue === name }"
            :title="name"
            @click="handleSelect(name)"
          >
            <el-icon :size="20">
              <component :is="name" />
            </el-icon>
            <span class="icon-name">{{ name }}</span>
          </div>
        </div>
        <div v-if="filteredIcons.length === 0" class="no-result">未找到图标</div>
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import * as Icons from '@element-plus/icons-vue'

defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const popoverVisible = ref(false)
const searchText = ref('')

const iconNames = Object.keys(Icons).filter(k => k !== 'default')

const filteredIcons = computed(() => {
  if (!searchText.value) return iconNames
  const keyword = searchText.value.toLowerCase()
  return iconNames.filter(name => name.toLowerCase().includes(keyword))
})

const handleSelect = (name: string) => {
  emit('update:modelValue', name)
  popoverVisible.value = false
  searchText.value = ''
}

const handleClear = () => {
  emit('update:modelValue', '')
}
</script>

<style scoped lang="scss">
.icon-picker {
  .icon-grid {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 4px;
  }

  .icon-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 8px 4px;
    border-radius: 4px;
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover {
      background-color: var(--el-color-primary-light-9);
    }

    &.active {
      background-color: var(--el-color-primary-light-7);
      color: var(--el-color-primary);
    }

    .icon-name {
      font-size: 10px;
      margin-top: 4px;
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
    color: #909399;
    padding: 20px 0;
  }
}

.clear-icon {
  cursor: pointer;
  color: #c0c4cc;

  &:hover {
    color: #909399;
  }
}
</style>
