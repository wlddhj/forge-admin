<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="文件名">
          <el-input v-model="queryParams.fileName" placeholder="请输入文件名" clearable />
        </el-form-item>
        <el-form-item label="文件类型">
          <el-select v-model="queryParams.fileType" placeholder="请选择类型" clearable style="width: 120px">
            <el-option label="PDF" value="pdf" />
            <el-option label="Word" value="docx" />
            <el-option label="TXT" value="txt" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="处理中" :value="0" />
            <el-option label="已完成" :value="1" />
            <el-option label="处理失败" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-upload
            ref="uploadRef"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="handleUpload"
            accept=".pdf,.docx,.doc,.txt"
          >
            <el-button type="primary">
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
          </el-upload>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="getList"></vxe-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="aiDocumentTable"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :checkbox-config="{ highlight: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        @checkbox-change="handleCheckboxChange"
      >
        <vxe-column type="checkbox" width="50" />
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="fileName" title="文件名" min-width="200" />
        <vxe-column field="fileType" title="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fileType.toUpperCase() }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="fileSize" title="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </vxe-column>
        <vxe-column title="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info">处理中</el-tag>
            <el-tag v-else-if="row.status === 1" type="success">已完成</el-tag>
            <el-tag v-else type="danger">失败</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="createTime" title="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">详情</el-button>
            <el-button type="success" link size="small" @click="handleSummary(row)" :disabled="row.status !== 1">摘要</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="文档详情" width="800px">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="内容" name="content">
          <div v-if="documentDetail?.content" class="content-box">
            {{ documentDetail.content }}
          </div>
          <el-empty v-else description="暂无内容" />
        </el-tab-pane>
        <el-tab-pane label="摘要" name="summary">
          <div v-if="documentDetail?.summary" class="summary-box">
            {{ documentDetail.summary }}
          </div>
          <el-empty v-else description="暂无摘要" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getDocumentList, getDocumentDetail, getDocumentSummary, uploadDocument, deleteDocument, batchDeleteDocuments } from '@/api/ai/document'
import type { DocumentResponse, DocumentQuery } from '@/api/ai/document'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)
const uploadRef = ref()

const queryParams = reactive<DocumentQuery>({
  fileName: '',
  fileType: '',
  status: undefined,
  pageNum: 1,
  pageSize: 20
})

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

const loading = ref(false)
const tableData = ref<DocumentResponse[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const detailDialogVisible = ref(false)
const detailTab = ref('content')
const documentDetail = ref<DocumentResponse | null>(null)

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getDocumentList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.fileName = ''
  queryParams.fileType = ''
  queryParams.status = undefined
  handleQuery()
}

const beforeUpload = (file: File) => {
  const allowedTypes = ['pdf', 'docx', 'doc', 'txt']
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !allowedTypes.includes(ext)) {
    ElMessage.error('只支持 PDF、Word、TXT 格式')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

const handleUpload = async (options: any) => {
  try {
    await uploadDocument(options.file)
    ElMessage.success('上传成功')
    getList()
  } catch (e) {
    ElMessage.error('上传失败')
  }
}

const handleDetail = async (row: DocumentResponse) => {
  documentDetail.value = await getDocumentDetail(row.id)
  detailDialogVisible.value = true
}

const handleSummary = async (row: DocumentResponse) => {
  try {
    ElMessage.info('正在生成摘要...')
    const result = await getDocumentSummary(row.id)
    documentDetail.value = result
    detailTab.value = 'summary'
    detailDialogVisible.value = true
    ElMessage.success('摘要生成成功')
    getList()  // 刷新列表显示摘要状态
  } catch (e) {
    ElMessage.error('摘要生成失败')
  }
}

const handleDelete = (row: DocumentResponse) => {
  ElMessageBox.confirm('确定要删除该文档吗？', '提示', { type: 'warning' }).then(async () => {
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    getList()
  })
}

const handleCheckboxChange = () => {
  const records = tableRef.value?.getCheckboxRecords() || []
  selectedIds.value = records.map((r: DocumentResponse) => r.id)
}

const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}
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

  .content-box,
  .summary-box {
    padding: 15px;
    background: #f5f7fa;
    border-radius: 8px;
    line-height: 1.8;
    max-height: 400px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>