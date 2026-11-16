<template>
  <el-card shadow="never" class="aside-tree-card" :style="{ height: height ? height + 'px' : undefined }">
    <div class="tree-header">
      <span>合同分类</span>
      <el-button v-if="canCreate" v-permission="'contract:category:create'" type="primary" link size="small"
        @click="quickAddVisible = true">
        <el-icon><Plus /></el-icon>新增
      </el-button>
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

  <!-- 快捷新增（完整维护入口在合同分类页） -->
  <el-dialog v-model="quickAddVisible" title="快捷新增分类" width="420px" append-to-body destroy-on-close>
    <el-form ref="quickFormRef" :model="quickForm" :rules="quickRules" label-width="80px">
      <el-form-item label="父分类">
        <el-tree-select
          v-model="quickForm.parentId"
          :data="parentOptions"
          :props="{ label: 'categoryName', children: 'children', disabled: 'disabled' }"
          node-key="id"
          check-strictly
          default-expand-all
          placeholder="不选=顶级分类"
          clearable
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="分类名称" prop="categoryName">
        <el-input v-model="quickForm.categoryName" maxlength="100" placeholder="如：劳动合同" />
      </el-form-item>
      <el-form-item label="分类编码" prop="categoryCode">
        <el-input v-model="quickForm.categoryCode" maxlength="32" placeholder="如 LD（叶子编码参与合同编号）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="quickAddVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmQuickAdd">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { categoryApi } from '@/api/contract/category'
import { hasPermission } from '@/directives/permission'
import type { CategoryNode } from '@/types/contract'

defineOptions({ name: 'CategoryAsideTree' })

/** v-model：选中分类 id（undefined=全部） */
const selectedId = defineModel<string | undefined>()
const emit = defineEmits<{ select: [] }>()

const canCreate = hasPermission('contract:category:create')

const treeRef = ref()
const categories = ref<CategoryNode[]>([])
const loading = ref(false)

/** 「全部」节点与真实顶级分类平铺；停用节点标注灰显 */
const treeData = computed(() => [
  { id: 'all', categoryName: '全部', children: [], $root: true },
  ...categories.value
])

/** 父候选：树（快捷新增不做叶子限制，可挂在任意节点下；仅叶子能挂模板的约束在模板表单侧） */
const parentOptions = computed(() => categories.value)

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

// ========== 快捷新增 ==========
const quickAddVisible = ref(false)
const saving = ref(false)
const quickFormRef = ref<FormInstance | null>(null)
const quickForm = ref({
  parentId: undefined as string | undefined,
  categoryName: '',
  categoryCode: ''
})
const quickRules: FormRules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  categoryCode: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{1,32}$/, message: '仅限 1-32 位字母数字下划线', trigger: 'blur' }
  ]
}

const confirmQuickAdd = async () => {
  await quickFormRef.value?.validate()
  saving.value = true
  try {
    await categoryApi.create({
      parentId: quickForm.value.parentId ?? '0',
      categoryName: quickForm.value.categoryName,
      categoryCode: quickForm.value.categoryCode.trim().toUpperCase()
    })
    ElMessage.success('分类已创建')
    quickAddVisible.value = false
    quickForm.value = { parentId: undefined, categoryName: '', categoryCode: '' }
    loadTree()
  } finally {
    saving.value = false
  }
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
