<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.name" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.processDefinitionName" placeholder="请输入流程名称" clearable />
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
        id="wfDoneTaskTable"
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
        <vxe-column field="name" title="任务名称" min-width="150" />
        <vxe-column field="processDefinitionName" title="流程名称" min-width="150" />
        <vxe-column v-if="!isMobile" field="assigneeName" title="受理人" width="100" />
        <vxe-column field="createTime" title="完成时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleView(row)">查看</el-button>
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

    <!-- 任务详情抽屉 -->
    <TaskDetailDrawer ref="drawerRef" @success="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { taskApi } from '@/api/workflow/task'
import type { TaskInfo, TaskQuery } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useResponsive } from '@/composables/useResponsive'
import TaskDetailDrawer from './components/TaskDetailDrawer.vue'

const { isMobile } = useResponsive()

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const drawerRef = ref<InstanceType<typeof TaskDetailDrawer> | null>(null)

// 序号计算
const loading = ref(false)
const tableData = ref<TaskInfo[]>([])
const total = ref(0)

const queryParams = reactive<TaskQuery>({
  name: '',
  processDefinitionName: '',
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
    const res = await taskApi.done(queryParams)
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
  queryParams.name = ''
  queryParams.processDefinitionName = ''
  queryParams.pageNum = 1
  getList()
}

// 查看任务详情
const handleView = (row: TaskInfo) => {
  drawerRef.value?.open(row.id)
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}
</style>
