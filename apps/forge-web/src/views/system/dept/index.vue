<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="部门名称">
          <el-input v-model="queryParams.deptName" placeholder="请输入部门名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">部门列表</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="部门名称">
          <el-input v-model="queryParams.deptName" placeholder="请输入部门名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span v-if="!isMobile">部门列表</span>
          <div v-if="!isMobile" class="header-btns">
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增部门
            </el-button>
            <el-button @click="handleToggleExpand">
              <el-icon><Fold v-if="allExpanded" /><Expand v-else /></el-icon>
              {{ allExpanded ? '全部折叠' : '全部展开' }}
            </el-button>
          </div>
        </div>
      </template>

      <div class="table-responsive">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="tableData"
          border
          stripe
          row-key="id"
          :tree-props="{ children: 'children' }"
          :default-expand-all="!isMobile"
          :row-class-name="getRowClassName"
          @row-click="handleRowClick"
        >
          <el-table-column prop="deptName" label="部门名称" width="200" />
          <el-table-column prop="leader" label="负责人" width="120" v-if="!isMobile" />
          <el-table-column prop="phone" label="联系电话" width="150" v-if="!isMobile" />
          <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="sortOrder" label="排序" width="80" v-if="!isMobile" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <DictValue :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" v-if="!isMobile">
            <template #default="{ row }">
              {{ formatDateTime(row.createTime) }}
            </template>
          </el-table-column>
          <!-- 桌面端操作列 -->
          <el-table-column v-if="!isMobile" label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button type="primary" link @click="handleAddChild(row)">新增</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.deptName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button size="small" type="primary" @click.stop="handleAddChild(item)">新增子部门</el-button>
        <el-button size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="dialog-form-responsive" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="formData.parentId"
            :data="deptTreeOptions"
            :props="{ label: 'deptName', value: 'id' }"
            placeholder="请选择上级部门（不选则为顶级部门）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="formData.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="formData.leader" placeholder="请输入负责人姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio
              v-for="item in statusOptions"
              :key="item.dictValue"
              :value="Number(item.dictValue)"
            >
              {{ item.dictLabel }}
            </el-radio>
          </el-radio-group>
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
import { reactive, ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Fold, Expand } from '@element-plus/icons-vue'
import { getDeptList, getDeptTree, addDept, updateDept, deleteDept } from '@/api/system'
import type { Dept, DeptTree, DeptRequest } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import DictValue from '@/components/DictValue.vue'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

const loading = ref(false)
const tableData = ref<DeptTree[]>([])
const tableRef = ref()
const allExpanded = ref(true)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<DeptTree | null>(null)

const queryParams = reactive({
  deptName: '',
  status: undefined as number | undefined
})

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.deptName) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)

const formRef = ref<FormInstance>()
const formData = reactive<DeptRequest>({
  id: undefined,
  deptName: '',
  parentId: undefined,
  leader: '',
  phone: '',
  email: '',
  sortOrder: 0,
  status: 1
})

const formRules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

const deptTreeOptions = ref<DeptTree[]>([])

const getList = async () => {
  loading.value = true
  try {
    const res = await getDeptList(queryParams)
    tableData.value = buildDeptTree(res)
    allExpanded.value = true
    nextTick(() => expandAll())
  } finally {
    loading.value = false
  }
}

const getDeptTreeData = async () => {
  try {
    const res = await getDeptTree()
    deptTreeOptions.value = [{ id: 0, deptName: '顶级部门', children: res } as any]
  } catch (error) {
    console.error('获取部门树失败:', error)
  }
}

const buildDeptTree = (flatList: Dept[]): DeptTree[] => {
  const map = new Map<number, DeptTree>()
  const tree: DeptTree[] = []

  flatList.forEach(item => {
    map.set(item.id!, { ...item, children: [] })
  })

  flatList.forEach(item => {
    const node = map.get(item.id!)!
    if (!item.parentId || item.parentId === 0) {
      tree.push(node)
    } else {
      const parent = map.get(item.parentId)
      if (parent) {
        if (!parent.children) parent.children = []
        parent.children.push(node)
      } else {
        tree.push(node)
      }
    }
  })

  return tree
}

const expandAll = () => {
  if (!tableRef.value) return
  const expandRows = (rows: DeptTree[]) => {
    rows.forEach(row => {
      if (row.children && row.children.length > 0) {
        tableRef.value?.toggleRowExpansion(row, true)
        expandRows(row.children)
      }
    })
  }
  expandRows(tableData.value)
}

const collapseAll = () => {
  if (!tableRef.value) return
  const collapseRows = (rows: DeptTree[]) => {
    rows.forEach(row => {
      if (row.children && row.children.length > 0) {
        tableRef.value?.toggleRowExpansion(row, false)
        collapseRows(row.children)
      }
    })
  }
  collapseRows(tableData.value)
}

const handleToggleExpand = () => {
  allExpanded.value = !allExpanded.value
  if (allExpanded.value) {
    expandAll()
  } else {
    collapseAll()
  }
}

const handleQuery = () => getList()
const handleReset = () => {
  queryParams.deptName = ''
  queryParams.status = undefined
  getList()
}

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  handleQuery()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增部门'
  Object.assign(formData, {
    id: undefined, deptName: '', parentId: undefined, leader: '',
    phone: '', email: '', sortOrder: 0, status: 1
  })
  dialogVisible.value = true
}

const handleAddChild = (row: DeptTree) => {
  cancelSelection()
  isEdit.value = false
  dialogTitle.value = '新增子部门'
  Object.assign(formData, {
    id: undefined, deptName: '', parentId: row.id, leader: '',
    phone: '', email: '', sortOrder: 0, status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row: DeptTree) => {
  cancelSelection()
  isEdit.value = true
  dialogTitle.value = '编辑部门'
  Object.assign(formData, {
    id: row.id, deptName: row.deptName, parentId: row.parentId, leader: row.leader,
    phone: row.phone, email: row.email, sortOrder: row.sortOrder, status: row.status
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateDept(formData)
      ElMessage.success('更新成功')
    } else {
      await addDept(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
    getDeptTreeData()
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => formRef.value?.resetFields()

const handleDelete = async (row: DeptTree) => {
  if (row.children && row.children.length > 0) {
    ElMessage.warning('该部门存在子部门，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除部门 "${row.deptName}" 吗？`, '警告', { type: 'warning' })
    await deleteDept(row.id!)
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
    getDeptTreeData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

// 获取行样式名
const getRowClassName = ({ row }: { row: DeptTree }) => {
  if (isMobile.value && selectedRow.value?.id === row.id) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: DeptTree) => {
  if (isMobile.value) {
    selectedRow.value = selectedRow.value?.id === row.id ? null : row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
}

onMounted(() => {
  getList()
  getDeptTreeData()
})
</script>

<style scoped lang="scss">
.app-container {
  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }
}
</style>
