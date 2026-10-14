# 前端开发规范

> 适用范围：`apps/forge-web`（管理端，Vue 3.4 + TypeScript + Element Plus + vxe-table）。小程序（`apps/forge-miniapp`）与大屏编辑器（`apps/forge-screen`）另行约定。
>
> 配套文档：后端接口规范见 [后端开发规范](./backend-development-specification.md)；新模块端到端流程见 [新模块开发向导](./module-development.md)。
>
> v2.0 变化：在 v1.0「页面组件组织与弹窗/抽屉拆分」专题基础上，扩充为覆盖页面开发全流程的完整规范（文件结构 / API / 页面模板 / vxe-table / 样式 / 字典 / 检查清单）。
>
> v2.1 变化：新增第 9 章「日期/时间格式化」（统一工具函数、表格列渲染、详情展示、时间范围查询）。

---

## 1. 核心原则

- **一个文件一个职责**：`index.vue` 只负责"搜索 + 列表 + 编排"，弹窗/抽屉的内部实现一律独立成文件
- **复杂弹窗禁止内联**：表单弹窗、详情抽屉、导入/分配等业务弹窗必须抽为独立 Vue 文件
- **封装边界清晰**：弹窗组件自包含（表单数据、校验、加载、提交全在弹窗内），父组件只控制开关与传参
- **表格组件统一**：列表一律使用 vxe-table（不是 el-table），配 `useTableHeight` / `useTableSeq` / `vxe-toolbar`
- **分页组件统一**：使用 `<TablePagination>`（不是 el-pagination），默认 `pageSize: 20`
- **枚举走数据字典**：下拉选项、状态标签全部通过 `useDict` + `DICT_TYPE` + `<dict-value>` 渲染，禁止硬编码

---

## 2. 文件结构

### 2.1 全局结构

```
src/
├── api/{module}/           API 接口文件
│   └── {entity}.ts         接口定义 + 类型 + 请求函数
├── views/{module}/         页面组件
│   └── {entity}/           页面目录
│       ├── index.vue       页面入口：搜索 + 列表 + 弹窗编排
│       └── components/     页面私有组件（弹窗/抽屉）
│           ├── XxxFormDialog.vue    新增/编辑表单弹窗
│           └── XxxDetailDrawer.vue  详情抽屉
├── constants/dict.ts       字典类型常量
├── components/             公共组件
│   ├── DictValue.vue       字典标签渲染
│   ├── MobileSearchDrawer.vue  移动端搜索抽屉
│   ├── MobileSearchButton.vue  移动端搜索按钮
│   └── MobileBottomActions.vue 移动端底部操作栏
└── composables/
    ├── useDict.ts          字典数据 composable
    ├── useResponsive.ts    响应式布局 composable
    ├── useTableHeight.ts   表格高度自适应
    ├── useTableSeq.ts      序号计算
    └── useTableSort.ts     排序处理
```

### 2.2 页面目录结构

```
src/views/{module}/{entity}/
├── index.vue                      # 页面入口：搜索栏 + 表格 + 弹窗编排
└── components/                    # 页面私有组件
    ├── XxxFormDialog.vue          # 新增/编辑表单弹窗
    ├── XxxDetailDrawer.vue        # 详情抽屉
    ├── XxxImportDialog.vue        # 导入弹窗（如有）
    └── XxxAssignYyyDialog.vue     # 分配/关联等业务弹窗（如有）
```

### 2.3 index.vue 职责边界

| 职责 | 归属 |
|------|------|
| 搜索表单、查询参数、重置 | `index.vue` |
| 表格数据加载（getList）、分页、多选 | `index.vue` |
| 移动端适配（isMobile、搜索抽屉、底部操作栏） | `index.vue` |
| 弹窗的引用与打开（handleAdd/handleEdit 只设开关与传参） | `index.vue` |
| 弹窗内部表单、校验规则、详情加载、提交/重置 | **弹窗组件** |
| 提交成功后的列表刷新 | `index.vue`（监听 `@success` 回调） |

`index.vue` 原则上控制在 **~400 行**以内；超出时优先检查是否有应抽离的弹窗或区块。

### 2.4 是否抽离弹窗的判定标准

| 场景 | 要求 | 示例 |
|------|------|------|
| 表单新增/编辑弹窗（含校验） | **必须抽离** | `XxxFormDialog.vue` |
| 详情弹窗/抽屉（多字段展示） | **必须抽离** | `XxxDetailDrawer.vue` |
| 导入、分配角色、批量操作等业务弹窗 | **必须抽离** | `UserImportDialog.vue` |
| 纯删除/风险确认 | 内联 `ElMessageBox.confirm`，不抽离 | — |
| 仅单个输入框、无校验逻辑的极简弹窗 | 可内联；超过一个输入框即抽离 | — |

### 2.5 项目内先例

新代码对齐以下现有写法：

| 先例 | 说明 |
|------|------|
| `views/workflow/process/components/FlowDiagramDialog.vue` | 流程图弹窗 |
| `views/workflow/task/components/TaskDetailDrawer.vue` | 任务详情抽屉 |
| `views/system/user/UserImportDialog.vue` | 用户导入弹窗 |
| `views/system/dict-type/components/DictDataList.vue` | 字典数据弹窗（复杂列表） |

---

## 3. API 文件规范

```typescript
// src/api/{module}/{entity}.ts
import request from '@/utils/request'
import type { PageResult } from '@/utils/request'

// 查询参数接口
export interface XxxQuery {
  pageNum: number
  pageSize: number
  name?: string
  status?: number
}

// 实体接口
export interface XxxEntity {
  id: number
  name: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}

// API 函数
export const xxxApi = {
  page: (params: XxxQuery) => request.get<PageResult<XxxEntity>>('/{module}/{entity}/list', { params }),
  get: (id: number) => request.get<XxxEntity>(`/{module}/{entity}/${id}`),
  add: (data: Partial<XxxEntity>) => request.post('/{module}/{entity}', data),
  update: (data: Partial<XxxEntity>) => request.put('/{module}/{entity}', data),
  delete: (ids: number[]) => request.delete('/{module}/{entity}', { data: ids })
}
```

**要点：**
- 查询用 `GET + params`，提交用 `POST/PUT + data`
- 删除用 `DELETE + body`（`{ data: ids }`）
- 所有接口字段可选（`?`）
- 接口返回值自动被 `request.ts` 拦截器解析为 `Result<T>`

---

## 4. 页面组件规范

### 4.1 Template 标准模板

```vue
<template>
  <div class="app-container">
    <!-- 1. 搜索栏（桌面端表单 + 移动端按钮） -->
    <el-card shadow="never" class="search-card">
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable style="width: 120px">
            <el-option v-for="item in statusOptions" :key="item.dictValue"
              :label="item.dictLabel" :value="item.dictValue" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <div v-else class="mobile-search-actions">
        <span class="title">{页面标题}</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount"
            @click="searchDrawerVisible = true" />
        </div>
      </div>
    </el-card>

    <!-- 2. 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams"
      @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="名称" style="width: 100%">
          <el-input v-model="queryParams.name" placeholder="请输入名称" clearable />
        </el-form-item>
        <el-form-item label="状态" style="width: 100%">
          <el-select v-model="queryParams.status" clearable style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.dictValue"
              :label="item.dictLabel" :value="item.dictValue" />
          </el-select>
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 3. 数据表格 -->
    <el-card shadow="never" class="table-card">
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button v-permission="'{module}:{entity}:add'" type="primary" @click="handleAdd">新增</el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-refresh" style="margin-right: 10px" @click="handleQuery" />
        </template>
      </vxe-toolbar>

      <vxe-table
        ref="tableRef"
        id="{entity}Table"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />
        <vxe-column field="name" title="名称" min-width="150" />
        <vxe-column field="status" title="状态" width="100">
          <template #default="{ row }">
            <dict-value :dict-type="DICT_TYPE.SYS_COMMON_STATUS" :value="row.status" />
          </template>
        </vxe-column>
        <vxe-column v-if="!isMobile" title="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'{module}:{entity}:query'" type="primary" link
              @click.stop="handleView(row)">详情</el-button>
            <el-button v-permission="'{module}:{entity}:edit'" type="warning" link
              @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'{module}:{entity}:delete'" type="danger" link
              @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </vxe-column>
      </vxe-table>

      <TablePagination v-model:page-num="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize" :total="total"
        @change="getList" />
    </el-card>

    <!-- 4. 移动端底部操作栏 -->
    <MobileBottomActions :show="!!selectedRow" :item="selectedRow"
      :item-title="selectedRow?.name" @cancel="cancelSelection">
      <template #actions="{ item }">
        <el-button v-permission="'{module}:{entity}:query'" size="small"
          @click.stop="handleView(item)">详情</el-button>
        <el-button v-permission="'{module}:{entity}:edit'" size="small" type="warning"
          @click.stop="handleEdit(item)">编辑</el-button>
        <el-button v-permission="'{module}:{entity}:delete'" size="small" type="danger"
          @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 5. 新增/编辑表单弹窗（独立组件，见第 6 章） -->
    <XxxFormDialog v-model="dialogVisible" :row="currentRow" @success="getList" />

    <!-- 6. 详情抽屉（独立组件，见第 6 章） -->
    <XxxDetailDrawer v-model="detailVisible" :row="detailRow" />
  </div>
</template>
```

### 4.2 Script 标准模板

```vue
<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { xxxApi } from '@/api/{module}/{entity}'
import type { XxxEntity, XxxQuery } from '@/api/{module}/{entity}'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import DictValue from '@/components/DictValue.vue'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import XxxFormDialog from './components/XxxFormDialog.vue'
import XxxDetailDrawer from './components/XxxDetailDrawer.vue'

const { isMobile } = useResponsive()
const { tableHeight } = useTableHeight()

// 字典数据（用于下拉选项，见第 8 章）
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_COMMON_STATUS)

// 表格引用
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 查询参数
const queryParams = reactive<XxxQuery>({
  pageNum: 1, pageSize: 20,
  name: '', status: undefined
})

// 表格数据
const loading = ref(false)
const tableData = ref<XxxEntity[]>([])
const total = ref(0)

// 移动端
const searchDrawerVisible = ref(false)
const selectedRow = ref<XxxEntity | null>(null)
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.name) count++
  if (queryParams.status !== undefined) count++
  return count
})

// 弹窗编排：只控制开关与传参，表单/详情逻辑封装在弹窗组件内（见第 6 章）
const dialogVisible = ref(false)
const currentRow = ref<XxxEntity | null>(null)
const detailVisible = ref(false)
const detailRow = ref<XxxEntity | null>(null)

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await xxxApi.page(queryParams)
    tableData.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.name = ''; queryParams.status = undefined; handleQuery() }

// 移动端搜索
const handleSearchFromDrawer = () => { handleQuery(); searchDrawerVisible.value = false }
const handleResetFromDrawer = () => { handleReset(); searchDrawerVisible.value = false }

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: XxxEntity | null }) => {
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

// 新增
const handleAdd = () => {
  currentRow.value = null
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: XxxEntity) => {
  currentRow.value = row
  dialogVisible.value = true
}

// 详情
const handleView = (row: XxxEntity) => {
  detailRow.value = row
  detailVisible.value = true
}

// 删除
const handleDelete = async (row: XxxEntity) => {
  try {
    await ElMessageBox.confirm('确认删除吗？', '提示', { type: 'warning' })
    await xxxApi.delete([row.id])
    ElMessage.success('删除成功')
    getList()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

// 初始化
onMounted(() => {
  getList()
  // 关联工具栏与表格
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})
</script>
```

---

## 5. vxe-table 规范

### 5.1 属性对照

**表格属性：**

| 属性 | 说明 |
|------|------|
| `id="{entity}Table"` | 唯一标识（必填，用于列配置持久化） |
| `:custom-config="{mode: 'modal'}"` | 自定义列弹窗模式 |
| `:height="tableHeight"` | 自适应高度（使用 useTableHeight） |
| `:loading="loading"` | 加载状态 |
| `:row-config="{ isCurrent: true, isHover: true }"` | 行配置 |
| `:column-config="{ resizable: true }"` | 列宽可调整 |
| `border="none"` | 边框样式（全局配置已设置） |
| `stripe` | 斑马纹 |
| `show-overflow="tooltip"` | 内容溢出提示 |
| `show-header-overflow="tooltip"` | 表头溢出提示 |
| `@current-change="handleCurrentChange"` | 行选中变化（移动端） |

**列属性：**

| 属性 | 说明 |
|------|------|
| `field` | 字段名 |
| `title` | 列标题 |
| `type="seq"` | 序号列（配合 :seq-method） |
| `width` | 固定宽度 |
| `min-width` | 最小宽度 |
| `fixed="right"` | 固定列 |
| `align="center"` / `align="right"` | 对齐方式 |

**工具栏属性：**

| 属性 | 说明 |
|------|------|
| `custom` | 启用自定义列功能 |
| `#buttons` | 左侧按钮插槽 |
| `#tools` | 右侧工具插槽 |

### 5.2 树形表格配置

**重要：后端接口数据规范**

树形表格接口必须返回**平铺的完整 List 数据**，由前端自动渲染树形结构：

| 做法 | 说明 |
|------|------|
| ❌ 后端组装 tree | 不要在后端组装 `children` 嵌套结构 |
| ✅ 后端返回平铺 List | 返回所有记录（含 `id`、`parentId` 字段） |
| ✅ 前端自动渲染树形 | vxe-table 根据 `rowField` + `parentField` 自动构建 |

**正确示例：**

```json
// 后端返回（平铺 List）
[
  {"id": 1, "parentId": 0, "dirName": "01"},
  {"id": 2, "parentId": 0, "dirName": "02"},
  {"id": 3, "parentId": 1, "dirName": "01-1"}
]

// 前端 tree-config 自动渲染为：
// 01
//   └─ 01-1
// 02
```

**后端 Service 实现：**

```java
// 正确做法：直接返回平铺列表
public List<XxxResponse> getTreeList() {
    return lambdaQuery()
            .orderByAsc(XxxEntity::getSortOrder)
            .list()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
}

// 错误做法：不要组装 children 结构
// ❌ response.setChildren(childrenList);
```

**前端配置：**

```vue
<vxe-table
  :tree-config="{
    expandAll: !isMobile,
    indent: 20,
    transform: true,      <!-- 关键：启用 transform 模式 -->
    rowField: 'id',       <!-- 行唯一标识字段 -->
    parentField: 'parentId'  <!-- 父节点字段 -->
  }"
  :row-config="{ keyField: 'id', isCurrent: true, isHover: true }"
  ...
>
```

**展开/折叠控制：**

```typescript
const isAllExpand = ref(true)

const toggleExpandAll = () => {
  if (tableRef.value) {
    if (isAllExpand.value) {
      tableRef.value.clearTreeExpand()
    } else {
      tableRef.value.setAllTreeExpand(true)
    }
    isAllExpand.value = !isAllExpand.value
  }
}

// 高度配置（树形表格无分页）
const { tableHeight } = useTableHeight({ hasPagination: false })
```

### 5.3 多选功能（批量操作）

```vue
<vxe-table
  :checkbox-config="{ reserve: true }"
  @checkbox-change="handleCheckboxChange"
  @checkbox-all="handleCheckboxChange"
  @checkbox-range-end="handleCheckboxChange"
>
  <vxe-column type="checkbox" width="50" />
  ...
</vxe-table>
```

**checkbox-config 配置：**

| 属性 | 说明 |
|------|------|
| `reserve: true` | 跨页保留选中状态 |
| `checkField: 'checked'` | 指定选中状态的字段名 |
| `highlight: true` | 选中行高亮显示 |

**处理批量选择：**

```typescript
const selectedIds = ref<number[]>([])

const handleCheckboxChange = () => {
  if (tableRef.value) {
    const selectRecords = tableRef.value.getCheckboxRecords()
    selectedIds.value = selectRecords.map((item: any) => item.id)
  }
}

const cancelSelection = () => {
  selectedRow.value = null
  selectedIds.value = []
  if (tableRef.value) {
    tableRef.value.clearCheckboxRow()
    tableRef.value.clearCurrentRow()
  }
}
```

---

## 6. 弹窗/抽屉组件规范

### 6.1 命名

- 弹窗：`XxxDialog.vue`；抽屉：`XxxDrawer.vue`；PascalCase
- 业务语义命名：表单类 `XxxFormDialog`、详情类 `XxxDetailDrawer` / `XxxDetailDialog`、导入类 `XxxImportDialog`、分配类 `XxxAssignYyyDialog`
- **新文件统一放在页面目录的 `components/` 子目录下**；历史文件放在模块根目录的（如 `UserImportDialog.vue`）可保持不动，不做强制回改

### 6.2 父子通信协议

| 通道 | 约定 |
|------|------|
| 显隐控制 | `v-model`：子组件 `modelValue` prop + `update:modelValue` emit + computed `visible` |
| 初始数据 | props 传入（如编辑时传 `row`），数据回显/详情加载由弹窗内部完成 |
| 结果通知 | `emit('success')`；父组件在回调中刷新列表。成功提示、关闭弹窗由弹窗自行处理 |
| 关闭/取消 | 弹窗内部处理，无需通知父组件 |

### 6.3 子组件标准模板（表单弹窗）

```vue
<!-- views/{module}/{entity}/components/XxxFormDialog.vue -->
<template>
  <el-dialog v-model="visible" :title="title" width="600px" @close="handleClose">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入名称" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="formData.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { xxxApi } from '@/api/{module}/{entity}'
import type { XxxEntity } from '@/api/{module}/{entity}'

const props = defineProps<{
  modelValue: boolean
  /** 编辑时传入行数据；新增时传 null */
  row?: XxxEntity | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const title = computed(() => (props.row?.id ? '编辑' : '新增'))

const formRef = ref<FormInstance>()
const submitting = ref(false)
const formData = ref<Partial<XxxEntity>>({})
const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// 每次打开时初始化：新增重置表单，编辑回显数据
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      formData.value = props.row?.id ? { ...props.row } : { status: 1 }
      nextTick(() => formRef.value?.clearValidate())
    }
  }
)

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    submitting.value = true
    if (formData.value.id) {
      await xxxApi.update(formData.value)
      ElMessage.success('更新成功')
    } else {
      await xxxApi.add(formData.value)
      ElMessage.success('新增成功')
    }
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  formData.value = {}
}
</script>
```

### 6.4 父组件标准用法（index.vue）

模板中只保留一行引用：

```vue
<XxxFormDialog v-model="dialogVisible" :row="currentRow" @success="getList" />
<XxxDetailDrawer v-model="detailVisible" :row="detailRow" />
```

编排逻辑只剩开关与传参：

```typescript
import XxxFormDialog from './components/XxxFormDialog.vue'
import XxxDetailDrawer from './components/XxxDetailDrawer.vue'

const dialogVisible = ref(false)
const currentRow = ref<XxxEntity | null>(null)
const detailVisible = ref(false)
const detailRow = ref<XxxEntity | null>(null)

const handleAdd = () => {
  currentRow.value = null
  dialogVisible.value = true
}

const handleEdit = (row: XxxEntity) => {
  currentRow.value = row
  dialogVisible.value = true
}

const handleView = (row: XxxEntity) => {
  detailRow.value = row
  detailVisible.value = true
}
```

### 6.5 封装要点

- **打开时初始化**：`watch(() => props.modelValue)`，在打开瞬间重置表单或回显数据，`nextTick` 后 `clearValidate()` 清除校验残留
- **编辑回显**：优先直接使用传入的 `row`；若列表字段不全或需要最新详情，由弹窗内部调用 `xxxApi.get(row.id)` 加载，**不要**由父组件取好详情再传入
- **校验**：`FormRules` 定义在弹窗组件内，提交前 `formRef.value.validate()`
- **提交**：弹窗内部调用 API，成功后 `ElMessage.success` → 关闭 → `emit('success')`；提交按钮加 `:loading` 防重复提交
- **禁止外漏**：`formData`、`formRules`、`handleSubmit`、`formRef` 不得出现在 `index.vue` 中
- **详情类弹窗/抽屉**：同样接收 `row`，内部按 `row.id` 加载详情并渲染 `el-descriptions`

---

## 7. 样式规范

页面样式**不需要**重复定义基础布局，只需定义特有的业务样式：

```scss
<style scoped lang="scss">
// 只定义页面特有的样式
.weight-warning {
  color: #F56C6C;
  font-weight: bold;
}

.mobile-search-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  .title { font-size: 18px; font-weight: bold; }
  .actions { display: flex; gap: 8px; }
}
</style>
```

**禁止使用：**
- ❌ `<el-card><template #header>` 包裹标题
- ❌ 自定义容器类名（如 `xxx-container`）
- ❌ 在页面中重复定义 `.app-container` / `.search-card` / `.table-card` 样式

**禁止重复定义布局类的原因：**
这些类的布局（间隔、分页对齐、移动端适配）统一定义在全局 `styles/table-page.scss`，页面重复定义会引发实际问题：
- 在 `.table-card` 上设置 `padding` 会与 `.el-card__body` 自带的 20px padding **双层叠加**，使表格区实际高度超出 `useTableHeight` 的计算预留值，页面出现**竖向滚动条**
- 覆盖 `.search-card` 的 `margin-bottom`（全局为 5px）会导致页面间隔与其他列表页不一致
- 页面 scoped 样式只需保留该页特有的业务样式（如内容格式化、代码块等）

---

## 8. 数据字典使用指南

数据字典用于管理枚举类型数据，实现下拉选项、状态标签等的统一管理和复用。

### 8.1 核心组件

| 组件/文件 | 说明 |
|-----------|------|
| `constants/dict.ts` | 字典类型常量定义 |
| `composables/useDict.ts` | 字典数据加载 Composable |
| `components/DictValue.vue` | 字典标签渲染组件 |

### 8.2 前端使用流程

**步骤 1：定义字典常量**

在 `constants/dict.ts` 中添加：

```typescript
/** {描述} */
{MODULE}_{ENTITY}_{ATTRIBUTE}: '{module}_{entity}_{attribute}',
```

命名规范：`UPPER_SNAKE_CASE`，格式为 `模块_实体_属性`。

**步骤 2：导入并使用字典**

```typescript
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import DictValue from '@/components/DictValue.vue'

// 获取字典数据（用于下拉选项）
const { dictData: xxxOptions } = useDict(DICT_TYPE.XXX)

// 获取字典标签（用于单独转换）
const { getDictLabel, getTagType } = useDict(DICT_TYPE.XXX)
```

**步骤 3：下拉选项绑定**

```vue
<el-select v-model="queryParams.status" clearable>
  <el-option v-for="item in statusOptions" :key="item.dictValue"
    :label="item.dictLabel" :value="item.dictValue" />
</el-select>
```

**步骤 4：表格列字典渲染**

```vue
<vxe-column field="status" title="状态" width="100">
  <template #default="{ row }">
    <dict-value :dict-type="DICT_TYPE.XXX" :value="row.status" />
  </template>
</vxe-column>
```

### 8.3 useDict Composable API

```typescript
const {
  dictData,       // DictData[] - 字典数据数组
  loading,        // boolean - 加载状态
  loadDictData,   // () => Promise<void> - 手动刷新
  getDictLabel,   // (value: string | number) => string - 获取标签文本
  getTagType,     // (value: string | number) => string - 获取标签类型
  getCssClass     // (value: string | number) => string - 获取自定义样式
} = useDict(dictType)
```

### 8.4 后端 SQL 模板

字典数据的表结构与命名规范见 [数据库设计规范](./database-design-specification.md) 第 11 章。

```sql
-- 字典类型
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `is_system`, `create_time`, `update_time`, `deleted`) VALUES
('{字典名称}', '{module}_{entity}_{attr}', 1, 0, NOW(), NOW(), 0);

-- 字典数据（listClass 用于指定标签样式）
INSERT INTO `sys_dict_data` (`dict_type`, `dict_label`, `dict_value`, `dict_sort`, `list_class`, `status`, `create_time`, `update_time`, `deleted`) VALUES
('{dict_type}', '启用', '1', 1, 'success', 1, NOW(), NOW(), 0),
('{dict_type}', '禁用', '0', 2, 'danger', 1, NOW(), NOW(), 0);
```

**list_class 常用值：**
- `success`（绿色）→ 正常/启用/成功状态
- `danger`（红色）→ 异常/禁用/失败状态
- `warning`（橙色）→ 警告/待处理状态
- `primary`（蓝色）→ 默认/重要状态
- `info`（灰色）→ 次要状态

---

## 9. 日期/时间格式化

### 9.1 统一工具函数

日期/时间显示**一律使用** `utils/dateFormat.ts` 的工具函数，禁止引入 dayjs/moment 等日期库，禁止手写 `toLocaleString` 或字符串拼接：

```typescript
import { formatDate, formatDateTime } from '@/utils/dateFormat'

formatDate(1718150400)        // "2024-06-12"（日期显示）
formatDateTime(1718150400)    // "2024-06-12 10:00:00"（日期时间显示）
formatDateTime(null)          // "-"（空值统一兜底）
```

**函数说明：**

| 函数 | 输出格式 | 适用场景 |
|------|---------|---------|
| `formatDate` | `YYYY-MM-DD` | 纯日期字段（生日、有效期等） |
| `formatDateTime` | `YYYY-MM-DD HH:mm:ss` | 时间字段（创建时间、操作时间、审批时间等） |

**要点：**
- 入参兼容秒级/毫秒级时间戳、字符串、`Date` 对象，内部自动判断
- 空值（`null`/`undefined`/空串/非法值）统一返回 `'-'`，模板中无需再判空

### 9.2 表格列渲染

时间列通过 `#default` 插槽调用 `formatDateTime`，列宽建议 160-180：

```vue
<vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="180">
  <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
</vxe-column>
```

### 9.3 详情展示

详情抽屉/弹窗的 `el-descriptions-item` 中直接插值：

```vue
<el-descriptions-item label="发起时间">
  {{ formatDateTime(instanceInfo?.startTime) }}
</el-descriptions-item>
```

### 9.4 搜索日期范围

时间范围查询使用独立的 `dateRange` ref（**不放入 queryParams**），通过 `el-date-picker type="daterange"` 选择，watch 同步到查询参数：

```vue
<el-form-item label="操作时间">
  <el-date-picker
    v-model="dateRange"
    type="daterange"
    range-separator="至"
    start-placeholder="开始日期"
    end-placeholder="结束日期"
    value-format="YYYY-MM-DD HH:mm:ss"
    :default-time="[new Date(0, 0, 0, 0, 0, 0), new Date(0, 0, 0, 23, 59, 59)]"
    style="width: 240px"
  />
</el-form-item>
```

```typescript
const dateRange = ref<[string, string] | null>(null)

// 监听日期范围变化，同步到查询参数
watch(dateRange, (val) => {
  if (val && val.length === 2) {
    queryParams.startTime = val[0]
    queryParams.endTime = val[1]
  } else {
    queryParams.startTime = ''
    queryParams.endTime = ''
  }
})
```

**要点：**
- `value-format="YYYY-MM-DD HH:mm:ss"`（token 为大写 `YYYY`/`HH`，dayjs 风格），提交给后端即为格式化字符串
- `:default-time` 自动补齐边界时刻：起始 00:00:00、结束 23:59:59，保证范围查询闭合
- 重置按钮中显式清空：`dateRange.value = null`
- 移动端搜索抽屉中同样配置，`style="width: 100%"`

---

## 10. 关键约定速查

| 项目 | 规范 |
|------|------|
| 前端权限 | `v-permission="'module:entity:action'"`（数组为任一匹配）或脚本 `hasPermission()` |
| 弹窗/抽屉 | 复杂弹窗抽为独立组件放页面 `components/` 目录，`v-model` 控显隐 + `@success` 刷新，禁止内联 index.vue（见第 6 章） |
| 下拉选项 | 全部用数据字典，通过 `useDict` + `DICT_TYPE` 获取 |
| 列表枚举显示 | 用 `<dict-value :dict-type="DICT_TYPE.XXX" :value="row.xxx" />` |
| API 请求 | `import request from '@/utils/request'`，GET 用 params，POST/PUT 用 data |
| 移动端适配 | `useResponsive()` → `isMobile`，搜索抽屉 + 底部操作栏 |
| 表格组件 | 使用 vxe-table（不是 el-table） |
| 分页组件 | `<TablePagination v-model:page-num v-model:page-size :total @change />`（不是 el-pagination），默认 `pageSize: 20` |
| 表格高度 | `useTableHeight()` / `useTableHeight({ hasPagination: false })` |
| 序号列 | `<vxe-column type="seq" :seq-method="seqMethod" />` + `useTableSeq` |
| 工具栏 | `<vxe-toolbar custom>` + `tableRef.value.connect(toolbarRef.value)` |
| 刷新按钮 | `<vxe-button circle icon="vxe-icon-refresh" style="margin-right: 10px" />` |
| 行选中 | `@current-change="handleCurrentChange"` + `tableRef.value.clearCurrentRow()` |
| 日期/时间显示 | `formatDate` / `formatDateTime`（`@/utils/dateFormat`），空值返回 `'-'`；禁止 dayjs/moment 与手写拼接（见第 9 章） |
| 时间范围查询 | 独立 `dateRange` + `el-date-picker type="daterange"` + `value-format="YYYY-MM-DD HH:mm:ss"` + `default-time` 边界补齐 |

---

## 11. 检查清单

新页面提交前自查：

### 11.1 基础项

- [ ] API 文件位于 `src/api/{module}/`，查询/实体类型与请求函数齐全（第 3 章）
- [ ] 页面位于 `src/views/{module}/{entity}/`，结构符合 2.2 节
- [ ] `index.vue` 行数控制在 ~400 行以内
- [ ] 纯确认操作使用 `ElMessageBox.confirm`，未抽成组件
- [ ] 组件命名 PascalCase，`XxxDialog` / `XxxDrawer` 后缀正确
- [ ] 页面只定义特有业务样式，未重复定义 `.app-container` / `.search-card` / `.table-card`
- [ ] 日期/时间显示统一用 `formatDate` / `formatDateTime`，无 dayjs/moment 引入与手写格式化（第 9 章）

### 11.2 vxe-table 必查项

- [ ] 添加 `id="xxxTable"` 唯一标识
- [ ] 添加 `:custom-config="{mode: 'modal'}"`
- [ ] 添加 `:row-config="{ isCurrent: true, isHover: true }"`
- [ ] 添加 `:column-config="{ resizable: true }"`
- [ ] 添加 `show-header-overflow="tooltip"`
- [ ] 添加序号列 `<vxe-column type="seq" ... :seq-method="seqMethod" />`
- [ ] 添加工具栏 `<vxe-toolbar>` 并关联表格
- [ ] 导入 `VxeTableInstance` / `VxeToolbarInstance` 类型
- [ ] 导入并使用 `useTableHeight` / `useTableSeq`
- [ ] 添加 `@current-change` 处理移动端选中

### 11.3 树形表格附加项

- [ ] 添加 `:tree-config="{ transform: true, ... }"`
- [ ] 添加 `:row-config="{ keyField: 'id', ... }"`
- [ ] 配置 `useTableHeight({ hasPagination: false })`
- [ ] 添加展开/折叠按钮和逻辑

### 11.4 多选表格附加项

- [ ] 添加 `<vxe-column type="checkbox" />`
- [ ] 添加 `:checkbox-config="{ reserve: true }"`
- [ ] 处理 `@checkbox-change` 事件
- [ ] 处理 `@checkbox-all` 事件
- [ ] 处理 `@checkbox-range-end` 事件（拖选范围选择）
- [ ] 添加 `clearCheckboxRow()` 取消选择逻辑

### 11.5 弹窗/抽屉项

- [ ] 所有表单弹窗、详情弹窗/抽屉、业务弹窗（导入、分配等）均为独立 `.vue` 文件，位于页面目录 `components/` 下
- [ ] `index.vue` 中无 `formRef` / `formRules` / `formData` / `handleSubmit` 等弹窗内部逻辑
- [ ] 弹窗显隐走 `v-model`，初始数据走 props，提交结果走 `@success`
- [ ] 弹窗打开时正确初始化（新增重置、编辑回显、清除校验残留）
- [ ] 提交按钮有 `:loading`，成功后弹窗自行关闭并提示

---

## 12. 常见问题

### Q1: 工具栏自定义列按钮不显示？

确保 `vxe-toolbar` 设置了 `custom` 属性，且表格有 `id` 和 `custom-config`。

### Q2: 序号不连续？

检查 `useTableSeq` 的 `currentPage` 和 `pageSize` 是否正确绑定到查询参数。

### Q3: 树形表格展开/折叠不生效？

检查 `tree-config` 中 `rowField` 和 `parentField` 是否匹配数据结构，`row-config` 中是否设置了 `keyField`。

### Q4: 列宽无法调整？

检查 `column-config="{ resizable: true }"` 是否设置，且列未设置 `fixed`（固定列不可调整宽度）。

### Q5: 移动端行选中不触发？

检查表格是否添加了 `@current-change="handleCurrentChange"`，且处理函数中判断了 `isMobile.value`。

---

*文档版本：v2.1*
*最后更新：2026-10-12*
