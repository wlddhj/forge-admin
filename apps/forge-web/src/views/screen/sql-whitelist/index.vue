<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="表名">
          <el-input v-model="queryParams.name" placeholder="请输入表名" clearable @keyup.enter="handleQuery" />
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
          <el-button type="primary" v-permission="'screen:sql-whitelist:add'" @click="handleAdd">新增</el-button>
        </template>
      </vxe-toolbar>
      <vxe-table ref="tableRef" id="sqlWhitelistTable" :data="tableData" :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true }" show-overflow="tooltip">
        <vxe-column type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="schemaName" title="库名" min-width="140" />
        <vxe-column field="tableName" title="表名" min-width="160" />
        <vxe-column field="columnList" title="允许列" min-width="200">
          <template #default="{ row }">
            <span>{{ row.columnList || '全部' }}</span>
          </template>
        </vxe-column>
        <vxe-column field="riskLevel" title="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)" size="small">
              {{ riskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="enabled" title="启用" width="70">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" disabled size="small" />
          </template>
        </vxe-column>
        <vxe-column field="remark" title="备注" min-width="160" />
        <vxe-column title="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-permission="'screen:sql-whitelist:edit'" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" v-permission="'screen:sql-whitelist:remove'" @click="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" @size-change="getList" @current-change="getList" />
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog
      :model-value="dialogVisible"
      :title="isEdit ? '编辑白名单' : '新增白名单'"
      width="560px"
      :close-on-click-modal="false"
      @update:model-value="(v: boolean) => dialogVisible = v"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" v-loading="formLoading">
        <el-form-item label="库名" prop="schemaName">
          <el-input v-model="form.schemaName" :disabled="isEdit" placeholder="forge_admin" />
        </el-form-item>
        <el-form-item label="表名" prop="tableName">
          <el-input v-model="form.tableName" :disabled="isEdit" placeholder="sys_user" />
        </el-form-item>
        <el-form-item label="允许列" prop="columnList">
          <el-input v-model="form.columnList" type="textarea" :rows="3" placeholder="逗号分隔，留空表示全部" />
        </el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-select v-model="form.riskLevel" style="width: 200px">
            <el-option label="公开" :value="0" />
            <el-option label="内部" :value="1" />
            <el-option label="敏感" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import {
  getSqlWhitelistList, createSqlWhitelist, updateSqlWhitelist, deleteSqlWhitelist,
  type SqlWhitelistItem, type SqlWhitelistQuery, getSqlWhitelistDetail
} from '@/api/screen'

const { tableHeight } = useTableHeight()

const tableRef = ref()
const toolbarRef = ref()
const formRef = ref()
const dialogVisible = ref(false)
const saving = ref(false)
const formLoading = ref(false)
const tableData = ref<SqlWhitelistItem[]>([])
const total = ref(0)

const queryParams = reactive<SqlWhitelistQuery>({ pageNum: 1, pageSize: 10, name: '' })

const { seqMethod } = useTableSeq({
  currentPage: computed(() => queryParams.pageNum),
  pageSize: computed(() => queryParams.pageSize)
})

const isEdit = computed(() => Boolean(form.id))

const form = reactive<SqlWhitelistItem>({
  schemaName: '', tableName: '', columnList: '', riskLevel: 0, enabled: 1, remark: ''
})

const rules = {
  schemaName: [{ required: true, message: '请输入库名', trigger: 'blur' }],
  tableName: [{ required: true, message: '请输入表名', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }]
}

const riskLabels: Record<number, string> = { 0: '公开', 1: '内部', 2: '敏感' }
const riskTagTypes: Record<number, string> = { 0: 'success', 1: 'warning', 2: 'danger' }
const riskLabel = (v: number) => riskLabels[v] ?? ''
const riskTagType = (v: number) => riskTagTypes[v] ?? 'info'

const resetForm = () => {
  Object.assign(form, { id: undefined, schemaName: '', tableName: '', columnList: '', riskLevel: 0, enabled: 1, remark: '' })
}

const getList = async () => {
  const res = await getSqlWhitelistList({ ...queryParams })
  tableData.value = res.list ?? []
  total.value = res.total ?? 0
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.name = ''
  handleQuery()
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: SqlWhitelistItem) => {
  formLoading.value = true
  try {
    const detail = await getSqlWhitelistDetail(row.id!)
    Object.assign(form, detail)
    dialogVisible.value = true
  } finally {
    formLoading.value = false
  }
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateSqlWhitelist({ ...form })
      ElMessage.success('保存成功')
    } else {
      await createSqlWhitelist({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: SqlWhitelistItem) => {
  await ElMessageBox.confirm(`确定删除白名单「${row.schemaName}.${row.tableName}」？`, '提示', { type: 'warning' })
  await deleteSqlWhitelist([row.id!])
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
  .search-card { margin-bottom: 15px; }
  .table-card .el-pagination { margin-top: 15px; justify-content: flex-end; }
}
</style>
