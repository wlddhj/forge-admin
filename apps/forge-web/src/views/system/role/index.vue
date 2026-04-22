<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="角色名称">
          <el-input v-model="queryParams.roleName" placeholder="请输入角色名称" clearable />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="queryParams.roleCode" placeholder="请输入角色编码" clearable />
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
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">角色列表</span>
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
        <el-form-item label="角色名称">
          <el-input v-model="queryParams.roleName" placeholder="请输入角色名称" clearable />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="queryParams.roleCode" placeholder="请输入角色编码" clearable />
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
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增角色
          </el-button>
          <el-button v-permission="'system:role:export'" type="success" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysRoleTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />

        <!-- 角色名称 -->
        <vxe-column field="roleName" title="角色名称" width="180" />

        <!-- 角色编码 -->
        <vxe-column field="roleCode" title="角色编码" width="180" />

        <!-- 描述（桌面端） -->
        <vxe-column v-if="!isMobile" field="description" title="描述" min-width="200" />

        <!-- 数据权限 -->
        <vxe-column title="数据权限" width="140">
          <template #default="{ row }">
            <dict-value :dict-type="DICT_TYPE.SYS_DATA_SCOPE" :value="row.dataScope" />
          </template>
        </vxe-column>

        <!-- 状态 -->
        <vxe-column title="状态" width="100">
          <template #default="{ row }">
            <dict-value :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
          </template>
        </vxe-column>

        <!-- 创建时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click.stop="handleAssignMenus(row)">分配菜单</el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(row)" :disabled="row.isFixed === 1">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.roleName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button size="small" @click.stop="handleAssignMenus(item)">分配菜单</el-button>
        <el-button size="small" type="danger" @click.stop="handleDelete(item)" :disabled="item.isFixed === 1">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="dialog-form-responsive" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="formData.roleCode" placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="数据权限" prop="dataScope">
          <el-select v-model="formData.dataScope" placeholder="请选择数据权限范围" style="width: 100%" @change="handleDataScopeChange">
            <el-option
              v-for="item in dataScopeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
          <div class="form-tip">
            <p>权限范围说明：</p>
            <p>• 全部数据权限：可查看所有数据</p>
            <p>• 自定义数据权限：可查看指定部门的数据</p>
            <p>• 本部门数据权限：仅可查看本部门数据</p>
            <p>• 本部门及以下：可查看本部门及子部门数据</p>
            <p>• 仅本人数据权限：仅可查看自己创建的数据</p>
          </div>
        </el-form-item>
        <el-form-item v-if="formData.dataScope === '2'" label="选择部门" prop="deptIds">
          <el-tree
            ref="deptTreeRef"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            show-checkbox
            default-expand-all
            :default-checked-keys="formData.deptIds"
            style="border: 1px solid #dcdfe6; border-radius: 4px; padding: 8px; max-height: 300px; overflow-y: auto;"
          />
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

    <!-- 分配菜单抽屉 -->
    <el-drawer v-model="menuDialogVisible" title="分配菜单" :size="isMobile ? '85%' : '400px'">
      <el-alert
        title="请勾选该角色可以访问的菜单"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-tree
        ref="treeRef"
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
      />
      <template #footer>
        <div style="display: flex; gap: 12px">
          <el-button @click="menuDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="assignLoading" @click="handleConfirmAssignMenus">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import {
  getRoleList, addRole, updateRole, deleteRole,
  assignRoleMenus, getMenuTree, getRoleMenus, getDeptTree, exportRoles
} from '@/api/system'
import type { Role, RoleRequest, MenuTree, DeptTree } from '@/types/system'
import { DICT_TYPE } from '@/constants/dict'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useDict } from '@/composables/useDict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import DictValue from '@/components/DictValue.vue'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)
const { dictData: dataScopeOptions } = useDict(DICT_TYPE.SYS_DATA_SCOPE)

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<Role[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<Role | null>(null)

const queryParams = reactive({
  roleName: '',
  roleCode: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.roleName) count++
  if (queryParams.roleCode) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)

const formRef = ref<FormInstance>()
const formData = reactive<RoleRequest>({
  id: undefined,
  roleName: '',
  roleCode: '',
  description: '',
  dataScope: '5',
  deptIds: [],
  status: 1
})

const formRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const menuDialogVisible = ref(false)
const assignLoading = ref(false)
const menuTree = ref<MenuTree[]>([])
const treeRef = ref()
const currentRoleId = ref(0)

// 部门树相关
const deptTree = ref<DeptTree[]>([])
const deptTreeRef = ref()

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getRoleList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const getMenuTreeData = async () => {
  try {
    menuTree.value = await getMenuTree()
  } catch (error) {
    console.error('获取菜单树失败:', error)
  }
}

const getDeptTreeData = async () => {
  try {
    deptTree.value = await getDeptTree()
  } catch (error) {
    console.error('获取部门树失败:', error)
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.roleName = ''
  queryParams.roleCode = ''
  queryParams.status = undefined
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增角色'
  Object.assign(formData, {
    roleName: '',
    roleCode: '',
    description: '',
    dataScope: '5',
    deptIds: [],
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row: Role) => {
  cancelSelection()
  isEdit.value = true
  dialogTitle.value = '编辑角色'
  Object.assign(formData, {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    description: row.description,
    dataScope: row.dataScope || '5',
    deptIds: row.deptIds || [],
    status: row.status
  })
  dialogVisible.value = true
  // 如果是自定义权限，延迟设置树选中状态
  if (row.dataScope === '2') {
    nextTick(() => {
      setTimeout(() => {
        deptTreeRef.value?.setCheckedKeys(row.deptIds || [])
      }, 100)
    })
  }
}

// 数据权限变化处理
const handleDataScopeChange = (value: string) => {
  if (value !== '2') {
    formData.deptIds = []
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  // 如果是自定义数据权限，获取选中的部门
  if (formData.dataScope === '2') {
    const checkedKeys = deptTreeRef.value?.getCheckedKeys() || []
    formData.deptIds = checkedKeys as number[]
  }

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateRole(formData)
      ElMessage.success('更新成功')
    } else {
      await addRole(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

const handleAssignMenus = async (row: Role) => {
  cancelSelection()
  currentRoleId.value = row.id
  menuDialogVisible.value = true
  try {
    const menuIds = await getRoleMenus(row.id)
    setTimeout(() => {
      treeRef.value?.setCheckedKeys(menuIds || [])
    }, 100)
  } catch (error) {
    console.error('获取角色菜单失败:', error)
  }
}

const handleConfirmAssignMenus = async () => {
  assignLoading.value = true
  try {
    const checkedKeys = treeRef.value?.getCheckedKeys() || []
    await assignRoleMenus(currentRoleId.value, checkedKeys as number[])
    ElMessage.success('分配成功')
    menuDialogVisible.value = false
  } finally {
    assignLoading.value = false
  }
}

const handleDelete = async (row: Role) => {
  try {
    await ElMessageBox.confirm(`确定要删除角色 "${row.roleName}" 吗？`, '警告', { type: 'warning' })
    await deleteRole([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: Role | null }) => {
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

// 导出角色
const handleExport = async () => {
  try {
    await exportRoles(queryParams)
    ElMessage.success('导出成功')
  } catch (e) {
    console.error('导出失败', e)
  }
}

onMounted(() => {
  getList()
  getMenuTreeData()
  getDeptTreeData()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }

  .form-tip {
    margin-top: 5px;
    font-size: 12px;
    color: #909399;
    line-height: 1.6;

    p {
      margin: 2px 0;
    }
  }
}
</style>