<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span v-if="!isMobile">在线用户列表</span>
          <div v-else class="header-actions">
            <span class="title">在线用户</span>
            <el-button @click="getList" :icon="Refresh" size="small">刷新</el-button>
          </div>
          <el-button v-if="!isMobile" @click="getList" :icon="Refresh">刷新</el-button>
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
          <el-table-column prop="tokenId" label="会话ID" min-width="200" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="nickname" label="昵称" width="120" v-if="!isMobile" />
          <el-table-column prop="loginIp" label="登录IP" width="130" />
          <el-table-column prop="loginLocation" label="登录地点" width="120" v-if="!isMobile" />
          <el-table-column prop="browser" label="浏览器" width="120" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="os" label="操作系统" width="120" show-overflow-tooltip v-if="!isMobile" />
          <el-table-column prop="loginTime" label="登录时间" width="180" v-if="!isMobile">
            <template #default="{ row }">{{ formatDateTime(row.loginTime) }}</template>
          </el-table-column>
          <el-table-column label="剩余有效期" width="120">
            <template #default="{ row }">{{ formatTTL(row.ttl) }}</template>
          </el-table-column>
          <!-- 桌面端操作列 -->
          <el-table-column v-if="!isMobile" label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link @click="handleForceLogout(row)">强制下线</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 移动端底部操作栏 -->
    <MobileBottomActions
      :show="!!selectedRow"
      :item="selectedRow"
      :item-title="selectedRow?.username"
      @cancel="cancelSelection"
    >
      <template #actions="{ item }">
        <el-button size="small" type="danger" @click.stop="handleForceLogout(item)">强制下线</el-button>
      </template>
    </MobileBottomActions>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getOnlineUsers, forceLogout } from '@/api/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const { isMobile } = useResponsive()

interface OnlineUser {
  tokenId: string
  userId: number
  username: string
  nickname: string
  loginIp: string
  loginLocation: string
  browser: string
  os: string
  loginTime: number
  ttl: number
}

const loading = ref(false)
const tableData = ref<OnlineUser[]>([])

// 移动端状态
const selectedRow = ref<OnlineUser | null>(null)

const getList = async () => {
  loading.value = true
  try {
    const res = await getOnlineUsers()
    tableData.value = res
  } finally {
    loading.value = false
  }
}

const formatTTL = (ttl: number): string => {
  if (ttl <= 0) return '已过期'
  const hours = Math.floor(ttl / 3600)
  const minutes = Math.floor((ttl % 3600) / 60)
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  }
  return `${minutes}分钟`
}

const handleForceLogout = async (row: OnlineUser) => {
  try {
    await ElMessageBox.confirm(`确定要强制用户 "${row.username}" 下线吗？`, '警告', { type: 'warning' })
    await forceLogout(row.tokenId)
    ElMessage.success('操作成功')
    cancelSelection()
    getList()
  } catch (e) {}
}

// 获取行样式名
const getRowClassName = ({ row }: { row: OnlineUser }) => {
  if (isMobile.value && selectedRow.value?.tokenId === row.tokenId) {
    return 'selected-row'
  }
  return ''
}

// 处理行点击（移动端）
const handleRowClick = (row: OnlineUser) => {
  if (isMobile.value) {
    selectedRow.value = selectedRow.value?.tokenId === row.tokenId ? null : row
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

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      width: 100%;

      .title {
        font-size: 16px;
        font-weight: 500;
        color: #303133;
      }
    }
  }
}
</style>
