<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="模板编号">
          <el-input v-model="queryParams.templateCode" placeholder="请输入模板编号" clearable @keyup.enter="handleQuery" style="width: 180px" />
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="queryParams.templateName" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery" style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
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

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" v-permission="'system:print-template:add'" @click="handleAdd">新增模板</el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="sysPrintTemplateTable"
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
        <vxe-column field="templateCode" title="模板编号" min-width="160" />
        <vxe-column field="templateName" title="模板名称" min-width="180" />
        <vxe-column field="version" title="版本号" width="90" align="center" />
        <vxe-column title="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </vxe-column>
        <vxe-column field="remark" title="备注" min-width="160" show-overflow="tooltip" />
        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>
        <vxe-column title="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleDesign(row)">设计</el-button>
            <el-button type="primary" link size="small" v-permission="'system:print-template:edit'" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click.stop="handlePrint(row)">测试打印</el-button>
            <el-button type="danger" link size="small" v-permission="'system:print-template:remove'" @click.stop="handleDelete(row)">删除</el-button>
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
        style="margin-top: 15px; justify-content: flex-end; display: flex"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <PrintTemplateForm ref="formRef" @success="getList" />

    <!-- 打印预览弹窗 -->
    <print-view ref="printViewRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { useRouter } from 'vue-router'
import { PrintTemplateApi, type PrintTemplateEntity } from '@/api/system/print-template'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import PrintTemplateForm from './PrintTemplateForm.vue'
import PrintView from '@/components/hiprint/print-view.vue'

defineOptions({ name: 'SysPrintTemplate' })

const router = useRouter()
const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<PrintTemplateEntity[]>([])
const total = ref(0)

const queryParams = reactive({
  templateCode: '',
  templateName: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})

const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

const formRef = ref()
const printViewRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const res = await PrintTemplateApi.list(queryParams)
    console.log(res)
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
  queryParams.templateCode = ''
  queryParams.templateName = ''
  queryParams.status = undefined
  handleQuery()
}

const handleAdd = () => {
  formRef.value.open('create')
}

const handleEdit = (row: PrintTemplateEntity) => {
  formRef.value.open('update', row.id)
}

const handleDesign = (row: PrintTemplateEntity) => {
  router.push({ path: '/system/print/design', query: { id: row.id } })
}

const handlePrint = (row: PrintTemplateEntity) => {
  printViewRef.value.prePrint(row.templateCode, {
    title: row.templateName,
    content: '示例数据'
  })
}

const handleDelete = async (row: PrintTemplateEntity) => {
  try {
    await ElMessageBox.confirm(`确定删除模板 "${row.templateName}"?`, '警告', { type: 'warning' })
    await PrintTemplateApi.delete(row.id)
    ElMessage.success('删除成功')
    getList()
  } catch (e) {}
}

onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
  getList()
})
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
}
</style>
