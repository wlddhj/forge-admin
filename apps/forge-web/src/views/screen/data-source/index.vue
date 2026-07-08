<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" clearable style="width: 120px">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="SQL" value="SQL" />
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
          <el-button v-permission="'screen:data-source:add'" type="primary" @click="handleCreate">新增数据源</el-button>
        </template>
      </vxe-toolbar>
      <vxe-table
        ref="tableRef" :data="tableData" :loading="loading" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" border="none" stripe show-overflow="tooltip"
      >
        <vxe-column type="seq" title="#" width="60" :seq-method="seqMethod" />
        <vxe-column field="code" title="编码" min-width="120" />
        <vxe-column field="name" title="名称" min-width="160" />
        <vxe-column field="type" title="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'SQL' ? 'warning' : 'primary'" size="small">{{ row.type }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column field="cacheSeconds" title="缓存(s)" width="100" />
        <vxe-column field="enabled" title="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'screen:data-source:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'screen:data-source:remove'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <DataSourceEditor
      v-model="editorVisible"
      :data-source="editingSource"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDataSourceList, deleteDataSource, type DataSourceListQuery, type ScreenDataSource } from '@/api/screen'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import DataSourceEditor from './editor.vue'

const { tableHeight } = useTableHeight()
const pageNum = computed({ get: () => queryParams.pageNum, set: v => { queryParams.pageNum = v } })
const pageSize = computed({ get: () => queryParams.pageSize, set: v => { queryParams.pageSize = v } })
const { seqMethod } = useTableSeq({ currentPage: pageNum, pageSize })

const queryParams = reactive<DataSourceListQuery>({ pageNum: 1, pageSize: 20, name: '', type: undefined })
const tableData = ref<ScreenDataSource[]>([])
const total = ref(0)
const loading = ref(false)
const tableRef = ref()
const toolbarRef = ref()

const editorVisible = ref(false)
const editingSource = ref<ScreenDataSource | null>(null)

const getList = async () => {
  loading.value = true
  try {
    const res = await getDataSourceList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.name = ''; queryParams.type = undefined; handleQuery() }
const handleCreate = () => { editingSource.value = null; editorVisible.value = true }
const handleEdit = (row: ScreenDataSource) => { editingSource.value = row; editorVisible.value = true }
const handleDelete = (row: ScreenDataSource) =>
  ElMessageBox.confirm(`确认删除数据源"${row.name}"？`, '危险操作', { type: 'error' })
    .then(async () => { await deleteDataSource([row.id!]); ElMessage.success('删除成功'); getList() })
const onSaved = () => { getList() }

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
