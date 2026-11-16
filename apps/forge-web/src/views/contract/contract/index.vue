<template>
  <div class="app-container">
    <div class="aside-tree-layout">
    <!-- 左侧分类树（点击筛选，选父含子孙） -->
    <CategoryAsideTree v-model="queryParams.categoryId" @select="handleQuery" />

    <div class="aside-main">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="合同标题">
          <el-input v-model="queryParams.title" placeholder="请输入合同标题" clearable />
        </el-form-item>
        <el-form-item label="合同编号">
          <el-input v-model="queryParams.contractNo" placeholder="如 CT-LD-20261116-0001" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option v-for="(label, value) in CONTRACT_STATUS_LABEL" :key="value" :label="label" :value="Number(value)" />
          </el-select>
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
        <template #buttons>
          <el-button v-permission="'contract:contract:create'" type="primary" @click="openForm(null)">
            <el-icon><Plus /></el-icon>新建合同
          </el-button>
          <el-button v-permission="'contract:contract:export'" @click="handleExport">
            <el-icon><Download /></el-icon>导出清单
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="contractTable"
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
      >
        <vxe-column type="seq" title="序号" width="60" align="center" />
        <vxe-column field="contractNo" title="合同编号" width="200">
          <template #default="{ row }">{{ row.contractNo || '-' }}</template>
        </vxe-column>
        <vxe-column field="title" title="合同标题" min-width="180" />
        <vxe-column title="分类" width="110" align="center">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </vxe-column>
        <vxe-column title="模板" width="150">
          <template #default="{ row }">
            {{ row.templateName ? `${row.templateName}·v${row.templateVersionNo}` : '-' }}
          </template>
        </vxe-column>
        <vxe-column title="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="CONTRACT_STATUS_TAG[row.status] || 'info'" size="small">
              {{ CONTRACT_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'contract:contract:list'" type="primary" link size="small"
              @click="openDetail(row)">
              详情
            </el-button>
            <el-button v-if="isEditableStatus(row.status)" v-permission="'contract:contract:update'"
              type="primary" link size="small" @click="openForm(row)">
              {{ row.status === 0 ? '编辑' : '重新编辑' }}
            </el-button>
            <el-button v-if="isEditableStatus(row.status)" v-permission="'contract:contract:submit'"
              type="success" link size="small" @click="submit(row)">
              提交审批
            </el-button>
            <el-button v-permission="'contract:contract:print'"
              type="warning" link size="small" @click="printRow(row)">
              打印
            </el-button>
            <el-button v-if="row.status === 0" v-permission="'contract:contract:delete'"
              type="danger" link size="small" @click="remove(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="getList" />
    </el-card>

    <!-- 新建/编辑草稿 -->
    <ContractForm v-model="formVisible" :contract="editing" @success="getList" />
    <!-- 详情抽屉 -->
    <ContractDetail v-model="detailVisible" :contract-id="detailId" />
    <!-- 列表行直接打印 -->
    <PrintDialog v-model="printVisible" :contract-id="printId" />
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'
import CategoryAsideTree from '@/views/contract/components/CategoryAsideTree.vue'
import TablePagination from '@/components/TablePagination.vue'
import { contractApi } from '@/api/contract/contract'
import { CONTRACT_STATUS_LABEL, CONTRACT_STATUS_TAG, isEditableStatus, type ContractItem } from '@/types/contract'
import ContractForm from './components/ContractForm.vue'
import ContractDetail from './components/ContractDetail.vue'
import PrintDialog from './components/PrintDialog.vue'

defineOptions({ name: 'ContractContract' })

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
  getList()
})

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<ContractItem[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  title: undefined as string | undefined,
  contractNo: undefined as string | undefined,
  categoryId: undefined as string | undefined,
  status: undefined as number | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await contractApi.page(queryParams)
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.title = undefined
  queryParams.contractNo = undefined
  queryParams.status = undefined
  handleQuery()
}

// ========== 表单 / 详情 / 打印 ==========
const formVisible = ref(false)
const editing = ref<ContractItem | null>(null)
const detailVisible = ref(false)
const detailId = ref('')
const printVisible = ref(false)
const printId = ref('')

const openForm = (row: ContractItem | null) => {
  editing.value = row
  formVisible.value = true
}

const openDetail = (row: ContractItem) => {
  detailId.value = row.id
  detailVisible.value = true
}

const printRow = (row: ContractItem) => {
  printId.value = row.id
  printVisible.value = true
}

// ========== 提交 / 删除 / 导出 ==========
const submit = async (row: ContractItem) => {
  await ElMessageBox.confirm(
    `确定提交「${row.title}」进入审批流程？提交前请确认变量已填写完整`,
    '提示',
    { type: 'warning' }
  )
  await contractApi.submit(row.id)
  ElMessage.success('已提交审批')
  getList()
}

const remove = async (row: ContractItem) => {
  await ElMessageBox.confirm(`确定删除草稿「${row.title}」？`, '提示', { type: 'warning' })
  await contractApi.remove(row.id)
  ElMessage.success('已删除')
  getList()
}

const handleExport = async () => {
  const blob = await contractApi.export({
    title: queryParams.title,
    contractNo: queryParams.contractNo,
    categoryId: queryParams.categoryId,
    status: queryParams.status
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `合同清单_${new Date().toISOString().slice(0, 10)}.xlsx`
  a.click()
  URL.revokeObjectURL(url)
}
</script>
