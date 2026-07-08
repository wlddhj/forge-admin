<template>
  <div class="chart-data-forge">
    <setting-item-box name="数据源 ID" :alone="true">
      <n-input-number
        :value="targetData.request.forgeDataSourceId || null"
        :min="1"
        :show-button="false"
        placeholder="forge-admin sys_screen_data_source.id"
        @update:value="(v: number) => (targetData.request.forgeDataSourceId = v)"
      />
    </setting-item-box>

    <setting-item-box name="参数（JSON）" :alone="true">
      <n-input
        type="textarea"
        :rows="3"
        :value="forgeParamsStr"
        placeholder='{"id": 1, "pageSize": 20}'
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, toRaw } from 'vue'
import { SettingItemBox } from '@/components/Pages/ChartItemSetting'
import { icon } from '@/plugins'
import { useTargetData } from '../../../hooks/useTargetData.hook'
import { executeDataSource } from '@/api/forge/dataSource'
// ElMessage via window['$message']

const { FlashIcon } = icon.carbon

const { targetData } = useTargetData()

const testResult = ref<unknown>(null)

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
    ElMessage.warning('请填写数据源 ID')
    return
  }
  try {
    const res = await executeDataSource(id, {
      params: toRaw((targetData.value.request as any).forgeParams || {})
    })
    testResult.value = res
    ElMessage.success('获取成功')
  } catch (e: any) {
    ElMessage.error('获取失败：' + (e?.message || String(e)))
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
