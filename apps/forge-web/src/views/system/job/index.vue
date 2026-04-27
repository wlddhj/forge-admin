<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="任务分组">
          <el-input v-model="queryParams.jobGroup" placeholder="请输入任务分组" clearable style="width: 150px" />
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
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">定时任务</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="任务分组">
          <el-input v-model="queryParams.jobGroup" placeholder="请输入任务分组" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增任务
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysJobTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />

        <!-- 任务名称 -->
        <vxe-column field="jobName" title="任务名称" width="150" />

        <!-- 任务分组（桌面端） -->
        <vxe-column v-if="!isMobile" field="jobGroup" title="任务分组" width="100" />

        <!-- 调用目标 -->
        <vxe-column field="invokeTarget" title="调用目标" min-width="200" />

        <!-- cron表达式 -->
        <vxe-column field="cronExpression" title="cron表达式" width="120" />

        <!-- 状态 -->
        <vxe-column title="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="handleStatusChange(row)" />
          </template>
        </vxe-column>

        <!-- 超时 -->
        <vxe-column v-if="!isMobile" title="超时" width="80" align="center">
          <template #default="{ row }">{{ row.timeout ? row.timeout + 's' : '-' }}</template>
        </vxe-column>

        <!-- 重试 -->
        <vxe-column v-if="!isMobile" title="重试" width="80" align="center">
          <template #default="{ row }">{{ row.retryCount || 0 }}次</template>
        </vxe-column>

        <!-- 最后执行 -->
        <vxe-column v-if="!isMobile" title="最后执行" width="170">
          <template #default="{ row }">
            <span v-if="row.lastExecuteAt">{{ formatDateTime(row.lastExecuteAt) }}</span>
            <span v-else style="color: var(--el-text-color-placeholder)">-</span>
          </template>
        </vxe-column>

        <!-- 执行状态 -->
        <vxe-column v-if="!isMobile" title="执行状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lastExecuteStatus === 'SUCCESS'" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="row.lastExecuteStatus === 'FAIL'" type="danger" size="small">失败</el-tag>
            <el-tag v-else-if="row.lastExecuteStatus === 'TIMEOUT'" type="warning" size="small">超时</el-tag>
            <span v-else style="color: var(--el-text-color-placeholder)">-</span>
          </template>
        </vxe-column>

        <!-- 执行统计 -->
        <vxe-column v-if="!isMobile" title="执行统计" width="130" align="center">
          <template #default="{ row }">
            <span v-if="row.totalExecuteCount">
              {{ row.successCount || 0 }}<span style="color: var(--el-color-success)">✓</span> /
              {{ row.failureCount || 0 }}<span style="color: var(--el-color-danger)">✗</span>
            </span>
            <span v-else style="color: var(--el-text-color-placeholder)">-</span>
          </template>
        </vxe-column>

        <!-- 创建时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" type="warning" link @click.stop="handlePause(row)">暂停</el-button>
            <el-button v-else type="success" link @click.stop="handleResume(row)">恢复</el-button>
            <el-button type="primary" link @click.stop="handleRunOnce(row)">执行一次</el-button>
            <el-button type="info" link @click.stop="handleViewLog(row)">日志</el-button>
            <el-button type="primary" link @click.stop="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.jobName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" v-if="item.status === 1" type="warning" @click.stop="handlePause(item)">暂停</el-button>
        <el-button size="small" v-else type="success" @click.stop="handleResume(item)">恢复</el-button>
        <el-button size="small" type="primary" @click.stop="handleRunOnce(item)">执行一次</el-button>
        <el-button size="small" type="info" @click.stop="handleViewLog(item)">日志</el-button>
        <el-button size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="dialog-form-responsive">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="jobName">
              <el-input v-model="formData.jobName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务分组" prop="jobGroup">
              <el-input v-model="formData.jobGroup" placeholder="请输入任务分组" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="调用目标" prop="invokeTarget">
          <el-input v-model="formData.invokeTarget" placeholder="如: demoTask.execute('test')" />
          <div class="form-tip">
            <p>调用目标格式：beanName.methodName(params)</p>
            <p>示例：demoTask.execute("test") 或 demoTask.cleanExpiredData()</p>
          </div>
        </el-form-item>
        <el-form-item label="cron表达式" prop="cronExpression">
          <el-input v-model="formData.cronExpression" placeholder="如: 0 0/5 * * * ?">
            <template #append>
              <el-button @click="cronDialogVisible = true">生成</el-button>
            </template>
          </el-input>
          <div class="form-tip">
            <p>Cron 表达式格式：秒 分 时 日 月 周 年(可选)</p>
            <p>示例：0 0/5 * * * ? - 每5分钟执行一次</p>
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="是否并发" label-width="120px">
              <el-radio-group v-model="formData.concurrent">
                <el-radio :value="0">禁止</el-radio>
                <el-radio :value="1">允许</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" label-width="120px">
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
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="超时时间(秒)" label-width="120px">
              <el-input-number v-model="formData.timeout" :min="0" :max="3600" placeholder="0=不限" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失败重试次数" label-width="120px">
              <el-input-number v-model="formData.retryCount" :min="0" :max="10" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="重试间隔(秒)" label-width="120px">
              <el-input-number v-model="formData.retryInterval" :min="10" :max="3600" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="任务参数">
          <el-input v-model="jobParamsJson" type="textarea" :rows="3" placeholder='JSON格式，如: {"key": "value"}' />
        </el-form-item>
        <el-form-item label="通知配置">
          <el-input v-model="notifyConfigJson" type="textarea" :rows="3" placeholder='{"emails": ["admin@example.com"]}' />
          <div class="form-tip">
            <p>可选字段：notifyOnFailure(失败通知，默认true)、notifyOnSuccess(成功通知，默认false)</p>
            <p>emails(邮件列表)、webhookUrl(Webhook地址)</p>
            <p>示例：{"notifyOnFailure": true, "emails": ["admin@example.com"], "webhookUrl": "https://hooks.example.com/job"}</p>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- Cron 表达式生成对话框 -->
    <el-dialog v-model="cronDialogVisible" title="Cron 表达式生成器" width="600px">
      <div class="cron-builder">
        <el-tabs v-model="cronTab">
          <el-tab-pane label="常用" name="common">
            <div class="cron-common">
              <el-button @click="setCron('0 0/5 * * * ?')">每5分钟</el-button>
              <el-button @click="setCron('0 0/10 * * * ?')">每10分钟</el-button>
              <el-button @click="setCron('0 0/30 * * * ?')">每30分钟</el-button>
              <el-button @click="setCron('0 0 0/1 * * ?')">每小时</el-button>
              <el-button @click="setCron('0 0 2 * * ?')">每天凌晨2点</el-button>
              <el-button @click="setCron('0 0 0 * * ?')">每天凌晨0点</el-button>
              <el-button @click="setCron('0 0 0 ? * MON')">每周一凌晨</el-button>
              <el-button @click="setCron('0 0 0 1 * ?')">每月1号凌晨</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
        <el-divider />
        <div class="cron-result">
          <el-input v-model="generatedCron" readonly>
            <template #prepend>生成的表达式</template>
          </el-input>
        </div>
      </div>
      <template #footer>
        <el-button @click="cronDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applyCronExpression">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { Plus } from '@element-plus/icons-vue'
import { getJobList, addJob, updateJob, deleteJob, changeJobStatus, runJobOnce } from '@/api/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import { useRouter } from 'vue-router'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const { isMobile } = useResponsive()
const router = useRouter()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

interface Job {
  id: number
  jobName: string
  jobGroup: string
  invokeTarget: string
  cronExpression: string
  status: number
  concurrent: number
  remark: string
  timeout?: number
  retryCount?: number
  retryInterval?: number
  notifyConfig?: Record<string, unknown>
  jobParams?: Record<string, unknown>
  lastExecuteAt?: string
  lastExecuteStatus?: string
  lastExecuteDuration?: number
  totalExecuteCount?: number
  successCount?: number
  failureCount?: number
  createTime: string
}

const loading = ref(false)
const tableData = ref<Job[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<Job | null>(null)

// Cron 表达式生成器
const cronDialogVisible = ref(false)
const cronTab = ref('common')
const generatedCron = ref('')

const queryParams = reactive({
  jobName: '',
  jobGroup: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.jobName) count++
  if (queryParams.jobGroup) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as number | undefined,
  jobName: '',
  jobGroup: 'DEFAULT',
  invokeTarget: '',
  cronExpression: '',
  status: 1,
  concurrent: 0,
  remark: '',
  timeout: undefined as number | undefined,
  retryCount: 0,
  retryInterval: 60,
  notifyConfig: undefined as Record<string, unknown> | undefined,
  jobParams: undefined as Record<string, unknown> | undefined,
})

// JSON 字段编辑用的字符串
const jobParamsJson = ref('')
const notifyConfigJson = ref('')

const formRules: FormRules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  invokeTarget: [{ required: true, message: '请输入调用目标', trigger: 'blur' }],
  cronExpression: [{ required: true, message: '请输入cron表达式', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getJobList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => {
  queryParams.jobName = ''
  queryParams.jobGroup = ''
  queryParams.status = undefined
  handleQuery()
}

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增任务'
  Object.assign(formData, { id: undefined, jobName: '', jobGroup: 'DEFAULT', invokeTarget: '', cronExpression: '', status: 1, concurrent: 0, remark: '', timeout: undefined, retryCount: 0, retryInterval: 60, notifyConfig: undefined, jobParams: undefined })
  jobParamsJson.value = ''
  notifyConfigJson.value = ''
  dialogVisible.value = true
}

const handleEdit = (row: Job) => {
  cancelSelection()
  isEdit.value = true
  dialogTitle.value = '编辑任务'
  Object.assign(formData, row)
  jobParamsJson.value = row.jobParams ? JSON.stringify(row.jobParams, null, 2) : ''
  notifyConfigJson.value = row.notifyConfig ? JSON.stringify(row.notifyConfig, null, 2) : ''
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  // 解析 JSON 字段
  if (jobParamsJson.value.trim()) {
    try {
      formData.jobParams = JSON.parse(jobParamsJson.value)
    } catch {
      ElMessage.warning('任务参数 JSON 格式不正确')
      return
    }
  } else {
    formData.jobParams = undefined
  }
  if (notifyConfigJson.value.trim()) {
    try {
      formData.notifyConfig = JSON.parse(notifyConfigJson.value)
    } catch {
      ElMessage.warning('通知配置 JSON 格式不正确')
      return
    }
  } else {
    formData.notifyConfig = undefined
  }

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateJob(formData)
      ElMessage.success('更新成功')
    } else {
      await addJob(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleStatusChange = async (row: Job) => {
  try {
    await changeJobStatus(row.id, row.status)
    ElMessage.success('状态修改成功')
  } catch {
    row.status = row.status === 1 ? 0 : 1
  }
}

const handleRunOnce = async (row: Job) => {
  try {
    await ElMessageBox.confirm(`确认要立即执行一次任务 "${row.jobName}" 吗？`, '提示', { type: 'warning' })
    await runJobOnce(row.id)
    ElMessage.success('执行成功')
  } catch (e) {}
}

const handleDelete = async (row: Job) => {
  try {
    await ElMessageBox.confirm(`确定删除任务 "${row.jobName}"?`, '警告', { type: 'warning' })
    await deleteJob([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  } catch (e) {}
}

const handlePause = async (row: Job) => {
  try {
    await changeJobStatus(row.id, 0)
    ElMessage.success('暂停成功')
    cancelSelection()
    getList()
  } catch {
    ElMessage.error('暂停失败')
  }
}

const handleResume = async (row: Job) => {
  try {
    await changeJobStatus(row.id, 1)
    ElMessage.success('恢复成功')
    cancelSelection()
    getList()
  } catch {
    ElMessage.error('恢复失败')
  }
}

const handleViewLog = (row: Job) => {
  router.push({
    path: '/system/job-log',
    query: { jobId: row.id, jobName: row.jobName }
  })
}

// Cron 表达式生成器相关方法
const setCron = (cronExpr: string) => {
  generatedCron.value = cronExpr
}

const applyCronExpression = () => {
  formData.cronExpression = generatedCron.value
  cronDialogVisible.value = false
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: Job | null }) => {
  if (isMobile.value) {
    selectedRow.value = row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
  if (tableRef.value) {
    tableRef.value.clearCurrentRow()
  }
}

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})
</script>

<style scoped lang="scss">
.form-tip {
  margin-top: 5px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.cron-builder {
  .cron-common {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
}
</style>
