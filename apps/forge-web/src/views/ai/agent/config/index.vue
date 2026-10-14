<template>
  <div class="app-container">
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="info" @click="getList">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </template>
      </vxe-toolbar>

      <vxe-table ref="tableRef" id="agentConfigTable" :data="tableData" :height="tableHeight" :loading="loading"
        :row-config="{ isHover: true }" stripe show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" />
        <vxe-column field="agentName" title="Agent 名称" min-width="160" />
        <vxe-column field="agentCode" title="编码" min-width="170" />
        <vxe-column field="graphKey" title="编排图" width="170" />
        <vxe-column field="description" title="描述" min-width="220" />
        <vxe-column field="maxSteps" title="步数上限" width="90" />
        <vxe-column title="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="'ai:agent:config'" @click="handleEdit(row)">编辑</el-button>
            <el-button :type="row.status === 1 ? 'danger' : 'success'" link size="small" v-permission="'ai:agent:config'" @click="handleToggle(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <AgentConfigForm ref="formRef" @success="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getAgentConfigs, agentApi } from '@/api/ai/agent'
import type { AgentConfig } from '@/api/ai/agent'
import { useTableHeight } from '@/composables/useTableHeight'
import AgentConfigForm from './components/AgentConfigForm.vue'

defineOptions({ name: 'AiAgentConfig' })

const { tableHeight } = useTableHeight({ hasPagination: false })
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const formRef = ref<InstanceType<typeof AgentConfigForm>>()
const loading = ref(false)
const tableData = ref<AgentConfig[]>([])

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value!)
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    tableData.value = await getAgentConfigs()
  } finally {
    loading.value = false
  }
}

const handleEdit = (row: AgentConfig) => formRef.value?.open(row)

const handleToggle = async (row: AgentConfig) => {
  await agentApi.updateConfig(row.id, { status: row.status === 1 ? 0 : 1 })
  ElMessage.success(row.status === 1 ? '已禁用' : '已启用')
  getList()
}
</script>
