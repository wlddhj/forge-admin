<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="回调名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.enabled" placeholder="请选择" clearable style="width: 120px">
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

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'workflow:callback:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="wfCallbackTable"
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
        <vxe-column type="seq" title="序号" width="50" align="center" />
        <vxe-column field="name" title="名称" min-width="140" />
        <vxe-column field="url" title="回调地址" min-width="220" />
        <vxe-column title="订阅事件" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="ev in row.events" :key="ev" size="small" style="margin-right: 4px">
              {{ eventLabel(ev) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="流程过滤" width="120">
          <template #default="{ row }">{{ row.processKey || '全部流程' }}</template>
        </vxe-column>
        <vxe-column field="enabled" title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="createTime" title="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'workflow:callback:query'" type="primary" link size="small" @click="openRecords(row)">投递记录</el-button>
            <el-button v-permission="'workflow:callback:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'workflow:callback:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="getList" />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="回调名称" prop="name">
          <el-input v-model="formData.name" placeholder="如：订单系统回调" />
        </el-form-item>
        <el-form-item label="回调地址" prop="url">
          <el-input v-model="formData.url" placeholder="https://biz.example.com/hook" />
        </el-form-item>
        <el-form-item label="订阅事件" prop="events">
          <el-checkbox-group v-model="formData.events">
            <el-checkbox value="taskCreated">任务创建</el-checkbox>
            <el-checkbox value="taskCompleted">任务办结</el-checkbox>
            <el-checkbox value="instanceFinished">实例终态</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="流程过滤">
          <el-input v-model="formData.processKey" placeholder="流程标识，留空订阅全部流程" />
        </el-form-item>
        <el-form-item label="签名密钥">
          <el-input v-model="formData.secret" placeholder="留空不签名" show-password />
        </el-form-item>
        <el-form-item label="携带流程变量">
          <el-switch v-model="formData.includeVariables" :active-value="1" :inactive-value="0" />
          <span class="form-tip">仅实例终态事件携带</span>
        </el-form-item>
        <el-form-item label="超时(毫秒)">
          <el-input-number v-model="formData.timeoutMs" :min="1000" :max="30000" :step="1000" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 投递记录抽屉（独立组件，表格高度自适应） -->
    <CallbackRecordDrawer v-model="recordsVisible" :callback="currentCallback" />
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
import { callbackApi, type CallbackRequest } from '@/api/workflow/callback'
import CallbackRecordDrawer from './components/CallbackRecordDrawer.vue'
import type { BusinessCallback } from '@/types/workflow'

defineOptions({ name: 'WorkflowCallback' })

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

const eventLabel = (ev: string): string => {
  const map: Record<string, string> = {
    taskCreated: '任务创建',
    taskCompleted: '任务办结',
    instanceFinished: '实例终态'
  }
  return map[ev] || ev
}

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<BusinessCallback[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  name: undefined as string | undefined,
  enabled: undefined as number | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await callbackApi.page(queryParams)
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
  queryParams.name = undefined
  queryParams.enabled = undefined
  handleQuery()
}

// ========== 新增/编辑 ==========
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance | null>(null)
const formData = reactive<CallbackRequest>({
  name: '',
  url: '',
  events: [],
  processKey: '',
  secret: '',
  includeVariables: 0,
  enabled: 1,
  timeoutMs: 5000
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入回调名称', trigger: 'blur' }],
  url: [
    { required: true, message: '请输入回调地址', trigger: 'blur' },
    { pattern: /^https?:\/\//, message: '必须是 http(s):// 开头', trigger: 'blur' }
  ],
  events: [{ required: true, type: 'array', min: 1, message: '至少订阅一个事件', trigger: 'change' }]
}

const handleAdd = () => {
  dialogTitle.value = '新增回调'
  Object.assign(formData, {
    id: undefined, name: '', url: '', events: [], processKey: '',
    secret: '', includeVariables: 0, enabled: 1, timeoutMs: 5000
  })
  dialogVisible.value = true
}

const handleEdit = (row: BusinessCallback) => {
  dialogTitle.value = '编辑回调'
  Object.assign(formData, {
    id: row.id,
    name: row.name,
    url: row.url,
    events: [...row.events],
    processKey: row.processKey || '',
    secret: row.secret || '',
    includeVariables: row.includeVariables,
    enabled: row.enabled,
    timeoutMs: row.timeoutMs
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      await callbackApi.update(formData)
      ElMessage.success('更新成功')
    } else {
      await callbackApi.create(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: BusinessCallback) => {
  await ElMessageBox.confirm(`确定删除回调「${row.name}」？`, '提示', { type: 'warning' })
  await callbackApi.remove(row.id)
  ElMessage.success('删除成功')
  getList()
}

// ========== 投递记录 ==========
const recordsVisible = ref(false)
const currentCallback = ref<BusinessCallback | null>(null)

const openRecords = (row: BusinessCallback) => {
  currentCallback.value = row
  recordsVisible.value = true
}
</script>

<style scoped lang="scss">
.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
