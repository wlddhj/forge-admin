<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.processDefinitionName" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item label="发起人">
          <el-input v-model="queryParams.startUserName" placeholder="请输入发起人" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="运行中" value="running" />
            <el-option label="已结束" value="finished" />
            <el-option label="已终止" value="terminated" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="wfProcessInstanceTable"
        :custom-config="{ mode: 'modal' }"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :seq-config="{ seqMethod }"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" />
        <vxe-column field="processNo" title="流程编号" width="160" />
        <vxe-column field="processDefinitionName" title="流程名称" min-width="150" />
        <vxe-column field="startUserName" title="发起人" width="100" />
        <vxe-column field="currentActivityName" title="当前节点" width="120" />
        <vxe-column title="受理人" min-width="150">
          <template #default="{ row }">
            <template v-if="row.currentAssigneeNames && row.currentAssigneeNames.length > 0">
              {{ row.currentAssigneeNames.join(', ') }}
            </template>
            <template v-else-if="row.currentCandidateNames && row.currentCandidateNames.length > 0">
              <el-tag type="warning" size="small">待认领</el-tag>
              <span style="margin-left: 4px; color: #909399; font-size: 12px">
                {{ row.currentCandidateNames.join(', ') }}
              </span>
            </template>
            <template v-else-if="row.endTime">
              <span style="color: #909399">-</span>
            </template>
          </template>
        </vxe-column>
        <vxe-column title="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="!row.endTime" type="primary">运行中</el-tag>
            <el-tag v-else-if="row.deleteReason" type="danger">已终止</el-tag>
            <el-tag v-else type="success">已结束</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="startTime" title="发起时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }}
          </template>
        </vxe-column>
        <vxe-column v-if="!isMobile" field="endTime" title="结束时间" width="170">
          <template #default="{ row }">
            {{ row.endTime ? formatDateTime(row.endTime) : '-' }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleDetail(row)">详情</el-button>
            <el-button
              v-if="!row.endTime"
              v-permission="'workflow:instance:cancel'"
              type="danger"
              link
              size="small"
              @click.stop="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination
        v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        @change="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { processInstanceApi } from '@/api/workflow/process-instance'
import type { ProcessInstance, ProcessInstanceQuery } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useResponsive } from '@/composables/useResponsive'

const router = useRouter()
const { isMobile } = useResponsive()

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

// 序号计算
const loading = ref(false)
const tableData = ref<ProcessInstance[]>([])
const total = ref(0)

const queryParams = reactive<ProcessInstanceQuery>({
  processDefinitionName: '',
  startUserName: '',
  status: undefined,
  pageNum: 1,
  pageSize: 20
})

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef }) as any

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

// 获取列表数据
const getList = async () => {
  loading.value = true
  try {
    const res = await processInstanceApi.page(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.processDefinitionName = ''
  queryParams.startUserName = ''
  queryParams.status = undefined
  queryParams.pageNum = 1
  getList()
}

// 查看详情
const handleDetail = (row: ProcessInstance) => {
  router.push({ path: '/workflow/instance/detail', query: { id: row.id } })
}

// 取消流程实例
const handleCancel = async (row: ProcessInstance) => {
  try {
    await ElMessageBox.confirm(
      `确定要取消流程 "${row.processDefinitionName}" 吗？取消后该流程将终止运行。`,
      '警告',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await processInstanceApi.cancel(row.id)
    ElMessage.success('取消成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">

</style>
