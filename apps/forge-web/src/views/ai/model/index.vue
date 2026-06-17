<template>
  <div class="app-container">
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" v-permission="'ai:model:add'" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增
          </el-button>
          <el-button type="info" @click="handleRefreshAll">
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
        <vxe-column field="modelName" title="模型名称" min-width="150" />
        <vxe-column field="provider" title="提供商" width="120" />
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
            <el-tag v-if="row.isDefault === 1" type="warning">默认</el-tag>
            <span v-else>-</span>
          </template>
        </vxe-column>
        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="'ai:model:config'" @click="handleConfig(row)">配置</el-button>
            <el-button type="warning" link size="small" v-permission="'ai:model:switch'" @click="handleSetDefault(row)" :disabled="row.isDefault === 1">设为默认</el-button>
            <el-button type="success" link size="small" @click="handleRefresh(row)">刷新</el-button>
            <el-button type="danger" link size="small" v-permission="'ai:model:delete'" @click="handleDelete(row)" :disabled="row.isDefault === 1">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 新增对话框 -->
    <el-dialog v-model="addDialogVisible" title="新增模型配置" width="600px" @close="handleAddDialogClose">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="120px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="addForm.modelName" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="模型代码" prop="modelCode">
          <el-input v-model="addForm.modelCode" placeholder="请输入模型代码（可选）" />
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-input v-model="addForm.provider" placeholder="请输入提供商" />
        </el-form-item>
        <el-form-item label="API Endpoint" prop="apiEndpoint">
          <el-input v-model="addForm.apiEndpoint" placeholder="请输入 API Endpoint（可选）" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="addForm.apiKey" type="password" placeholder="请输入 API Key（可选）" show-password />
        </el-form-item>
        <el-form-item label="最大 Tokens" prop="maxTokens">
          <el-input-number v-model="addForm.maxTokens" :min="1" :max="100000" :step="100" />
        </el-form-item>
        <el-form-item label="Temperature" prop="temperature">
          <el-slider v-model="addForm.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="addForm.remark" type="textarea" placeholder="请输入备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitLoading" @click="handleAddSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 配置对话框 -->
    <el-dialog v-model="configDialogVisible" title="模型配置" width="600px" @close="handleConfigDialogClose">
      <el-form ref="configFormRef" :model="configForm" :rules="configRules" label-width="120px">
        <el-form-item label="模型名称">
          <el-input :value="currentModel?.modelName" disabled />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="configForm.apiKey" type="password" placeholder="请输入 API Key" show-password />
        </el-form-item>
        <el-form-item label="API Endpoint" prop="apiEndpoint">
          <el-input v-model="configForm.apiEndpoint" placeholder="请输入 API Endpoint（可选）" />
        </el-form-item>
        <el-form-item label="最大 Tokens" prop="maxTokens">
          <el-input-number v-model="configForm.maxTokens" :min="1" :max="100000" :step="100" />
        </el-form-item>
        <el-form-item label="Temperature" prop="temperature">
          <el-slider v-model="configForm.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSubmitLoading" @click="handleConfigSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getModelList, addModel, configModel, switchModel, deleteModel, refreshModelStatus, refreshAllModels } from '@/api/ai/model'
import type { ModelConfigResponse, AddModelRequest, ModelConfigRequest } from '@/api/ai/model'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'

const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<ModelConfigResponse[]>([])

// 新增对话框
const addDialogVisible = ref(false)
const addFormRef = ref<FormInstance>()
const addSubmitLoading = ref(false)
const addForm = reactive<AddModelRequest>({
  modelName: '',
  modelCode: '',
  provider: '',
  apiEndpoint: '',
  apiKey: '',
  maxTokens: 4096,
  temperature: 0.7,
  remark: ''
})

const addRules: FormRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入提供商', trigger: 'blur' }]
}

// 配置对话框
const configDialogVisible = ref(false)
const configFormRef = ref<FormInstance>()
const configSubmitLoading = ref(false)
const currentModel = ref<ModelConfigResponse | null>(null)
const configForm = reactive<ModelConfigRequest>({
  apiKey: '',
  apiEndpoint: '',
  maxTokens: 4096,
  temperature: 0.7
})

const configRules: FormRules = {
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

// 新增
const handleAdd = () => {
  addDialogVisible.value = true
}

const handleAddSubmit = async () => {
  if (!addFormRef.value) return
  await addFormRef.value.validate(async (valid) => {
    if (valid) {
      addSubmitLoading.value = true
      try {
        await addModel(addForm)
        ElMessage.success('新增成功')
        addDialogVisible.value = false
        getList()
      } finally {
        addSubmitLoading.value = false
      }
    }
  })
}

const handleAddDialogClose = () => {
  addFormRef.value?.resetFields()
}

// 配置
const handleConfig = (row: ModelConfigResponse) => {
  currentModel.value = row
  configForm.apiKey = row.apiKey || ''
  configForm.apiEndpoint = row.apiEndpoint || ''
  configForm.maxTokens = row.maxTokens
  configForm.temperature = row.temperature
  configDialogVisible.value = true
}

const handleConfigSubmit = async () => {
  if (!configFormRef.value || !currentModel.value) return
  await configFormRef.value.validate(async (valid) => {
    if (valid) {
      configSubmitLoading.value = true
      try {
        await configModel(currentModel.value!.id, configForm)
        ElMessage.success('配置成功')
        configDialogVisible.value = false
        getList()
      } finally {
        configSubmitLoading.value = false
      }
    }
  })
}

const handleConfigDialogClose = () => {
  configFormRef.value?.resetFields()
  currentModel.value = null
}

// 设为默认
const handleSetDefault = async (row: ModelConfigResponse) => {
  ElMessageBox.confirm(`确定要将"${row.modelName}"设为默认模型吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    await switchModel(row.id)
    ElMessage.success('设置成功')
    getList()
  })
}

// 删除
const handleDelete = async (row: ModelConfigResponse) => {
  ElMessageBox.confirm(`确定要删除"${row.modelName}"吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    await deleteModel(row.id)
    ElMessage.success('删除成功')
    getList()
  })
}

// 刷新状态
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
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .table-card {
    padding: 15px;
  }
}
</style>