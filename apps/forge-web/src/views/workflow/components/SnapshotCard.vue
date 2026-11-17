<template>
  <el-card v-if="items.length > 0" shadow="never" class="snapshot-card">
    <template #header>申请数据</template>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="该流程未配置表单，以下为发起时提交的变量快照"
      class="snapshot-tip"
    />
    <el-descriptions :column="isMobile ? 1 : 2" size="small" border>
      <el-descriptions-item v-for="item in items" :key="item.key" :label="item.key">
        {{ item.value }}
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useResponsive } from '@/composables/useResponsive'

defineOptions({ name: 'SnapshotCard' })

const props = defineProps<{
  /** 实例变量（原始 Map，内部剔除 __ 前缀系统变量） */
  variables?: Record<string, unknown> | null
}>()

const { isMobile } = useResponsive()

interface SnapshotItem {
  key: string
  value: string
}

/** 剔除系统变量后的可读快照：空值显示 -，数组连接，对象 JSON 化 */
const items = computed<SnapshotItem[]>(() => {
  const source = props.variables ?? {}
  return Object.entries(source)
    .filter(([key]) => !key.startsWith('__'))
    .map(([key, raw]) => {
      let value: string
      if (raw === null || raw === undefined || raw === '') {
        value = '-'
      } else if (Array.isArray(raw)) {
        value = raw.length > 0 ? raw.map(v => String(v)).join('、') : '-'
      } else if (typeof raw === 'object') {
        value = JSON.stringify(raw)
      } else {
        value = String(raw)
      }
      return { key, value }
    })
})
</script>

<style scoped>
.snapshot-card {
  margin-top: 12px;
}

.snapshot-tip {
  margin-bottom: 12px;
}
</style>
