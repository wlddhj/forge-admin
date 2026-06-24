<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="模型名称">
          <el-input v-model="queryParams.name" placeholder="请输入模型名称" clearable />
        </el-form-item>
        <el-form-item label="模型标识">
          <el-input v-model="queryParams.key" placeholder="请输入模型标识" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="请选择分类" clearable style="width: 160px">
            <el-option
              v-for="item in categoryList"
              :key="item.categoryCode"
              :label="item.categoryName"
              :value="item.categoryCode"
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
        <span class="title">模型管理</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button v-permission="'workflow:model:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="模型名称">
          <el-input v-model="queryParams.name" placeholder="请输入模型名称" clearable />
        </el-form-item>
        <el-form-item label="模型标识">
          <el-input v-model="queryParams.key" placeholder="请输入模型标识" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryList"
              :key="item.categoryCode"
              :label="item.categoryName"
              :value="item.categoryCode"
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
          <el-button v-permission="'workflow:model:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增模型
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="wfModelTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :seq-config="{seqMethod}"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" />

        <!-- 模型名称 -->
        <vxe-column field="name" title="模型名称" min-width="180" />

        <!-- 模型标识（桌面端） -->
        <vxe-column v-if="!isMobile" field="key" title="模型标识" min-width="150" />

        <!-- 分类 -->
        <vxe-column v-if="!isMobile" field="category" title="分类" width="120" />

        <!-- 版本（桌面端） -->
        <vxe-column v-if="!isMobile" field="version" title="版本" width="80" align="center" />

        <!-- 部署状态 -->
        <vxe-column title="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.deployed ? 'success' : 'info'">
              {{ row.deployed ? '已部署' : '未部署' }}
            </el-tag>
          </template>
        </vxe-column>

        <!-- 创建时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>

        <!-- 最后更新时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="lastUpdateTime" title="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.lastUpdateTime) }}</template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleDesign(row)">设计</el-button>
            <el-button v-permission="'workflow:model:deploy'" type="success" link size="small" @click.stop="handleDeploy(row)">部署</el-button>
            <el-button v-permission="'workflow:model:edit'" type="warning" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'workflow:model:delete'" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination
        v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        @change="getList"
      />
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.name"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" type="primary" @click.stop="handleDesign(item)">设计</el-button>
        <el-button v-permission="'workflow:model:deploy'" size="small" type="success" @click.stop="handleDeploy(item)">部署</el-button>
        <el-button v-permission="'workflow:model:edit'" size="small" type="warning" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button v-permission="'workflow:model:delete'" size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="模型名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="模型标识" prop="key">
          <el-input v-model="formData.key" placeholder="请输入模型标识（英文）" :disabled="isEditDialog" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="formData.categoryId" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="表单类型">
          <el-select v-model="formData.formType" placeholder="请选择表单类型" clearable style="width: 100%">
            <el-option label="流程表单" :value="10" />
            <el-option label="业务表单" :value="20" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.formType" label="关联表单">
          <el-select v-model="formData.formId" placeholder="请选择表单" clearable filterable style="width: 100%">
            <el-option
              v-for="item in formList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">自动抄送</el-divider>
        <el-form-item label="抄送策略">
          <el-select v-model="formData.autoCopyStrategy" placeholder="不自动抄送" clearable style="width: 100%">
            <el-option
              v-for="item in autoCopyStrategyOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.autoCopyStrategy === 10" label="抄送角色">
          <el-select v-model="formData.autoCopyParam" placeholder="请选择角色" clearable filterable style="width: 100%">
            <el-option v-for="item in roleList" :key="item.id" :label="item.roleName" :value="String(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="formData.autoCopyStrategy === 20 || formData.autoCopyStrategy === 21" label="抄送部门">
          <el-tree-select
            v-model="formData.autoCopyParam"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            placeholder="请选择部门"
            clearable
            check-strictly
            filterable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-else-if="formData.autoCopyStrategy === 22" label="抄送岗位">
          <el-select v-model="formData.autoCopyParam" placeholder="请选择岗位" clearable filterable style="width: 100%">
            <el-option v-for="item in positionList" :key="item.id" :label="item.positionName" :value="String(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="formData.autoCopyStrategy === 30" label="抄送用户">
          <el-select v-model="formData.autoCopyParam" placeholder="请选择用户" clearable filterable style="width: 100%">
            <el-option v-for="item in userList" :key="item.id" :label="item.nickname" :value="String(item.id)" />
          </el-select>
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
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { modelApi } from '@/api/workflow/model'
import type { WfModel } from '@/api/workflow/model'
import { categoryApi } from '@/api/workflow/category'
import { formApi } from '@/api/workflow/form'
import { getAllUsersSimple, getAllRoles, getDeptTree, getAllPositions } from '@/api/system'
import type { WfCategory } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const router = useRouter()
const { isMobile } = useResponsive()

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<WfModel[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<WfModel | null>(null)

const queryParams = reactive({
  name: '',
  key: '',
  category: undefined as string | undefined,
  pageNum: 1,
  pageSize: 20
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef }) as any

// 分类列表
const categoryList = ref<WfCategory[]>([])

// 表单列表
const formList = ref<{ id: number; name: string }[]>([])

// 自动抄送策略选项
const autoCopyStrategyOptions = [
  { label: '指定角色', value: 10 },
  { label: '部门成员', value: 20 },
  { label: '部门负责人', value: 21 },
  { label: '指定岗位', value: 22 },
  { label: '指定用户', value: 30 },
  { label: '发起人自己', value: 36 },
  { label: '发起人部门负责人', value: 37 },
  { label: '表达式', value: 60 },
]

// 自动抄送引用数据
const userList = ref<{ id: number; nickname: string }[]>([])
const roleList = ref<{ id: number; roleName: string }[]>([])
const deptTree = ref<any[]>([])
const positionList = ref<{ id: number; positionName: string }[]>([])

// 对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const isEditDialog = ref(false)
const dialogTitle = computed(() => isEditDialog.value ? '编辑模型' : '新增模型')

const formData = reactive({
  id: undefined as string | undefined,
  name: '',
  key: '',
  categoryId: undefined as number | undefined,
  description: '',
  formType: undefined as number | undefined,
  formId: undefined as number | undefined,
  autoCopyStrategy: undefined as number | undefined,
  autoCopyParam: undefined as string | undefined,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  key: [
    { required: true, message: '请输入模型标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_-]*$/, message: '模型标识需以字母开头，仅允许字母、数字、下划线和横线', trigger: 'blur' }
  ]
}

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.name) count++
  if (queryParams.key) count++
  if (queryParams.category) count++
  return count
})

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

/** 获取模型列表 */
const getList = async () => {
  loading.value = true
  try {
    const res = await modelApi.page(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.name = ''
  queryParams.key = ''
  queryParams.category = undefined
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

/** 跳转模型设计器 */
const handleDesign = (row: WfModel) => {
  cancelSelection()
  router.push(`/workflow/model/designer?id=${row.id}`)
}

/** 新增模型 */
const handleAdd = () => {
  cancelSelection()
  isEditDialog.value = false
  formData.id = undefined
  formData.name = ''
  formData.key = ''
  formData.categoryId = undefined
  formData.description = ''
  formData.formType = undefined
  formData.formId = undefined
  formData.autoCopyStrategy = undefined
  formData.autoCopyParam = undefined
  dialogVisible.value = true
}

/** 编辑模型 */
const handleEdit = (row: WfModel) => {
  cancelSelection()
  isEditDialog.value = true
  formData.id = row.id
  formData.name = row.name
  formData.key = row.key
  formData.categoryId = row.categoryId || undefined
  formData.description = row.description || ''
  formData.formType = row.formType || undefined
  formData.formId = row.formId || undefined
  formData.autoCopyStrategy = row.autoCopyStrategy || undefined
  formData.autoCopyParam = row.autoCopyParam || undefined
  dialogVisible.value = true
}

/** 提交表单 */
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  submitLoading.value = true
  try {
    // 构建 metaInfo JSON（包含 formType、formId、autoCopyStrategy、autoCopyParam）
    const metaInfo: Record<string, any> = {}
    if (formData.formType !== undefined) {
      metaInfo.formType = formData.formType
    }
    if (formData.formId !== undefined) {
      metaInfo.formId = formData.formId
    }
    if (formData.autoCopyStrategy !== undefined) {
      metaInfo.autoCopyStrategy = formData.autoCopyStrategy
    }
    if (formData.autoCopyParam !== undefined) {
      metaInfo.autoCopyParam = formData.autoCopyParam
    }

    if (isEditDialog.value) {
      await modelApi.update({
        id: formData.id,
        name: formData.name,
        key: formData.key,
        categoryId: formData.categoryId,
        description: formData.description,
        formType: formData.formType,
        formId: formData.formId,
        autoCopyStrategy: formData.autoCopyStrategy,
        autoCopyParam: formData.autoCopyParam,
        metaInfo: Object.keys(metaInfo).length > 0 ? JSON.stringify(metaInfo) : undefined,
      })
      ElMessage.success('更新成功')
    } else {
      await modelApi.add({
        name: formData.name,
        key: formData.key,
        categoryId: formData.categoryId,
        description: formData.description,
        formType: formData.formType,
        formId: formData.formId,
        autoCopyStrategy: formData.autoCopyStrategy,
        autoCopyParam: formData.autoCopyParam,
        metaInfo: Object.keys(metaInfo).length > 0 ? JSON.stringify(metaInfo) : undefined,
      })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

/** 部署模型 */
const handleDeploy = async (row: WfModel) => {
  cancelSelection()
  try {
    await ElMessageBox.confirm(`确定部署模型 "${row.name}"（版本 ${row.version}）？`, '部署确认', { type: 'info' })
    await modelApi.deploy(row.id)
    ElMessage.success('部署成功')
    getList()
  } catch (e) {
    // 用户取消
  }
}

/** 删除模型 */
const handleDelete = async (row: WfModel) => {
  cancelSelection()
  try {
    await ElMessageBox.confirm(`确定删除模型 "${row.name}"？`, '警告', { type: 'warning' })
    await modelApi.delete(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    // 用户取消
  }
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: WfModel | null }) => {
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

/** 加载分类列表 */
const getCategoryList = async () => {
  try {
    categoryList.value = await categoryApi.listAll()
  } catch (e) {
    // ignore
  }
}

/** 加载表单列表 */
const getFormList = async () => {
  try {
    formList.value = await formApi.listAll()
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  getList()
  getCategoryList()
  getFormList()
  loadAutoCopyReferenceData()
})

/** 加载自动抄送引用数据 */
const loadAutoCopyReferenceData = async () => {
  try {
    const [users, roles, depts, positions] = await Promise.all([
      getAllUsersSimple(),
      getAllRoles(),
      getDeptTree(),
      getAllPositions(),
    ])
    userList.value = users
    roleList.value = roles
    deptTree.value = depts
    positionList.value = positions
  } catch (e) {
    // 加载失败不影响主流程
  }
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    :deep(.el-pagination) {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}
</style>
