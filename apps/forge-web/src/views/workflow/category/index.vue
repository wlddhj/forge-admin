<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="分类名称">
          <el-input v-model="queryParams.categoryName" placeholder="请输入分类名称" clearable />
        </el-form-item>
        <el-form-item label="分类编码">
          <el-input v-model="queryParams.categoryCode" placeholder="请输入分类编码" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">流程分类</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button v-permission="'workflow:category:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="分类名称">
          <el-input v-model="queryParams.categoryName" placeholder="请输入分类名称" clearable />
        </el-form-item>
        <el-form-item label="分类编码">
          <el-input v-model="queryParams.categoryCode" placeholder="请输入分类编码" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'workflow:category:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增分类
          </el-button>
          <el-button @click="handleToggleExpand">
            <el-icon><component :is="allExpanded ? 'Fold' : 'Expand'" /></el-icon>
            {{ allExpanded ? '全部折叠' : '全部展开' }}
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 树形表格（平铺数据由 transform 按 parentId 组装） -->
      <vxe-table
        id="wfCategoryTable"
        ref="tableRef"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true, keyField: 'id' }"
        :tree-config="{ expandAll: !isMobile, indent: 20, transform: true, rowField: 'id', parentField: 'parentId' }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <!-- 分类名称 -->
        <vxe-column field="categoryName" title="分类名称" min-width="200" tree-node />

        <!-- 分类编码 -->
        <vxe-column field="categoryCode" title="分类编码" width="150" />

        <!-- 排序（桌面端） -->
        <vxe-column v-if="!isMobile" field="sortOrder" title="排序" width="80" align="center" />

        <!-- 状态 -->
        <vxe-column title="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </vxe-column>

        <!-- 备注（桌面端） -->
        <vxe-column v-if="!isMobile" field="remark" title="备注" min-width="150" />

        <!-- 创建时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'workflow:category:edit'" type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'workflow:category:add'" type="primary" link size="small" @click.stop="handleAddChild(row)">新增子分类</el-button>
            <el-button v-permission="'workflow:category:delete'" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.categoryName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button v-permission="'workflow:category:edit'" size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button v-permission="'workflow:category:add'" size="small" type="primary" @click.stop="handleAddChild(item)">新增子分类</el-button>
        <el-button v-permission="'workflow:category:delete'" size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="dialog-form-responsive" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="父分类">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentTreeOptions"
            :props="{ label: 'categoryName' }"
            node-key="id"
            placeholder="请选择父分类（不选则为顶级分类）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="formData.categoryName" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="formData.categoryCode" placeholder="请输入分类编码" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { categoryApi } from '@/api/workflow/category'
import type { WfCategory, CategoryRequest } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const { isMobile } = useResponsive()

// 表格高度自适应
const { tableHeight } = useTableHeight({ hasPagination: false })

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<WfCategory[]>([])
const allExpanded = ref(true)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<WfCategory | null>(null)

const queryParams = reactive({
  categoryName: '',
  categoryCode: '',
  status: undefined as number | undefined
})

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.categoryName) count++
  if (queryParams.categoryCode) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<CategoryRequest>({
  id: undefined,
  parentId: undefined,
  categoryName: '',
  categoryCode: '',
  sortOrder: 0,
  status: 1,
  remark: ''
})
const formRules: FormRules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请输入分类编码', trigger: 'blur' }]
}

/** 启用状态的分类树（父分类选择器数据源） */
const categoryTree = ref<WfCategory[]>([])

/** 递归剔除指定节点及其子树（编辑时禁止把自身/子孙选为父级） */
const filterOutSubtree = (nodes: WfCategory[], excludeId: number): WfCategory[] =>
  nodes
    .filter(node => node.id !== excludeId)
    .map(node => ({ ...node, children: filterOutSubtree(node.children ?? [], excludeId) }))

/** 父分类选项：合成顶级根节点 + 启用树（编辑时排除自身子树） */
const parentTreeOptions = computed(() => {
  const editingId = isEdit.value ? formData.id : undefined
  const children = editingId ? filterOutSubtree(categoryTree.value, editingId) : categoryTree.value
  return [{ id: 0, categoryName: '顶级分类', children }]
})

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

/** 获取分类列表（平铺，树形由 vxe-table transform 组装） */
const getList = async () => {
  loading.value = true
  try {
    const res = await categoryApi.list(queryParams)
    tableData.value = res
    allExpanded.value = !isMobile.value
    nextTick(() => {
      if (!isMobile.value && tableRef.value) {
        tableRef.value.setAllTreeExpand(true)
      }
    })
  } finally {
    loading.value = false
  }
}

/** 拉取启用分类树（弹窗每次打开都重新拉，避免新增/编辑后选项过期） */
const getCategoryTreeData = async () => {
  try {
    categoryTree.value = await categoryApi.listAll()
  } catch (error) {
    console.error('获取分类树失败:', error)
  }
}

const handleToggleExpand = () => {
  allExpanded.value = !allExpanded.value
  if (tableRef.value) {
    if (allExpanded.value) {
      tableRef.value.setAllTreeExpand(true)
    } else {
      tableRef.value.clearTreeExpand()
    }
  }
}

const handleQuery = () => getList()

const handleReset = () => {
  queryParams.categoryName = ''
  queryParams.categoryCode = ''
  queryParams.status = undefined
  getList()
}

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => {
  cancelSelection()
  isEdit.value = false
  dialogTitle.value = '新增分类'
  Object.assign(formData, { id: undefined, parentId: undefined, categoryName: '', categoryCode: '', sortOrder: 0, status: 1, remark: '' })
  dialogVisible.value = true
  getCategoryTreeData()
}

const handleAddChild = (row: WfCategory) => {
  cancelSelection()
  isEdit.value = false
  dialogTitle.value = '新增子分类'
  Object.assign(formData, { id: undefined, parentId: row.id, categoryName: '', categoryCode: '', sortOrder: 0, status: 1, remark: '' })
  dialogVisible.value = true
  getCategoryTreeData()
}

const handleEdit = (row: WfCategory) => {
  cancelSelection()
  isEdit.value = true
  dialogTitle.value = '编辑分类'
  Object.assign(formData, {
    id: row.id,
    parentId: row.parentId,
    categoryName: row.categoryName,
    categoryCode: row.categoryCode,
    sortOrder: row.sortOrder,
    status: row.status,
    remark: row.remark
  })
  dialogVisible.value = true
  getCategoryTreeData()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await categoryApi.update(formData)
      ElMessage.success('更新成功')
    } else {
      await categoryApi.add(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => formRef.value?.resetFields()

const handleDelete = async (row: WfCategory) => {
  cancelSelection()
  try {
    await ElMessageBox.confirm(`确定删除分类 "${row.categoryName}"？`, '警告', { type: 'warning' })
    await categoryApi.delete([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    // 用户取消
  }
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: WfCategory | null }) => {
  if (isMobile.value) {
    selectedRow.value = row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
  if (tableRef.value) {
    tableRef.value.clearCurrentRow()
  }
}

onMounted(() => getList())
</script>

<style scoped lang="scss">

</style>
