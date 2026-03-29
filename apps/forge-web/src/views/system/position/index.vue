<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="岗位名称">
          <el-input v-model="queryParams.positionName" placeholder="请输入岗位名称" clearable />
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
        <span class="title">岗位列表</span>
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
        <el-form-item label="岗位名称">
          <el-input v-model="queryParams.positionName" placeholder="请输入岗位名称" clearable />
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
          <span v-if="!isMobile">岗位列表</span>
          <div v-if="!isMobile" class="header-btns">
            <el-button v-permission="'system:position:add'" type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增岗位
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
          <el-table-column prop="positionName" label="岗位名称" width="150" />
          <el-table-column prop="positionCode" label="岗位编码" width="150" />
          <el-table-column prop="sortOrder" label="排序" width="80" v-if="!isMobile" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <dict-value :dict-type="DICT_TYPE.SYS_NORMAL_DISABLE" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" v-if="!isMobile">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <!-- 桌面端操作列 -->
          <el-table-column v-if="!isMobile" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'system:position:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button v-permission="'system:position:delete'" type="danger" link @click="handleDelete(row)">删除</el-button>
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
      :item-title="selectedRow?.positionName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button v-permission="'system:position:edit'" size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button v-permission="'system:position:delete'" size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="dialog-form-responsive">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="岗位名称" prop="positionName">
          <el-input v-model="formData.positionName" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="岗位编码" prop="positionCode">
          <el-input v-model="formData.positionCode" placeholder="请输入岗位编码" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
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
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getPositionList, addPosition, updatePosition, deletePosition } from '@/api/system'
import type { Position, PositionRequest } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'
import DictValue from '@/components/DictValue.vue'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_NORMAL_DISABLE)

const loading = ref(false)
const tableData = ref<Position[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<Position | null>(null)

const queryParams = reactive({ positionName: '', status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.positionName) count++
  if (queryParams.status !== undefined) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<PositionRequest>({ positionName: '', positionCode: '', sortOrder: 0, status: 1 })
const formRules: FormRules = {
  positionName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  positionCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getPositionList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.positionName = ''; queryParams.status = undefined; handleQuery() }

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => { isEdit.value = false; dialogTitle.value = '新增岗位'; Object.assign(formData, { positionName: '', positionCode: '', sortOrder: 0, status: 1 }); dialogVisible.value = true }
const handleEdit = (row: Position) => {
  cancelSelection()
  isEdit.value = true; dialogTitle.value = '编辑岗位'; Object.assign(formData, row); dialogVisible.value = true
}
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updatePosition(formData)
      ElMessage.success('更新成功')
    } else {
      await addPosition(formData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}
const handleDelete = async (row: Position) => {
  try {
    await ElMessageBox.confirm(`确定删除岗位 "${row.positionName}"?`, '警告', { type: 'warning' })
    await deletePosition([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  } catch (e) { }
}

// 获取行样式名
const getRowClassName = ({ row }: { row: Position }) => {
  if (isMobile.value && selectedRow.value?.id === row.id) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: Position) => {
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
