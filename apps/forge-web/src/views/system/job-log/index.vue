<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="执行状态">
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
          <el-button type="danger" @click="handleClear">清空</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">任务日志</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button type="danger" @click="handleClear">
            <el-icon><Delete /></el-icon>
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
        <el-form-item label="执行状态">
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
          <el-button type="danger" @click="handleClear">
            <el-icon><Delete /></el-icon>
            清空日志
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysJobLogTable"
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
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />

        <!-- 任务名称 -->
        <vxe-column field="jobName" title="任务名称" width="150" />

        <!-- 任务分组（桌面端） -->
        <vxe-column v-if="!isMobile" field="jobGroup" title="任务分组" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.jobGroup === 'SYSTEM'" type="danger" size="small">系统</el-tag>
            <el-tag v-else size="small">默认</el-tag>
          </template>
        </vxe-column>

        <!-- 调用目标（桌面端） -->
        <vxe-column v-if="!isMobile" field="invokeTarget" title="调用目标" min-width="200" />

        <!-- 日志信息 -->
        <vxe-column field="jobMessage" title="日志信息" width="120" />

        <!-- 执行状态 -->
        <vxe-column title="执行状态" width="90">
          <template #default="{ row }">
            <DictValue :dict-type="DICT_TYPE.SYS_SUCCESS_FAIL" :value="row.status" />
          </template>
        </vxe-column>

        <!-- 耗时（桌面端） -->
        <vxe-column v-if="!isMobile" field="duration" title="耗时(毫秒)" width="110">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.duration > 5000 }">{{ row.duration }}</span>
          </template>
        </vxe-column>

        <!-- 执行时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="startTime" title="执行时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </vxe-column>

        <!-- 操作列 -->
        <vxe-column title="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">详情</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <!-- 分页 -->
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

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="日志详情" width="700px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="任务名称">{{ currentLog?.jobName }}</el-descriptions-item>
        <el-descriptions-item label="任务分组">{{ currentLog?.jobGroup }}</el-descriptions-item>
        <el-descriptions-item label="调用目标">{{ currentLog?.invokeTarget }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="currentLog?.status === 1 ? 'success' : 'danger'">
            {{ currentLog?.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行时长">{{ currentLog?.duration }} ms</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDateTime(currentLog?.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatDateTime(currentLog?.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="日志信息">{{ currentLog?.jobMessage }}</el-descriptions-item>
        <el-descriptions-item v-if="currentLog?.exceptionInfo" label="异常信息">
          <div class="exception-info">{{ currentLog?.exceptionInfo }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { Delete } from '@element-plus/icons-vue'
import { getJobLogList, clearJobLogs } from '@/api/system'
import type { JobLog } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import { useRoute } from 'vue-router'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import DictValue from '@/components/DictValue.vue'

const { isMobile } = useResponsive()
const route = useRoute()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_SUCCESS_FAIL)

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<JobLog[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  jobId: route.query.jobId as string | undefined,
  jobName: (route.query.jobName as string) || '',
  status: undefined as number | undefined
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.jobName) count++
  if (queryParams.status !== undefined) count++
  return count
})

const detailDialogVisible = ref(false)
const currentLog = ref<JobLog | null>(null)

const getList = async () => {
  loading.value = true
  try {
    const res = await getJobLogList(queryParams)
    tableData.value = res.list || []
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => getList()

const handleReset = () => {
  queryParams.jobName = (route.query.jobName as string) || ''
  queryParams.status = undefined
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  handleQuery()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有日志吗？此操作不可恢复！', '警告', { type: 'warning' })
    await clearJobLogs(queryParams.jobId ? Number(queryParams.jobId) : undefined)
    ElMessage.success('清空成功')
    getList()
  } catch {
    // 用户取消
  }
}

const handleViewDetail = (row: JobLog) => {
  currentLog.value = row
  detailDialogVisible.value = true
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
.app-container {
  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }

  .text-danger {
    color: #f56c6c;
    font-weight: bold;
  }

  .exception-info {
    max-height: 300px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-all;
    font-size: 12px;
    color: #f56c6c;
    background-color: #fef0f0;
    padding: 10px;
    border-radius: 4px;
  }
}
</style>
