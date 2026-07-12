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
        ref="tableRef"
        id="sysTenantPackageTable"
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

    <!-- 新增/编辑抽屉 -->
    <el-drawer
      v-model="dialogVisible"
      :title="dialogTitle"
      size="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="套餐名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入套餐名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="套餐编码" prop="code">
          <el-input
            v-model="formData.code"
            placeholder="请输入套餐编码（如 standard）"
            maxlength="50"
            :disabled="!!formData.id"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio
              v-for="item in statusOptions"
              :key="item.dictValue"
              :value="Number(item.dictValue)"
            >
              {{ item.dictLabel }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
            maxlength="200"
          />
        </el-form-item>
        <el-form-item label="关联菜单">
          <div class="menu-tree-toolbar">
            <el-checkbox v-model="menuCheckAll" @change="handleMenuCheckAll">全选</el-checkbox>
            <el-button link type="primary" @click="handleMenuExpandToggle(!menuExpandAll)">
              {{ menuExpandAll ? '折叠全部' : '展开全部' }}
            </el-button>
          </div>
          <div class="menu-tree-container">
            <el-tree
              ref="treeRef"
              :data="menuTree"
              :props="{ label: 'menuName', children: 'children' }"
              node-key="id"
              show-checkbox
              :default-expanded-keys="defaultExpandedKeys"
              :default-checked-keys="defaultCheckedKeys"
              @check="handleMenuCheck"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import {
  getTenantPackageList,
  getTenantPackageDetail,
  addTenantPackage,
  updateTenantPackage,
  deleteTenantPackage,
  type TenantPackageEntity,
  type TenantPackageQuery,
  type TenantPackageRequest
} from '@/api/system/tenant-package'
import { getMenuTree } from '@/api/system'
import type { MenuTree } from '@/types/system'
import { useDict } from '@/composables/useDict'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { DICT_TYPE } from '@/constants/dict'
import DictValue from '@/components/DictValue.vue'
import TablePagination from '@/components/TablePagination.vue'
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

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新增套餐')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 菜单树
const menuTree = ref<MenuTree[]>([])
const treeRef = ref<any>(null)
const menuExpandAll = ref(false)
const menuCheckAll = ref(false)
// el-tree 仅在初始化时读 default-*-keys，切换值不响应；但仅作为初始展开/选中的载体，
// 实际切换由 ref 实例方法控制
const defaultExpandedKeys = ref<number[]>([])
const defaultCheckedKeys = ref<number[]>([])

// 表单数据
const formData = reactive<TenantPackageRequest>({
  id: undefined,
  name: '',
  code: '',
  status: 1,
  remark: '',
  menuIds: []
})

// 表单校验规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入套餐编码', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_-]{1,49}$/,
      message: '编码需以字母开头，仅允许字母、数字、下划线、短横线',
      trigger: 'blur'
    }
  ]
}

// 工具函数：递归获取所有菜单 ID
const getAllMenuIds = (tree: MenuTree[]): number[] => {
  const ids: number[] = []
  const walk = (nodes: MenuTree[]) => {
    nodes.forEach((n) => {
      ids.push(n.id)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(tree)
  return ids
}

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

// 加载菜单树
const loadMenuTree = async () => {
  if (menuTree.value.length > 0) return
  try {
    menuTree.value = await getMenuTree()
  } catch {
    menuTree.value = []
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

// 重置表单
const resetForm = () => {
  formData.id = undefined
  formData.name = ''
  formData.code = ''
  formData.status = 1
  formData.remark = ''
  formData.menuIds = []
  menuCheckAll.value = false
  formRef.value?.resetFields()
}

// 新增
const handleAdd = async () => {
  dialogTitle.value = '新增套餐'
  resetForm()
  await loadMenuTree()
  // 抽屉打开前预设默认展开/选中（destroy-on-close 后 tree 会重新渲染读这些 keys）
  defaultExpandedKeys.value = []
  defaultCheckedKeys.value = []
  menuExpandAll.value = false
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: TenantPackageEntity) => {
  dialogTitle.value = '编辑套餐'
  resetForm()
  await loadMenuTree()
  let detail: TenantPackageEntity
  try {
    detail = await getTenantPackageDetail(row.id)
  } catch {
    detail = row
  }
  const ids = detail.menuIds || []
  Object.assign(formData, {
    id: detail.id,
    name: detail.name,
    code: detail.code,
    status: detail.status,
    remark: detail.remark || '',
    menuIds: ids
  })
  // 默认展开包含选中节点的父链 + 选中节点本身；展开全部由用户点击切换
  defaultExpandedKeys.value = ids
  defaultCheckedKeys.value = ids
  menuExpandAll.value = false
  dialogVisible.value = true
  await nextTick()
  // setCheckedKeys 用于精确控制半选状态（父子联动）
  treeRef.value?.setCheckedKeys(ids, false)
  // 同步全选 checkbox 状态
  handleMenuCheck()
}

// 提交
const handleSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const checkedKeys = treeRef.value?.getCheckedKeys(false) || []
    const halfCheckedKeys = treeRef.value?.getHalfCheckedKeys() || []
    formData.menuIds = [...checkedKeys, ...halfCheckedKeys]

    if (formData.id) {
      await updateTenantPackage(formData)
      ElMessage.success('更新成功')
    } else {
      await addTenantPackage(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
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

// 菜单树相关操作
const handleMenuCheck = () => {
  const allIds = getAllMenuIds(menuTree.value)
  const checked = treeRef.value?.getCheckedKeys(false) || []
  menuCheckAll.value = checked.length === allIds.length && allIds.length > 0
}

const handleMenuCheckAll = (val: any) => {
  if (val) {
    treeRef.value?.setCheckedKeys(getAllMenuIds(menuTree.value))
  } else {
    treeRef.value?.setCheckedKeys([])
  }
}

const handleMenuExpandToggle = (expand: boolean) => {
  if (!treeRef.value) return
  menuExpandAll.value = expand
  // Element Plus Tree 没有公开 expandAll API，通过 Node.expanded 属性直接控制
  const walk = (nodes: MenuTree[]) => {
    nodes.forEach((n) => {
      const node = treeRef.value?.getNode(n.id)
      if (node) node.expanded = expand
      if (n.children?.length) walk(n.children)
    })
  }
  walk(menuTree.value)
}

// 初始化
onMounted(() => {
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
    .pagination-container {
      margin-top: 15px;
      display: flex;
      justify-content: flex-end;
    }
  }
}

.menu-tree-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.menu-tree-container {
  width: 100%;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px 12px;
}
</style>
