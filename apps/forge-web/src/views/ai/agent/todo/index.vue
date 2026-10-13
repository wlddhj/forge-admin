<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-table ref="tableRef" id="agentTodoTable" :data="tableData" :height="tableHeight" :loading="loading"
        :row-config="{ isHover: true }" stripe show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="id" title="待办ID" width="90" />
        <vxe-column field="runId" title="运行ID" width="90" />
        <vxe-column field="title" title="标题" min-width="200" />
        <vxe-column title="内容" min-width="260">
          <template #default="{ row }">{{ row.content }}</template>
        </vxe-column>
        <vxe-column title="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="todoType(row.status)">{{ todoLabel(row.status) }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="handlerName" title="处理人" width="100" />
        <vxe-column field="handleTime" title="处理时间" width="170">
          <template #default="{ row }">{{ row.handleTime ? formatDateTime(row.handleTime) : '-' }}</template>
        </vxe-column>
        <vxe-column title="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button type="success" link size="small" v-permission="'ai:agent:approve'" @click="handle(row, true)">通过</el-button>
              <el-button type="danger" link size="small" v-permission="'ai:agent:reject'" @click="handle(row, false)">驳回</el-button>
            </template>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @size-change="getList" @current-change="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getAgentTodos, agentApi } from '@/api/ai/agent'
import type { AgentTodo } from '@/api/ai/agent'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

defineOptions({ name: 'AiAgentTodo' })

const { tableHeight } = useTableHeight()
const { seqMethod } = useTableSeq({
  currentPage: computed(() => queryParams.pageNum),
  pageSize: computed(() => queryParams.pageSize)
})

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const loading = ref(false)
const tableData = ref<AgentTodo[]>([])
const total = ref(0)
const queryParams = reactive({ status: '', pageNum: 1, pageSize: 10 })

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value!)
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    const result = await getAgentTodos({ status: queryParams.status || undefined, pageNum: queryParams.pageNum, pageSize: queryParams.pageSize })
    tableData.value = result.records
    total.value = Number(result.total)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.status = ''; handleQuery() }

const handle = (row: AgentTodo, approved: boolean) => {
  ElMessageBox.prompt(
    approved ? '确认允许该高危操作继续执行？可填写意见' : '确认驳回该高危操作？可填写意见',
    approved ? '通过确认' : '驳回确认',
    { type: 'warning', inputPlaceholder: '处理意见（可选）' }
  ).then(async ({ value }) => {
    const run = approved
      ? await agentApi.approveTodo(row.id, value).then(r => r.data)
      : await agentApi.rejectTodo(row.id, value).then(r => r.data)
    ElMessage.success(`处理完成，运行 #${run.id} 状态: ${run.status}`)
    getList()
  }).catch(() => { /* 取消 */ })
}

const todoLabel = (s: string) => ({ PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回' } as Record<string, string>)[s] || s
const todoType = (s: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' =>
  (({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>)[s] || 'info')
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card { margin-bottom: 15px; }
  .table-card { padding: 15px; .el-pagination { margin-top: 15px; justify-content: flex-end; } }
}
</style>
