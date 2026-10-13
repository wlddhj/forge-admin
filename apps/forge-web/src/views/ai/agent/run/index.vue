<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 180px">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="待确认" value="WAITING_APPROVAL" />
            <el-option label="成功" value="SUCCEEDED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-table ref="tableRef" id="agentRunTable" :data="tableData" :height="tableHeight" :loading="loading"
        :row-config="{ isHover: true }" stripe show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="id" title="运行ID" width="90" />
        <vxe-column field="agentId" title="AgentID" width="90" />
        <vxe-column field="inputSummary" title="输入摘要" min-width="240" />
        <vxe-column title="触发方式" width="90">
          <template #default="{ row }">
            <el-tag :type="triggerType(row.triggerType)" size="small">{{ triggerLabel(row.triggerType) }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column title="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="lastNode" title="最后节点" width="110" />
        <vxe-column field="errorMsg" title="错误" min-width="160" />
        <vxe-column field="startTime" title="开始时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </vxe-column>
        <vxe-column field="endTime" title="结束时间" width="170">
          <template #default="{ row }">{{ row.endTime ? formatDateTime(row.endTime) : '-' }}</template>
        </vxe-column>
        <vxe-column title="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">步骤明细</el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @size-change="getList" @current-change="getList" />
    </el-card>

    <el-drawer v-model="detailVisible" :title="`运行 #${currentRun?.id} 步骤明细`" size="60%">
      <el-table :data="steps" border size="small">
        <el-table-column prop="seq" label="#" width="50" />
        <el-table-column prop="nodeName" label="节点" width="110" />
        <el-table-column prop="stepType" label="类型" width="100" />
        <el-table-column prop="toolName" label="工具" width="110" />
        <el-table-column label="输入/输出" min-width="320">
          <template #default="{ row }">
            <el-collapse>
              <el-collapse-item :title="String(row.stepOutput || row.errorMsg || '').slice(0, 80)" name="1">
                <pre class="step-json">{{ prettyJson(row.stepOutput) || row.errorMsg }}</pre>
              </el-collapse-item>
            </el-collapse>
          </template>
        </el-table-column>
        <el-table-column label="Tokens(in/out)" width="120">
          <template #default="{ row }">{{ row.tokensIn }}/{{ row.tokensOut }}</template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'FAILED' ? 'danger' : 'success'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getAgentRuns, getAgentSteps } from '@/api/ai/agent'
import type { AgentRun, AgentRunStep } from '@/api/ai/agent'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

defineOptions({ name: 'AiAgentRun' })

const { tableHeight } = useTableHeight()
const { seqMethod } = useTableSeq({
  currentPage: computed(() => queryParams.pageNum),
  pageSize: computed(() => queryParams.pageSize)
})

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const loading = ref(false)
const tableData = ref<AgentRun[]>([])
const total = ref(0)
const queryParams = reactive({ status: '', pageNum: 1, pageSize: 10 })
const detailVisible = ref(false)
const currentRun = ref<AgentRun | null>(null)
const steps = ref<AgentRunStep[]>([])

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value!)
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    const result = await getAgentRuns({ status: queryParams.status || undefined, pageNum: queryParams.pageNum, pageSize: queryParams.pageSize })
    tableData.value = result.records
    total.value = Number(result.total)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.status = ''; handleQuery() }

const handleDetail = async (row: AgentRun) => {
  currentRun.value = row
  steps.value = await getAgentSteps(row.id)
  detailVisible.value = true
}

const statusLabel = (s: string) => ({ RUNNING: '运行中', WAITING_APPROVAL: '待确认', SUCCEEDED: '成功', FAILED: '失败', CANCELLED: '已取消' } as Record<string, string>)[s] || s
const triggerLabel = (t: string) => ({ CONVERSATION: '会话', MANUAL: '手动', SCHEDULED: '调度' } as Record<string, string>)[t] || t
const triggerType = (t: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' =>
  (({ CONVERSATION: 'primary', MANUAL: 'warning', SCHEDULED: 'success' } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>)[t] || 'info')
const statusType = (s: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' =>
  (({ RUNNING: 'primary', WAITING_APPROVAL: 'warning', SUCCEEDED: 'success', FAILED: 'danger', CANCELLED: 'info' } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>)[s] || 'info')

const prettyJson = (raw: string | null) => {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card { margin-bottom: 15px; }
  .table-card { padding: 15px; .el-pagination { margin-top: 15px; justify-content: flex-end; } }
  .step-json { white-space: pre-wrap; word-break: break-all; font-size: 12px; max-height: 320px; overflow-y: auto; }
}
</style>
