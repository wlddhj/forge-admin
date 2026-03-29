<template>
  <div class="file-config-container">
    <!-- 搜索表单 -->
    <el-card class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="配置名称">
          <el-input
            v-model="queryParams.configName"
            placeholder="请输入配置名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="存储类型">
          <el-select v-model="queryParams.storageType" placeholder="请选择存储类型" clearable style="width: 140px">
            <el-option
              v-for="item in storageTypeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span class="title">文件存储配置列表</span>
          <div class="toolbar">
            <el-button type="primary" @click="handleAdd" v-permission="['system:file-config:add']">
              <el-icon><Plus /></el-icon>
              新增配置
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="configName" label="配置名称" min-width="150" />
        <el-table-column prop="storageType" label="存储类型" width="120">
          <template #default="{ row }">
            <dict-value :dict-type="DICT_TYPE.SYS_STORAGE_TYPE" :value="row.storageType" />
          </template>
        </el-table-column>
        <el-table-column prop="endpoint" label="服务端点" min-width="200" show-overflow-tooltip />
        <el-table-column prop="bucketName" label="存储桶" width="150" show-overflow-tooltip />
        <el-table-column prop="basePath" label="基础路径" width="150" show-overflow-tooltip />
        <el-table-column prop="isDefault" label="默认" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success">默认</el-tag>
            <el-link v-else type="primary" @click="handleSetDefault(row)">设为默认</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)" v-permission="['system:file-config:edit']">
              编辑
            </el-button>
            <el-button link type="primary" @click="handleTest(row)">
              测试
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)" v-permission="['system:file-config:delete']">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleQuery"
          @current-change="handleQuery"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="formData.configName" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="存储类型" prop="storageType">
          <el-select v-model="formData.storageType" placeholder="请选择存储类型" @change="handleStorageTypeChange">
            <el-option
              v-for="item in storageTypeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>

        <!-- 本地存储配置 -->
        <template v-if="formData.storageType === 'local'">
          <el-form-item label="基础路径" prop="basePath">
            <el-input v-model="formData.basePath" placeholder="/uploads" />
          </el-form-item>
        </template>

        <!-- 云存储配置 -->
        <template v-if="['aliyun_oss', 'tencent_cos', 'minio'].includes(formData.storageType)">
          <el-form-item label="服务端点" prop="endpoint">
            <el-input v-model="formData.endpoint" placeholder="请输入服务端点URL" />
          </el-form-item>
          <el-form-item label="存储桶名称" prop="bucketName">
            <el-input v-model="formData.bucketName" placeholder="请输入存储桶名称" />
          </el-form-item>
          <el-form-item label="AccessKey" prop="accessKey">
            <el-input v-model="formData.accessKey" placeholder="请输入 AccessKey" />
          </el-form-item>
          <el-form-item label="SecretKey" prop="secretKey">
            <el-input v-model="formData.secretKey" type="password" placeholder="请输入 SecretKey" show-password />
          </el-form-item>
          <el-form-item label="自定义域名" prop="domain">
            <el-input v-model="formData.domain" placeholder="可选，自定义访问域名" />
          </el-form-item>
        </template>

        <el-form-item label="设为默认">
          <el-switch v-model="formData.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio
              v-for="item in statusOptions"
              :key="item.dictValue"
              :value="Number(item.dictValue)"
            >
              {{ item.dictLabel }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getFileConfigList,
  addFileConfig,
  updateFileConfig,
  deleteFileConfig,
  setDefaultConfig,
  updateFileConfigStatus,
  testFileConfig
} from '@/api/system/file-config'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import DictValue from '@/components/DictValue.vue'

defineOptions({
  name: 'FileConfig'
})

const { dictData: storageTypeOptions } = useDict(DICT_TYPE.SYS_STORAGE_TYPE)
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  configName: '',
  storageType: '',
  status: undefined as number | undefined
})

// 表格数据
const tableData = ref<any[]>([])
const loading = ref(false)
const total = ref(0)

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新增配置')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive({
  id: undefined as number | undefined,
  configName: '',
  storageType: 'local',
  endpoint: '',
  bucketName: '',
  accessKey: '',
  secretKey: '',
  domain: '',
  basePath: '/uploads',
  isDefault: 0,
  status: 1,
  remark: ''
})

// 表单校验规则
const formRules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  storageType: [{ required: true, message: '请选择存储类型', trigger: 'change' }]
}

// 获取存储类型标签
const getStorageTypeTag = (type: string) => {
  const map: Record<string, string> = {
    local: '',
    aliyun_oss: 'success',
    tencent_cos: 'warning',
    minio: 'info'
  }
  return map[type] || ''
}

// 获取存储类型标签
const getStorageTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    local: '本地存储',
    aliyun_oss: '阿里云 OSS',
    tencent_cos: '腾讯云 COS',
    minio: 'MinIO'
  }
  return map[type] || type
}

// 查询列表
const getList = async () => {
  loading.value = true
  try {
    const { data } = await getFileConfigList(queryParams)
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.configName = ''
  queryParams.storageType = ''
  queryParams.status = undefined
  handleQuery()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增配置'
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: any) => {
  dialogTitle.value = '编辑配置'
  resetForm()
  Object.assign(formData, row)
  dialogVisible.value = true
}

// 重置表单
const resetForm = () => {
  formData.id = undefined
  formData.configName = ''
  formData.storageType = 'local'
  formData.endpoint = ''
  formData.bucketName = ''
  formData.accessKey = ''
  formData.secretKey = ''
  formData.domain = ''
  formData.basePath = '/uploads'
  formData.isDefault = 1
  formData.status = 1
  formData.remark = ''
  formRef.value?.resetFields()
}

// 存储类型变更
const handleStorageTypeChange = () => {
  // 切换存储类型时清空相关配置
  formData.endpoint = ''
  formData.bucketName = ''
  formData.accessKey = ''
  formData.secretKey = ''
  formData.domain = ''
  if (formData.storageType === 'local') {
    formData.basePath = '/uploads'
  }
}

// 提交
const handleSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      await updateFileConfig(formData)
      ElMessage.success('更新成功')
    } else {
      await addFileConfig(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

// 删除
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除配置「${row.configName}」吗?`, '提示', {
    type: 'warning'
  }).then(async () => {
    await deleteFileConfig([row.id])
    ElMessage.success('删除成功')
    getList()
  })
}

// 设为默认
const handleSetDefault = async (row: any) => {
  await setDefaultConfig(row.id)
  ElMessage.success('设置成功')
  getList()
}

// 状态变更
const handleStatusChange = async (row: any) => {
  try {
    await updateFileConfigStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === 1 ? 0 : 1
  }
}

// 测试连接
const handleTest = async (row: any) => {
  try {
    await testFileConfig(row.id)
    ElMessage.success('连接测试成功')
  } catch {
    ElMessage.error('连接测试失败')
  }
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.file-config-container {
  padding: 20px;

  .search-card {
    margin-bottom: 20px;
  }

  .table-card{
    .card-header{
      display: flex;
      justify-content: space-between;
      align-items: center;

      .title{
        font-size: 16px;
        font-weight: 500;
      }
    }
  }

  .pagination-container{
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
