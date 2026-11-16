<template>
  <div class="app-container">
    <div class="aside-tree-layout">
    <!-- 左侧分类树（点击筛选，选父含子孙） -->
    <CategoryAsideTree v-model="queryParams.categoryId" @select="handleQuery" />

    <div class="aside-main">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="模板名称">
          <el-input v-model="queryParams.name" placeholder="请输入模板名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="2" />
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
          <el-button v-permission="'contract:template:create'" type="primary" @click="formRef?.open()">
            <el-icon><Plus /></el-icon>新建模板
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="templateTable"
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
        <vxe-column field="name" title="模板名称" min-width="180" />
        <vxe-column title="合同分类" width="130" align="center">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </vxe-column>
        <vxe-column title="当前版本" width="90" align="center">
          <template #default="{ row }">{{ row.currentVersionNo ? 'v' + row.currentVersionNo : '-' }}</template>
        </vxe-column>
        <vxe-column title="纸张" width="90" align="center">
          <template #default="{ row }">{{ paperLabel(row.paperSize) }}</template>
        </vxe-column>
        <vxe-column title="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="TEMPLATE_STATUS_TAG[row.status] || 'info'" size="small">
              {{ TEMPLATE_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'contract:template:version'" type="primary" link size="small"
              @click="openEditor(row)">
              编辑内容
            </el-button>
            <el-button v-permission="'contract:template:version'" type="primary" link size="small"
              @click="openVersions(row)">
              版本历史
            </el-button>
            <el-button v-permission="'contract:template:update'" type="primary" link size="small"
              @click="formRef?.open(row)">
              修改
            </el-button>
            <el-button
              v-if="row.status === 1"
              v-permission="'contract:template:status'" type="warning" link size="small"
              @click="changeStatus(row, 2)"
            >
              停用
            </el-button>
            <el-button
              v-else-if="row.currentVersionNo"
              v-permission="'contract:template:status'" type="success" link size="small"
              @click="changeStatus(row, 1)"
            >
              启用
            </el-button>
            <el-button v-permission="'contract:template:delete'" type="danger" link size="small"
              @click="remove(row)">
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="getList" />
    </el-card>

    <!-- 基础信息表单 -->
    <TemplateForm ref="formRef" @success="getList" />
    <!-- 内容编辑器 -->
    <TemplateEditor v-model="editorVisible" :template="editingTemplate" @saved="onEditorSaved" />
    <!-- 版本历史 -->
    <VersionList v-model="versionVisible" :template="editingTemplate" @changed="getList" />
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
import { templateApi } from '@/api/contract/template'
import type { PaperSizeCode, TagType, TemplateItem } from '@/types/contract'
import TemplateForm from './components/TemplateForm.vue'
import TemplateEditor from './components/TemplateEditor.vue'
import VersionList from './components/VersionList.vue'

defineOptions({ name: 'ContractTemplate' })

const TEMPLATE_STATUS_TAG: Record<number, TagType> = { 0: 'info', 1: 'success', 2: 'warning' }
const TEMPLATE_STATUS_LABEL: Record<number, string> = { 0: '草稿', 1: '启用', 2: '停用' }

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

const paperLabel = (code: PaperSizeCode): string => {
  const map: Record<PaperSizeCode, string> = { A4: 'A4', A5: 'A5', B3: 'B3', LETTER: 'Letter', LEGAL: 'Legal', CUSTOM: '自定义' }
  return map[code] ?? code
}

// ========== 列表 ==========
const loading = ref(false)
const tableData = ref<TemplateItem[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  name: undefined as string | undefined,
  categoryId: undefined as string | undefined,
  status: undefined as number | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await templateApi.page(queryParams)
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
  queryParams.name = undefined
  queryParams.status = undefined
  handleQuery()
}

// ========== 编辑器 / 版本历史 ==========
const formRef = ref<InstanceType<typeof TemplateForm> | null>(null)
const editorVisible = ref(false)
const versionVisible = ref(false)
const editingTemplate = ref<TemplateItem | null>(null)

const openEditor = (row: TemplateItem) => {
  editingTemplate.value = row
  editorVisible.value = true
}

const openVersions = (row: TemplateItem) => {
  editingTemplate.value = row
  versionVisible.value = true
}

const onEditorSaved = async () => {
  // 刷新当前行（状态/当前版本变化）
  if (editingTemplate.value) {
    const fresh = await templateApi.detail(editingTemplate.value.id)
    Object.assign(editingTemplate.value, fresh)
  }
  getList()
}

// ========== 状态 / 删除 ==========
const changeStatus = async (row: TemplateItem, status: number) => {
  const action = status === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${action}模板「${row.name}」？`, '提示', { type: 'warning' })
  await templateApi.changeStatus(row.id, status)
  ElMessage.success(`已${action}`)
  getList()
}

const remove = async (row: TemplateItem) => {
  await ElMessageBox.confirm(`确定删除模板「${row.name}」？已保存的版本将一并删除`, '提示', { type: 'warning' })
  await templateApi.remove(row.id)
  ElMessage.success('已删除')
  getList()
}
</script>
