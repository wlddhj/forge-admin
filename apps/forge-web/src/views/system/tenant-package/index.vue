<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="套餐名称">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入套餐名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="套餐编码">
          <el-input
            v-model="queryParams.code"
            placeholder="请输入套餐编码"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'system:tenant-package:add'" type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增套餐
          </el-button>
        </template>
        <template #tools>
          <vxe-button
            circle
            icon="vxe-icon-refresh"
            style="margin-right: 10px"
            @click="getList"
          />
        </template>
      </vxe-toolbar>

      <vxe-table
        id="sysTenantPackageTable"
        ref="tableRef"
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
        show-header-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" />

        <vxe-column field="name" title="套餐名称" min-width="180" />

        <vxe-column field="code" title="套餐编码" width="160" />

        <vxe-column title="关联菜单数" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">
              {{ row.menuIds?.length || 0 }} 项
            </el-tag>
          </template>
        </vxe-column>

        <vxe-column title="状态" width="100" align="center">
          <template #default="{ row }">
            <dict-value :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
          </template>
        </vxe-column>

        <vxe-column field="remark" title="备注" min-width="160">
          <template #default="{ row }">
            {{ row.remark || '-' }}
          </template>
        </vxe-column>

        <vxe-column field="createTime" title="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </vxe-column>

        <vxe-column title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:tenant-package:update'"
              link
              type="primary"
              size="small"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'system:tenant-package:delete'"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <div class="pagination-container">
        <TablePagination
          v-model:page-num="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          @change="getList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑抽屉（v-if 挂载使组件每次打开重新初始化，@closed 动画结束后卸载） -->
    <PackageFormDrawer
      v-if="formDrawerAlive"
      v-model="formDrawerVisible"
      :row="editRow"
      @success="getList"
      @closed="formDrawerAlive = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import {
  getTenantPackageList,
  deleteTenantPackage,
  type TenantPackageEntity,
  type TenantPackageQuery
} from '@/api/system/tenant-package'
import { useDict } from '@/composables/useDict'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { DICT_TYPE } from '@/constants/dict'
import DictValue from '@/components/DictValue.vue'
import TablePagination from '@/components/TablePagination.vue'
import PackageFormDrawer from './components/PackageFormDrawer.vue'
import { formatDateTime } from '@/utils/dateFormat'

defineOptions({
  name: 'SystemTenantPackage'
})

const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

// 查询参数
const queryParams = reactive<TenantPackageQuery>({
  pageNum: 1,
  pageSize: 20,
  name: '',
  code: '',
  status: undefined
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 表格数据
const tableData = ref<TenantPackageEntity[]>([])
const loading = ref(false)
const total = ref(0)

// 新增/编辑抽屉
const formDrawerAlive = ref(false)
const formDrawerVisible = ref(false)
const editRow = ref<TenantPackageEntity | null>(null)

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

// 查询列表
const getList = async () => {
  loading.value = true
  try {
    const data = await getTenantPackageList(queryParams)
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.name = ''
  queryParams.code = ''
  queryParams.status = undefined
  handleQuery()
}

// 新增
const handleAdd = () => {
  editRow.value = null
  formDrawerAlive.value = true
  formDrawerVisible.value = true
}

// 编辑
const handleEdit = (row: TenantPackageEntity) => {
  editRow.value = row
  formDrawerAlive.value = true
  formDrawerVisible.value = true
}

// 删除
const handleDelete = (row: TenantPackageEntity) => {
  ElMessageBox.confirm(
    `确定要删除套餐「${row.name}」吗？已分配该套餐的租户将失去对应菜单权限。`,
    '提示',
    { type: 'warning' }
  ).then(async () => {
    await deleteTenantPackage([row.id])
    ElMessage.success('删除成功')
    getList()
  })
}

// 初始化
onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.pagination-container {
  margin-top: 15px;
  display: flex;
  justify-content: flex-end;
}
</style>
