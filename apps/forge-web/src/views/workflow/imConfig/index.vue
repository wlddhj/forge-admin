<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form inline @submit.prevent>
        <el-form-item label="租户">
          <el-select
            v-model="tenantId"
            placeholder="请选择租户"
            style="width: 260px"
            :loading="tenantLoading"
            @change="getList"
          >
            <el-option v-for="t in tenantOptions" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="getList"><el-icon><Refresh /></el-icon>刷新</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        title="每租户每渠道一行；租户未配置或停用时该租户的审批消息不推送（含钉钉已扫码用户）。secret 加密存储、不回显，留空表示保持不变。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 4px"
      />
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-dropdown v-permission="'workflow:im:config:edit'" @command="handleAdd">
            <el-button type="primary" :disabled="missingChannels.length === 0">
              <el-icon><Plus /></el-icon>新增渠道配置<el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="ch in missingChannels" :key="ch" :command="ch">
                  {{ channelLabel(ch) }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="getList" />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="wfImConfigTable"
        ref="tableRef"
        :custom-config="{ mode: 'modal' }"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
      >
        <vxe-column title="渠道" width="140">
          <template #default="{ row }">
            <el-tag :type="row.channel === 'wework' ? 'success' : 'primary'" size="small">
              {{ channelLabel(row.channel) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="corpId / appKey" min-width="180">
          <template #default="{ row }">{{ row.channel === 'wework' ? row.corpId : row.appKey }}</template>
        </vxe-column>
        <vxe-column field="agentId" title="agentId" width="140" />
        <vxe-column title="secret" width="100">
          <template #default="{ row }">
            <el-tag :type="row.hasSecret ? 'success' : 'danger'" size="small">
              {{ row.hasSecret ? '已配置' : '未配置' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="remark" title="备注" min-width="140" />
        <vxe-column field="updateTime" title="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'workflow:im:config:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'workflow:im:config:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="渠道">
          <el-tag :type="formData.channel === 'wework' ? 'success' : 'primary'">
            {{ channelLabel(formData.channel) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="formData.channel === 'wework'" label="企业微信 corpId" prop="corpId">
          <el-input v-model="formData.corpId" placeholder="企微管理后台-我的企业-企业ID" />
        </el-form-item>
        <el-form-item v-if="formData.channel === 'dingtalk'" label="钉钉 appKey" prop="appKey">
          <el-input v-model="formData.appKey" placeholder="钉钉开放平台-应用凭证 AppKey" />
        </el-form-item>
        <el-form-item label="agentId" prop="agentId">
          <el-input v-model="formData.agentId" placeholder="应用 agentId（数字）" />
        </el-form-item>
        <el-form-item label="secret" prop="secret">
          <el-input
            v-model="formData.secret"
            :placeholder="editingId ? '留空保持不变' : '应用 secret'"
            show-password
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Refresh, Plus, ArrowDown } from '@element-plus/icons-vue'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import { formatDateTime } from '@/utils/dateFormat'
import { imConfigApi, type ImChannelConfig, type ImChannelConfigRequest, type TenantOption } from '@/api/workflow/im-config'

defineOptions({ name: 'WorkflowImConfig' })

const { tableHeight } = useTableHeight()
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const ALL_CHANNELS = ['wework', 'dingtalk'] as const

const channelLabel = (ch: string): string =>
  ch === 'wework' ? '企业微信' : ch === 'dingtalk' ? '钉钉' : ch

// ========== 租户下拉 ==========
const tenantLoading = ref(false)
const tenantOptions = ref<TenantOption[]>([])
const tenantId = ref<number>(1)

const loadTenants = async () => {
  tenantLoading.value = true
  try {
    const data = await imConfigApi.tenantList({ pageNum: 1, pageSize: 100 })
    tenantOptions.value = data.list || []
    if (tenantOptions.value.length > 0 && !tenantOptions.value.some(t => t.id === tenantId.value)) {
      tenantId.value = tenantOptions.value[0].id
    }
  } finally {
    tenantLoading.value = false
  }
}

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<ImChannelConfig[]>([])

const getList = async () => {
  if (!tenantId.value) return
  loading.value = true
  try {
    tableData.value = await imConfigApi.listByTenant(tenantId.value)
  } finally {
    loading.value = false
  }
}

const missingChannels = computed(() => {
  const configured = new Set(tableData.value.map(row => row.channel))
  return ALL_CHANNELS.filter(ch => !configured.has(ch))
})

onMounted(async () => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  await loadTenants()
  getList()
})

// ========== 新增/编辑 ==========
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance | null>(null)
const editingId = ref<number | null>(null)
const formData = reactive<ImChannelConfigRequest>({
  tenantId: 1,
  channel: 'wework',
  enabled: 1,
  corpId: '',
  appKey: '',
  agentId: '',
  secret: '',
  remark: ''
})

const formRules: FormRules = {
  agentId: [
    { required: true, message: 'agentId 不能为空', trigger: 'blur' },
    { pattern: /^\d+$/, message: 'agentId 必须为数字', trigger: 'blur' }
  ],
  corpId: [{ required: true, message: '企业微信 corpId 不能为空', trigger: 'blur' }],
  appKey: [{ required: true, message: '钉钉 appKey 不能为空', trigger: 'blur' }]
}

const handleAdd = (channel: string) => {
  editingId.value = null
  dialogTitle.value = `新增渠道配置 - ${channelLabel(channel)}`
  Object.assign(formData, {
    tenantId: tenantId.value,
    channel,
    enabled: 1,
    corpId: '',
    appKey: '',
    agentId: '',
    secret: '',
    remark: ''
  })
  dialogVisible.value = true
}

const handleEdit = (row: ImChannelConfig) => {
  editingId.value = row.id
  dialogTitle.value = `编辑渠道配置 - ${channelLabel(row.channel)}`
  Object.assign(formData, {
    tenantId: row.tenantId,
    channel: row.channel,
    enabled: row.enabled,
    corpId: row.corpId || '',
    appKey: row.appKey || '',
    agentId: row.agentId,
    secret: '',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!editingId.value && !formData.secret) {
    ElMessage.warning('新建配置时 secret 不能为空')
    return
  }
  submitLoading.value = true
  try {
    await imConfigApi.save({
      ...formData,
      secret: formData.secret || undefined
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: ImChannelConfig) => {
  const confirmed = await ElMessageBox.confirm(
    `确认删除租户 ${row.tenantId} 的 ${channelLabel(row.channel)} 配置？删除后该租户将不再推送。`,
    '删除确认',
    { type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return
  await imConfigApi.remove(row.id)
  ElMessage.success('删除成功')
  getList()
}
</script>

<style scoped>
.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
