<template>
  <div class="app-container">
    <!-- 汇总卡(本页特有业务区块) -->
    <el-card shadow="never" class="summary-card">
      <el-row :gutter="12">
        <el-col :span="5"><div class="summary-label">总请求</div><div class="summary-value">{{ summary.totalRequest }}</div></el-col>
        <el-col :span="5"><div class="summary-label">成功率</div><div class="summary-value">{{ successRate }}%</div></el-col>
        <el-col :span="5"><div class="summary-label">总 Token</div><div class="summary-value">{{ summary.totalTokens }}</div></el-col>
        <el-col :span="5"><div class="summary-label">平均时延</div><div class="summary-value">{{ summary.avgResponseMs }}ms</div></el-col>
        <el-col :span="4"><div class="summary-label">失败次数</div><div class="summary-value danger">{{ summary.failureCount }}</div></el-col>
      </el-row>
    </el-card>

    <!-- 1. 搜索栏(桌面端表单 + 移动端按钮) -->
    <el-card shadow="never" class="search-card">
      <el-form v-if="!isMobile" :model="queryParams" inline @submit.prevent>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="queryParams.modelId" placeholder="全部模型" clearable style="width: 180px">
            <el-option v-for="m in modelList" :key="m.id" :label="m.modelName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input-number v-model="queryParams.userId" :min="1" controls-position="right" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <div v-else class="mobile-search-actions">
        <span class="title">用量统计</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
        </div>
      </div>
    </el-card>

    <!-- 2. 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams"
      @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="日期" style="width: 100%">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="模型" style="width: 100%">
          <el-select v-model="queryParams.modelId" placeholder="全部模型" clearable style="width: 100%">
            <el-option v-for="m in modelList" :key="m.id" :label="m.modelName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID" style="width: 100%">
          <el-input-number v-model="queryParams.userId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 3. 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #tools>
          <vxe-button circle icon="vxe-icon-refresh" style="margin-right: 10px" @click="handleQuery" />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="aiUsageTable"
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
        show-header-overflow="tooltip"
      >
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="usageDate" title="日期" width="110" />
        <vxe-column field="userId" title="用户ID" width="90" />
        <vxe-column title="模型" min-width="140">
          <template #default="{ row }">{{ modelName(row.modelId) }}</template>
        </vxe-column>
        <vxe-column field="requestCount" title="请求数" width="90" align="right" />
        <vxe-column field="successCount" title="成功" width="80" align="right" />
        <vxe-column field="failureCount" title="失败" width="80" align="right" />
        <vxe-column title="Token入/出" width="150" align="right">
          <template #default="{ row }">{{ row.totalInputTokens }} / {{ row.totalOutputTokens }}</template>
        </vxe-column>
        <vxe-column field="avgResponseTime" title="均时延(ms)" width="110" align="right" />
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { usageApi } from '@/api/ai/usage'
import type { ApiUsageRow, ApiUsageSummary } from '@/api/ai/usage'
import { modelApi } from '@/api/ai/model'
import type { ModelConfigResponse } from '@/api/ai/model'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import TablePagination from '@/components/TablePagination.vue'

defineOptions({ name: 'AiUsage' })

const { isMobile } = useResponsive()
const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 查询参数(日期范围走独立 dateRange + watch 同步,规范 9.4;用量为日粒度,日期格式即可)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  userId: undefined as number | undefined,
  modelId: undefined as number | undefined,
  startDate: '',
  endDate: ''
})

function defaultRange(): [string, string] {
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  return [fmt(new Date(Date.now() - 6 * 86400000)), fmt(new Date())]
}

const dateRange = ref<[string, string] | null>(defaultRange())

watch(dateRange, val => {
  if (val && val.length === 2) {
    queryParams.startDate = val[0]
    queryParams.endDate = val[1]
  } else {
    queryParams.startDate = ''
    queryParams.endDate = ''
  }
})

// 表格数据
const loading = ref(false)
const tableData = ref<ApiUsageRow[]>([])
const total = ref(0)
const summary = ref<ApiUsageSummary>({ totalRequest: 0, successCount: 0, failureCount: 0, totalTokens: 0, avgResponseMs: 0 })
const modelList = ref<ModelConfigResponse[]>([])

// 移动端
const searchDrawerVisible = ref(false)
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.userId) count++
  if (queryParams.modelId) count++
  if (queryParams.startDate) count++
  return count
})

const successRate = computed(() => {
  const t = summary.value.totalRequest
  return t > 0 ? Math.round((summary.value.successCount / t) * 1000) / 10 : 0
})

function modelName(modelId: number): string {
  return modelList.value.find(m => m.id === modelId)?.modelName || `模型#${modelId}`
}

const getList = async () => {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([
      usageApi.page(queryParams),
      usageApi.summary(queryParams)
    ])
    tableData.value = page.list || []
    total.value = page.total || 0
    summary.value = sum
  } catch (error) {
    console.error('获取用量数据失败:', error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  dateRange.value = defaultRange()
  queryParams.userId = undefined
  queryParams.modelId = undefined
  handleQuery()
}

const handleSearchFromDrawer = () => {
  handleQuery()
  searchDrawerVisible.value = false
}

const handleResetFromDrawer = () => {
  handleReset()
  searchDrawerVisible.value = false
}

onMounted(async () => {
  try {
    const models = await modelApi.list()
    modelList.value = (models as { data?: ModelConfigResponse[] }).data || (models as unknown as ModelConfigResponse[])
  } catch {
    modelList.value = []
  }
  getList()
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})
</script>

<style scoped lang="scss">
// 本页特有:汇总卡业务样式(布局类由全局 table-page.scss 提供)
.summary-card {
  margin-bottom: 5px;
}

.summary-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary-value {
  font-size: 20px;
  font-weight: 600;
  margin-top: 4px;

  &.danger {
    color: var(--el-color-danger);
  }
}
</style>
