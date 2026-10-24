<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="表名">
          <el-input v-model="queryParams.tableName" placeholder="请输入表名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
          <el-button v-permission="'ai:sql-whitelist:add'" type="primary" @click="handleAdd">新增</el-button>
        </template>
      </vxe-toolbar>
      <vxe-table id="aiSqlWhitelistTable" ref="tableRef" :data="tableData" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="tableName" title="表名" min-width="180" />
        <vxe-column field="tableComment" title="表注释" min-width="180" />
        <vxe-column field="enabled" title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="remark" title="备注" min-width="160" />
        <vxe-column field="updateTime" title="更新时间" width="170" />
        <vxe-column title="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'ai:sql-whitelist:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'ai:sql-whitelist:remove'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <TablePagination v-model:page-num="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @change="getList" />
    </el-card>

    <WhitelistFormDialog v-model:visible="dialogVisible" :row="currentRow" @success="getList" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { sqlWhitelistApi, type SqlWhitelistItem } from '@/api/ai/sql-whitelist'
import WhitelistFormDialog from './components/WhitelistFormDialog.vue'

const { tableHeight } = useTableHeight()

const tableRef = ref()
const toolbarRef = ref()
const dialogVisible = ref(false)
const currentRow = ref<SqlWhitelistItem | null>(null)
const tableData = ref<SqlWhitelistItem[]>([])
const total = ref(0)

const queryParams = reactive({ pageNum: 1, pageSize: 10, tableName: '', enabled: undefined as number | undefined })

const { seqMethod } = useTableSeq({
  currentPage: computed(() => queryParams.pageNum),
  pageSize: computed(() => queryParams.pageSize)
})

const getList = async () => {
  const res = await sqlWhitelistApi.page({ ...queryParams })
  tableData.value = res.list ?? []
  total.value = res.total ?? 0
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.tableName = ''
  queryParams.enabled = undefined
  handleQuery()
}

const handleAdd = () => {
  currentRow.value = null
  dialogVisible.value = true
}

const handleEdit = (row: SqlWhitelistItem) => {
  currentRow.value = row
  dialogVisible.value = true
}

const handleDelete = async (row: SqlWhitelistItem) => {
  await ElMessageBox.confirm(`确定删除白名单「${row.tableName}」？删除后 Agent 将无法查询该表`, '提示', { type: 'warning' })
  await sqlWhitelistApi.remove([row.id!])
  ElMessage.success('删除成功')
  getList()
}

onMounted(() => {
  tableRef.value?.connect(toolbarRef.value!)
  getList()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;
}
</style>
