<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="queryParams.nickname" placeholder="请输入昵称" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable />
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
        <span class="title">用户列表</span>
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
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="queryParams.nickname" placeholder="请输入昵称" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable />
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
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom >
        <template #buttons>
          <el-button v-permission="'system:user:import'" type="warning" @click="handleImport">
            <el-icon><Upload /></el-icon>
            导入
          </el-button>
          <el-button v-permission="'system:user:export'" type="success" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
          <el-button v-permission="'system:user:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        :data="tableData"
        id="sysUserTable"
        :custom-config="{mode: 'modal'}"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :checkbox-config="{ highlight: true, range: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />

        <!-- 用户名 -->
        <vxe-column field="username" title="用户名" width="120" />

        <!-- 昵称 -->
        <vxe-column field="nickname" title="昵称" width="120" />

        <!-- 手机号 -->
        <vxe-column field="phone" title="手机号" width="130" />

        <!-- 部门 -->
        <vxe-column field="deptName" title="部门" width="120" />

        <!-- 邮箱（桌面端） -->
        <vxe-column v-if="!isMobile" field="email" title="邮箱" width="180" />

        <!-- 岗位 -->
        <vxe-column title="岗位" width="150">
          <template #default="{ row }">
            <template v-if="row.positionNames && row.positionNames.length > 0">
              <el-tag v-for="(name, index) in row.positionNames.slice(0, 2)" :key="index" size="small" style="margin-right: 4px;">
                {{ name }}
              </el-tag>
              <span v-if="row.positionNames.length > 2" style="color: #909399;">+{{ row.positionNames.length - 2 }}</span>
            </template>
            <span v-else style="color: #909399;">-</span>
          </template>
        </vxe-column>

        <!-- 角色 -->
        <vxe-column title="角色" width="150">
          <template #default="{ row }">
            <template v-if="row.roleNames && row.roleNames.length > 0">
              <el-tag v-for="(name, index) in row.roleNames.slice(0, 2)" :key="index" size="small" style="margin-right: 4px;">
                {{ name }}
              </el-tag>
              <span v-if="row.roleNames.length > 2" style="color: #909399;">+{{ row.roleNames.length - 2 }}</span>
            </template>
            <span v-else style="color: #909399;">-</span>
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
        <vxe-column v-if="!isMobile" title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:user:edit'" type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:user:resetPwd'" type="warning" link size="small" @click.stop="handleResetPwd(row)">重置密码</el-button>
            <el-button v-permission="'system:user:delete'" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
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
      :item-title="selectedRow?.nickname || selectedRow?.username"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button v-permission="'system:user:edit'" size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button v-permission="'system:user:resetPwd'" size="small" @click.stop="handleResetPwd(item)">重置密码</el-button>
        <el-button v-permission="'system:user:delete'" size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="dialog-form-responsive" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-tree-select
            v-model="form.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', value: 'id' }"
            placeholder="请选择部门"
            check-strictly
            clearable
          />
        </el-form-item>
        <el-form-item label="岗位" prop="positionIds">
          <el-select v-model="form.positionIds" multiple placeholder="请选择岗位">
            <el-option v-for="pos in positionList" :key="pos.id" :label="pos.positionName" :value="pos.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择角色">
            <el-option v-for="role in roleList" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
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

    <!-- 导入对话框 -->
    <UserImportDialog ref="importDialogRef" @success="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import {
  getUserList, getUser, addUser, updateUser, deleteUser, resetPassword, exportUsers
} from '@/api/system'
import { getAllRoles } from '@/api/system'
import { getAllPositions } from '@/api/system'
import { getDeptTree } from '@/api/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import UserImportDialog from './UserImportDialog.vue'
import type { User, UserQuery, UserRequest, Role, DeptTree, Position } from '@/types/system'
import { DICT_TYPE } from '@/constants/dict'
import DictValue from '@/components/DictValue.vue'
import { useDict } from '@/composables/useDict'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

// 表格高度自适应
const { tableHeight, updateHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

// 查询参数
const queryParams = reactive<UserQuery>({
  pageNum: 1,
  pageSize: 10,
  username: '',
  nickname: '',
  phone: '',
  status: undefined
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 表格数据
const loading = ref(false)
const tableData = ref<User[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<User | null>(null)

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.username) count++
  if (queryParams.nickname) count++
  if (queryParams.phone) count++
  if (queryParams.status !== undefined) count++
  return count
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = computed(() => (form.id ? '编辑用户' : '新增用户'))
const formRef = ref<FormInstance>()
const submitLoading = ref(false)

// 导入对话框
const importDialogRef = ref<InstanceType<typeof UserImportDialog>>()

// 表单数据
const form = reactive<UserRequest>({
  id: undefined,
  username: '',
  nickname: '',
  password: '',
  phone: '',
  email: '',
  deptId: undefined,
  positionIds: [],
  roleIds: [],
  status: 1
})

// 表单验证规则
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 部门树和角色列表
const deptTree = ref<DeptTree[]>([])
const roleList = ref<Role[]>([])
const positionList = ref<Position[]>([])

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await getUserList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.username = ''
  queryParams.nickname = ''
  queryParams.phone = ''
  queryParams.status = undefined
  handleQuery()
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

// 新增
const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: User) => {
  cancelSelection()
  resetForm()
  // 获取用户详情（包含角色ID）
  const userDetail = await getUser(row.id)
  Object.assign(form, userDetail)
  dialogVisible.value = true
}

// 删除
const handleDelete = (row: User) => {
  ElMessageBox.confirm('确定要删除该用户吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    await deleteUser([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  })
}

// 重置密码
const handleResetPwd = (row: User) => {
  ElMessageBox.confirm('确定要重置该用户的密码吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    await resetPassword(row.id)
    ElMessage.success('密码已重置为: 123456')
  })
}

// 导出
const handleExport = async () => {
  try {
    await exportUsers(queryParams)
    ElMessage.success('导出成功')
  } catch (e) {
    console.error('导出失败', e)
  }
}

// 导入
const handleImport = () => {
  importDialogRef.value?.open()
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (form.id) {
          await updateUser(form)
          ElMessage.success('修改成功')
        } else {
          await addUser(form)
          ElMessage.success('新增成功')
        }
        dialogVisible.value = false
        getList()
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 对话框关闭
const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// 重置表单
const resetForm = () => {
  form.id = undefined
  form.username = ''
  form.nickname = ''
  form.password = ''
  form.phone = ''
  form.email = ''
  form.deptId = undefined
  form.positionIds = []
  form.roleIds = []
  form.status = 1
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: User | null }) => {
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

// 初始化
onMounted(async () => {
  getList()
  // 获取部门树
  deptTree.value = await getDeptTree()
  // 获取角色列表
  roleList.value = await getAllRoles()
  // 获取岗位列表
  positionList.value = await getAllPositions()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}
</style>
