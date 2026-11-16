<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="分类名称">
          <el-input v-model="filterName" placeholder="请输入分类名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 树形表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'contract:category:create'" type="primary" @click="openForm(null, 'child')">
            <el-icon><Plus /></el-icon>新增分类
          </el-button>
          <el-button @click="allExpanded = !allExpanded; toggleExpand()">
            <el-icon><component :is="allExpanded ? 'Fold' : 'Expand'" /></el-icon>
            {{ allExpanded ? '全部折叠' : '全部展开' }}
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="contractCategoryTable"
        ref="tableRef"
        :custom-config="{ mode: 'modal' }"
        :data="filteredTree"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true, keyField: 'id' }"
        :tree-config="{ expandAll: true, indent: 20, transform: true, rowField: 'id', parentField: 'parentId' }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" align="center" />
        <vxe-column field="categoryName" title="分类名称" min-width="220" tree-node />
        <vxe-column field="categoryCode" title="分类编码" width="140">
          <template #default="{ row }">
            <span>{{ row.categoryCode }}</span>
            <el-tag v-if="row.leaf" type="success" size="small" style="margin-left: 6px">可挂模板</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="sortOrder" title="排序" width="80" align="center" />
        <vxe-column title="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="remark" title="备注" min-width="140" />
        <vxe-column title="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'contract:category:create'" type="primary" link size="small"
              @click="openForm(row, 'child')">
              加子级
            </el-button>
            <el-button v-permission="'contract:category:update'" type="primary" link size="small"
              @click="openForm(row, 'edit')">
              修改
            </el-button>
            <el-button v-permission="'contract:category:delete'" type="danger" link size="small"
              @click="remove(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="formTitle" width="480px" destroy-on-close append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="父分类">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'categoryName', children: 'children' }"
            check-strictly
            default-expand-all
            placeholder="不选=顶级分类"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" maxlength="100" placeholder="如：劳动合同" />
        </el-form-item>
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="form.categoryCode" maxlength="32" placeholder="字母数字下划线，如 LD" />
          <div class="form-tip">租户内唯一；模板仅可挂叶子节点，叶子编码参与合同编号 CT-编码-日期-序列</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import { formatDateTime } from '@/utils/dateFormat'
import { categoryApi } from '@/api/contract/category'
import type { CategoryNode } from '@/types/contract'

defineOptions({ name: 'ContractCategory' })

// 无分页页豁免口径：高度计算不预留分页空白（spec-guard R6）
const { tableHeight } = useTableHeight({ hasPagination: false })
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const allExpanded = ref(true)

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  loadTree()
})

// ========== 树数据 ==========
const loading = ref(false)
const treeData = ref<CategoryNode[]>([])
const queryParams = reactive({ status: undefined as number | undefined })
const filterName = ref('')

const loadTree = async () => {
  loading.value = true
  try {
    treeData.value = await categoryApi.tree(queryParams.status)
  } finally {
    loading.value = false
  }
}

/** 名称关键字过滤（命中节点连带父链保留，前端轻量过滤） */
const filteredTree = computed<CategoryNode[]>(() => {
  if (!filterName.value.trim()) return treeData.value
  const keyword = filterName.value.trim()
  const walk = (nodes: CategoryNode[]): CategoryNode[] => {
    const result: CategoryNode[] = []
    for (const node of nodes) {
      const children = walk(node.children ?? [])
      if (node.categoryName.includes(keyword) || node.categoryCode.includes(keyword) || children.length) {
        result.push({ ...node, children })
      }
    }
    return result
  }
  return walk(treeData.value)
})

const handleQuery = () => loadTree()

const handleReset = () => {
  queryParams.status = undefined
  filterName.value = ''
  handleQuery()
}

const toggleExpand = () => {
  const table = tableRef.value
  if (!table) return
  table.setAllTreeExpand(allExpanded.value)
}

// ========== 表单 ==========
const formVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)
const editingId = ref<string | null>(null)
const form = reactive({
  parentId: undefined as string | undefined,
  categoryName: '',
  categoryCode: '',
  sortOrder: 0,
  status: 1,
  remark: ''
})
/** 父分类候选：编辑时排除自身子树（防环由服务端双保险） */
const parentOptions = ref<CategoryNode[]>([])

const formTitle = computed(() =>
  editingId.value ? '修改分类' : form.parentId ? '新增子分类' : '新增分类')

const rules: FormRules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  categoryCode: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{1,32}$/, message: '仅限 1-32 位字母数字下划线', trigger: 'blur' }
  ]
}

const openForm = async (row: CategoryNode | null, mode: 'edit' | 'child') => {
  const all = await categoryApi.treeAll()
  if (row && mode === 'edit') {
    editingId.value = row.id
    form.parentId = row.parentId === '0' ? undefined : row.parentId
    form.categoryName = row.categoryName
    form.categoryCode = row.categoryCode
    form.sortOrder = row.sortOrder
    form.status = row.status
    form.remark = row.remark ?? ''
    parentOptions.value = excludeSelfTree(all, row.id)
  } else {
    editingId.value = null
    form.parentId = row ? row.id : undefined
    form.categoryName = ''
    form.categoryCode = ''
    form.sortOrder = 0
    form.status = 1
    form.remark = ''
    parentOptions.value = all
  }
  formVisible.value = true
}

/** 从父候选中剔除自身及其子树 */
const excludeSelfTree = (nodes: CategoryNode[], selfId: string): CategoryNode[] =>
  nodes
    .filter(n => n.id !== selfId)
    .map(n => ({ ...n, children: n.children ? excludeSelfTree(n.children, selfId) : n.children }))

const confirm = async () => {
  await formRef.value?.validate()
  form.categoryCode = form.categoryCode.trim().toUpperCase()
  saving.value = true
  try {
    if (editingId.value) {
      await categoryApi.update({
        id: editingId.value,
        parentId: form.parentId ?? '0',
        categoryName: form.categoryName,
        categoryCode: form.categoryCode,
        sortOrder: form.sortOrder,
        status: form.status,
        remark: form.remark
      })
      ElMessage.success('分类已更新')
    } else {
      await categoryApi.create({
        parentId: form.parentId ?? '0',
        categoryName: form.categoryName,
        categoryCode: form.categoryCode,
        sortOrder: form.sortOrder,
        status: form.status,
        remark: form.remark
      })
      ElMessage.success('分类已创建')
    }
    formVisible.value = false
    loadTree()
  } finally {
    saving.value = false
  }
}

const remove = async (row: CategoryNode) => {
  await ElMessageBox.confirm(
    `确定删除分类「${row.categoryName}」？存在子分类或已挂载模板时将无法删除`,
    '提示',
    { type: 'warning' }
  )
  await categoryApi.remove([row.id])
  ElMessage.success('已删除')
  loadTree()
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  margin-top: 4px;
}
</style>
