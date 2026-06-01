<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="分类编码">
          <el-input v-model="queryParams.keyCategory" placeholder="请输入分类编码" clearable />
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
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增序列
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="keySequenceTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :seq-config="{seqMethod}"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" />
        <vxe-column field="keyCategory" title="分类编码" min-width="150" />
        <vxe-column field="keyPrefix" title="前缀" width="100" />
        <vxe-column field="dateRule" title="日期规则" width="120" />
        <vxe-column field="maxValue" title="当前值" width="90" />
        <vxe-column field="seqLength" title="位数" width="70" />
        <vxe-column field="lastDateVal" title="最近日期值" width="120" />
        <vxe-column field="remark" title="备注" min-width="120" />
        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="分类编码" prop="keyCategory">
          <el-input v-model="formData.keyCategory" placeholder="如 order_no、ticket_no" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="前缀" prop="keyPrefix">
          <el-input v-model="formData.keyPrefix" placeholder="如 ORD、{0}-TK（支持 {0} 占位符）" />
        </el-form-item>
        <el-form-item label="日期规则" prop="dateRule">
          <el-input v-model="formData.dateRule" placeholder="如 yyyyMMdd、yyyyMM、yyyy" />
        </el-form-item>
        <el-form-item label="顺序号位数" prop="seqLength">
          <el-input-number v-model="formData.seqLength" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { Plus } from '@element-plus/icons-vue'
import { getKeySequenceList, addKeySequence, updateKeySequence, deleteKeySequence } from '@/api/system'
import type { KeySequence } from '@/api/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'

const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<KeySequence[]>([])
const total = ref(0)

const queryParams = reactive({
  keyCategory: '',
  pageNum: 1,
  pageSize: 20
})

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as number | undefined,
  keyCategory: '',
  keyPrefix: '',
  dateRule: '',
  seqLength: 4,
  remark: ''
})

const formRules: FormRules = {
  keyCategory: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  seqLength: [{ required: true, message: '请输入顺序号位数', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getKeySequenceList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => {
  queryParams.keyCategory = ''
  handleQuery()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增序列'
  Object.assign(formData, { id: undefined, keyCategory: '', keyPrefix: '', dateRule: '', seqLength: 4, remark: '' })
  dialogVisible.value = true
}

const handleEdit = (row: KeySequence) => {
  isEdit.value = true
  dialogTitle.value = '编辑序列'
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateKeySequence(formData)
      ElMessage.success('更新成功')
    } else {
      await addKeySequence(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: KeySequence) => {
  try {
    await ElMessageBox.confirm(`确定删除分类编码 "${row.keyCategory}" 的序列配置?`, '警告', { type: 'warning' })
    await deleteKeySequence([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {}
}

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

onMounted(() => getList())
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
