<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="字典名称">
          <el-input v-model="queryParams.dictName" placeholder="请输入字典名称" clearable />
        </el-form-item>
        <el-form-item label="字典类型">
          <el-input v-model="queryParams.dictType" placeholder="请输入字典类型" clearable />
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
        <span class="title">字典类型</span>
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
        <el-form-item label="字典名称">
          <el-input v-model="queryParams.dictName" placeholder="请输入字典名称" clearable />
        </el-form-item>
        <el-form-item label="字典类型">
          <el-input v-model="queryParams.dictType" placeholder="请输入字典类型" clearable />
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
      <template #header>
        <div class="card-header">
          <span v-if="!isMobile">字典类型列表</span>
          <div v-if="!isMobile" class="header-btns">
            <el-button v-permission="'system:dict:edit'" @click="handleRefreshCache">
              <el-icon><Refresh /></el-icon>
              刷新缓存
            </el-button>
            <el-button v-permission="'system:dict:add'" type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增字典
            </el-button>
          </div>
        </div>
      </template>

      <div class="table-responsive">
        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          :row-class-name="getRowClassName"
          @row-click="handleRowClick"
        >
          <el-table-column prop="id" label="ID" width="80" v-if="!isMobile" />
          <el-table-column prop="dictName" label="字典名称" width="150" />
          <el-table-column prop="dictType" label="字典类型" width="180" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="createTime" label="创建时间" width="180" v-if="!isMobile">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <!-- 桌面端操作列 -->
          <el-table-column v-if="!isMobile" label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'system:dict:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button type="primary" link @click="handleViewData(row)">字典数据</el-button>
              <el-button v-permission="'system:dict:delete'" type="danger" link @click="handleDelete(row)" :disabled="row.isSystem === 1">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.dictName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button v-permission="'system:dict:edit'" size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button size="small" @click.stop="handleViewData(item)">字典数据</el-button>
        <el-button v-permission="'system:dict:delete'" size="small" type="danger" @click.stop="handleDelete(item)" :disabled="item.isSystem === 1">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="dialog-form-responsive">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="formData.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="formData.dictType" placeholder="请输入字典类型" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
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

    <!-- 字典数据抽屉 -->
    <el-drawer v-model="dataDrawerVisible" title="字典数据" :size="isMobile ? '85%' : '50%'">
      <dict-data-list v-if="dataDrawerVisible" :dict-type="currentDictType" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getDictTypeList, addDictType, updateDictType, deleteDictType, refreshDictCache } from '@/api/system'
import type { DictType } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import DictDataList from './components/DictDataList.vue'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

const loading = ref(false)
const tableData = ref<DictType[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<DictType | null>(null)

const queryParams = reactive({ dictName: '', dictType: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.dictName) count++
  if (queryParams.dictType) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({ id: undefined as number | undefined, dictName: '', dictType: '', status: 1, remark: '' })
const formRules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }]
}

const dataDrawerVisible = ref(false)
const currentDictType = ref('')

const getList = async () => {
  loading.value = true
  try {
    const res = await getDictTypeList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.dictName = ''; queryParams.dictType = ''; queryParams.status = undefined; handleQuery() }

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => { isEdit.value = false; dialogTitle.value = '新增字典'; Object.assign(formData, { id: undefined, dictName: '', dictType: '', status: 1, remark: '' }); dialogVisible.value = true }
const handleEdit = (row: DictType) => {
  cancelSelection()
  isEdit.value = true; dialogTitle.value = '编辑字典'; Object.assign(formData, row); dialogVisible.value = true
}
const handleViewData = (row: DictType) => {
  cancelSelection()
  currentDictType.value = row.dictType; dataDrawerVisible.value = true
}
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateDictType({ id: formData.id!, dictName: formData.dictName, dictType: formData.dictType, status: formData.status, remark: formData.remark })
      ElMessage.success('更新成功')
    } else {
      await addDictType({ dictName: formData.dictName, dictType: formData.dictType, status: formData.status, remark: formData.remark })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}
const handleDelete = async (row: DictType) => {
  try {
    await ElMessageBox.confirm(`确定删除字典 "${row.dictName}"?`, '警告', { type: 'warning' })
    await deleteDictType([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  } catch (e) { }
}
const handleRefreshCache = async () => {
  try {
    await ElMessageBox.confirm('确定刷新字典缓存？', '提示', { type: 'warning' })
    await refreshDictCache()
    ElMessage.success('刷新缓存成功')
  } catch (e) { }
}

// 获取行样式名
const getRowClassName = ({ row }: { row: DictType }) => {
  if (isMobile.value && selectedRow.value?.id === row.id) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: DictType) => {
  if (isMobile.value) {
    selectedRow.value = selectedRow.value?.id === row.id ? null : row
  }
}

// 取消选择
const cancelSelection = () => {
  selectedRow.value = null
}

onMounted(() => getList())
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card { margin-bottom: 15px; }
  .table-card {
    .card-header { display: flex; justify-content: space-between; align-items: center; }
    .el-pagination { margin-top: 15px; justify-content: flex-end; }
  }
}
</style>
