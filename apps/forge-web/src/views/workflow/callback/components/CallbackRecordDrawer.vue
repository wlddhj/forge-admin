<template>
  <el-drawer
    :model-value="modelValue"
    :title="`投递记录 - ${callback?.name || ''}`"
    size="70%"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div ref="containerRef" style="height: 100%">
      <el-form inline class="records-filter">
        <el-form-item label="状态">
          <el-select v-model="recordQuery.status" placeholder="全部" clearable style="width: 120px" @change="reload">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>

      <vxe-table
        id="wfCallbackRecordTable"
        :data="recordData"
        :loading="recordLoading"
        border="none"
        stripe
        show-overflow="tooltip"
        :row-config="{ isHover: true }"
        :column-config="{ resizable: true }"
        :height="tableHeight"
      >
        <vxe-column type="seq" title="序号" width="50" align="center" />
        <vxe-column field="createTime" title="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="事件" width="100">
          <template #default="{ row }">{{ eventLabel(row.eventType) }}</template>
        </vxe-column>
        <vxe-column field="processKey" title="流程" width="130" />
        <vxe-column field="instanceId" title="实例ID" width="180" />
        <vxe-column field="nodeKey" title="节点" width="120" />
        <vxe-column title="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="httpStatus" title="HTTP" width="80" align="center" />
        <vxe-column field="retryCount" title="重试" width="60" align="center" />
        <vxe-column field="errorMsg" title="失败原因" min-width="160" />
        <vxe-column title="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              v-permission="'workflow:callback:edit'"
              type="primary"
              link
              size="small"
              @click="handleResend(row)"
            >
重发
</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination
        v-model:page-num="recordQuery.pageNum"
        v-model:page-size="recordQuery.pageSize"
        :total="recordTotal"
        @change="getRecords"
      />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDrawerTableHeight } from '@/composables/useDrawerTableHeight'
import TablePagination from '@/components/TablePagination.vue'
import { formatDateTime } from '@/utils/dateFormat'
import { callbackApi } from '@/api/workflow/callback'
import type { BusinessCallback, BusinessCallbackRecord } from '@/types/workflow'

defineOptions({ name: 'CallbackRecordDrawer' })

const props = defineProps<{
  modelValue: boolean
  callback: BusinessCallback | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

// 抽屉内表格高度自适应（同 dict-type 字典数据抽屉示例）
const containerRef = ref<HTMLElement>()
// 组件常驻挂载而 el-drawer 内容打开时才渲染，composable 的 onMounted 时机扑空——
// 打开时主动重算（并补挂观察器场景：ResizeObserver 未附着时重算兜底）
const { tableHeight, updateHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.records-filter', '.el-pagination']
})

const eventLabel = (ev: string): string => {
  const map: Record<string, string> = {
    taskCreated: '任务创建',
    taskCompleted: '任务办结',
    instanceFinished: '实例终态'
  }
  return map[ev] || ev
}

const recordLoading = ref(false)
const recordData = ref<BusinessCallbackRecord[]>([])
const recordTotal = ref(0)
const recordQuery = reactive({
  pageNum: 1,
  pageSize: 20,
  status: undefined as string | undefined
})

const reload = () => {
  recordQuery.pageNum = 1
  getRecords()
}

const getRecords = async () => {
  if (!props.callback) return
  recordLoading.value = true
  try {
    const data = await callbackApi.records(props.callback.id, recordQuery)
    recordData.value = data.list
    recordTotal.value = data.total
  } finally {
    recordLoading.value = false
  }
}

// 打开抽屉时重置筛选并加载；内容渲染完成后重算表格高度（抽屉动画下一帧再补一次）
watch(
  () => props.modelValue,
  (visible) => {
    if (visible && props.callback) {
      recordQuery.status = undefined
      reload()
      nextTick(updateHeight)
      requestAnimationFrame(updateHeight)
    }
  }
)

const handleResend = async (row: BusinessCallbackRecord) => {
  await ElMessageBox.confirm('确定重发该投递？', '提示', { type: 'warning' })
  await callbackApi.resend(row.id)
  ElMessage.success('已重新投递')
  getRecords()
}
</script>
