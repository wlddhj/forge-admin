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
          <template #default="{ row }">
            <span class="todo-content">{{ row.content }}</span>
          </template>
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
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button type="success" link size="small" v-permission="'ai:agent:approve'" @click="handle(row, true)">通过</el-button>
              <el-button type="danger" link size="small" v-permission="'ai:agent:reject'" @click="handle(row, false)">驳回</el-button>
            </template>
          </template>
        </vxe-column>
      </vxe-table>
      <TablePagination v-model:page-num="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @change="getList" />
    </el-card>

    <el-dialog v-model="detailVisible" :title="`待办详情 #${currentTodo?.id}`" width="640px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="运行ID">#{{ currentTodo?.runId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="todoType(currentTodo?.status || '')" size="small">{{ todoLabel(currentTodo?.status || '') }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentTodo?.createTime ? formatDateTime(currentTodo.createTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ currentTodo?.handleTime ? formatDateTime(currentTodo.handleTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ currentTodo?.handlerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ currentTodo?.title }}</el-descriptions-item>
        <el-descriptions-item label="待执行内容" :span="2">
          <div class="todo-review-detail">{{ currentTodo?.content || '（无明细）' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="处理意见" :span="2">{{ currentTodo?.handleComment || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
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

const detailVisible = ref(false)
const currentTodo = ref<AgentTodo | null>(null)
const handleDetail = (row: AgentTodo) => {
  currentTodo.value = row
  detailVisible.value = true
}

const handle = (row: AgentTodo, approved: boolean) => {
  ElMessageBox.prompt(
    h('div', null, [
      h('div', { class: 'todo-review-detail' }, row.content || '（无调用明细）'),
      h('div', { style: 'margin-top:10px;color:var(--el-text-color-regular);' },
        approved ? '确认允许以上高危操作继续执行？可填写意见。' : '确认驳回以上高危操作？可填写意见。')
    ]),
    approved ? '通过确认' : '驳回确认',
    { type: 'warning', inputPlaceholder: '处理意见（可选）', customStyle: { maxWidth: '640px', width: '90%' } }
  ).then(async ({ value }) => {
    const run = approved
      ? await agentApi.approveTodo(row.id, value).then(r => r.data)
      : await agentApi.rejectTodo(row.id, value).then(r => r.data)
    ElMessage.success(`已提交：运行 #${run.id} 正在后台恢复执行，结果请稍后在运行历史查看`)
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

  .todo-content {
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 12px;
  }
}

:deep(.todo-review-detail) {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
  max-height: 320px;
  overflow-y: auto;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
