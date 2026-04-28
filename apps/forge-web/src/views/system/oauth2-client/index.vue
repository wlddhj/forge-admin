<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="客户端ID">
          <el-input v-model="queryParams.clientId" placeholder="请输入客户端ID" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'system:oauth2-client:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="sysOAuth2ClientTable"
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
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="clientId" title="客户端ID" min-width="180" />
        <vxe-column field="clientName" title="客户端名称" min-width="150" />
        <vxe-column field="authorizationGrantTypes" title="授权类型" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="t in row.authorizationGrantTypes" :key="t" size="small" type="info" style="margin-right: 4px">{{ t }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="scopes" title="权限范围" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="s in row.scopes" :key="s" size="small" style="margin-right: 4px">{{ s }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="redirectUris" title="重定向URI" min-width="200">
          <template #default="{ row }">
            <span v-for="(uri, i) in row.redirectUris" :key="i">
              {{ uri }}<br v-if="i < row.redirectUris.length - 1" />
            </span>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:oauth2-client:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'system:oauth2-client:edit'" type="warning" link size="small" @click="handleResetSecret(row)">重置密钥</el-button>
            <el-button v-permission="'system:oauth2-client:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="客户端ID" prop="clientId">
          <el-input v-model="formData.clientId" placeholder="请输入客户端ID" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="客户端名称" prop="clientName">
          <el-input v-model="formData.clientName" placeholder="请输入客户端名称" />
        </el-form-item>
        <el-form-item label="授权类型">
          <el-checkbox-group v-model="formData.authorizationGrantTypes">
            <el-checkbox label="authorization_code">授权码模式</el-checkbox>
            <el-checkbox label="client_credentials">客户端凭证</el-checkbox>
            <el-checkbox label="refresh_token">刷新令牌</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="权限范围">
          <el-select v-model="formData.scopes" multiple filterable allow-create placeholder="请选择或输入">
            <el-option label="openid" value="openid" />
            <el-option label="profile" value="profile" />
            <el-option label="email" value="email" />
          </el-select>
        </el-form-item>
        <el-form-item label="重定向URI">
          <div style="width: 100%">
            <div v-for="(_uri, index) in formData.redirectUris" :key="index" style="display: flex; gap: 8px; margin-bottom: 8px">
              <el-input v-model="formData.redirectUris[index]" placeholder="请输入URI" />
              <el-button type="danger" link @click="formData.redirectUris.splice(index, 1)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" link @click="formData.redirectUris.push('')">添加</el-button>
          </div>
        </el-form-item>
        <el-form-item label="Token有效期(秒)">
          <el-input-number v-model="formData.accessTokenTimeToLive" :min="60" :max="86400" placeholder="Access Token" />
          <span style="margin: 0 8px">/</span>
          <el-input-number v-model="formData.refreshTokenTimeToLive" :min="60" :max="2592000" placeholder="Refresh Token" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 密钥展示对话框 -->
    <el-dialog v-model="secretDialogVisible" title="客户端密钥" width="500px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
        请妥善保存客户端密钥，关闭后无法再次查看！
      </el-alert>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="客户端ID">{{ secretInfo.clientId }}</el-descriptions-item>
        <el-descriptions-item label="客户端密钥">
          <div style="display: flex; align-items: center; gap: 8px">
            <code style="word-break: break-all">{{ secretInfo.clientSecret }}</code>
            <el-button type="primary" link @click="copySecret">复制</el-button>
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="secretDialogVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { oauth2ClientApi } from '@/api/oauth2-client'
import type { OAuth2Client, ClientCreateRequest, ClientUpdateRequest } from '@/types/oauth2-client'
import { Delete } from '@element-plus/icons-vue'

const { tableHeight } = useTableHeight()
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const loading = ref(false)
const tableData = ref<OAuth2Client[]>([])

const pageNumRef = computed(() => 1)
const pageSizeRef = computed(() => 100)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})

// 查询参数
const queryParams = reactive({
  clientId: '',
  clientName: ''
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive<{
  id: string
  clientId: string
  clientName: string
  authorizationGrantTypes: string[]
  scopes: string[]
  redirectUris: string[]
  accessTokenTimeToLive: number
  refreshTokenTimeToLive: number
}>({
  id: '',
  clientId: '',
  clientName: '',
  authorizationGrantTypes: ['authorization_code', 'refresh_token'],
  scopes: ['openid', 'profile'],
  redirectUris: [''],
  accessTokenTimeToLive: 3600,
  refreshTokenTimeToLive: 86400
})

const formRules: FormRules = {
  clientId: [{ required: true, message: '请输入客户端ID', trigger: 'blur' }],
  clientName: [{ required: true, message: '请输入客户端名称', trigger: 'blur' }]
}

// 密钥展示
const secretDialogVisible = ref(false)
const secretInfo = reactive({ clientId: '', clientSecret: '' })

const getList = async () => {
  loading.value = true
  try {
    const res = await oauth2ClientApi.list(queryParams)
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

const handleQuery = () => getList()
const handleReset = () => {
  queryParams.clientId = ''
  queryParams.clientName = ''
  getList()
}

const resetForm = () => {
  formData.id = ''
  formData.clientId = ''
  formData.clientName = ''
  formData.authorizationGrantTypes = ['authorization_code', 'refresh_token']
  formData.scopes = ['openid', 'profile']
  formData.redirectUris = ['']
  formData.accessTokenTimeToLive = 3600
  formData.refreshTokenTimeToLive = 86400
}

const handleAdd = () => {
  resetForm()
  isEdit.value = false
  dialogTitle.value = '新增OAuth2客户端'
  dialogVisible.value = true
}

const handleEdit = (row: OAuth2Client) => {
  formData.id = row.id
  formData.clientId = row.clientId
  formData.clientName = row.clientName
  formData.authorizationGrantTypes = [...row.authorizationGrantTypes]
  formData.scopes = [...row.scopes]
  formData.redirectUris = [...row.redirectUris]
  formData.accessTokenTimeToLive = 3600
  formData.refreshTokenTimeToLive = 86400
  isEdit.value = true
  dialogTitle.value = '编辑OAuth2客户端'
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (isEdit.value) {
          const data: ClientUpdateRequest = {
            id: formData.id,
            clientName: formData.clientName,
            redirectUris: formData.redirectUris.filter(Boolean),
            authorizationGrantTypes: formData.authorizationGrantTypes,
            scopes: formData.scopes,
            accessTokenTimeToLive: formData.accessTokenTimeToLive,
            refreshTokenTimeToLive: formData.refreshTokenTimeToLive
          }
          await oauth2ClientApi.update(data)
          ElMessage.success('修改成功')
        } else {
          const data: ClientCreateRequest = {
            clientId: formData.clientId,
            clientName: formData.clientName,
            redirectUris: formData.redirectUris.filter(Boolean),
            authorizationGrantTypes: formData.authorizationGrantTypes,
            clientAuthenticationMethods: ['client_secret_basic'],
            scopes: formData.scopes,
            accessTokenTimeToLive: formData.accessTokenTimeToLive,
            refreshTokenTimeToLive: formData.refreshTokenTimeToLive
          }
          const res = await oauth2ClientApi.add(data)
          ElMessage.success('新增成功')
          // 展示密钥
          secretInfo.clientId = res.data?.clientId || ''
          secretInfo.clientSecret = res.data?.clientSecret || ''
          secretDialogVisible.value = true
        }
        dialogVisible.value = false
        getList()
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleDelete = async (row: OAuth2Client) => {
  await ElMessageBox.confirm(`确认删除客户端「${row.clientName}」？`, '提示', { type: 'warning' })
  await oauth2ClientApi.delete([row.id])
  ElMessage.success('删除成功')
  getList()
}

const handleResetSecret = async (row: OAuth2Client) => {
  await ElMessageBox.confirm(`确认重置客户端「${row.clientName}」的密钥？旧密钥将立即失效。`, '提示', { type: 'warning' })
  const res = await oauth2ClientApi.regenerateSecret(row.id)
  secretInfo.clientId = row.clientId
  secretInfo.clientSecret = res.data?.clientSecret || ''
  secretDialogVisible.value = true
}

const copySecret = () => {
  navigator.clipboard.writeText(secretInfo.clientSecret)
  ElMessage.success('已复制到剪贴板')
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .search-card { margin-bottom: 15px; }
}
</style>
