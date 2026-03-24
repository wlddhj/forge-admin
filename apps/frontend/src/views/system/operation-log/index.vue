<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="操作标题">
          <el-input v-model="queryParams.title" placeholder="请输入操作标题" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="queryParams.operatorName" placeholder="请输入操作人" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="queryParams.businessType" placeholder="请选择业务类型" clearable style="width: 130px">
            <el-option
              v-for="item in operationTypeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100px">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD HH:mm:ss"
            :default-time="[new Date(0, 0, 0, 0, 0, 0), new Date(0, 0, 0, 23, 59, 59)]"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">操作日志</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button type="danger" @click="handleClear">清空</el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="操作标题">
          <el-input v-model="queryParams.title" placeholder="请输入操作标题" clearable />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="queryParams.operatorName" placeholder="请输入操作人" clearable />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="queryParams.businessType" placeholder="请选择业务类型" clearable style="width: 100%">
            <el-option
              v-for="item in operationTypeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
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
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD HH:mm:ss"
            :default-time="[new Date(0, 0, 0, 0, 0, 0), new Date(0, 0, 0, 23, 59, 59)]"
            style="width: 100%"
          />
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span v-if="!isMobile">操作日志列表</span>
          <el-button v-if="!isMobile" type="danger" @click="handleClear">清空日志</el-button>
        </div>
      </template>

      <div class="table-responsive">
        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          :row-class-name="getRowClassName"
          @row-click="handleRowClick"
        >
          <el-table-column prop="title" label="操作标题" width="120" />
          <el-table-column label="业务类型" width="90">
            <template #default="{ row }">
              <dict-value :dict-type="DICT_TYPE.SYS_OPERATION_TYPE" :value="row.businessType" />
            </template>
          </el-table-column>
          <el-table-column label="请求方式" width="100" v-if="!isMobile">
            <template #default="{ row }">
              <el-tag :type="getMethodTag(row.requestMethod)" size="small">{{ row.requestMethod }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="100" />
          <el-table-column prop="deptName" label="部门" width="120" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="operateIp" label="操作IP" width="130" v-if="!isMobile" />
          <el-table-column prop="operateLocation" label="操作地点" width="100" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <dict-value :dict-type="DICT_TYPE.SYS_SUCCESS_FAIL" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="operateTime" label="操作时间" width="180" v-if="!isMobile">
            <template #default="{ row }">{{ formatDateTime(row.operateTime) }}</template>
          </el-table-column>
          <el-table-column prop="costTime" label="耗时(ms)" width="85" v-if="!isMobile" />
          <!-- 桌面端操作列 -->
          <el-table-column v-if="!isMobile" label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="handleView(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.title"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" type="primary" @click.stop="handleView(item)">查看详情</el-button>
      </template>
    </MobileBottomActions>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      title="操作日志详情"
      direction="rtl"
      size="50%"
      class="operation-log-drawer"
    >
      <template #default v-if="currentLog">
        <!-- 基础信息 -->
        <div class="detail-section">
          <div class="section-title">基础信息</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="操作模块">{{ currentLog.title }}</el-descriptions-item>
            <el-descriptions-item label="业务类型">
              <dict-value :dict-type="DICT_TYPE.SYS_OPERATION_TYPE" :value="currentLog.businessType" />
            </el-descriptions-item>
            <el-descriptions-item label="请求方式">
              <el-tag :type="getMethodTag(currentLog.requestMethod)" size="small">{{ currentLog.requestMethod }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="操作人">{{ currentLog.operatorName }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ currentLog.deptName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="操作IP">{{ currentLog.operateIp }}</el-descriptions-item>
            <el-descriptions-item label="操作地点">{{ currentLog.operateLocation || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <dict-value :dict-type="DICT_TYPE.SYS_SUCCESS_FAIL" :value="currentLog.status" />
            </el-descriptions-item>
            <el-descriptions-item label="操作时间">{{ formatDateTime(currentLog.operateTime) }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ currentLog.costTime }}ms</el-descriptions-item>
            <el-descriptions-item label="请求URL" :span="2">
              <el-text type="primary" class="url-text">{{ currentLog.requestUrl }}</el-text>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 请求参数 -->
        <div class="detail-section">
          <div class="section-title">请求参数</div>
          <div class="code-block">
            <pre>{{ formatJson(currentLog.requestParam) || '(无)' }}</pre>
          </div>
        </div>

        <!-- 响应结果 -->
        <div class="detail-section">
          <div class="section-title">响应结果</div>
          <div class="code-block">
            <pre>{{ formatJson(currentLog.jsonResult) || '(无)' }}</pre>
          </div>
        </div>

        <!-- 错误信息 -->
        <div v-if="currentLog.errorMsg" class="detail-section">
          <div class="section-title error">错误信息</div>
          <div class="code-block error-block">
            <pre>{{ currentLog.errorMsg }}</pre>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOperationLogList, getOperationLog, clearOperationLogs } from '@/api/system'
import type { OperationLog } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import DictValue from '@/components/DictValue.vue'

const { isMobile } = useResponsive()
const { dictData: operationTypeOptions } = useDict(DICT_TYPE.SYS_OPERATION_TYPE)
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_SUCCESS_FAIL)

const loading = ref(false)
const tableData = ref<OperationLog[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<OperationLog | null>(null)

const queryParams = reactive({
  title: '',
  operatorName: '',
  businessType: '',
  status: undefined as number | undefined,
  startTime: '',
  endTime: '',
  pageNum: 1,
  pageSize: 10
})

const dateRange = ref<[string, string] | null>(null)

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.title) count++
  if (queryParams.operatorName) count++
  if (queryParams.businessType) count++
  if (queryParams.status !== undefined) count++
  if (queryParams.startTime || queryParams.endTime) count++
  return count
})

// 监听日期范围变化
watch(dateRange, (val) => {
  if (val && val.length === 2) {
    queryParams.startTime = val[0]
    queryParams.endTime = val[1]
  } else {
    queryParams.startTime = ''
    queryParams.endTime = ''
  }
})

const detailVisible = ref(false)
const currentLog = ref<OperationLog | null>(null)

// 业务类型映射
const businessTypeMap: Record<string, { label: string; type: string }> = {
  INSERT: { label: '新增', type: 'success' },
  UPDATE: { label: '修改', type: 'warning' },
  DELETE: { label: '删除', type: 'danger' },
  GRANT: { label: '授权', type: 'primary' },
  EXPORT: { label: '导出', type: 'info' },
  IMPORT: { label: '导入', type: 'info' },
  OTHER: { label: '其他', type: 'info' }
}

const getBusinessTypeLabel = (type: string) => businessTypeMap[type]?.label || type
const getBusinessTypeTag = (type: string) => businessTypeMap[type]?.type || 'info'

// 请求方式标签颜色
const getMethodTag = (method: string) => {
  const methodColors: Record<string, string> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
    PATCH: 'info'
  }
  return methodColors[method] || 'info'
}

// 格式化JSON
const formatJson = (str: string | undefined) => {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getOperationLogList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }

const handleReset = () => {
  queryParams.title = ''
  queryParams.operatorName = ''
  queryParams.businessType = ''
  queryParams.status = undefined
  queryParams.startTime = ''
  queryParams.endTime = ''
  dateRange.value = null
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

const handleView = async (row: OperationLog) => {
  cancelSelection()
  try {
    const res = await getOperationLog(row.id)
    currentLog.value = res
    detailVisible.value = true
  } catch (e) {}
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有日志吗？此操作不可恢复！', '警告', { type: 'warning' })
    await clearOperationLogs()
    ElMessage.success('清空成功')
    cancelSelection()
    getList()
  } catch (e) {}
}

// 获取行样式名
const getRowClassName = ({ row }: { row: OperationLog }) => {
  if (isMobile.value && selectedRow.value?.id === row.id) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: OperationLog) => {
  if (isMobile.value) {
    selectedRow.value = selectedRow.value?.id === row.id ? null : row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
}

onMounted(() => getList())
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }
  .table-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}

// 操作日志详情抽屉样式
.operation-log-drawer {
  :deep(.el-drawer__body) {
    padding: 20px;
    overflow-y: auto;
  }

  .detail-section {
    margin-bottom: 24px;

    &:last-child {
      margin-bottom: 0;
    }

    .section-title {
      font-size: 15px;
      font-weight: 500;
      color: #303133;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid #ebeef5;

      &.error {
        color: #f56c6c;
      }
    }

    .code-block {
      background: #f5f7fa;
      border: 1px solid #e4e7ed;
      border-radius: 4px;
      padding: 12px 16px;
      max-height: 400px;
      overflow: auto;

      &.error-block {
        background: #fef0f0;
        border-color: #fbc4c4;
        color: #f56c6c;
      }

      pre {
        margin: 0;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace;
        font-size: 13px;
        line-height: 1.6;
        color: #606266;
        white-space: pre-wrap;
        word-break: break-all;
      }
    }
  }

  .url-text {
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
    font-size: 13px;
    word-break: break-all;
  }

  // 移动端适配
  @media (max-width: 768px) {
    :deep(.el-drawer) {
      width: 100% !important;
    }

    :deep(.el-drawer__body) {
      padding: 16px;
    }

    .detail-section {
      margin-bottom: 20px;

      .code-block {
        max-height: 200px;
        padding: 10px 12px;

        pre {
          font-size: 12px;
        }
      }
    }
  }
}
</style>
