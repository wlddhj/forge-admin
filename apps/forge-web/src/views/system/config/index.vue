<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="配置名称">
          <el-input v-model="queryParams.configName" placeholder="请输入配置名称" clearable />
        </el-form-item>
        <el-form-item label="配置键">
          <el-input v-model="queryParams.configKey" placeholder="请输入配置键" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 移动端搜索按钮 -->
      <div v-else class="mobile-search-actions">
        <span class="title">系统配置</span>
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
        <el-form-item label="配置名称">
          <el-input v-model="queryParams.configName" placeholder="请输入配置名称" clearable />
        </el-form-item>
        <el-form-item label="配置键">
          <el-input v-model="queryParams.configKey" placeholder="请输入配置键" clearable />
        </el-form-item>
      </template>
    </MobileSearchDrawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span v-if="!isMobile">系统配置列表</span>
          <div v-if="!isMobile" class="header-btns">
            <el-button v-permission="'system:config:add'" type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增配置
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
          <el-table-column prop="configName" label="配置名称" width="150" />
          <el-table-column prop="configKey" label="配置键" width="220" />
          <el-table-column prop="configValue" label="配置值" min-width="150" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="configGroup" label="分组" width="100" v-if="!isMobile" />
          <el-table-column prop="createTime" label="创建时间" width="180" v-if="!isMobile">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <!-- 桌面端操作列 -->
          <el-table-column v-if="!isMobile" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'system:config:edit'" type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button v-permission="'system:config:delete'" type="danger" link @click="handleDelete(row)" :disabled="row.isSystem === 1">删除</el-button>
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
      :item-title="selectedRow?.configName"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button v-permission="'system:config:edit'" size="small" type="primary" @click.stop="handleEdit(item)">编辑</el-button>
        <el-button v-permission="'system:config:delete'" size="small" type="danger" @click.stop="handleDelete(item)" :disabled="item.isSystem === 1">删除</el-button>
      </template>
    </MobileBottomActions>

    <!-- 对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" class="dialog-form-responsive">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="formData.configName" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="formData.configKey" placeholder="请输入配置键" />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="formData.configValue" type="textarea" :rows="2" placeholder="请输入配置值" />
        </el-form-item>
        <el-form-item label="配置类型">
          <el-select v-model="formData.configType" style="width: 100%">
            <el-option
              v-for="item in configTypeOptions"
              :key="item.dictValue"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="配置分组">
          <el-input v-model="formData.configGroup" placeholder="请输入配置分组" />
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
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getConfigList, addConfig, updateConfig, deleteConfig } from '@/api/system'
import type { Config, ConfigRequest } from '@/types/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const { isMobile } = useResponsive()
const { dictData: configTypeOptions } = useDict(DICT_TYPE.SYS_CONFIG_TYPE)

const loading = ref(false)
const tableData = ref<Config[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<Config | null>(null)

const queryParams = reactive({ configName: '', configKey: '', pageNum: 1, pageSize: 10 })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.configName) count++
  if (queryParams.configKey) count++
  return count
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<ConfigRequest>({ configName: '', configKey: '', configValue: '', configType: 'text', configGroup: 'system', remark: '' })
const formRules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getConfigList(queryParams)
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => { queryParams.configName = ''; queryParams.configKey = ''; handleQuery() }

// 移动端抽屉搜索
const handleSearchFromDrawer = () => {
  queryParams.pageNum = 1
  getList()
}

// 移动端抽屉重置
const handleResetFromDrawer = () => {
  handleReset()
}

const handleAdd = () => { isEdit.value = false; dialogTitle.value = '新增配置'; Object.assign(formData, { id: undefined, configName: '', configKey: '', configValue: '', configType: 'text', configGroup: 'system', remark: '' }); dialogVisible.value = true }
const handleEdit = (row: Config) => {
  cancelSelection()
  isEdit.value = true; dialogTitle.value = '编辑配置'; Object.assign(formData, row); dialogVisible.value = true
}
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateConfig({ id: formData.id, configName: formData.configName, configKey: formData.configKey, configValue: formData.configValue, configType: formData.configType, configGroup: formData.configGroup, remark: formData.remark })
      ElMessage.success('更新成功')
    } else {
      await addConfig({ configName: formData.configName, configKey: formData.configKey, configValue: formData.configValue, configType: formData.configType, configGroup: formData.configGroup, remark: formData.remark })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}
const handleDelete = async (row: Config) => {
  try {
    await ElMessageBox.confirm(`确定删除配置 "${row.configName}"?`, '警告', { type: 'warning' })
    await deleteConfig([row.id])
    ElMessage.success('删除成功')
    cancelSelection()
    getList()
  } catch (e) {}
}

// 获取行样式名
const getRowClassName = ({ row }: { row: Config }) => {
  if (isMobile.value && selectedRow.value?.id === row.id) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: Config) => {
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
