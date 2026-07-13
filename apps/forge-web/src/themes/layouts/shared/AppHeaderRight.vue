<template>
  <div class="header-right">
    <!-- 通知 popover -->
    <el-popover
      :visible="notificationVisible"
      placement="bottom-end"
      :width="360"
      trigger="click"
      @update:visible="(val: boolean) => notificationVisible = val"
    >
      <template #reference>
        <el-badge :value="wsUnreadCount" :hidden="wsUnreadCount === 0" :max="99">
          <el-icon class="header-icon">
            <Bell />
          </el-icon>
        </el-badge>
      </template>
      <div class="notification-panel">
        <div class="notification-header">
          <span class="notification-title">通知</span>
          <div class="notification-actions">
            <el-button v-if="wsNotifications.length > 0" type="primary" link size="small" @click="wsMarkAllRead">全部已读</el-button>
            <el-button type="primary" link size="small" @click="goToNoticePage">查看全部</el-button>
          </div>
        </div>
        <el-scrollbar max-height="320px">
          <div v-if="wsNotifications.length > 0" class="notification-list">
            <div v-for="item in wsNotifications" :key="item.timestamp" class="notification-item">
              <div class="notification-item-title">{{ item.title }}</div>
              <div class="notification-item-content">{{ item.content }}</div>
              <div class="notification-item-time">{{ formatNotificationTime(item.timestamp) }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无通知" :image-size="60" />
        </el-scrollbar>
      </div>
    </el-popover>

    <!-- 主题切换 -->
    <el-tooltip :content="pageConfigStore.config.theme === 'light' ? '切换暗黑模式' : '切换明亮模式'" placement="bottom">
      <el-icon class="header-icon" @click="pageConfigStore.toggleTheme()">
        <Sunny v-if="pageConfigStore.config.theme === 'light'" />
        <Moon v-else />
      </el-icon>
    </el-tooltip>

    <!-- 租户切换（仅平台超管可见） -->
    <TenantSwitcher />

    <!-- 设置按钮 -->
    <el-tooltip content="页面设置" placement="bottom">
      <el-icon class="header-icon" @click="pageConfigStore.openSettings()">
        <Setting />
      </el-icon>
    </el-tooltip>

    <!-- 用户菜单 -->
    <el-dropdown @command="handleCommand">
      <span class="user-info">
        <el-avatar :size="isMobile ? 28 : 32" :src="userStore.userInfo?.avatar">
          {{ userStore.userInfo?.nickname?.charAt(0) }}
        </el-avatar>
        <span v-if="!isMobile" class="username">{{ userStore.userInfo?.nickname }}</span>
        <el-icon v-if="!isMobile"><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">个人中心</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import { useWebSocket } from '@/composables/useWebSocket'
import { resetRouter } from '@/router'
import { Sunny, Moon, Setting, Bell, ArrowDown } from '@element-plus/icons-vue'
import TenantSwitcher from '@/themes/layouts/shared/TenantSwitcher.vue'

const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()
const pageConfigStore = usePageConfigStore()
const { isMobile } = useResponsive()
const {
  connect: wsConnect,
  disconnect: wsDisconnect,
  unreadCount: wsUnreadCount,
  notifications: wsNotifications,
  markAllRead: wsMarkAllRead
} = useWebSocket()
const notificationVisible = ref(false)

// 下拉菜单命令
const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await userStore.logoutAction()
    } catch (e) {
      console.error('退出失败', e)
    } finally {
      permissionStore.resetRoutes()
      tabsStore.clearAllTabs()
      resetRouter()
      router.push('/login')
    }
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

// 通知时间格式化
const formatNotificationTime = (timestamp: number) => {
  const now = Date.now()
  const diff = now - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

// 跳转通知列表页
const goToNoticePage = () => {
  notificationVisible.value = false
  router.push('/system/notice')
}

// WebSocket 通知连接
onMounted(() => {
  if (userStore.token) wsConnect()
})

onUnmounted(() => {
  wsDisconnect()
})

// 监听登录状态变化
watch(() => userStore.token, (newToken) => {
  if (newToken) wsConnect()
  else wsDisconnect()
})
</script>

<style scoped lang="scss">
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;

  .header-icon {
    font-size: 20px;
    cursor: pointer;
    color: var(--el-text-color-regular);

    &:hover {
      color: var(--app-color-primary);
    }
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;

    .username {
      color: var(--el-text-color-primary);
    }
  }
}

// 通知面板
.notification-panel {
  .notification-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    margin-bottom: 8px;

    .notification-title {
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .notification-actions {
      display: flex;
      gap: 8px;
    }
  }

  .notification-list {
    .notification-item {
      padding: 10px 0;
      border-bottom: 1px solid var(--el-border-color-lighter);
      cursor: pointer;
      transition: background 0.3s;

      &:last-child {
        border-bottom: none;
      }

      &:hover {
        background: var(--el-bg-color-page);
      }

      .notification-item-title {
        font-size: 14px;
        font-weight: 500;
        color: var(--el-text-color-primary);
        margin-bottom: 4px;
      }

      .notification-item-content {
        font-size: 13px;
        color: var(--el-text-color-regular);
        line-height: 1.5;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .notification-item-time {
        font-size: 12px;
        color: var(--el-text-color-placeholder);
        margin-top: 4px;
      }
    }
  }
}
</style>
