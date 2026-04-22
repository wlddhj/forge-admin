<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="文件名">
          <el-input v-model="queryParams.fileName" placeholder="请输入文件名" clearable />
        </el-form-item>
        <el-form-item label="文件类型">
          <el-input v-model="queryParams.fileType" placeholder="请输入文件类型" clearable />
        </el-form-item>
        <el-form-item label="上传者">
          <el-input v-model="queryParams.uploaderName" placeholder="请输入上传者" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">附件列表</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :before-upload="beforeUpload"
          >
            <el-button type="primary">
              <el-icon><Upload /></el-icon>
            </el-button>
          </el-upload>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="文件名">
          <el-input v-model="queryParams.fileName" placeholder="请输入文件名" clearable />
        </el-form-item>
        <el-form-item label="文件类型">
          <el-input v-model="queryParams.fileType" placeholder="请输入文件类型" clearable />
        </el-form-item>
        <el-form-item label="上传者">
          <el-input v-model="queryParams.uploaderName" placeholder="请输入上传者" clearable />
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :before-upload="beforeUpload"
          >
            <el-button type="primary">上传文件</el-button>
          </el-upload>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-refresh" style="margin-right: 10px" @click="handleQuery"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysAttachmentTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />

        <!-- 文件名 -->
        <vxe-column field="originalName" title="文件名" min-width="200">
          <template #default="{ row }">
            <div class="file-name">
              <el-icon v-if="isImage(row.fileExtension)" class="file-icon"><Picture /></el-icon>
              <el-icon v-else-if="isDocument(row.fileExtension)" class="file-icon"><Document /></el-icon>
              <el-icon v-else class="file-icon"><Folder /></el-icon>
              <span>{{ row.originalName }}</span>
            </div>
          </template>
        </vxe-column>

        <!-- 文件大小 -->
        <vxe-column title="文件大小" width="120">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </vxe-column>

        <!-- 文件类型（桌面端） -->
        <vxe-column v-if="!isMobile" field="fileType" title="文件类型" width="150" />

        <!-- 存储方式（桌面端） -->
        <vxe-column v-if="!isMobile" field="storageType" title="存储方式" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.storageType === 'local' ? '本地存储' : row.storageType }}</el-tag>
          </template>
        </vxe-column>

        <!-- 上传者（桌面端） -->
        <vxe-column v-if="!isMobile" field="uploaderName" title="上传者" width="100" />

        <!-- 上传时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="createTime" title="上传时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isImage(row.fileExtension)" type="primary" link @click="handlePreview(row)">预览</el-button>
            <el-button type="primary" link @click="handleDownload(row)">下载</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.originalName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button v-if="isImage(item.fileExtension)" size="small" type="primary" @click.stop="handlePreview(item)">预览</el-button>
        <el-button size="small" type="primary" @click.stop="handleDownload(item)">下载</el-button>
        <el-button size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 图片预览对话框 -->
    <el-dialog v-model="previewVisible" title="图片预览" width="800px">
      <img :src="previewUrl" style="width: 100%;" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { Picture, Document, Folder, Upload } from '@element-plus/icons-vue'
import { getAttachmentList, deleteAttachment } from '@/api/system'
import type { Attachment } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useUserStore } from '@/stores/user'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const userStore = useUserStore()
const { isMobile } = useResponsive()

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<Attachment[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<Attachment | null>(null)

const queryParams = reactive({
  fileName: '',
  fileType: '',
  uploaderName: '',
  pageNum: 1,
  pageSize: 10
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.fileName) count++
  if (queryParams.fileType) count++
  if (queryParams.uploaderName) count++
  return count
})

const previewVisible = ref(false)
const previewUrl = ref('')

const uploadUrl = computed(() => import.meta.env.VITE_API_BASE_URL + '/system/attachment/upload')
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${userStore.token}` }))

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getAttachmentList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => {
  queryParams.fileName = ''
  queryParams.fileType = ''
  queryParams.uploaderName = ''
  handleQuery()
}

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const beforeUpload = (file: File) => {
  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    ElMessage.error('上传文件大小不能超过 50MB!')
    return false
  }
  return true
}

const handleUploadSuccess = () => {
  ElMessage.success('上传成功')
  getList()
}

const handleUploadError = () => {
  ElMessage.error('上传失败')
}

const handlePreview = (row: Attachment) => {
  previewUrl.value = row.fileUrl
  previewVisible.value = true
}

const handleDownload = (row: Attachment) => {
  window.open(row.fileUrl, '_blank')
}

const handleDelete = async (row: Attachment) => {
  try {
    await ElMessageBox.confirm(`确定删除文件 "${row.originalName}"?`, '警告', { type: 'warning' })
    await deleteAttachment([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  } catch (e) {}
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: Attachment | null }) => {
  if (isMobile.value) {
    selectedRow.value = row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
  if (tableRef.value) {
    tableRef.value.clearCurrentRow()
  }
}

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const isImage = (ext: string): boolean => {
  const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  return imageExts.includes(ext?.toLowerCase())
}

const isDocument = (ext: string): boolean => {
  const docExts = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf', 'txt']
  return docExts.includes(ext?.toLowerCase())
}

onMounted(() => getList())
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .file-name {
      display: flex;
      align-items: center;
      gap: 8px;
      .file-icon {
        font-size: 18px;
        color: var(--el-color-primary);
      }
    }
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}
</style>