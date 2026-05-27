<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="监听器名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item label="监听类型">
          <el-select v-model="queryParams.type" placeholder="请选择" clearable style="width: 140px">
            <el-option label="执行监听" value="execution" />
            <el-option label="任务监听" value="task" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
          <el-button v-permission="'workflow:listener:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" @click="handleReset" />
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="wfListenerTable"
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
        <vxe-column field="name" title="名称" min-width="150" />
        <vxe-column field="type" title="监听类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.type === 'execution' ? 'primary' : 'warning'" size="small">
              {{ row.type === 'execution' ? '执行监听' : '任务监听' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="event" title="事件" width="120" />
        <vxe-column field="valueType" title="值类型" width="130">
          <template #default="{ row }">
            {{ valueTypeLabel(row.valueType) }}
          </template>
        </vxe-column>
        <vxe-column field="value" title="值" min-width="200" />
        <vxe-column field="status" title="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'workflow:listener:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'workflow:listener:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入监听器名称" />
        </el-form-item>
        <el-form-item label="监听类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择监听类型" style="width: 100%">
            <el-option label="执行监听" value="execution" />
            <el-option label="任务监听" value="task" />
          </el-select>
        </el-form-item>
        <el-form-item label="监听事件" prop="event">
          <el-select v-model="formData.event" placeholder="请选择监听事件" style="width: 100%">
            <el-option v-if="formData.type === 'execution'" label="start" value="start" />
            <el-option v-if="formData.type === 'execution'" label="end" value="end" />
            <el-option v-if="formData.type === 'task'" label="create" value="create" />
            <el-option v-if="formData.type === 'task'" label="assignment" value="assignment" />
            <el-option v-if="formData.type === 'task'" label="complete" value="complete" />
            <el-option v-if="formData.type === 'task'" label="delete" value="delete" />
          </el-select>
        </el-form-item>
        <el-form-item label="值类型" prop="valueType">
          <el-select v-model="formData.valueType" placeholder="请选择值类型" style="width: 100%">
            <el-option label="Java 类" value="class" />
            <el-option label="委托表达式" value="delegateExpression" />
            <el-option label="表达式" value="expression" />
          </el-select>
        </el-form-item>
        <el-form-item label="值" prop="value">
          <el-input v-model="formData.value" placeholder="请输入值" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" placeholder="请输入备注" />
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { formatDateTime } from '@/utils/dateFormat'
import { listenerApi, type ListenerQuery, type ListenerRequest, type ProcessListener } from '@/api/workflow/process-listener'

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

const valueTypeLabel = (type: string) => {
  const map: Record<string, string> = { class: 'Java 类', delegateExpression: '委托表达式', expression: '表达式' }
  return map[type] || type
}

const loading = ref(false)
const tableData = ref<ProcessListener[]>([])
const total = ref(0)

const queryParams = reactive<ListenerQuery>({
  pageNum: 1,
  pageSize: 10,
  name: undefined,
  type: undefined,
  status: undefined,
})

const getList = async () => {
  loading.value = true
  try {
    const res = await listenerApi.page(queryParams)
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
  queryParams.name = undefined
  queryParams.type = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  getList()
}

getList()

// 对话框
const dialogVisible = ref(false)
const dialogTitle = computed(() => formData.id ? '编辑监听器' : '新增监听器')
const submitLoading = ref(false)
const formRef = ref<FormInstance | null>(null)

const formData = reactive<ListenerRequest>({
  id: undefined,
  name: '',
  status: 1,
  type: 'execution',
  event: '',
  valueType: 'class',
  value: '',
  remark: '',
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择监听类型', trigger: 'change' }],
  event: [{ required: true, message: '请选择监听事件', trigger: 'change' }],
  valueType: [{ required: true, message: '请选择值类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入值', trigger: 'blur' }],
}

// 监听类型变化时清除事件选择
watch(() => formData.type, () => {
  formData.event = ''
})

const resetForm = () => {
  formData.id = undefined
  formData.name = ''
  formData.status = 1
  formData.type = 'execution'
  formData.event = ''
  formData.valueType = 'class'
  formData.value = ''
  formData.remark = ''
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: ProcessListener) => {
  formData.id = row.id
  formData.name = row.name
  formData.status = row.status
  formData.type = row.type
  formData.event = row.event
  formData.valueType = row.valueType
  formData.value = row.value
  formData.remark = row.remark
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      await listenerApi.update(formData)
      ElMessage.success('更新成功')
    } else {
      await listenerApi.add(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch (e) {
    // error handled
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: ProcessListener) => {
  try {
    await ElMessageBox.confirm(`确定删除监听器「${row.name}」？`, '删除确认', { type: 'warning' })
    await listenerApi.delete([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    // user cancelled
  }
}
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
