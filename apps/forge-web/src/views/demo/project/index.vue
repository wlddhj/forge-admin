<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目名称">
          <el-input v-model="queryParams.projectName" placeholder="请输入项目名称" clearable />
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="queryParams.approveStatus" placeholder="请选择" clearable style="width: 140px">
            <el-option label="审批中" value="APPROVING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已撤销" value="CANCELED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'demo:project:add'" type="primary" @click="handleSubmit()">
            <el-icon><Plus /></el-icon>提交立项
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="projectTable"
        ref="tableRef"
        :custom-config="{ mode: 'modal' }"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :seq-config="{ seqMethod }"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" align="center" />
        <vxe-column field="projectName" title="项目名称" min-width="180" />
        <vxe-column field="totalAmount" title="项目金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template>
        </vxe-column>
        <vxe-column field="bizUser" title="责任人" width="110" />
        <vxe-column field="deptName" title="责任部门" width="120" />
        <vxe-column title="审批状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="PROJECT_STATUS_TAG[row.approveStatus] || 'info'" size="small">
              {{ row.approveStatusName }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.processInstanceId"
              v-permission="'demo:project:query'"
              type="primary" link size="small" @click="viewProcess(row)"
            >
查看流程
</el-button>
            <el-button
              v-if="row.cancelable"
              v-permission="'demo:project:edit'"
              type="danger" link size="small" @click="handleCancel(row)"
            >
撤销
</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="getList" />
    </el-card>

    <!-- 提交立项对话框 -->
    <el-dialog v-model="dialogVisible" title="提交立项" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="formData.projectName" placeholder="请输入项目名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="项目金额" prop="totalAmount">
          <el-input-number v-model="formData.totalAmount" :min="0" :precision="2" :step="1000"
            placeholder="元" style="width: 200px" controls-position="right" />
        </el-form-item>
        <el-form-item label="责任人" prop="bizUser">
          <el-input v-model="formData.bizUser" placeholder="请输入责任人" maxlength="64" />
        </el-form-item>
        <el-form-item label="责任部门" prop="deptName">
          <el-input v-model="formData.deptName" placeholder="请输入责任部门" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="选填" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="confirmSubmit">提交并发起审批</el-button>
      </template>
    </el-dialog>

    <!-- 流程详情抽屉 -->
    <InstanceDetailDrawer v-model="detailDrawerVisible" :instance-id="detailInstanceId" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import TablePagination from '@/components/TablePagination.vue'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'
import InstanceDetailDrawer from '@/views/workflow/instance/components/InstanceDetailDrawer.vue'
import { projectApi } from '@/api/demo/project'
import { PROJECT_STATUS_TAG, type ProjectItem, type ProjectSubmitRequest } from '@/types/project'

defineOptions({ name: 'ProjectApproval' })

const { tableHeight } = useTableHeight()
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})

const formatAmount = (value?: number): string => {
  if (value == null) return '-'
  return `¥ ${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<ProjectItem[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  projectName: undefined as string | undefined,
  approveStatus: undefined as string | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await projectApi.page(queryParams)
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.projectName = undefined
  queryParams.approveStatus = undefined
  handleQuery()
}

// ========== 提交立项 ==========
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance | null>(null)
const formData = reactive<ProjectSubmitRequest>({
  projectName: '',
  totalAmount: undefined,
  bizUser: '',
  deptName: '',
  remark: ''
})

const formRules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

const handleSubmit = () => {
  dialogVisible.value = true
}

const resetForm = () => {
  formData.projectName = ''
  formData.totalAmount = undefined
  formData.bizUser = ''
  formData.deptName = ''
  formData.remark = ''
  formRef.value?.clearValidate()
}

const confirmSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await projectApi.submit(formData)
    ElMessage.success('立项已提交，审批流程已发起')
    dialogVisible.value = false
    handleQuery()
  } finally {
    submitLoading.value = false
  }
}

// ========== 撤销 / 查看流程 ==========
const handleCancel = async (row: ProjectItem) => {
  await ElMessageBox.confirm(`确定撤销「${row.projectName}」的审批流程？`, '提示', { type: 'warning' })
  await projectApi.cancel(row.id)
  ElMessage.success('撤销已提交，状态将随流程事件更新')
  getList()
}

// 流程详情抽屉
const detailDrawerVisible = ref(false)
const detailInstanceId = ref<string>('')

const viewProcess = (row: ProjectItem) => {
  detailInstanceId.value = row.processInstanceId as string
  detailDrawerVisible.value = true
}
</script>
