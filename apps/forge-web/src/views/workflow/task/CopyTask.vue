<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.processInstanceName" placeholder="请输入流程名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery"><el-icon><Search /></el-icon>搜索</el-button>
          <el-button @click="handleReset"><el-icon><Refresh /></el-icon>重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="wfCopyTable"
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
      >
        <vxe-column type="seq" title="序号" width="60" />
        <vxe-column field="processInstanceName" title="流程名称" min-width="180" />
        <vxe-column field="activityName" title="抄送节点" width="140" />
        <vxe-column field="startUserName" title="发起人" width="100" />
        <vxe-column field="userName" title="被抄送人" width="100" />
        <vxe-column field="reason" title="抄送原因" min-width="200" />
        <vxe-column field="createTime" title="抄送时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'
import { copyApi, type CopyQuery, type ProcessInstanceCopy } from '@/api/workflow/process-instance-copy'

const { tableHeight } = useTableHeight()
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

const loading = ref(false)
const tableData = ref<ProcessInstanceCopy[]>([])
const total = ref(0)

const queryParams = reactive<CopyQuery>({
  pageNum: 1,
  pageSize: 10,
  processInstanceName: undefined,
})

const getList = async () => {
  loading.value = true
  try {
    const res = await copyApi.page(queryParams)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // error handled
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.processInstanceName = undefined
  queryParams.pageNum = 1
  getList()
}

getList()
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
  .search-card { margin-bottom: 15px; }
  .table-card {
    .el-pagination { margin-top: 15px; justify-content: flex-end; }
  }
}
</style>
