<template>
  <div class="app-container">
    <el-card shadow="never" class="table-card">
      <!-- vxe-toolbar 工具栏（桌面端） -->
      <vxe-toolbar v-if="!isMobile" ref="toolbarRef" custom>
        <template #buttons>
          <el-button @click="getList">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </template>
        <template #tools>
          <vxe-button circle icon="vxe-icon-repeat" style="margin-right: 10px" @click="getList"></vxe-button>
        </template>
      </vxe-toolbar>

      <!-- 移动端头部 -->
      <div v-if="isMobile" class="mobile-header">
        <span class="title">在线用户</span>
        <el-button @click="getList" :icon="Refresh" size="small">刷新</el-button>
      </div>

      <!-- vxe-table 表格 -->
      <vxe-table
        ref="tableRef"
        id="sysOnlineUserTable"
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
        <vxe-column v-if="!isMobile" type="seq" title="序号" width="60" :seq-method="seqMethod" />

        <!-- 会话ID（桌面端） -->
        <vxe-column v-if="!isMobile" field="tokenId" title="会话ID" min-width="200" />

        <!-- 用户名 -->
        <vxe-column field="username" title="用户名" width="100" />

        <!-- 昵称（桌面端） -->
        <vxe-column v-if="!isMobile" field="nickname" title="昵称" width="100" />

        <!-- 状态 -->
        <vxe-column title="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 'online' ? 'success' : 'warning'" size="small">
              {{ row.status === 'online' ? '在线' : '闲置' }}
            </el-tag>
          </template>
        </vxe-column>

        <!-- 登录IP -->
        <vxe-column field="loginIp" title="登录IP" width="130" />

        <!-- 登录地点（桌面端） -->
        <vxe-column v-if="!isMobile" field="loginLocation" title="登录地点" width="120" />

        <!-- 浏览器（桌面端） -->
        <vxe-column v-if="!isMobile" field="browser" title="浏览器" width="100" />

        <!-- 操作系统（桌面端） -->
        <vxe-column v-if="!isMobile" field="os" title="操作系统" width="100" />

        <!-- 登录时间（桌面端） -->
        <vxe-column v-if="!isMobile" field="loginTime" title="登录时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.loginTime) }}</template>
        </vxe-column>

        <!-- 剩余有效期 -->
        <vxe-column title="剩余有效期" width="130">
          <template #default="{ row }">{{ formatTTL(row.ttl) }}</template>
        </vxe-column>

        <!-- 桌面端操作列 -->
        <vxe-column v-if="!isMobile" title="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click.stop="handleForceLogout(row)">强制下线</el-button>
          </template>
        </vxe-column>
      </vxe-table>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance, VxeToolbarInstance } from 'vxe-table'
import { Refresh } from '@element-plus/icons-vue'
import { getOnlineUsers, forceLogout, type OnlineUser } from '@/api/system'
import { formatDateTime } from '@/utils/dateFormat'
import { useResponsive } from '@/composables/useResponsive'
import { useTableHeight } from '@/composables/useTableHeight'
import { useTableSeq } from '@/composables/useTableSeq'
import MobileBottomActions from '@/components/MobileBottomActions.vue'

const { isMobile } = useResponsive()

// 表格高度自适应
const { tableHeight } = useTableHeight()

// 表格实例
const tableRef = ref<VxeTableInstance | null>(null)
const toolbarRef = ref<VxeToolbarInstance | null>(null)

const loading = ref(false)
const tableData = ref<OnlineUser[]>([])

// 移动端状态
const selectedRow = ref<OnlineUser | null>(null)

// 序号计算（虽然不分页，但保持一致性）
const pageNumRef = computed(() => 1)
const pageSizeRef = computed(() => tableData.value.length)
const { seqMethod } = useTableSeq({ currentPage: pageNumRef, pageSize: pageSizeRef })

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

// 当前行变化（移动端选中）
const handleCurrentChange = ({ row }: { row: OnlineUser | null }) => {
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

// 关联工具栏与表格
onMounted(() => {
  if (tableRef.value && toolbarRef.value) {
    tableRef.value.connect(toolbarRef.value)
  }
})

onMounted(() => getList())
</script>

<style scoped lang="scss">
.app-container {
  padding: 0;

  .table-card {
    .mobile-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 15px;

      .title {
        font-size: 16px;
        font-weight: 500;
        color: #303133;
      }
    }
  }
}
</style>
