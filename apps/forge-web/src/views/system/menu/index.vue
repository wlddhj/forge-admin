<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="菜单名称">
          <el-input v-model="queryParams.menuName" placeholder="请输入菜单名称" clearable />
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

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">菜单列表</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="菜单名称">
          <el-input v-model="queryParams.menuName" placeholder="请输入菜单名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增菜单
          </el-button>
          <el-button @click="handleToggleExpand">
            <el-icon><component :is="allExpanded ? 'Fold' : 'Expand'" /></el-icon>
            {{ allExpanded ? '全部折叠' : '全部展开' }}
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysMenuTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :loading="loading"
        :height="tableHeight"
        :row-config="{ isCurrent: true, isHover: true, keyField: 'id' }"
        :tree-config="{ expandAll: !isMobile, indent: 20,transform: true, rowField: 'id', parentField: 'parentId'}"
        :column-config="{ resizable: true }"
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <vxe-column title="序号" type="seq" align="left" width="70"/>
        <!-- 菜单名称 -->
        <vxe-column field="menuName" title="菜单名称" width="200" tree-node />

        <!-- 图标（桌面端） -->
        <vxe-column v-if="!isMobile" field="icon" title="图标" width="80" align="center">
          <template #default="{ row }">
            <IconPreview v-if="row.icon" :icon="row.icon" :size="16" />
          </template>
        </vxe-column>

        <!-- 类型 -->
        <vxe-column field="menuType" title="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="getMenuTypeColor(row.menuType)" size="small">
              {{ getMenuTypeText(row.menuType) }}
            </el-tag>
          </template>
        </vxe-column>

        <!-- 路由路径（桌面端） -->
        <vxe-column v-if="!isMobile" field="routePath" title="路由路径" min-width="180" />

        <!-- 组件路径（桌面端） -->
        <vxe-column v-if="!isMobile" field="componentPath" title="组件路径" min-width="180" />

        <!-- 权限标识（桌面端） -->
        <vxe-column v-if="!isMobile" field="permission" title="权限标识" width="150" />

        <!-- 排序（桌面端） -->
        <vxe-column v-if="!isMobile" field="sortOrder" title="排序" width="80" />

        <!-- 状态 -->
        <vxe-column title="状态" width="80">
          <template #default="{ row }">
            <dict-value :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
          </template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click.stop="handleAddChild(row)">新增</el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.menuName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button size="small" @click.stop="handleAddChild(item)">新增子菜单</el-button>
        <el-button size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="dialog-form-responsive" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="formData.parentId"
            :data="menuTreeOptions"
            :props="{ label: 'menuName', value: 'id' }"
            placeholder="请选择上级菜单（不选则为顶级菜单）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="formData.menuType">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item v-if="formData.menuType !== 2" label="路由路径" prop="routePath">
          <el-input v-model="formData.routePath" placeholder="请输入路由路径，如：/system/user" />
        </el-form-item>
        <el-form-item v-if="formData.menuType === 1" label="组件路径" prop="componentPath">
          <el-input v-model="formData.componentPath" placeholder="请输入组件路径，如：/views/system/user/index" />
        </el-form-item>
        <el-form-item v-if="formData.menuType !== 2" label="图标">
          <IconPicker v-model="formData.icon" />
        </el-form-item>
        <el-form-item v-if="formData.menuType === 2" label="权限标识">
          <el-input v-model="formData.permission" placeholder="请输入权限标识，如：system:user:list" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" controls-position="right" style="width: 100%" />
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
        <el-form-item v-if="formData.menuType !== 2" label="显示状态">
          <el-radio-group v-model="formData.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
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
import { reactive, ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getMenuList, getMenuTree, addMenu, updateMenu, deleteMenu } from '@/api/system'
import type { Menu, MenuTree, MenuRequest } from '@/types/system'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import DictValue from '@/components/DictValue.vue'
import IconPicker from '@/components/IconPicker.vue'
import IconPreview from '@/components/IconPreview.vue'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

// 表格高度自适应（树形表格无分页）
const { tableHeight } = useTableHeight({ hasPagination: false })

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<MenuTree[]>([])
const allExpanded = ref(true)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<MenuTree | null>(null)

const queryParams = reactive({
  menuName: '',
  status: undefined as number | undefined
})

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.menuName) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)

const formRef = ref<FormInstance>()
const formData = reactive<MenuRequest>({
  id: undefined,
  parentId: undefined,
  menuType: 1,
  menuName: '',
  routePath: '',
  componentPath: '',
  icon: '',
  permission: '',
  sortOrder: 0,
  status: 1,
  visible: 1
})

const formRules: FormRules = {
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }]
}

const menuTreeOptions = ref<MenuTree[]>([])

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getMenuList(queryParams)
    tableData.value = res//buildMenuTree(res)
    allExpanded.value = !isMobile.value
    nextTick(() => {
      if (!isMobile.value && tableRef.value) {
        tableRef.value.setAllTreeExpand(true)
      }
    })
  } finally {
    loading.value = false
  }
}

const getMenuTreeData = async () => {
  try {
    const res = await getMenuTree()
    menuTreeOptions.value = [{ id: 0, menuName: '顶级菜单', children: res } as any]
  } catch (error) {
    console.error('获取菜单树失败:', error)
  }
}

const buildMenuTree = (flatList: Menu[]): MenuTree[] => {
  const map = new Map<number, MenuTree>()
  const tree: MenuTree[] = []

  flatList.forEach(item => {
    map.set(item.id!, { ...item, children: [] })
  })

  flatList.forEach(item => {
    const node = map.get(item.id!)!
    if (!item.parentId || item.parentId === 0) {
      tree.push(node)
    } else {
      const parent = map.get(item.parentId)
      if (parent) {
        if (!parent.children) parent.children = []
        parent.children.push(node)
      } else {
        tree.push(node)
      }
    }
  })

  return tree
}

const handleToggleExpand = () => {
  allExpanded.value = !allExpanded.value
  if (tableRef.value) {
    if (allExpanded.value) {
      tableRef.value.setAllTreeExpand(true)
    } else {
      tableRef.value.clearTreeExpand()
    }
  }
}

const handleQuery = () => getList()
const handleReset = () => {
  queryParams.menuName = ''
  queryParams.status = undefined
  getList()
}

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增菜单'
  Object.assign(formData, {
    id: undefined, parentId: undefined, menuType: 1, menuName: '',
    routePath: '', componentPath: '', icon: '', permission: '', sortOrder: 0, status: 1, visible: 1
  })
  dialogVisible.value = true
}

const handleAddChild = (row: MenuTree) => {
  cancelSelection()
  isEdit.value = false
  dialogTitle.value = '新增子菜单'
  Object.assign(formData, {
    id: undefined, parentId: row.id, menuType: 1, menuName: '',
    routePath: '', componentPath: '', icon: '', permission: '', sortOrder: 0, status: 1, visible: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row: MenuTree) => {
  cancelSelection()
  isEdit.value = true
  dialogTitle.value = '编辑菜单'
  Object.assign(formData, {
    id: row.id, parentId: row.parentId, menuType: row.menuType, menuName: row.menuName,
    routePath: row.routePath, componentPath: row.componentPath,
    icon: row.icon, permission: row.permission, sortOrder: row.sortOrder, status: row.status, visible: row.visible
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateMenu(formData)
      ElMessage.success('更新成功')
    } else {
      await addMenu(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
    getMenuTreeData()
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => formRef.value?.resetFields()

const handleDelete = async (row: MenuTree) => {
  if (row.children && row.children.length > 0) {
    ElMessage.warning('该菜单存在子菜单，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除菜单 "${row.menuName}" 吗？`, '警告', { type: 'warning' })
    await deleteMenu(row.id!)
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
    getMenuTreeData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const getMenuTypeColor = (type: number) => {
  const map: Record<number, string> = { 0: 'primary', 1: 'success', 2: 'warning' }
  return map[type] || 'info'
}

const getMenuTypeText = (type: number) => {
  const map: Record<number, string> = { 0: '目录', 1: '菜单', 2: '按钮' }
  return map[type] || '未知'
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: MenuTree | null }) => {
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

onMounted(() => {
  getList()
  getMenuTreeData()
})
</script>