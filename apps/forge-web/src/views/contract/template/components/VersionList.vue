<template>
  <el-drawer v-model="visible" title="版本历史" size="720px" destroy-on-close append-to-body @opened="loadList">
    <div ref="containerRef" class="drawer-body">
      <!-- 无分页豁免说明：版本历史有分页 -->
      <vxe-table
        id="templateVersionTable"
        ref="tableRef"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :seq-config="{ seqMethod }"
        :row-config="{ isHover: true }"
        border="none"
        stripe
        show-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" align="center" />
        <vxe-column field="versionNo" title="版本号" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.id === currentVersionId" type="success" size="small">v{{ row.versionNo }} 当前</el-tag>
            <span v-else>v{{ row.versionNo }}</span>
          </template>
        </vxe-column>
        <vxe-column field="changeLog" title="变更说明" min-width="200">
          <template #default="{ row }">{{ row.changeLog || '-' }}</template>
        </vxe-column>
        <vxe-column title="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'contract:template:version'" type="primary" link size="small" @click="viewContent(row)">
              查看内容
            </el-button>
            <el-button
              v-if="row.id !== currentVersionId"
              v-permission="'contract:template:version'"
              type="warning" link size="small" @click="setCurrent(row)"
            >
              设为当前
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="loadList" />
    </div>

    <!-- 内容预览 -->
    <el-dialog v-model="contentVisible" :title="`v${viewingVersionNo} 内容预览`" width="760px" append-to-body>
      <div class="content-preview" v-html="viewingHtml" />
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance } from 'vxe-table'
import { templateApi } from '@/api/contract/template'
import { useDrawerTableHeight } from '@/composables/useDrawerTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'
import TablePagination from '@/components/TablePagination.vue'
import type { TemplateItem, TemplateVersionItem } from '@/types/contract'

defineOptions({ name: 'TemplateVersionList' })

const props = defineProps<{ template: TemplateItem | null }>()
const emit = defineEmits<{ changed: [] }>()
const visible = defineModel<boolean>({ default: false })

const containerRef = ref<HTMLElement>()
const tableRef = ref<VxeTableInstance | null>(null)
const { tableHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.el-pagination']
})

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

const loading = ref(false)
const tableData = ref<TemplateVersionItem[]>([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 10 })

const currentVersionId = computed(() => props.template?.currentVersionId)

const loadList = async () => {
  if (!props.template) return
  loading.value = true
  try {
    const data = await templateApi.pageVersions(props.template.id, queryParams)
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const contentVisible = ref(false)
const viewingHtml = ref('')
const viewingVersionNo = ref(0)
const viewContent = async (row: TemplateVersionItem) => {
  const version = await templateApi.getVersion(row.id)
  viewingVersionNo.value = row.versionNo
  viewingHtml.value = version.contentHtml ?? ''
  contentVisible.value = true
}

const setCurrent = async (row: TemplateVersionItem) => {
  await ElMessageBox.confirm(
    `确定将 v${row.versionNo} 设为当前版本？后续新合同将引用该版本（在途与已定稿合同不受影响）`,
    '提示',
    { type: 'warning' }
  )
  await templateApi.setCurrentVersion(row.id)
  ElMessage.success(`已回滚到 v${row.versionNo}`)
  emit('changed')
  loadList()
}
</script>

<style scoped>
.drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
}
/* 芯片在预览中的样式 */
.content-preview :deep(.ct-var) {
  display: inline-block;
  padding: 0 6px;
  margin: 0 2px;
  border-radius: 3px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 13px;
}
</style>
