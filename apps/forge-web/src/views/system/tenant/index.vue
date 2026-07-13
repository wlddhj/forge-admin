<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="租户名称">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入租户名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="租户标识">
          <el-input
            v-model="queryParams.code"
            placeholder="请输入租户标识"
            clearable
            @keyup.enter="handleQuery"
          />
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
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'system:tenant:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增租户
          </el-button>
        </template>
        <template #tools>
          <vxe-button
            circle
            icon="vxe-icon-refresh"
            style="margin-right: 10px"
            @click="getList"
          />
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="sysTenantTable"
        :custom-config="{ mode: 'modal' }"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :seq-config="{ seqMethod }"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" />

        <vxe-column field="name" title="租户名称" min-width="160" />

        <vxe-column field="code" title="租户标识" width="140" />

        <vxe-column title="联系人" min-width="120">
          <template #default="{ row }">
            {{ row.contactName || '-' }}
          </template>
        </vxe-column>

        <vxe-column title="联系电话" width="140">
          <template #default="{ row }">
            {{ row.contactPhone || '-' }}
          </template>
        </vxe-column>

        <vxe-column title="套餐" min-width="140">
          <template #default="{ row }">
            <el-tag v-if="row.packageName" type="info" size="small">
              {{ row.packageName }}
            </el-tag>
            <span v-else style="color: #909399;">未分配</span>
          </template>
        </vxe-column>

        <vxe-column title="到期时间" width="180">
          <template #default="{ row }">
            <span v-if="row.expireTime">{{ formatDateTime(row.expireTime) }}</span>
            <el-tag v-else type="success" size="small">永久</el-tag>
          </template>
        </vxe-column>

        <vxe-column title="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="!hasPermission('system:tenant:update')"
              @change="handleStatusChange(row)"
            />
          </template>
        </vxe-column>

        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>

        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:tenant:update'"
              link
              type="primary"
              size="small"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'system:tenant:delete'"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <div class="pagination-container">
        <TablePagination
          v-model:page-num="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          @change="getList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="租户名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入租户名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="租户标识" prop="code">
          <el-input
            v-model="formData.code"
            placeholder="登录用租户标识（如 acme）"
            maxlength="50"
            :disabled="!!formData.id"
          />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="formData.contactName" placeholder="请输入联系人" maxlength="50" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" maxlength="20" />
        </el-form-item>
        <el-form-item label="套餐" prop="packageId">
          <el-select
            v-model="formData.packageId"
            placeholder="请选择套餐（可选）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in packageOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="到期时间" prop="expireTime">
          <el-date-picker
            v-model="formData.expireTime"
            type="datetime"
            placeholder="选择到期时间（留空表示永久）"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="官网" prop="website">
          <el-input v-model="formData.website" placeholder="请输入租户官网（可选）" maxlength="200" />
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
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
            maxlength="200"
          />
        </el-form-item>
        <el-form-item v-if="!formData.id" label="管理员账号">
          <el-input
            v-model="formData.adminUsername"
            placeholder="租户管理员用户名（默认 admin）"
            maxlength="20"
          />
          <div class="form-tip">创建后系统会自动生成初始密码，请妥善保存。</div>
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
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import {
  getTenantList,
  addTenant,
  updateTenant,
  deleteTenant,
  changeTenantStatus,
  type TenantEntity,
  type TenantQuery,
  type TenantRequest
} from '@/api/system/tenant'
import { getEnabledTenantPackages } from '@/api/system/tenant-package'
import { useDict } from '@/composables/useDict'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { DICT_TYPE } from '@/constants/dict'
import { formatDateTime } from '@/utils/dateFormat'
import TablePagination from '@/components/TablePagination.vue'
import { hasPermission } from '@/directives/permission'

defineOptions({
  name: 'SystemTenant'
})

const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

// 查询参数
const queryParams = reactive<TenantQuery>({
  pageNum: 1,
  pageSize: 20,
  name: '',
  code: '',
  status: undefined
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 表格数据
const tableData = ref<TenantEntity[]>([])
const loading = ref(false)
const total = ref(0)

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新增租户')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 套餐下拉选项
const packageOptions = ref<{ id: number; name: string }[]>([])

// 表单数据
const formData = reactive<TenantRequest>({
  id: undefined,
  name: '',
  code: '',
  contactName: '',
  contactPhone: '',
  status: 1,
  packageId: null,
  expireTime: null,
  website: '',
  remark: '',
  adminUsername: 'admin'
})

// 表单校验规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入租户标识', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_-]{1,49}$/,
      message: '租户标识需以字母开头，仅允许字母、数字、下划线、短横线',
      trigger: 'blur'
    }
  ],
  contactPhone: [
    {
      pattern: /^$|^1[3-9]\d{9}$|^0\d{2,3}-?\d{7,8}$/,
      message: '请输入有效的手机号或座机号',
      trigger: 'blur'
    }
  ]
}

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

// 查询列表
const getList = async () => {
  loading.value = true
  try {
    const data = await getTenantList(queryParams)
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 加载启用的套餐
const loadPackageOptions = async () => {
  try {
    packageOptions.value = await getEnabledTenantPackages()
  } catch {
    packageOptions.value = []
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.name = ''
  queryParams.code = ''
  queryParams.status = undefined
  handleQuery()
}

// 重置表单
const resetForm = () => {
  formData.id = undefined
  formData.name = ''
  formData.code = ''
  formData.contactName = ''
  formData.contactPhone = ''
  formData.status = 1
  formData.packageId = null
  formData.expireTime = null
  formData.website = ''
  formData.remark = ''
  formRef.value?.resetFields()
}

// 新增
const handleAdd = () => {
  dialogTitle.value = '新增租户'
  resetForm()
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: TenantEntity) => {
  dialogTitle.value = '编辑租户'
  resetForm()
  Object.assign(formData, {
    id: row.id,
    name: row.name,
    code: row.code,
    contactName: row.contactName || '',
    contactPhone: row.contactPhone || '',
    status: row.status,
    packageId: row.packageId ?? null,
    expireTime: row.expireTime || null,
    website: row.website || '',
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

// 提交
const handleSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      await updateTenant(formData)
      ElMessage.success('更新成功')
      dialogVisible.value = false
    } else {
      const result = await addTenant(formData)
      dialogVisible.value = false
      // 创建成功：弹窗展示初始凭据，提示用户保存
      showInitialCredentials(result)
    }
    getList()
  } finally {
    submitLoading.value = false
  }
}

// 展示初始管理员凭据
const showInitialCredentials = (tenant: TenantEntity) => {
  const adminUsername = formData.adminUsername || 'admin'
  const password = tenant.initialAdminPassword || '(未返回)'
  ElMessageBox.alert(
    `<div style="line-height: 1.8">
      <p style="margin: 0 0 8px 0">租户「<strong>${tenant.name}</strong>」已创建，请保存以下管理员凭据：</p>
      <p style="margin: 4px 0">租户标识：<code style="background: #f5f7fa; padding: 2px 6px; border-radius: 3px;">${tenant.code}</code></p>
      <p style="margin: 4px 0">登录账号：<code style="background: #f5f7fa; padding: 2px 6px; border-radius: 3px;">${adminUsername}</code></p>
      <p style="margin: 4px 0">初始密码：<code style="background: #fff7e6; padding: 2px 6px; border-radius: 3px; color: #d46b08; font-weight: 600;">${password}</code></p>
      <p style="margin: 12px 0 0 0; color: #d46b08; font-size: 13px;">
        ⚠ 该密码仅本次显示，无法再次查看。租户管理员首次登录需强制修改密码。
      </p>
    </div>`,
    '租户创建成功',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '我已保存',
      type: 'success',
      callback: () => {
        // 自动复制密码到剪贴板
        if (navigator.clipboard && password !== '(未返回)') {
          navigator.clipboard.writeText(password).then(() => {
            ElMessage.success('初始密码已复制到剪贴板')
          }).catch(() => {})
        }
      }
    }
  )
}

// 删除
const handleDelete = (row: TenantEntity) => {
  ElMessageBox.confirm(
    `确定要删除租户「${row.name}」吗？该操作会级联清理相关数据，请谨慎操作。`,
    '提示',
    { type: 'warning' }
  ).then(async () => {
    await deleteTenant([row.id])
    ElMessage.success('删除成功')
    getList()
  })
}

// 状态变更
const handleStatusChange = async (row: TenantEntity) => {
  try {
    await changeTenantStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === 1 ? 0 : 1
  }
}

// 初始化
onMounted(() => {
  getList()
  loadPackageOptions()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .form-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
    line-height: 1.5;
  }

  .table-card {
    .pagination-container {
      margin-top: 15px;
      display: flex;
      justify-content: flex-end;
    }
  }
}
</style>
