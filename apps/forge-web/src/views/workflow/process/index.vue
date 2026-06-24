<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.name" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item label="流程标识">
          <el-input v-model="queryParams.key" placeholder="请输入流程标识" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.categoryId" placeholder="请选择分类" clearable style="width: 150px">
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.processState" placeholder="请选择" clearable style="width: 120px">
            <el-option label="激活" :value="1" />
            <el-option label="挂起" :value="0" />
            <el-option label="历史" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">流程定义</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.name" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item label="流程标识">
          <el-input v-model="queryParams.key" placeholder="请输入流程标识" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.categoryId" placeholder="请选择分类" clearable style="width: 100%">
            <el-option
              v-for="item in categoryList"
              :key="item.id"
              :label="item.categoryName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.processState" placeholder="请选择" clearable style="width: 100%">
            <el-option label="激活" :value="1" />
            <el-option label="挂起" :value="0" />
            <el-option label="历史" :value="2" />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <!-- 新增流程请前往模型管理 -->
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="wfProcessDefinitionTable"
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

        <!-- 流程名称 -->
        <vxe-column field="name" title="流程名称" min-width="150" />

        <!-- 流程标识 -->
        <vxe-column v-if="!isMobile" field="key" title="流程标识" width="150" />

        <!-- 分类 -->
        <vxe-column v-if="!isMobile" field="categoryName" title="分类" width="120" />

        <!-- 版本（桌面端） -->
        <vxe-column v-if="!isMobile" field="version" title="版本" width="70" align="center" />

        <!-- 状态 -->
        <vxe-column title="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.processState === 1 ? 'success' : 'warning'">
              {{ row.processState === 1 ? '激活' : row.processState === 1 ? '挂起': '历史' }}
            </el-tag>
          </template>
        </vxe-column>

        <!-- 部署人（桌面端） -->
        <vxe-column v-if="!isMobile" field="deployUserName" title="部署人" width="100" />

        <!-- 创建时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.processState === 1"
              v-permission="'workflow:instance:start'"
              type="success" link size="small"
              @click.stop="handleStartProcess(row)"
            >发起</el-button>
            <el-button type="primary" link size="small" @click.stop="handleViewJson(row)">JSON</el-button>
            <el-button
              v-if="row.processState === 1"
              v-permission="'workflow:process:edit'"
              type="warning" link size="small"
              @click.stop="handleSuspend(row)"
            >挂起</el-button>
            <el-button
              v-if="row.processState === 0"
              v-permission="'workflow:process:edit'"
              type="success" link size="small"
              @click.stop="handleActivate(row)"
            >激活</el-button>
            <el-button
              v-permission="'workflow:process:delete'"
              type="danger" link size="small"
              @click.stop="handleDelete(row)"
            >删除</el-button>
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
        <el-button
          v-if="item.suspensionState === 1"
          v-permission="'workflow:instance:start'"
          size="small" type="success"
          @click.stop="handleStartProcess(item)"
        >发起</el-button>
        <el-button size="small" @click.stop="handleViewJson(item)">JSON</el-button>
        <el-button
          v-if="item.suspensionState === 1"
          v-permission="'workflow:process:edit'"
          size="small" type="warning"
          @click.stop="handleSuspend(item)"
        >挂起</el-button>
        <el-button
          v-if="item.suspensionState === 2"
          v-permission="'workflow:process:edit'"
          size="small" type="success"
          @click.stop="handleActivate(item)"
        >激活</el-button>
        <el-button
          v-permission="'workflow:process:delete'"
          size="small" type="danger"
          @click.stop="handleDelete(item)"
        >删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- JSON预览对话框 -->
    <el-dialog v-model="jsonDialogVisible" title="流程模型 JSON" width="700px">
      <el-input
        type="textarea"
        :model-value="currentJson"
        :rows="20"
        readonly
        style="font-family: monospace"
      />
    </el-dialog>

    <!-- 发起流程对话框 -->
    <el-dialog v-model="startDialogVisible" title="发起流程" width="600px" @close="handleStartDialogClose">
      <el-form label-width="100px">
        <el-form-item label="流程名称">
          <el-input :model-value="startForm.processName" disabled />
        </el-form-item>
        <el-form-item label="业务标识">
          <el-input v-model="startForm.businessKey" placeholder="请输入业务标识（可选）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="startForm.comment" type="textarea" :rows="2" placeholder="请输入备注（可选）" />
        </el-form-item>
        <template v-if="startForm.formRule.length > 0">
          <el-divider content-position="left">流程表单</el-divider>
          <form-create
            ref="formCreateRef"
            v-model="startFormData"
            :rule="startForm.formRule"
            :option="startForm.formOption"
          />
        </template>
        <template v-if="startForm.startSelectTasks.length > 0">
          <el-divider content-position="left">审批人选择</el-divider>
          <el-form-item
            v-for="task in startForm.startSelectTasks"
            :key="task.taskDefKey"
            :label="task.taskName"
            :required="true"
          >
            <el-select
              v-model="startForm.selectedUsers[task.taskDefKey]"
              multiple
              filterable
              remote
              reserve-keyword
              placeholder="请输入用户名搜索"
              :remote-method="searchUsers"
              :loading="userSearchLoading"
              style="width: 100%"
            >
              <el-option
                v-for="user in userOptions"
                :key="user.id"
                :label="`${user.nickname}（${user.username}）`"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="startLoading" @click="handleConfirmStart">确定发起</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useRouter } from 'vue-router'
import { processDefinitionApi } from '@/api/workflow/process-definition'
import { processInstanceApi } from '@/api/workflow/process-instance'
import { categoryApi } from '@/api/workflow/category'
import { formApi } from '@/api/workflow/form'
import type { ProcessDefinition, UserTaskNode } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { decodeFields } from '@/utils/formCreate'
import { getUserList } from '@/api/system'
import type { User } from '@/types/system'
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
const tableData = ref<ProcessDefinition[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<ProcessDefinition | null>(null)

// 分类列表
const categoryList = ref<{ id: number; categoryName: string }[]>([])

const queryParams = reactive({
  name: '',
  key: '',
  categoryId: undefined as number | undefined,
  processState: undefined as number | undefined,
  pageNum: 1,
  pageSize: 20
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef }) as any

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.name) count++
  if (queryParams.key) count++
  if (queryParams.categoryId !== undefined) count++
  if (queryParams.suspensionState !== undefined) count++
  return count
})

// JSON预览
const jsonDialogVisible = ref(false)
const currentJson = ref('')

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

/** 加载分类列表 */
const getCategoryList = async () => {
  try {
    categoryList.value = await categoryApi.listAll()
  } catch (e) {
    // ignore
  }
}

/** 获取流程定义列表 */
const getList = async () => {
  loading.value = true
  try {
    const res = await processDefinitionApi.page(queryParams)
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
  queryParams.categoryId = undefined
  queryParams.processState = undefined
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

/** 查看JSON */
const handleViewJson = async (row: ProcessDefinition) => {
  cancelSelection()
  try {
    currentJson.value = await processDefinitionApi.getJson(row.id)
    jsonDialogVisible.value = true
  } catch (e) {
    ElMessage.error('获取JSON失败')
  }
}

/** 挂起 */
const handleSuspend = async (row: ProcessDefinition) => {
  cancelSelection()
  try {
    await ElMessageBox.confirm(`确定挂起流程 "${row.name}"？挂起后该流程将无法发起新实例`, '警告', { type: 'warning' })
    await processDefinitionApi.suspend(row.id)
    ElMessage.success('挂起成功')
    getList()
  } catch (e) {
    // 用户取消
  }
}

/** 激活 */
const handleActivate = async (row: ProcessDefinition) => {
  cancelSelection()
  try {
    await ElMessageBox.confirm(`确定激活流程 "${row.name}"？`, '提示', { type: 'info' })
    await processDefinitionApi.activate(row.id)
    ElMessage.success('激活成功')
    getList()
  } catch (e) {
    // 用户取消
  }
}

/** 删除 */
const handleDelete = async (row: ProcessDefinition) => {
  cancelSelection()
  try {
    await ElMessageBox.confirm(`确定删除流程 "${row.name}"？删除后不可恢复`, '警告', { type: 'warning' })
    await processDefinitionApi.delete(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    // 用户取消
  }
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: ProcessDefinition | null }) => {
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

// 发起流程
const startDialogVisible = ref(false)
const startLoading = ref(false)
const formCreateRef = ref<any>(null)
const startFormData = ref<Record<string, any>>({})
const startForm = reactive({
  processDefinitionId: '',
  processName: '',
  businessKey: '',
  comment: '',
  formRule: [] as any[],
  formOption: { submitBtn: false, resetBtn: false } as any,
  startSelectTasks: [] as UserTaskNode[], // 发起人自选节点
  approveSelectTasks: [] as UserTaskNode[], // 审批人自选节点
  selectedUsers: {} as Record<string, number[]>, // { taskDefKey: [userId1, userId2] }
})

// 用户搜索
const userSearchLoading = ref(false)
const userOptions = ref<User[]>([])
const searchUsers = async (query: string) => {
  if (!query) return
  userSearchLoading.value = true
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 20, username: query })
    userOptions.value = res.list
  } finally {
    userSearchLoading.value = false
  }
}

const handleStartProcess = async (row: ProcessDefinition) => {
  startForm.processDefinitionId = row.id
  startForm.processName = row.name
  startForm.businessKey = ''
  startForm.comment = ''
  startForm.formRule = []
  startForm.startSelectTasks = []
  startForm.approveSelectTasks = []
  startForm.selectedUsers = {}
  startFormData.value = {}

  // 加载流程定义详情获取关联表单
  try {
    const detail = await processDefinitionApi.getById(row.id)
    if (detail.formId) {
      const formData = await formApi.getById(detail.formId)
      if (formData.conf && formData.fields) {
        startForm.formRule = decodeFields(formData.fields)
        try {
          startForm.formOption = JSON.parse(formData.conf)
        } catch { /* ignore */ }
        startForm.formOption.submitBtn = false
        startForm.formOption.resetBtn = false
      }
    }
  } catch { /* ignore */ }

  // 加载需要自选审批人的任务节点
  try {
    const nodes = await processDefinitionApi.getUserTaskNodes(row.id)
    startForm.startSelectTasks = (nodes || []).filter(n => n.candidateStrategy === 35)
    startForm.approveSelectTasks = (nodes || []).filter(n => n.candidateStrategy === 34)
  } catch { /* ignore */ }

  startDialogVisible.value = true
}

const handleConfirmStart = async () => {
  // 校验发起人自选节点
  for (const task of startForm.startSelectTasks) {
    const users = startForm.selectedUsers[task.taskDefKey]
    if (!users || users.length === 0) {
      ElMessage.warning(`请为"${task.taskName}"选择审批人`)
      return
    }
  }

  startLoading.value = true
  try {
    // 使用 form-create v-model 绑定的表单数据
    let variables: Record<string, any> = {}
    if (startFormData.value && Object.keys(startFormData.value).length > 0) {
      Object.assign(variables, startFormData.value)
    }

    // 构建发起人自选审批人数据
    const startUserSelectActors: Record<string, string[]> = {}
    for (const task of startForm.startSelectTasks) {
      const users = startForm.selectedUsers[task.taskDefKey] || []
      if (users.length > 0) {
        startUserSelectActors[task.taskDefKey] = users.map(String)
      }
    }

    await processInstanceApi.start({
      processDefinitionId: startForm.processDefinitionId,
      businessKey: startForm.businessKey || undefined,
      variables: Object.keys(variables).length > 0 ? variables : undefined,
      comment: startForm.comment || undefined,
      startUserSelectActors: Object.keys(startUserSelectActors).length > 0 ? startUserSelectActors : undefined,
    })
    ElMessage.success('流程发起成功')
    startDialogVisible.value = false
  } catch (e){
    console.error( e)
    ElMessage.error('流程发起失败')
  } finally {
    startLoading.value = false
  }
}

const handleStartDialogClose = () => {
  startForm.formRule = []
  startFormData.value = {}
}

onMounted(() => {
  getCategoryList()
  getList()
})
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
