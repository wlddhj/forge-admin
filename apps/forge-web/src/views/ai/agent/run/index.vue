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
      <vxe-table id="agentRunTable" ref="tableRef" :data="tableData" :height="tableHeight" :loading="loading"
        :row-config="{ isHover: true }" stripe show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="id" title="运行ID" width="90" />
        <vxe-column title="来源" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.teamName" type="warning" size="small">团队：{{ row.teamName }}</el-tag>
            <span v-else>Agent#{{ row.agentId }}</span>
          </template>
        </vxe-column>
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
      <TablePagination v-model:page-num="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @change="getList" />
    </el-card>

    <RunDetailDrawer ref="drawerRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getAgentRuns } from '@/api/ai/agent'
import type { AgentRun } from '@/api/ai/agent'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import RunDetailDrawer from './components/RunDetailDrawer.vue'

defineOptions({ name: 'AiAgentRun' })

const { tableHeight } = useTableHeight()
const { seqMethod } = useTableSeq({
  currentPage: computed(() => queryParams.pageNum),
  pageSize: computed(() => queryParams.pageSize)
})

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const drawerRef = ref<InstanceType<typeof RunDetailDrawer>>()
const loading = ref(false)
const tableData = ref<AgentRun[]>([])
const total = ref(0)
const queryParams = reactive({ status: '', pageNum: 1, pageSize: 10 })
const route = useRoute()

onMounted(async () => {
  tableRef.value?.connect(toolbarRef.value!)
  await getList()
  // 支持外部定位：?runId=N 直接打开该运行的步骤明细（Agent 任务页"结果"入口）
  const runId = Number(route.query.runId)
  if (runId) {
    try {
      await drawerRef.value?.open(runId)
    } catch {
      // 运行不存在则忽略
    }
  }
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

const handleDetail = (row: AgentRun) => drawerRef.value?.open(row)

const statusLabel = (s: string) => ({ RUNNING: '运行中', WAITING_APPROVAL: '待确认', SUCCEEDED: '成功', FAILED: '失败', CANCELLED: '已取消' } as Record<string, string>)[s] || s
const triggerLabel = (t: string) => ({ CONVERSATION: '会话', MANUAL: '手动', SCHEDULED: '调度' } as Record<string, string>)[t] || t
const triggerType = (t: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' =>
  (({ CONVERSATION: 'primary', MANUAL: 'warning', SCHEDULED: 'success' } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>)[t] || 'info')
const statusType = (s: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' =>
  (({ RUNNING: 'primary', WAITING_APPROVAL: 'warning', SUCCEEDED: 'success', FAILED: 'danger', CANCELLED: 'info' } as Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'>)[s] || 'info')
</script>

<style scoped lang="scss">

</style>
