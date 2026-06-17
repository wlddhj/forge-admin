<template>
  <div class="app-container">
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" @click="handleRefreshAll">
            <el-icon><Refresh /></el-icon>
            刷新状态
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="getList"></vxe-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="aiModelTable"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        border="none"
        stripe
        show-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" />
        <vxe-column field="name" title="模型名称" min-width="150" />
        <vxe-column field="provider" title="提供商" width="120" />
        <vxe-column field="modelType" title="模型类型" width="120" />
        <vxe-column field="maxTokens" title="最大 Tokens" width="120" />
        <vxe-column field="temperature" title="Temperature" width="100" />
        <vxe-column title="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">正常</el-tag>
            <el-tag v-else-if="row.status === 0" type="info">禁用</el-tag>
            <el-tag v-else type="danger">异常</el-tag>
          </template>
        </vxe-column>
        <vxe-column title="默认" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="warning">默认</el-tag>
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleConfig(row)">配置</el-button>
            <el-button type="warning" link size="small" @click="handleSetDefault(row)" :disabled="row.isDefault">设为默认</el-button>
            <el-button type="success" link size="small" @click="handleRefresh(row)">刷新状态</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 配置对话框 -->
    <el-dialog v-model="dialogVisible" title="模型配置" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="模型名称">
          <el-input :value="currentModel?.name" disabled />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" placeholder="请输入 API Key" show-password />
        </el-form-item>
        <el-form-item label="API Endpoint" prop="apiEndpoint">
          <el-input v-model="form.apiEndpoint" placeholder="请输入 API Endpoint（可选）" />
        </el-form-item>
        <el-form-item label="最大 Tokens" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="100000" :step="100" />
        </el-form-item>
        <el-form-item label="Temperature" prop="temperature">
          <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getModelList, configModel, switchModel, refreshModelStatus, refreshAllModels } from '@/api/ai/model'
import type { ModelConfigResponse, ModelConfigRequest } from '@/api/ai/model'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'

const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<ModelConfigResponse[]>([])

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const currentModel = ref<ModelConfigResponse | null>(null)

const form = reactive<ModelConfigRequest>({
  apiKey: '',
  apiEndpoint: '',
  maxTokens: 4096,
  temperature: 0.7
})

const rules: FormRules = {
  maxTokens: [{ required: true, message: '请输入最大 Tokens', trigger: 'blur' }],
  temperature: [{ required: true, message: '请设置 Temperature', trigger: 'change' }]
}

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    tableData.value = await getModelList()
  } finally {
    loading.value = false
  }
}

const handleConfig = (row: ModelConfigResponse) => {
  currentModel.value = row
  form.apiKey = row.apiKey || ''
  form.apiEndpoint = row.apiEndpoint || ''
  form.maxTokens = row.maxTokens
  form.temperature = row.temperature
  dialogVisible.value = true
}

const handleSetDefault = async (row: ModelConfigResponse) => {
  ElMessageBox.confirm(`确定要将"${row.name}"设为默认模型吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    await switchModel(row.id)
    ElMessage.success('设置成功')
    getList()
  })
}

const handleRefresh = async (row: ModelConfigResponse) => {
  try {
    const result = await refreshModelStatus(row.id)
    ElMessage.success(result.status === 1 ? '连接正常' : '连接异常')
    getList()
  } catch (e) {
    ElMessage.error('刷新失败')
  }
}

const handleRefreshAll = async () => {
  try {
    await refreshAllModels()
    ElMessage.success('刷新完成')
    getList()
  } catch (e) {
    ElMessage.error('刷新失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value || !currentModel.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        await configModel(currentModel.value!.id, form)
        ElMessage.success('配置成功')
        dialogVisible.value = false
        getList()
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  currentModel.value = null
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .table-card {
    padding: 15px;
  }
}
</style>