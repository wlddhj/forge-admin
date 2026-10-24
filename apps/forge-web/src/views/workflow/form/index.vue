<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="表单名称">
          <el-input v-model="queryParams.name" placeholder="请输入表单名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">表单管理</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button v-permission="'workflow:form:add'" type="primary" @click="handleDesign()">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="表单名称">
          <el-input v-model="queryParams.name" placeholder="请输入表单名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'workflow:form:add'" type="primary" @click="handleDesign()">
            <el-icon><Plus /></el-icon>
            设计表单
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        id="wfFormTable"
        ref="tableRef"
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
        @current-change="handleCurrentChange"
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" />

        <!-- 表单名称 -->
        <vxe-column field="name" title="表单名称" min-width="180" />

        <!-- 状态 -->
        <vxe-column title="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </vxe-column>

        <!-- 备注（桌面端） -->
        <vxe-column v-if="!isMobile" field="remark" title="备注" min-width="150" />

        <!-- 创建时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'workflow:form:edit'" type="primary" link size="small" @click.stop="handleDesign(row.id)">编辑</el-button>
            <el-button type="success" link size="small" @click.stop="handlePreview(row)">预览</el-button>
            <el-button v-permission="'workflow:form:delete'" type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
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

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.name"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button v-permission="'workflow:form:edit'" size="small" type="primary" @click.stop="handleDesign(item.id)">编辑</el-button>
        <el-button size="small" type="success" @click.stop="handlePreview(item)">预览</el-button>
        <el-button v-permission="'workflow:form:delete'" size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" title="表单预览" width="700px" destroy-on-close class="dialog-form-responsive">
      <div v-if="previewData" class="form-preview">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="表单名称">{{ previewData.name }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="previewData.status === 0 ? 'success' : 'danger'">
              {{ previewData.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ previewData.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(previewData.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(previewData.updateTime) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider>表单字段</el-divider>

        <form-create
          v-if="previewRule.length > 0"
          :rule="previewRule"
          :option="previewOption"
          :value="previewValue"
        />
        <el-empty v-else description="暂无表单字段配置" />
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import formCreate from '@form-create/element-ui'
import { formApi } from '@/api/workflow/form'
import type { WfForm } from '@/api/workflow/form'
import { formatDateTime } from '@/utils/dateFormat'
import { decodeFields } from '@/utils/formCreate'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const router = useRouter()
const { isMobile } = useResponsive()

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<WfForm[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<WfForm | null>(null)

const queryParams = reactive({
  name: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 20
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef }) as any

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.name) count++
  if (queryParams.status !== undefined) count++
  return count
})

// 预览相关
const previewVisible = ref(false)
const previewData = ref<WfForm | null>(null)
const previewRule = ref<any[]>([])
const previewOption = ref<any>({})
const previewValue = ref<Record<string, any>>({})

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

/** 获取表单列表 */
const getList = async () => {
  loading.value = true
  try {
    const res = await formApi.page(queryParams)
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
  queryParams.name = ''
  queryParams.status = undefined
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

/** 跳转表单设计器 */
const handleDesign = (id?: number) => {
  cancelSelection()
  if (id) {
    router.push(`/workflow/form/editor?id=${id}`)
  } else {
    router.push('/workflow/form/editor')
  }
}

/** 预览表单 */
const handlePreview = async (row: WfForm) => {
  cancelSelection()
  previewData.value = row
  previewRule.value = decodeFields(row.fields)
  if (row.conf) {
    try {
      previewOption.value = JSON.parse(row.conf)
    } catch {
      previewOption.value = {}
    }
  } else {
    previewOption.value = {}
  }
  previewValue.value = {}
  previewVisible.value = true
}

/** 删除表单 */
const handleDelete = async (row: WfForm) => {
  cancelSelection()
  try {
    await ElMessageBox.confirm(`确定删除表单 "${row.name}"？`, '警告', { type: 'warning' })
    await formApi.delete([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (e) {
    // 用户取消
  }
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: WfForm | null }) => {
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

onMounted(() => getList())
</script>

<style scoped lang="scss">


.form-preview {
  max-height: 60vh;
  overflow-y: auto;
}
</style>
