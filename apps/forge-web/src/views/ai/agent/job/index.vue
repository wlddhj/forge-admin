<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline @submit.prevent>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="启用" :value="1" />
            <el-option label="停止" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" v-permission="'ai:agent:job:config'" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增任务
          </el-button>
        </template>
      </vxe-toolbar>

      <vxe-table ref="tableRef" id="agentJobTable" :data="tableData" :height="tableHeight" :loading="loading"
        :row-config="{ isHover: true }" stripe show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="id" title="ID" width="70" />
        <vxe-column field="jobName" title="任务名称" min-width="150" />
        <vxe-column field="agentCode" title="Agent" min-width="170" />
        <vxe-column field="cronExpression" title="cron 表达式" width="130" />
        <vxe-column field="inputTemplate" title="输入模板" min-width="220" />
        <vxe-column title="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停止' }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="lastRunId" title="最近运行" width="110">
          <template #default="{ row }">
            <el-button v-if="row.lastRunId" type="primary" link size="small" @click="viewRunResult(row.lastRunId)">
              #{{ row.lastRunId }} 结果
            </el-button>
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="updateTime" title="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="'ai:agent:job:config'" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" v-permission="'ai:agent:job:run'" :loading="runningId === row.id" @click="handleRun(row)">执行</el-button>
            <el-button :type="row.status === 1 ? 'warning' : 'success'" link size="small" v-permission="'ai:agent:job:config'" @click="handleToggle(row)">
              {{ row.status === 1 ? '停止' : '启用' }}
            </el-button>
            <el-button type="danger" link size="small" v-permission="'ai:agent:job:config'" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @size-change="getList" @current-change="getList" />
    </el-card>

    <AgentJobForm ref="formRef" @success="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { agentJobApi } from '@/api/ai/agent'
import type { AgentJob } from '@/api/ai/agent'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import AgentJobForm from './components/AgentJobForm.vue'

defineOptions({ name: 'AiAgentJob' })

const { tableHeight } = useTableHeight()
const { seqMethod } = useTableSeq({
  currentPage: computed(() => queryParams.pageNum),
  pageSize: computed(() => queryParams.pageSize)
})

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const formRef = ref<InstanceType<typeof AgentJobForm>>()
const loading = ref(false)
const runningId = ref<number>()
const tableData = ref<AgentJob[]>([])
const total = ref(0)
const queryParams = reactive({ jobName: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value!)
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    const result = await agentJobApi.page({
      jobName: queryParams.jobName || undefined,
      status: queryParams.status,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }).then(r => r.data)
    tableData.value = result.records
    total.value = Number(result.total)
  } finally {
    loading.value = false
  }
}

const router = useRouter()
const viewRunResult = (runId: number) => {
  router.push({ path: '/ai/agent/run', query: { runId: String(runId) } })
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.jobName = ''; queryParams.status = undefined; handleQuery() }

const handleAdd = () => formRef.value?.open('create')
const handleEdit = (row: AgentJob) => formRef.value?.open('update', row)

const handleRun = (row: AgentJob) => {
  ElMessageBox.confirm(`确定立即执行任务"${row.jobName}"吗？执行期间请勿重复操作。`, '提示', { type: 'warning' })
    .then(async () => {
      runningId.value = row.id
      try {
        const run = await agentJobApi.runOnce(row.id).then(r => r.data)
        ElMessage.success(`执行完成：运行 #${run.id} 状态 ${run.status}，详情见运行历史`)
        getList()
      } finally {
        runningId.value = undefined
      }
    }).catch(() => { /* 取消 */ })
}

const handleToggle = async (row: AgentJob) => {
  await agentJobApi.changeStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? '已停止' : '已启用')
  getList()
}

const handleDelete = (row: AgentJob) => {
  ElMessageBox.confirm(`确定删除任务"${row.jobName}"吗？将同步移除调度。`, '提示', { type: 'warning' })
    .then(async () => {
      await agentJobApi.remove(row.id)
      ElMessage.success('删除成功')
      getList()
    }).catch(() => { /* 取消 */ })
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card { margin-bottom: 15px; }
  .table-card { padding: 15px; .el-pagination { margin-top: 15px; justify-content: flex-end; } }
}
</style>
