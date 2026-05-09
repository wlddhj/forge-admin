<template>
  <div ref="containerRef" class="dict-data-container" style="height: 100%">
    <!-- 搜索栏 -->
    <!-- 桌面端搜索表单 -->
    <el-form v-if="!isMobile" :model="queryParams" inline class="search-form">
      <el-form-item label="字典标签">
        <el-input v-model="queryParams.dictLabel" placeholder="请输入字典标签" clearable />
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
        <el-button type="primary" @click="handleQuery">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 移动端搜索按钮 -->
    <div v-else class="mobile-search-actions">
      <span class="title">字典数据</span>
      <div class="actions">
        <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="字典标签">
          <el-input v-model="queryParams.dictLabel" placeholder="请输入字典标签" clearable />
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

    <!-- vxe-toolbar 工具栏（桌面端） -->
    <vxe-toolbar v-if="!isMobile" ref="toolbarRef" >
      <template #buttons>
        <el-button type="primary" @click="handleAdd">新增数据</el-button>
      </template>
      <template #tools>
        <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
      </template>
    </vxe-toolbar>

    <!-- vxe-table 表格 -->
    <vxe-table
      ref="tableRef"
      id="sysDictDataTable"
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
      <!-- 序号列（桌面端） -->
      <vxe-column v-if="!isMobile" type="seq" title="序号" width="50" align="center" />

      <!-- 字典标签 -->
      <vxe-column field="dictLabel" title="字典标签" width="150" />

      <!-- 字典值 -->
      <vxe-column field="dictValue" title="字典值" width="120" />

      <!-- 排序（桌面端） -->
      <vxe-column v-if="!isMobile" field="dictSort" title="排序" width="70" align="center" />

      <!-- CSS样式（桌面端） -->
      <vxe-column v-if="!isMobile" title="CSS样式" width="120" align="center">
        <template #default="{ row }">
          <span v-if="row.cssClass" class="dict-value-container">
            <el-tag
              :type="getCssClassTagType(row.cssClass)"
              :class="row.cssClass || undefined"
              size="small"
            >
              {{ row.cssClass }}
            </el-tag>
          </span>
          <span v-else style="color: var(--el-text-color-secondary);">-</span>
        </template>
      </vxe-column>

      <!-- 表格样式（桌面端） -->
      <vxe-column v-if="!isMobile" title="表格样式" width="100" align="center">
        <template #default="{ row }">
          <dict-value v-if="row.listClass" :dict-type="DICT_TYPE.SYS_TAG_TYPE" :value="row.listClass" />
        </template>
      </vxe-column>

      <!-- 状态 -->
      <vxe-column title="状态" width="70" align="center">
        <template #default="{ row }">
          <dict-value :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
        </template>
      </vxe-column>

      <!-- 备注（桌面端） -->
      <vxe-column v-if="!isMobile" field="remark" title="备注" min-width="120" />

      <!-- 创建时间（桌面端） -->
      <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="150" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </vxe-column>

      <!-- 桌面端操作列 -->
      <vxe-column v-if="!isMobile" title="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
          <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
        </template>
      </vxe-column>
    </vxe-table>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.dictLabel"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="dialog-form-responsive">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="formData.dictLabel" placeholder="请输入字典标签" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="formData.dictValue" placeholder="请输入字典值" />
        </el-form-item>
        <el-form-item label="排序" prop="dictSort">
          <el-input-number v-model="formData.dictSort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="CSS样式">
          <el-select v-model="formData.cssClass" placeholder="请选择CSS样式" clearable style="width: 100%" :teleported="false" popper-class="css-class-popper">
            <el-option
              v-for="item in cssClassOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            >
              <span class="dict-value-container" style="display: inline-flex; align-items: center; gap: 8px;">
                <el-tag :type="item.tagType" :class="item.value" size="small">{{ item.label }}</el-tag>
                <span style="color: var(--el-text-color-secondary); font-size: 12px;">{{ item.value }}</span>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="表格样式">
          <el-select v-model="formData.listClass" placeholder="请选择表格样式" clearable style="width: 100%">
            <el-option
              v-for="item in tagTypeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
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
import { reactive, ref, watch, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { Plus } from '@element-plus/icons-vue'
import { getDictDataList, addDictData, updateDictData, deleteDictData } from '@/api/system'
import type { DictData } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useDrawerTableHeight } from '@/composables/useDrawerTableHeight'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import DictValue from '@/components/DictValue.vue'

const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)
const { dictData: tagTypeOptions } = useDict(DICT_TYPE.SYS_TAG_TYPE)

// CSS 样式选项
const cssClassOptions = [
  // 基础色
  { label: '默认', value: 'default', tagType: 'info' },
  { label: '主要', value: 'primary', tagType: 'primary' },
  { label: '成功', value: 'success', tagType: 'success' },
  { label: '信息', value: 'info', tagType: 'info' },
  { label: '警告', value: 'warning', tagType: 'warning' },
  { label: '危险', value: 'danger', tagType: 'danger' },
  // 扩展色
  { label: '青色', value: 'cyan', tagType: 'primary' },
  { label: '紫色', value: 'purple', tagType: 'primary' },
  { label: '橙色', value: 'orange', tagType: 'warning' },
  { label: '粉色', value: 'pink', tagType: 'danger' },
  { label: '靛蓝', value: 'indigo', tagType: 'primary' },
  { label: '棕色', value: 'brown', tagType: 'warning' },
  { label: '灰色', value: 'grey', tagType: 'info' },
  { label: '青柠', value: 'lime', tagType: 'success' },
  // 圆角
  { label: '圆角-主要', value: 'primary-round', tagType: 'primary' },
  { label: '圆角-成功', value: 'success-round', tagType: 'success' },
  { label: '圆角-警告', value: 'warning-round', tagType: 'warning' },
  { label: '圆角-危险', value: 'danger-round', tagType: 'danger' },
  { label: '圆角-青色', value: 'cyan-round', tagType: 'primary' },
  { label: '圆角-紫色', value: 'purple-round', tagType: 'primary' },
  { label: '圆角-橙色', value: 'orange-round', tagType: 'warning' },
  { label: '圆角-粉色', value: 'pink-round', tagType: 'danger' },
  // 纯文本色（无背景）
  { label: '文本-主要', value: 'text-primary', tagType: 'primary' },
  { label: '文本-成功', value: 'text-success', tagType: 'success' },
  { label: '文本-警告', value: 'text-warning', tagType: 'warning' },
  { label: '文本-危险', value: 'text-danger', tagType: 'danger' },
  { label: '文本-青色', value: 'text-cyan', tagType: 'primary' },
  { label: '文本-紫色', value: 'text-purple', tagType: 'primary' },
  { label: '文本-橙色', value: 'text-orange', tagType: 'warning' },
  { label: '文本-粉色', value: 'text-pink', tagType: 'danger' },
  // 粗体
  { label: '粗体-主要', value: 'bold-primary', tagType: 'primary' },
  { label: '粗体-成功', value: 'bold-success', tagType: 'success' },
  { label: '粗体-警告', value: 'bold-warning', tagType: 'warning' },
  { label: '粗体-危险', value: 'bold-danger', tagType: 'danger' },
  { label: '粗体-青色', value: 'bold-cyan', tagType: 'primary' },
  { label: '粗体-紫色', value: 'bold-purple', tagType: 'primary' },
  { label: '粗体-橙色', value: 'bold-orange', tagType: 'warning' },
  { label: '粗体-粉色', value: 'bold-pink', tagType: 'danger' },
  // 大号
  { label: '大号-主要', value: 'large-primary', tagType: 'primary' },
  { label: '大号-成功', value: 'large-success', tagType: 'success' },
  { label: '大号-警告', value: 'large-warning', tagType: 'warning' },
  { label: '大号-危险', value: 'large-danger', tagType: 'danger' },
  { label: '大号-青色', value: 'large-cyan', tagType: 'primary' },
  { label: '大号-紫色', value: 'large-purple', tagType: 'primary' },
  { label: '大号-橙色', value: 'large-orange', tagType: 'warning' },
  { label: '大号-粉色', value: 'large-pink', tagType: 'danger' },
]

// 根据 cssClass 值查找对应的 tagType
const cssClassMap = new Map(cssClassOptions.map(opt => [opt.value, opt.tagType]))
const getCssClassTagType = (cssClass: string) => cssClassMap.get(cssClass) || 'info'

const props = defineProps<{
  dictType: string
}>()

const { isMobile } = useResponsive()

// 表格高度自适应（基于容器高度）
const containerRef = ref<HTMLElement>()
const { tableHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.search-form', '.vxe-toolbar', '.mobile-search-actions'],
})

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<DictData[]>([])

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<DictData | null>(null)

const queryParams = reactive({
  dictType: '',
  dictLabel: '',
  status: undefined as number | undefined
})

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.dictLabel) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as number | undefined,
  dictType: '',
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  cssClass: '',
  listClass: '',
  status: 1,
  remark: ''
})

const formRules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
}

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

const getList = async () => {
  if (!queryParams.dictType) return
  loading.value = true
  try {
    const res = await getDictDataList({ ...queryParams, pageNum: 1, pageSize: 9999 })
    tableData.value = res.list
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  getList()
}

const handleReset = () => {
  queryParams.dictLabel = ''
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
  cancelSelection()
  isEdit.value = false
  dialogTitle.value = '新增字典数据'
  Object.assign(formData, {
    id: undefined,
    dictType: props.dictType,
    dictLabel: '',
    dictValue: '',
    dictSort: 0,
    cssClass: '',
    listClass: '',
    status: 1,
    remark: ''
  })
  dialogVisible.value = true
}

const handleEdit = (row: DictData) => {
  cancelSelection()
  isEdit.value = true
  dialogTitle.value = '编辑字典数据'
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateDictData({
        id: formData.id!,
        dictType: formData.dictType,
        dictLabel: formData.dictLabel,
        dictValue: formData.dictValue,
        dictSort: formData.dictSort,
        cssClass: formData.cssClass,
        listClass: formData.listClass,
        status: formData.status,
        remark: formData.remark
      })
      ElMessage.success('更新成功')
    } else {
      await addDictData({
        dictType: formData.dictType,
        dictLabel: formData.dictLabel,
        dictValue: formData.dictValue,
        dictSort: formData.dictSort,
        cssClass: formData.cssClass,
        listClass: formData.listClass,
        status: formData.status,
        remark: formData.remark
      })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: DictData) => {
  try {
    await ElMessageBox.confirm(`确定删除字典数据 "${row.dictLabel}"?`, '警告', { type: 'warning' })
    await deleteDictData([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  } catch (e) {}
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: DictData | null }) => {
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

watch(
  () => props.dictType,
  (val) => {
    if (val) {
      queryParams.dictType = val
      getList()
    }
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
@use '@/styles/dict-tag.scss' as *;

.dict-data-container {
  .search-form {
    margin-bottom: 15px;
  }
}

.dict-value-container {
  @include dict-tag-styles;
}

html.dark .dict-value-container {
  @include dict-tag-dark-styles;
}
</style>

<style lang="scss">
// CSS样式下拉框面板高度
.css-class-popper .el-select-dropdown__wrap {
  max-height: 420px;
}
</style>