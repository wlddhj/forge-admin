<template>
  <div class="app-container">
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="info" @click="getList">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button v-permission="'ai:team:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增团队
          </el-button>
        </template>
      </vxe-toolbar>

      <vxe-table id="agentTeamTable" ref="tableRef" :data="tableData" :height="tableHeight" :loading="loading"
        :row-config="{ isHover: true }" stripe show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" />
        <vxe-column field="teamName" title="团队名称" min-width="140" />
        <vxe-column field="teamCode" title="编码" min-width="130" />
        <vxe-column title="成员" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="m in row.members" :key="m.agentId" size="small" style="margin-right: 4px">
              {{ m.agentName || m.agentId }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="maxRounds" title="轮次上限" width="90" />
        <vxe-column field="description" title="描述" min-width="180" />
        <vxe-column title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:team:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'ai:team:remove'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <TeamForm ref="formRef" :agent-options="agentOptions" @success="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getAgentTeams, agentTeamApi } from '@/api/ai/agentTeam'
import type { AgentTeam } from '@/api/ai/agentTeam'
import { getAgentConfigs } from '@/api/ai/agent'
import { useTableHeight } from '@/composables/useTableHeight'
import TeamForm from './components/TeamForm.vue'

defineOptions({ name: 'AiAgentTeam' })

const { tableHeight } = useTableHeight({ hasPagination: false })
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const formRef = ref<InstanceType<typeof TeamForm>>()
const loading = ref(false)
const tableData = ref<AgentTeam[]>([])
const agentConfigs = ref<{ id: number; agentName: string; agentCode: string }[]>([])

const agentOptions = computed(() =>
  agentConfigs.value.map(c => ({ value: c.id, label: `${c.agentName}（${c.agentCode}）` }))
)

const getList = async () => {
  loading.value = true
  try {
    tableData.value = await getAgentTeams()
  } finally {
    loading.value = false
  }
}

const handleAdd = () => formRef.value?.open()
const handleEdit = (row: AgentTeam) => formRef.value?.open(row)

const handleDelete = (row: AgentTeam) => {
  ElMessageBox.confirm(`确认删除团队「${row.teamName}」？成员配置将一并删除。`, '提示', { type: 'warning' })
    .then(async () => {
      await agentTeamApi.remove([row.id])
      ElMessage.success('删除成功')
      getList()
    })
    .catch(() => { })
}

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value!)
  getList()
  getAgentConfigs(1).then(list => (agentConfigs.value = list))
})
</script>
