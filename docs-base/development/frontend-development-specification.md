# 前端开发规范

> 适用范围：`apps/forge-web`（管理端）。小程序（`apps/forge-miniapp`）与大屏编辑器（`apps/forge-screen`）另行约定。
>
> 本规范第一版聚焦**页面组件组织与弹窗/抽屉拆分**，后续版本可扩展其他前端规范。

---

## 1. 核心原则

- **一个文件一个职责**：`index.vue` 只负责"搜索 + 列表 + 编排"，弹窗/抽屉的内部实现一律独立成文件
- **复杂弹窗禁止内联**：表单弹窗、详情抽屉、导入/分配等业务弹窗必须抽为独立 Vue 文件
- **封装边界清晰**：弹窗组件自包含（表单数据、校验、加载、提交全在弹窗内），父组件只控制开关与传参

---

## 2. 页面目录结构

### 2.1 标准结构

```
src/views/{module}/{entity}/
├── index.vue                      # 页面入口：搜索栏 + 表格 + 弹窗编排
└── components/                    # 页面私有组件
    ├── XxxFormDialog.vue          # 新增/编辑表单弹窗
    ├── XxxDetailDrawer.vue        # 详情抽屉
    ├── XxxImportDialog.vue        # 导入弹窗（如有）
    └── XxxAssignYyyDialog.vue     # 分配/关联等业务弹窗（如有）
```

### 2.2 index.vue 职责边界

| 职责 | 归属 |
|------|------|
| 搜索表单、查询参数、重置 | `index.vue` |
| 表格数据加载（getList）、分页、多选 | `index.vue` |
| 移动端适配（isMobile、搜索抽屉、底部操作栏） | `index.vue` |
| 弹窗的引用与打开（handleAdd/handleEdit 只设开关与传参） | `index.vue` |
| 弹窗内部表单、校验规则、详情加载、提交/重置 | **弹窗组件** |
| 提交成功后的列表刷新 | `index.vue`（监听 `@success` 回调） |

`index.vue` 原则上控制在 **~400 行**以内；超出时优先检查是否有应抽离的弹窗或区块。

### 2.3 是否抽离的判定标准

| 场景 | 要求 | 示例 |
|------|------|------|
| 表单新增/编辑弹窗（含校验） | **必须抽离** | `XxxFormDialog.vue` |
| 详情弹窗/抽屉（多字段展示） | **必须抽离** | `XxxDetailDrawer.vue` |
| 导入、分配角色、批量操作等业务弹窗 | **必须抽离** | `UserImportDialog.vue` |
| 纯删除/风险确认 | 内联 `ElMessageBox.confirm`，不抽离 | — |
| 仅单个输入框、无校验逻辑的极简弹窗 | 可内联；超过一个输入框即抽离 | — |

### 2.4 项目内先例

新代码对齐以下现有写法：

| 先例 | 说明 |
|------|------|
| `views/workflow/process/components/FlowDiagramDialog.vue` | 流程图弹窗 |
| `views/workflow/task/components/TaskDetailDrawer.vue` | 任务详情抽屉 |
| `views/system/user/UserImportDialog.vue` | 用户导入弹窗 |
| `views/system/dict-type/components/DictDataList.vue` | 字典数据弹窗（复杂列表） |

---

## 3. 弹窗/抽屉组件规范

### 3.1 命名

- 弹窗：`XxxDialog.vue`；抽屉：`XxxDrawer.vue`；PascalCase
- 业务语义命名：表单类 `XxxFormDialog`、详情类 `XxxDetailDrawer` / `XxxDetailDialog`、导入类 `XxxImportDialog`、分配类 `XxxAssignYyyDialog`
- **新文件统一放在页面目录的 `components/` 子目录下**；历史文件放在模块根目录的（如 `UserImportDialog.vue`）可保持不动，不做强制回改

### 3.2 父子通信协议

| 通道 | 约定 |
|------|------|
| 显隐控制 | `v-model`：子组件 `modelValue` prop + `update:modelValue` emit + computed `visible` |
| 初始数据 | props 传入（如编辑时传 `row`），数据回显/详情加载由弹窗内部完成 |
| 结果通知 | `emit('success')`；父组件在回调中刷新列表。成功提示、关闭弹窗由弹窗自行处理 |
| 关闭/取消 | 弹窗内部处理，无需通知父组件 |

### 3.3 子组件标准模板（表单弹窗）

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
// Vue API（ref/computed/watch 等）已通过 unplugin-auto-import 自动导入
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

### 3.4 父组件标准用法（index.vue）

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

### 3.5 封装要点

- **打开时初始化**：`watch(() => props.modelValue)`，在打开瞬间重置表单或回显数据，`nextTick` 后 `clearValidate()` 清除校验残留
- **编辑回显**：优先直接使用传入的 `row`；若列表字段不全或需要最新详情，由弹窗内部调用 `xxxApi.get(row.id)` 加载，**不要**由父组件取好详情再传入
- **校验**：`FormRules` 定义在弹窗组件内，提交前 `formRef.value.validate()`
- **提交**：弹窗内部调用 API，成功后 `ElMessage.success` → 关闭 → `emit('success')`；提交按钮加 `:loading` 防重复提交
- **禁止外漏**：`formData`、`formRules`、`handleSubmit`、`formRef` 不得出现在 `index.vue` 中
- **详情类弹窗/抽屉**：同样接收 `row`，内部按 `row.id` 加载详情并渲染 `el-descriptions`

---

## 4. 检查清单

新页面 / 新弹窗提交前自查：

- [ ] 所有表单弹窗、详情弹窗/抽屉、业务弹窗（导入、分配等）均为独立 `.vue` 文件，位于页面目录 `components/` 下
- [ ] `index.vue` 中无 `formRef` / `formRules` / `formData` / `handleSubmit` 等弹窗内部逻辑
- [ ] 弹窗显隐走 `v-model`，初始数据走 props，提交结果走 `@success`
- [ ] 弹窗打开时正确初始化（新增重置、编辑回显、清除校验残留）
- [ ] 提交按钮有 `:loading`，成功后弹窗自行关闭并提示
- [ ] `index.vue` 行数控制在 ~400 行以内
- [ ] 纯确认操作使用 `ElMessageBox.confirm`，未抽成组件
- [ ] 组件命名 PascalCase，`XxxDialog` / `XxxDrawer` 后缀正确

---

*文档版本：v1.0*
*最后更新：2026-10-10*
