<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline @submit.prevent>
        <el-form-item label="模型类型">
          <el-select v-model="queryParams.modelType" placeholder="全部类型" clearable style="width: 200px">
            <el-option
              v-for="item in dictData"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="getList">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'ai:model:add'" type="primary" @click="handleAdd">
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
        id="aiModelTable"
        ref="tableRef"
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
        <vxe-column field="modelCode" title="模型代码" min-width="140" />
        <vxe-column field="provider" title="提供商" width="100" />
        <vxe-column title="模型类型" width="110">
          <template #default="{ row }">
            <DictValue :dict-type="DICT_TYPE.AI_MODEL_TYPE" :value="row.modelType" />
          </template>
        </vxe-column>
        <vxe-column field="maxTokens" title="最大 Tokens" width="110" />
        <vxe-column field="temperature" title="Temperature" width="100" />
        <vxe-column title="状态" width="90">
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
        <vxe-column field="createTime" title="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:model:config'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'ai:model:switch'" type="warning" link size="small" :disabled="row.isDefault === 1" @click="handleSetDefault(row)">设为默认</el-button>
            <el-button type="success" link size="small" @click="handleRefresh(row)">刷新</el-button>
            <el-button v-permission="'ai:model:delete'" type="danger" link size="small" :disabled="row.isDefault === 1" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <ModelConfigForm ref="formRef" @success="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getModelList, switchModel, deleteModel, refreshModelStatus, refreshAllModels } from '@/api/ai/model'
import type { ModelConfigResponse } from '@/api/ai/model'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { DICT_TYPE } from '@/constants/dict'
import { useDict } from '@/composables/useDict'
import DictValue from '@/components/DictValue.vue'
import ModelConfigForm from './components/ModelConfigForm.vue'

defineOptions({ name: 'AiModel' })

const { tableHeight } = useTableHeight({ hasPagination: false })
const { dictData } = useDict(DICT_TYPE.AI_MODEL_TYPE)

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const formRef = ref<InstanceType<typeof ModelConfigForm>>()

const loading = ref(false)
const tableData = ref<ModelConfigResponse[]>([])
const queryParams = reactive({ modelType: '' })

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    tableData.value = await getModelList(queryParams.modelType || undefined)
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  queryParams.modelType = ''
  getList()
}

// 新增 / 编辑
const handleAdd = () => {
  formRef.value?.open('create')
}

const handleEdit = (row: ModelConfigResponse) => {
  formRef.value?.open('update', row)
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
}
</style>
