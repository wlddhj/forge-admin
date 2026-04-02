<template>
  <div class="dict-data-container">
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

    <!-- 操作按钮 -->
    <div v-if="!isMobile" class="action-bar">
      <el-button type="primary" @click="handleAdd">新增数据</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="tableData"
      border
      stripe
      :row-class-name="getRowClassName"
      @row-click="handleRowClick"
    >
      <el-table-column prop="id" label="ID" width="80" v-if="!isMobile" />
      <el-table-column prop="dictLabel" label="字典标签" width="150" />
      <el-table-column prop="dictValue" label="字典值" width="120" />
      <el-table-column prop="dictSort" label="排序" width="80" v-if="!isMobile" />
      <el-table-column prop="cssClass" label="CSS样式" width="120" v-if="!isMobile" show-overflow-tooltip />
      <el-table-column label="表格样式" width="120" v-if="!isMobile">
        <template #default="{ row }">
          <dict-value v-if="row.listClass" :dict-type="DICT_TYPE.SYS_TAG_TYPE" :value="row.listClass" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <dict-value :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip v-if="!isMobile" />
      <el-table-column prop="createTime" label="创建时间" width="180" v-if="!isMobile">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <!-- 桌面端操作列 -->
      <el-table-column v-if="!isMobile" label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="queryParams.pageNum"
      v-model:page-size="queryParams.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
      @size-change="getList"
      @current-change="getList"
    />

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
          <el-select v-model="formData.cssClass" placeholder="请选择CSS样式" clearable style="width: 100%">
            <el-option
              v-for="item in cssClassOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            >
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-tag :type="item.tagType" :class="item.value" size="small">{{ item.label }}</el-tag>
                <span style="color: var(--el-text-color-secondary); font-size: 12px;">{{ item.value }}</span>
              </div>
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
import { Plus } from '@element-plus/icons-vue'
import { getDictDataList, addDictData, updateDictData, deleteDictData } from '@/api/system'
import type { DictData } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
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
  // 圆角
  { label: '圆角-主要', value: 'primary-round', tagType: 'primary' },
  { label: '圆角-成功', value: 'success-round', tagType: 'success' },
  { label: '圆角-警告', value: 'warning-round', tagType: 'warning' },
  { label: '圆角-危险', value: 'danger-round', tagType: 'danger' },
  // 纯文本色（无背景）
  { label: '文本-主要', value: 'text-primary', tagType: 'primary' },
  { label: '文本-成功', value: 'text-success', tagType: 'success' },
  { label: '文本-警告', value: 'text-warning', tagType: 'warning' },
  { label: '文本-危险', value: 'text-danger', tagType: 'danger' },
  // 粗体
  { label: '粗体-主要', value: 'bold-primary', tagType: 'primary' },
  { label: '粗体-成功', value: 'bold-success', tagType: 'success' },
  { label: '粗体-警告', value: 'bold-warning', tagType: 'warning' },
  { label: '粗体-危险', value: 'bold-danger', tagType: 'danger' },
  // 大号
  { label: '大号-主要', value: 'large-primary', tagType: 'primary' },
  { label: '大号-成功', value: 'large-success', tagType: 'success' },
  { label: '大号-警告', value: 'large-warning', tagType: 'warning' },
  { label: '大号-危险', value: 'large-danger', tagType: 'danger' },
]

const props = defineProps<{
  dictType: string
}>()

const { isMobile } = useResponsive()

const loading = ref(false)
const tableData = ref<DictData[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<DictData | null>(null)

const queryParams = reactive({
  dictType: '',
  dictLabel: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
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

const getList = async () => {
  if (!queryParams.dictType) return
  loading.value = true
  try {
    const res = await getDictDataList(queryParams)
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
  queryParams.dictLabel = ''
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

// 获取行样式名
const getRowClassName = ({ row }: { row: DictData }) => {
  if (isMobile.value && selectedRow.value?.id === row.id) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: DictData) => {
  if (isMobile.value) {
    selectedRow.value = selectedRow.value?.id === row.id ? null : row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
}

// 获取表格样式标签
const getListClassLabel = (value: string) => {
  const map: Record<string, string> = {
    default: '默认',
    primary: '主要',
    success: '成功',
    warning: '警告',
    danger: '危险',
    info: '信息'
  }
  return map[value] || value
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
.dict-data-container {
  .search-form {
    margin-bottom: 15px;
  }
  .action-bar {
    margin-bottom: 15px;
  }
  .el-pagination {
    margin-top: 15px;
    justify-content: flex-end;
  }
}
</style>
