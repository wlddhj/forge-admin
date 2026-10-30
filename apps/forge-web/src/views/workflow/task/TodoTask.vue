<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="任务范围">
          <el-radio-group v-model="source" @change="handleSourceChange">
            <el-radio-button value="todo">我的待办</el-radio-button>
            <el-radio-button value="claimable">抢办池</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.name" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.processDefinitionName" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" placeholder="请输入流程标题" clearable />
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
        id="wfTodoTaskTable"
        ref="tableRef"
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
        <vxe-column field="processNo" title="流程编号" width="135" />
        <vxe-column title="流程名称" min-width="160" show-overflow>
          <template #default="{ row }">{{ row.instanceTitle || row.processDefinitionName }}</template>
        </vxe-column>
        <vxe-column field="name" title="任务名称" min-width="120" />
        <vxe-column field="assigneeName" title="受理人" min-width="150">
          <template #default="{ row }">
            <template v-if="row.candidate">
              <el-tag type="warning" size="small">待认领</el-tag>
              <span v-if="row.candidateUsers?.length" style="margin-left: 6px; color: #909399; font-size: 12px">
                {{ row.candidateUsers.join(', ') }}
              </span>
            </template>
            <span v-else>{{ row.assigneeName || '-' }}</span>
          </template>
        </vxe-column>
        <vxe-column field="createTime" title="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="source === 'claimable'">
              <el-button type="warning" link size="small" @click.stop="handleClaim(row)">认领</el-button>
            </template>
            <template v-else>
              <el-button v-if="row.candidate" type="warning" link size="small" @click.stop="handleClaim(row)">认领</el-button>
              <el-button
                v-if="row.claimedFromGroup"
                v-permission="'workflow:task:claim'"
                type="info"
                link
                size="small"
                @click.stop="handleUnclaim(row)"
              >
                取消签收
              </el-button>
              <el-button type="primary" link size="small" @click.stop="handleProcess(row)">处理</el-button>
            </template>
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
import { ElMessage } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { taskApi } from '@/api/workflow/task'
import type { TaskInfo, TaskQuery } from '@/types/workflow'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useResponsive } from '@/composables/useResponsive'
import TaskDetailDrawer from './components/TaskDetailDrawer.vue'

const { isMobile: _isMobile } = useResponsive()

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
// 数据源：todo 我的待办 / claimable 抢办池（角色/部门候选组）
const source = ref<'todo' | 'claimable'>('todo')

const queryParams = reactive<TaskQuery>({
  name: '',
  processDefinitionName: '',
  title: '',
  pageNum: 1,
  pageSize: 20
})

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

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
    const res = source.value === 'claimable' ? await taskApi.claimable(queryParams) : await taskApi.todo(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 切换数据源
const handleSourceChange = () => {
  queryParams.pageNum = 1
  getList()
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
  queryParams.title = ''
  queryParams.pageNum = 1
  getList()
}

// 处理任务
const handleProcess = (row: TaskInfo) => {
  drawerRef.value?.open(row.id)
}

// 认领任务
const handleClaim = async (row: TaskInfo) => {
  try {
    await taskApi.claim(row.id)
    ElMessage.success('认领成功')
    getList()
  } catch {
    // handled by request util
  }
}

// 取消签收（还原为候选组）
const handleUnclaim = async (row: TaskInfo) => {
  try {
    await taskApi.unclaim(row.id)
    ElMessage.success('已取消签收，任务归还候选组')
    getList()
  } catch {
    // handled by request util
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">

</style>
