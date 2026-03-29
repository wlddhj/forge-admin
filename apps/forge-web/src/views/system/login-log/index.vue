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
      <template #header>
        <div class="card-header">
          <span v-if="!isMobile">登录日志列表</span>
          <el-button v-if="!isMobile" type="danger" @click="handleClear">清空日志</el-button>
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
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="loginIp" label="登录IP" width="140" />
          <el-table-column prop="loginLocation" label="登录地点" width="150" v-if="!isMobile" />
          <el-table-column prop="browser" label="浏览器" width="120" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="os" label="操作系统" width="120" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <dict-value :dict-type="DICT_TYPE.SYS_SUCCESS_FAIL" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="msg" label="提示消息" min-width="150" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="loginTime" label="登录时间" width="180" v-if="!isMobile">
            <template #default="{ row }">{{ formatDateTime(row.loginTime) }}</template>
          </el-table-column>
        </el-table>
      </div>

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
import { getLoginLogList, clearLoginLogs } from '@/api/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useDict } from '@/composables/useDict'
import { DICT_TYPE } from '@/constants/dict'
import MobileSearchDrawer from '@/components/MobileSearchDrawer.vue'
import MobileSearchButton from '@/components/MobileSearchButton.vue'
import DictValue from '@/components/DictValue.vue'

const { isMobile } = useResponsive()
const { dictData: statusOptions } = useDict(DICT_TYPE.SYS_SUCCESS_FAIL)

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

// 计算激活的搜索条件数量
const activeConditionsCount = computed(() => {
  let count = 0
  if (queryParams.username) count++
  if (queryParams.loginIp) count++
  if (queryParams.status !== undefined) count++
  return count
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

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有登录日志吗？此操作不可恢复！', '警告', { type: 'warning' })
    await clearLoginLogs()
    ElMessage.success('清空成功')
    getList()
  } catch (e) {}
}

// 获取行样式名
const getRowClassName = ({ row }: { row: LoginLog }) => {
  if (isMobile.value && selectedRow.value?.id === row.id) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: LoginLog) => {
  if (isMobile.value) {
    selectedRow.value = selectedRow.value?.id === row.id ? null : row
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
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .el-pagination {
      margin-top: 15px;
      justify-content: flex-end;
    }
  }
}
</style>
