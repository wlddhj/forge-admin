<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'screen:screen:add'" type="primary" @click="handleCreate">新增大屏</el-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef" :data="tableData" :loading="loading" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" border="none" stripe show-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="name" title="名称" min-width="160" />
        <vxe-column field="code" title="路由编码" min-width="140" />
        <vxe-column title="主题" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ themeLabel(row.theme) }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column title="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">已发布</el-tag>
            <el-tag v-else type="info" size="small">草稿</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="updateTime" title="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'screen:screen:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
            <el-button v-if="row.status === 1" link size="small" @click="handleRender(row)">渲染</el-button>
            <el-button v-if="row.status === 1" link size="small" @click="handleCopyLink(row)">使用链接</el-button>
            <el-button v-permission="'screen:screen:copy'" link size="small" @click="handleCopy(row)">复制</el-button>
            <el-button v-permission="'screen:screen:publish'" link size="small" :disabled="row.status === 1" @click="handlePublish(row)">发布</el-button>
            <el-button v-permission="'screen:screen:remove'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="copyDialogVisible" title="复制大屏" width="420px">
      <el-form :model="copyForm" label-width="80px">
        <el-form-item label="新编码" required>
          <el-input v-model="copyForm.newCode" placeholder="路由编码（小写字母+数字）" />
        </el-form-item>
        <el-form-item label="新名称" required>
          <el-input v-model="copyForm.newName" placeholder="显示名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="copying" @click="confirmCopy">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="linkDialogVisible" title="使用链接" width="640px">
      <el-alert
        title="使用方法"
        type="info"
        :closable="false"
        show-icon
        description="将下方链接复制到浏览器、iframe 嵌入、移动端 H5 中打开；调用方需携带有效的 forge-admin 登录 Token。"
        style="margin-bottom: 16px"
      />
      <el-form label-width="90px">
        <el-form-item label="渲染地址">
          <el-input v-model="linkInfo.url" readonly>
            <template #append>
              <el-button @click="copyToClipboard(linkInfo.url)">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="iframe 嵌入">
          <el-input
            v-model="linkInfo.iframe"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            readonly
          />
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getScreenList, createScreen, copyScreen, deleteScreen, publishScreen,
  type ScreenListQuery, type ScreenDetailResponse, type ScreenCopyRequest
} from '@/api/screen'
import { SCREEN_THEMES } from '@/constants/screen'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'

const { tableHeight } = useTableHeight()
const pageNum = computed({ get: () => queryParams.pageNum, set: v => { queryParams.pageNum = v } })
const pageSize = computed({ get: () => queryParams.pageSize, set: v => { queryParams.pageSize = v } })
const { seqMethod } = useTableSeq({ currentPage: pageNum, pageSize })

const queryParams = reactive<ScreenListQuery>({ pageNum: 1, pageSize: 20, name: '', status: undefined })
const tableData = ref<ScreenDetailResponse[]>([])
const total = ref(0)
const loading = ref(false)

const tableRef = ref()
const toolbarRef = ref()

const themeLabel = (theme: string): string =>
  SCREEN_THEMES.find(t => t.value === theme)?.label ?? theme

const getList = async () => {
  loading.value = true
  try {
    const res = await getScreenList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.name = ''; queryParams.status = undefined; handleQuery() }

const handleCreate = async () => {
  const newId = await createScreen({
    code: `screen-${Date.now()}`,
    name: '未命名大屏',
    theme: 'dark-tech'
  }).catch((e) => {
    console.error('创建大屏失败', e)
    ElMessage.error('创建大屏失败：' + (e instanceof Error ? e.message : String(e)))
    return null
  })
  if (newId) window.open(`/screen/editor/${newId}?template=blank`, '_blank')
}

const handleEdit = (row: ScreenDetailResponse) => window.open(`/screen/editor/${row.id}`, '_blank')
const handlePreview = (row: ScreenDetailResponse) => window.open(`/screen/preview/${row.code}`, '_blank')
const handleRender = (row: ScreenDetailResponse) => window.open(`/screen/render/${row.code}`, '_blank')

const linkDialogVisible = ref(false)
const linkInfo = reactive({ url: '', iframe: '' })
const handleCopyLink = (row: ScreenDetailResponse) => {
  const base = window.location.origin
  const url = `${base}/screen/render/${row.code}`
  linkInfo.url = url
  linkInfo.iframe = `<iframe\n  src="${url}"\n  width="100%"\n  height="100%"\n  frameborder="0"\n  allowfullscreen\n  style="border:none;display:block"\n></iframe>`
  linkDialogVisible.value = true
}
const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败，请手动复制')
  }
}

const copyDialogVisible = ref(false)
const copying = ref(false)
const copyForm = reactive<ScreenCopyRequest & { sourceCode: string }>({
  sourceCode: '', newCode: '', newName: ''
})
const handleCopy = (row: ScreenDetailResponse) => {
  copyForm.sourceCode = row.code
  copyForm.newCode = `${row.code}-copy`
  copyForm.newName = `${row.name} - 副本`
  copyDialogVisible.value = true
}
const confirmCopy = async () => {
  if (!copyForm.newCode || !copyForm.newName) { ElMessage.error('请填写完整'); return }
  copying.value = true
  try {
    await copyScreen(copyForm.sourceCode, { newCode: copyForm.newCode, newName: copyForm.newName })
    ElMessage.success('复制成功')
    copyDialogVisible.value = false
    getList()
  } finally {
    copying.value = false
  }
}

const handlePublish = (row: ScreenDetailResponse) => {
  ElMessageBox.confirm(`确认发布"${row.name}"？发布后所有有权限的用户可访问。`, '提示', { type: 'warning' })
    .then(async () => { await publishScreen(row.code); ElMessage.success('发布成功'); getList() })
}

const handleDelete = (row: ScreenDetailResponse) => {
  ElMessageBox.confirm(`确认删除"${row.name}"？此操作不可恢复。`, '危险操作', { type: 'error' })
    .then(async () => { await deleteScreen([row.id]); ElMessage.success('删除成功'); getList() })
}

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value)
  getList()
})
</script>

<style scoped>
.app-container { padding: 0; }
.search-card { margin-bottom: 15px; }
.table-card .TablePagination { margin-top: 15px; }
</style>
