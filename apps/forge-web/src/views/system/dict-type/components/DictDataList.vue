<template>
  <div ref="containerRef" class="dict-data-container" style="height: 100%">
    <!-- 搜索栏（桌面端） -->
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
        <el-button type="primary" @click="handleMobileAdd">
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

    <!-- 工具栏 -->
    <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
      <template #buttons>
        <el-button type="primary" @click="handleAddRow">新增数据</el-button>
        <el-button type="danger" :disabled="!hasCheckedRows" @click="handleDeleteRows">删除勾选</el-button>
        <el-button type="success" :disabled="!hasChanges" :loading="loading" @click="handleBatchSave">保存修改</el-button>
      </template>
      <template #tools>
        <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
      </template>
    </vxe-toolbar>

    <!-- 可编辑表格 -->
    <vxe-table
      id="sysDictDataTable"
      ref="tableRef"
      keep-source
      border="none"
      stripe
      show-overflow="tooltip"
      show-header-overflow="tooltip"
      :custom-config="{ mode: 'modal' }"
      :data="tableData"
      :height="tableHeight"
      :loading="loading"
      :row-config="{ isCurrent: true, isHover: true }"
      :column-config="{ resizable: true }"
      :keyboard-config="{}"
      :checkbox-config="{ reserve: true }"
      :mouse-config="{}"
      :edit-config="{}"
      :edit-rules="validRules"
      @edit-closed="onEditClosed"
    >
      <!-- 勾选列 -->
      <vxe-column v-if="!isMobile" type="checkbox" width="40" align="center" />
      <!-- 序号列 -->
      <vxe-column v-if="!isMobile" type="seq" title="序号" width="50" align="center" />

      <!-- 字典标签 -->
      <vxe-column field="dictLabel" title="字典标签" min-width="140" align="center" :edit-render="{autoFocus: true}">
        <template #edit="{ row }">
          <el-input v-model="row.dictLabel" placeholder="请输入字典标签" size="small" />
        </template>
      </vxe-column>

      <!-- 字典值 -->
      <vxe-column field="dictValue" title="字典值" min-width="120" align="center" :edit-render="{autoFocus: true}">
        <template #edit="{ row }">
          <el-input v-model="row.dictValue" placeholder="请输入字典值" size="small" style="width: 100%" />
        </template>
      </vxe-column>

      <!-- 排序 -->
      <vxe-column field="dictSort" title="排序" width="80" align="center" :edit-render="{autoFocus: true}">
        <template #edit="{ row }">
          <el-input-number v-model="row.dictSort" :min="0" :max="999" size="small" style="width: 100%" :controls="false" controls-position="right" />
        </template>
      </vxe-column>

      <!-- CSS样式（桌面端） -->
      <vxe-column v-if="!isMobile" field="cssClass" title="CSS样式" width="140" align="center" :edit-render="{autoFocus: true}">
        <template #default="{ row }">
          <span v-if="row.cssClass" class="dict-value-container">
            <el-tag :type="(getCssClassTagType(row.cssClass) as any)" :class="row.cssClass || undefined" size="small">
              {{ row.cssClass }}
            </el-tag>
          </span>
          <span v-else style="color: var(--el-text-color-secondary);">-</span>
        </template>
        <template #edit="{ row }">
          <el-select v-model="row.cssClass" placeholder="请选择" clearable style="width: 100%" :teleported="false" popper-class="css-class-popper vxe-table--ignore-clear">
            <el-option
              v-for="item in cssClassOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            >
              <span class="dict-value-container" style="display: inline-flex; align-items: center; gap: 8px;">
                <el-tag :type="(item.tagType as any)" :class="item.value" size="small">{{ item.label }}</el-tag>
                <span style="color: var(--el-text-color-secondary); font-size: 12px;">{{ item.value }}</span>
              </span>
            </el-option>
          </el-select>
        </template>
      </vxe-column>

      <!-- 表格样式（桌面端） -->
      <vxe-column v-if="!isMobile" field="listClass" title="表格样式" width="120" align="center" :edit-render="{}">
        <template #default="{ row }">
          <dict-value v-if="row.listClass" :dict-type="DICT_TYPE.SYS_TAG_TYPE" :value="row.listClass" />
          <span v-else style="color: var(--el-text-color-secondary);">-</span>
        </template>
        <template #edit="{ row }">
          <el-select v-model="row.listClass" placeholder="请选择" clearable style="width: 100%" popper-class="vxe-table--ignore-clear">
            <el-option
              v-for="item in tagTypeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </template>
      </vxe-column>

      <!-- 状态 -->
      <vxe-column field="status" title="状态" width="90" align="center" :edit-render="{}">
        <template #default="{ row }">
          <dict-value :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
        </template>
        <template #edit="{ row }">
          <el-select v-model="row.status" style="width: 100%" popper-class="vxe-table--ignore-clear">
            <el-option
              v-for="item in statusOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="Number(item.dictValue)"
            />
          </el-select>
        </template>
      </vxe-column>

      <!-- 备注 -->
      <vxe-column field="remark" title="备注" min-width="140" :edit-render="{autoFocus: true}">
        <template #edit="{ row }">
          <el-input v-model="row.remark" placeholder="请输入备注" />
        </template>
      </vxe-column>

      <!-- 创建时间（桌面端，不可编辑） -->
      <vxe-column v-if="!isMobile" field="createTime" title="创建时间" width="150" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </vxe-column>

      <!-- 移动端操作列 -->
      <vxe-column v-if="isMobile" title="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link size="small" @click.stop="handleDeleteRow(row)">删除</el-button>
        </template>
      </vxe-column>
    </vxe-table>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="isMobile && hasChanges"
      :item="null"
      item-title=""
      @cancel="handleCancelChanges"
    >
      <template #actions>
        <el-button size="small" @click="handleCancelChanges">取消修改</el-button>
        <el-button size="small" type="success" :loading="loading" @click="handleBatchSave">保存修改</el-button>
      </template>
    </MobileBottomActions>

    <!-- 移动端新增弹窗 -->
    <el-dialog v-model="addDialogVisible" title="新增字典数据" width="90%" class="dialog-form-responsive">
      <el-form ref="addFormRef" :model="addFormData" :rules="addFormRules" label-width="80px">
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="addFormData.dictLabel" placeholder="请输入字典标签" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="addFormData.dictValue" placeholder="请输入字典值" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="addFormData.dictSort" :min="0" :max="999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="addFormData.status">
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
          <el-input v-model="addFormData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleMobileAddSubmit">确定</el-button>
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
import { getDictDataList, batchSaveDictData } from '@/api/system'
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
  { label: '默认', value: 'default', tagType: 'info' },
  { label: '主要', value: 'primary', tagType: 'primary' },
  { label: '成功', value: 'success', tagType: 'success' },
  { label: '信息', value: 'info', tagType: 'info' },
  { label: '警告', value: 'warning', tagType: 'warning' },
  { label: '危险', value: 'danger', tagType: 'danger' },
  { label: '青色', value: 'cyan', tagType: 'primary' },
  { label: '紫色', value: 'purple', tagType: 'primary' },
  { label: '橙色', value: 'orange', tagType: 'warning' },
  { label: '粉色', value: 'pink', tagType: 'danger' },
  { label: '靛蓝', value: 'indigo', tagType: 'primary' },
  { label: '棕色', value: 'brown', tagType: 'warning' },
  { label: '灰色', value: 'grey', tagType: 'info' },
  { label: '青柠', value: 'lime', tagType: 'success' },
  { label: '圆角-主要', value: 'primary-round', tagType: 'primary' },
  { label: '圆角-成功', value: 'success-round', tagType: 'success' },
  { label: '圆角-警告', value: 'warning-round', tagType: 'warning' },
  { label: '圆角-危险', value: 'danger-round', tagType: 'danger' },
  { label: '圆角-青色', value: 'cyan-round', tagType: 'primary' },
  { label: '圆角-紫色', value: 'purple-round', tagType: 'primary' },
  { label: '圆角-橙色', value: 'orange-round', tagType: 'warning' },
  { label: '圆角-粉色', value: 'pink-round', tagType: 'danger' },
  { label: '文本-主要', value: 'text-primary', tagType: 'primary' },
  { label: '文本-成功', value: 'text-success', tagType: 'success' },
  { label: '文本-警告', value: 'text-warning', tagType: 'warning' },
  { label: '文本-危险', value: 'text-danger', tagType: 'danger' },
  { label: '文本-青色', value: 'text-cyan', tagType: 'primary' },
  { label: '文本-紫色', value: 'text-purple', tagType: 'primary' },
  { label: '文本-橙色', value: 'text-orange', tagType: 'warning' },
  { label: '文本-粉色', value: 'text-pink', tagType: 'danger' },
  { label: '粗体-主要', value: 'bold-primary', tagType: 'primary' },
  { label: '粗体-成功', value: 'bold-success', tagType: 'success' },
  { label: '粗体-警告', value: 'bold-warning', tagType: 'warning' },
  { label: '粗体-危险', value: 'bold-danger', tagType: 'danger' },
  { label: '粗体-青色', value: 'bold-cyan', tagType: 'primary' },
  { label: '粗体-紫色', value: 'bold-purple', tagType: 'primary' },
  { label: '粗体-橙色', value: 'bold-orange', tagType: 'warning' },
  { label: '粗体-粉色', value: 'bold-pink', tagType: 'danger' },
  { label: '大号-主要', value: 'large-primary', tagType: 'primary' },
  { label: '大号-成功', value: 'large-success', tagType: 'success' },
  { label: '大号-警告', value: 'large-warning', tagType: 'warning' },
  { label: '大号-危险', value: 'large-danger', tagType: 'danger' },
  { label: '大号-青色', value: 'large-cyan', tagType: 'primary' },
  { label: '大号-紫色', value: 'large-purple', tagType: 'primary' },
  { label: '大号-橙色', value: 'large-orange', tagType: 'warning' },
  { label: '大号-粉色', value: 'large-pink', tagType: 'danger' },
]

const cssClassMap = new Map(cssClassOptions.map(opt => [opt.value, opt.tagType]))
const getCssClassTagType = (cssClass: string) => cssClassMap.get(cssClass) || 'info'

const props = defineProps<{
  dictType: string
}>()

const { isMobile } = useResponsive()

const containerRef = ref<HTMLElement>()
const { tableHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.search-form', '.vxe-toolbar', '.mobile-search-actions'],
})

const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<DictData[]>([])
const dirty = ref(false)

// 移动端状态
const searchDrawerVisible = ref(false)

const queryParams = reactive({
  dictType: '',
  dictLabel: '',
  status: undefined as number | undefined
})

const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.dictLabel) count++
  if (queryParams.status !== undefined) count++
  return count
})

const hasCheckedRows = computed(() => {
  if (!tableRef.value) return false
  return tableRef.value.getCheckboxRecords().length > 0
})

const hasChanges = computed(() => dirty.value)

// 编辑校验规则
const validRules = {
  dictLabel: [{ required: true, message: '请输入字典标签' }],
  dictValue: [{ required: true, message: '请输入字典值' }],
}

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
    dirty.value = false
  } finally {
    loading.value = false
  }
}

const handleQuery = () => getList()

const handleReset = () => {
  queryParams.dictLabel = ''
  queryParams.status = undefined
  getList()
}

const handleSearchFromDrawer = () => getList()
const handleResetFromDrawer = () => handleReset()

const handleAddRow = () => {
  const newRow: DictData = {
    id: undefined as any,
    dictType: props.dictType,
    dictLabel: '',
    dictValue: '',
    dictSort: tableData.value.length,
    cssClass: '',
    listClass: '',
    status: 1,
    remark: '',
    createTime: '',
  }
  tableData.value.push(newRow)
  dirty.value = true
  nextTick(() => {
    tableRef.value?.setEditRow(newRow, 'dictLabel')
  })
}

// 移动端新增弹窗
const addDialogVisible = ref(false)
const addFormRef = ref<FormInstance>()
const addFormData = reactive({
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  status: 1,
  remark: '',
})
const addFormRules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }],
}

const handleMobileAdd = () => {
  Object.assign(addFormData, { dictLabel: '', dictValue: '', dictSort: tableData.value.length, status: 1, remark: '' })
  addDialogVisible.value = true
}

const handleMobileAddSubmit = async () => {
  if (!addFormRef.value) return
  await addFormRef.value.validate()
  tableData.value.push({
    id: undefined as any,
    dictType: props.dictType,
    dictLabel: addFormData.dictLabel,
    dictValue: addFormData.dictValue,
    dictSort: addFormData.dictSort,
    cssClass: '',
    listClass: '',
    status: addFormData.status,
    remark: addFormData.remark,
    createTime: '',
  })
  dirty.value = true
  addDialogVisible.value = false
}

const handleDeleteRows = async () => {
  if (!tableRef.value) return
  const checkedRows = tableRef.value.getCheckboxRecords()
  if (checkedRows.length === 0) return

  try {
    await ElMessageBox.confirm(`确定删除选中的 ${checkedRows.length} 条数据?`, '警告', { type: 'warning' })
    tableRef.value.removeCheckboxRow()
    tableData.value = tableData.value.filter(item => !checkedRows.includes(item))
    dirty.value = true
  } catch {}
}

const handleDeleteRow = async (row: DictData) => {
  try {
    await ElMessageBox.confirm(`确定删除 "${row.dictLabel}"?`, '警告', { type: 'warning' })
    tableData.value = tableData.value.filter(item => item !== row)
    dirty.value = true
  } catch {}
}

const handleBatchSave = async () => {
  if (!tableRef.value) return

  const errMap = await tableRef.value.validate(true).catch(() => true)
  if (errMap) {
    ElMessage.error('请填写必填字段')
    return
  }

  loading.value = true
  try {
    await batchSaveDictData({
      dictType: props.dictType,
      dataList: tableData.value.map(row => ({
        id: row.id && row.id > 0 ? row.id : undefined,
        dictType: props.dictType,
        dictLabel: row.dictLabel,
        dictValue: row.dictValue,
        dictSort: row.dictSort,
        cssClass: row.cssClass,
        listClass: row.listClass,
        status: row.status,
        remark: row.remark,
      })),
    })
    ElMessage.success('保存成功')
    getList()
  } finally {
    loading.value = false
  }
}

const handleCancelChanges = () => {
  getList()
}

const onEditClosed = () => {
  dirty.value = true
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
.css-class-popper .el-select-dropdown__wrap {
  max-height: 420px;
  width: 250px;
  text-align: left;
}
</style>
