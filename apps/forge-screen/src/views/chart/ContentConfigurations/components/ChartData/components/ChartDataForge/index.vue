<template>
  <div class="chart-data-forge">
    <setting-item-box name="数据源" :alone="true">
      <n-select
        :value="(targetData.request as any).forgeDataSourceId || null"
        :options="dataSourceOptions"
        :loading="listLoading"
        filterable
        placeholder="选择 forge 数据源"
        @update:value="(v: number) => ((targetData.request as any).forgeDataSourceId = v)"
      />
    </setting-item-box>

    <setting-item-box name="参数（JSON）" :alone="true">
      <n-input
        type="textarea"
        :rows="3"
        :value="forgeParamsStr"
        placeholder='{"accountSetId": 1}'
        @update:value="updateForgeParams"
      />
    </setting-item-box>

    <setting-item-box name="操作" :alone="true">
      <n-button type="primary" ghost @click="testFetch">
        <template #icon><n-icon><flash-icon /></n-icon></template>
        测试获取
      </n-button>
    </setting-item-box>

    <setting-item-box v-if="testResult" name="测试结果" :alone="true">
      <pre class="result">{{ JSON.stringify(testResult, null, 2) }}</pre>
    </setting-item-box>

    <chart-data-matching-and-show :show="!!testResult" :ajax="false"></chart-data-matching-and-show>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, toRaw, onMounted } from 'vue'
import { SettingItemBox } from '@/components/Pages/ChartItemSetting'
import { icon } from '@/plugins'
import { useTargetData } from '../../../hooks/useTargetData.hook'
import { executeDataSource, listDataSources } from '@/api/forge/dataSource'
import { ChartDataMatchingAndShow } from '../ChartDataMatchingAndShow'
// ElMessage via window['$message']

const { FlashIcon } = icon.carbon

const { targetData } = useTargetData()

const testResult = ref<unknown>(null)
const dataSourceOptions = ref<{ label: string; value: number }[]>([])
const listLoading = ref(false)

onMounted(async () => {
  listLoading.value = true
  try {
    const res = await listDataSources()
    dataSourceOptions.value = (res?.list ?? []).map((d) => ({
      label: `${d.name}（${d.code}）`,
      value: Number(d.id)
    }))
  } catch (e) {
    // 列表加载失败不阻塞面板（可手填兜底场景后续再议）
    console.error('[forge data source] list failed', e)
  } finally {
    listLoading.value = false
  }
})

const forgeParamsStr = computed(() => {
  const p = (targetData.value?.request as any)?.forgeParams
  if (!p) return ''
  try { return JSON.stringify(p, null, 2) } catch { return '' }
})

const updateForgeParams = (v: string) => {
  try {
    const parsed = v.trim() ? JSON.parse(v) : {}
    ;(targetData.value.request as any).forgeParams = parsed
  } catch {
    // 解析失败不写入，避免误删现有 params
  }
}

const testFetch = async () => {
  const id = (targetData.value.request as any).forgeDataSourceId
  if (!id) {
    window['$message']?.warning('请选择数据源')
    return
  }
  try {
    const res = await executeDataSource(id, {
      params: toRaw((targetData.value.request as any).forgeParams || {})
    })
    testResult.value = res
    // 只替换 dataset.source，保留现有 dimensions 映射
    if (res && res.data && targetData.value.option) {
      if (!targetData.value.option.dataset) {
        targetData.value.option.dataset = { dimensions: [], source: [] }
      }
      const data = res.data as any
      const source = Array.isArray(data) ? data : (data?.records ?? data?.list ?? data?.data ?? [])
      targetData.value.option.dataset.source = source
    }
    window['$message']?.success('获取成功')
  } catch (e: any) {
    window['$message']?.error('获取失败：' + (e?.message || String(e)))
  }
}
</script>

<style scoped>
.chart-data-forge { padding: 8px 0; }
.result {
  background: rgba(0,0,0,0.3);
  color: #a3d9b1;
  padding: 8px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
  font-size: 12px;
}
</style>
