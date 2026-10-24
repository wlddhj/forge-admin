<template>
  <div class="app-container">
    <!-- 汇总卡片 -->
    <el-row :gutter="12" class="summary-row">
      <el-col :span="5">
        <el-card shadow="never"><div class="summary-label">总请求</div><div class="summary-value">{{ summary.totalRequest }}</div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never"><div class="summary-label">成功率</div><div class="summary-value">{{ successRate }}%</div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never"><div class="summary-label">总 Token</div><div class="summary-value">{{ summary.totalTokens }}</div></el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never"><div class="summary-label">平均时延</div><div class="summary-value">{{ summary.avgResponseMs }}ms</div></el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never"><div class="summary-label">失败次数</div><div class="summary-value danger">{{ summary.failureCount }}</div></el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <el-form inline @submit.prevent>
      <el-form-item label="日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item label="模型">
        <el-select v-model="queryModelId" placeholder="全部模型" clearable style="width: 180px">
          <el-option v-for="m in modelList" :key="m.id" :label="m.modelName" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input-number v-model="queryUserId" :min="1" placeholder="用户ID" clearable controls-position="right" style="width: 140px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 明细表 -->
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="usageDate" label="日期" width="110" />
      <el-table-column prop="userId" label="用户ID" width="90" />
      <el-table-column label="模型" min-width="140">
        <template #default="{ row }">{{ modelName(row.modelId) }}</template>
      </el-table-column>
      <el-table-column prop="requestCount" label="请求数" width="90" align="right" />
      <el-table-column prop="successCount" label="成功" width="80" align="right" />
      <el-table-column prop="failureCount" label="失败" width="80" align="right" />
      <el-table-column label="Token入/出" width="150" align="right">
        <template #default="{ row }">{{ row.totalInputTokens }} / {{ row.totalOutputTokens }}</template>
      </el-table-column>
      <el-table-column prop="avgResponseTime" label="均时延(ms)" width="110" align="right" />
    </el-table>
    <TablePagination v-model:page-num="query.pageNum" v-model:page-size="query.pageSize" :total="total" @change="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { usageApi } from '@/api/ai/usage'
import type { ApiUsageRow, ApiUsageSummary } from '@/api/ai/usage'
import { modelApi } from '@/api/ai/model'
import type { ModelConfigResponse } from '@/api/ai/model'
import TablePagination from '@/components/TablePagination.vue'

defineOptions({ name: 'AiUsage' })

const loading = ref(false)
const rows = ref<ApiUsageRow[]>([])
const total = ref(0)
const summary = ref<ApiUsageSummary>({ totalRequest: 0, successCount: 0, failureCount: 0, totalTokens: 0, avgResponseMs: 0 })
const modelList = ref<ModelConfigResponse[]>([])

const dateRange = ref<[string, string] | null>(defaultRange())
const queryUserId = ref<number | undefined>(undefined)
const queryModelId = ref<number | undefined>(undefined)
const query = reactive({ pageNum: 1, pageSize: 10 })

/** 默认近 7 天 */
function defaultRange(): [string, string] {
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  const end = new Date()
  const start = new Date(Date.now() - 6 * 86400000)
  return [fmt(start), fmt(end)]
}

const successRate = computed(() => {
  const total = summary.value.totalRequest
  return total > 0 ? Math.round((summary.value.successCount / total) * 1000) / 10 : 0
})

function modelName(modelId: number): string {
  return modelList.value.find(m => m.id === modelId)?.modelName || `模型#${modelId}`
}

async function load() {
  loading.value = true
  try {
    const params = {
      ...query,
      userId: queryUserId.value || undefined,
      modelId: queryModelId.value || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
    }
    const [page, sum] = await Promise.all([usageApi.page(params), usageApi.summary(params)])
    rows.value = page.list || []
    total.value = page.total || 0
    summary.value = sum
  } finally {
    loading.value = false
  }
}

function reset() {
  dateRange.value = defaultRange()
  queryUserId.value = undefined
  queryModelId.value = undefined
  query.pageNum = 1
  load()
}

onMounted(async () => {
  try {
    const models = await modelApi.list()
    modelList.value = (models as { data?: ModelConfigResponse[] }).data || (models as unknown as ModelConfigResponse[])
  } catch {
    modelList.value = []
  }
  load()
})
</script>

<style scoped>
.summary-row {
  margin-bottom: 12px;
}
.summary-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.summary-value {
  font-size: 20px;
  font-weight: 600;
  margin-top: 4px;
}
.summary-value.danger {
  color: var(--el-color-danger);
}
</style>
