<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <!-- 桌面端搜索表单 -->
      <el-form v-if="!isMobile" :model="queryParams" inline>
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="登录IP">
          <el-input v-model="queryParams.loginIp" placeholder="请输入登录IP" clearable />
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
        <span class="title">登录日志</span>
        <div class="actions">
          <MobileSearchButton :badge-count="activeConditionsCount" @click="searchDrawerVisible = true" />
          <el-button type="danger" @click="handleClear">清空</el-button>
        </div>
      </div>
    </el-card>

    <!-- 移动端搜索抽屉 -->
    <MobileSearchDrawer v-model="searchDrawerVisible" :form-data="queryParams" @search="handleSearchFromDrawer" @reset="handleResetFromDrawer">
      <template #form-items>
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="登录IP">
          <el-input v-model="queryParams.loginIp" placeholder="请输入登录IP" clearable />
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
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom >
        <template #buttons>
          <el-button type="success" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
          <el-button type="danger" @click="handleClear">
            <el-icon><Delete /></el-icon>
            清空日志
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="handleReset"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysLoginLogTable"
        :custom-config="{mode: 'modal'}"
        :data="tableData"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isCurrent: true, isHover: true }"
        :checkbox-config="{ highlight: true, range: true }"
        :column-config="{ resizable: true }"
        border="none"
        stripe
        show-overflow="tooltip"
        show-header-overflow="tooltip"
        @current-change="handleCurrentChange"
      >
        <!-- 序号列（桌面端） -->
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />

        <!-- 用户名 -->
        <vxe-column field="username" title="用户名" width="120" />

        <!-- 登录IP -->
        <vxe-column field="loginIp" title="登录IP" width="140" />

        <!-- 登录地点（桌面端） -->
        <vxe-column v-if="!isMobile" field="loginLocation" title="登录地点" width="150" />

        <!-- 浏览器（桌面端） -->
        <vxe-column v-if="!isMobile" field="browser" title="浏览器" width="120" />

        <!-- 操作系统（桌面端） -->
        <vxe-column v-if="!isMobile" field="os" title="操作系统" width="120" />

        <!-- 状态 -->
        <vxe-column title="状态" width="80">
          <template #default="{ row }">
            <dict-value :dict-type="DICT_TYPE.SYS_SUCCESS_FAIL" :value="row.status" />
          </template>
        </vxe-column>

        <!-- 提示消息（桌面端） -->
        <vxe-column v-if="!isMobile" field="msg" title="提示消息" min-width="150" />

        <!-- 登录时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="loginTime" title="登录时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.loginTime) }}
          </template>
        </vxe-column>
      </vxe-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { getLoginLogList, clearLoginLogs, exportLoginLogs } from '@/api/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import DictValue from '@/components/DictValue.vue'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_SUCCESS_FAIL)

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

interface LoginLog {
  id: number
  username: string
  loginIp: string
  loginLocation: string
  browser: string
  os: string
  status: number
  msg: string
  loginTime: string
}

const loading = ref(false)
const tableData = ref<LoginLog[]>([])
const total = ref(0)

// 移动端状态
const searchDrawerVisible = ref(false)
const selectedRow = ref<LoginLog | null>(null)

const queryParams = reactive({
  username: '',
  loginIp: '',
  status: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})

// 序号计算
const pageNumRef = computed(() => queryParams.pageNum)
const pageSizeRef = computed(() => queryParams.pageSize)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.username) count++
  if (queryParams.loginIp) count++
  if (queryParams.status !== undefined) count++
  return count
})

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getLoginLogList(queryParams)
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
  queryParams.username = ''
  queryParams.loginIp = ''
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

const handleExport = async () => {
  try {
    await exportLoginLogs(queryParams)
    ElMessage.success('导出成功')
  } catch (e) {}
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有登录日志吗？此操作不可恢复！', '警告', { type: 'warning' })
    await clearLoginLogs()
    ElMessage.success('清空成功')
    getList()
  } catch (e) {}
}

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: LoginLog | null }) => {
  if (isMobile.value) {
    selectedRow.value = row
  }
}

onMounted(() => getList())
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .search-card {
    margin-bottom: 15px;
  }

  .table-card {
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}
</style>