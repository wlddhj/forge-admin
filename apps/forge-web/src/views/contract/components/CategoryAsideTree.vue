<template>
  <el-card shadow="never" class="aside-tree-card" :style="{ height: height ? height + 'px' : undefined }">
    <div class="tree-header">
      <span>合同分类</span>
    </div>
    <el-tree
      ref="treeRef"
      :data="treeData"
      :props="{ label: 'categoryName', children: 'children' }"
      node-key="id"
      highlight-current
      default-expand-all
      :expand-on-click-node="false"
      @node-click="handleNodeClick"
    />
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { categoryApi } from '@/api/contract/category'
import type { CategoryNode } from '@/types/contract'

defineOptions({ name: 'CategoryAsideTree' })

/** v-model：选中分类 id（undefined=全部） */
const selectedId = defineModel<string | undefined>()
const emit = defineEmits<{ select: [] }>()

const treeRef = ref()
const categories = ref<CategoryNode[]>([])
const loading = ref(false)

/** 「全部」节点与真实顶级分类平铺；停用节点标注灰显 */
const treeData = computed(() => [
  { id: 'all', categoryName: '全部', children: [], $root: true },
  ...categories.value
])

const loadTree = async () => {
  loading.value = true
  try {
    categories.value = await categoryApi.treeAll()
  } finally {
    loading.value = false
  }
}

const handleNodeClick = (data: { id: string; $root?: boolean }) => {
  selectedId.value = data.$root ? undefined : data.id
  emit('select')
}


/** 左侧树高度：铺满视口剩余区（口径与 model 页一致） */
const height = ref<number | undefined>(undefined)
const calcHeight = () => {
  const headerH = document.querySelector('.layout-header')?.getBoundingClientRect().height || 60
  const tabsH = document.querySelector('.tabs-view')?.getBoundingClientRect().height || 0
  height.value = Math.max(window.innerHeight - headerH - tabsH - 23, 200)
}
onMounted(() => {
  loadTree()
  calcHeight()
  window.addEventListener('resize', calcHeight)
})
</script>

<style scoped lang="scss">
.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
:deep(.el-tree-node__content) {
  height: 30px;
}
</style>
