<template>
  <el-container class="layout-container layout-top" :class="{ 'is-mobile': isMobile }">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="logo">
          <img src="/logo.svg" alt="logo" />
          <span>{{ appTitle }}</span>
        </div>
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          :ellipsis="false"
          background-color="transparent"
          text-color="var(--el-text-color-primary)"
          active-text-color="var(--app-color-primary)"
          router
        >
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>
          <template v-for="menu in topMenuList" :key="menu.id || menu.path">
            <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.routePath || menu.path">
              <template #title>
                <IconPreview v-if="menu.icon" :icon="menu.icon" :size="18" />
                <span>{{ menu.menuName || menu.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="child in menu.children.filter((c: any) => c.menuType !== 2)"
                :key="child.id"
                :index="getChildPath(menu.routePath || menu.path, child.routePath)"
              >
                <IconPreview v-if="child.icon" :icon="child.icon" :size="18" />
                <span>{{ child.menuName }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.routePath || menu.path">
              <IconPreview v-if="menu.icon" :icon="menu.icon" :size="18" />
              <span>{{ menu.menuName || menu.meta?.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
      <div class="header-right">
        <el-popover
          :visible="notificationVisible"
          placement="bottom-end"
          :width="360"
          trigger="click"
          @update:visible="(val: boolean) => notificationVisible = val"
        >
          <template #reference>
            <el-badge :value="wsUnreadCount" :hidden="wsUnreadCount === 0" :max="99">
              <el-icon class="header-icon"><Bell /></el-icon>
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

        <el-tooltip :content="pageConfigStore.config.theme === 'light' ? '切换暗黑模式' : '切换明亮模式'" placement="bottom">
          <el-icon class="header-icon" @click="pageConfigStore.toggleTheme()">
            <Sunny v-if="pageConfigStore.config.theme === 'light'" />
            <Moon v-else />
          </el-icon>
        </el-tooltip>

        <el-tooltip content="页面设置" placement="bottom">
          <el-icon class="header-icon" @click="pageConfigStore.openSettings()">
            <Setting />
          </el-icon>
        </el-tooltip>

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
    </el-header>

    <TabsView v-if="shouldShowTabs" />

    <el-main class="layout-content">
      <router-view v-slot="{ Component }">
        <keep-alive v-if="pageConfigStore.config.keepAlive" :include="tabsStore.cachedViews">
          <component :is="Component" :key="$route.path" />
        </keep-alive>
        <component v-else :is="Component" />
      </router-view>
    </el-main>

    <SettingsPanel />
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useResponsive } from '@/composables/useResponsive'
import { useWebSocket } from '@/composables/useWebSocket'
import { resetRouter } from '@/router'
import { HomeFilled, Sunny, Moon, Setting, Bell, ArrowDown } from '@element-plus/icons-vue'
import TabsView from '@/components/TabsView.vue'
import SettingsPanel from '@/components/SettingsPanel.vue'
import IconPreview from '@/components/IconPreview.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()
const pageConfigStore = usePageConfigStore()
const { isMobile } = useResponsive()
const { connect: wsConnect, disconnect: wsDisconnect, unreadCount: wsUnreadCount, notifications: wsNotifications, markAllRead: wsMarkAllRead } = useWebSocket()
const notificationVisible = ref(false)

const appTitle = import.meta.env.VITE_APP_TITLE

const activeMenu = computed(() => route.path)

const shouldShowTabs = computed(() => {
  if (isMobile.value && pageConfigStore.config.autoHideTabsOnMobile) return false
  return pageConfigStore.config.showTabs
})

// 顶栏布局：只显示一级菜单（有子菜单的作为折叠入口）
const topMenuList = computed(() => {
  const menus = userStore.menus
  if (menus && menus.length > 0) {
    return menus
      .filter((item: any) => item.visible !== 0 && item.menuType !== 2)
      .map((item: any) => {
        if (item.children && item.children.length > 0) {
          const filteredChildren = item.children.filter((c: any) => c.menuType !== 2 && c.visible !== 0)
          return { ...item, children: filteredChildren.length > 0 ? filteredChildren : undefined }
        }
        return item
      })
  }
  return permissionStore.routes
    .find((r: any) => r.path === '/')?.children
    ?.filter((item: any) => !item.meta?.hidden) || []
})

const getChildPath = (parentPath: string, childPath: string) => {
  if (childPath.startsWith('/')) return childPath
  return `${parentPath}/${childPath}`
}

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

const formatNotificationTime = (timestamp: number) => {
  const now = Date.now()
  const diff = now - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

const goToNoticePage = () => {
  notificationVisible.value = false
  router.push('/system/notice')
}

watch(
  () => route.path,
  (path) => {
    if (path && route.meta?.title && shouldShowTabs.value) {
      if (tabsStore.tabs.length >= pageConfigStore.config.maxTabsCount) {
        const closableTab = tabsStore.tabs.find(t => t.closable)
        if (closableTab) tabsStore.removeTab(closableTab.path)
      }
      tabsStore.addTab({
        path,
        title: route.meta.title as string,
        icon: route.meta.icon as string,
        closable: path !== '/dashboard',
        routeName: route.name as string
      })
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (userStore.token) wsConnect()
})

onUnmounted(() => {
  wsDisconnect()
})

watch(() => userStore.token, (newToken) => {
  if (newToken) wsConnect()
  else wsDisconnect()
})
</script>

<style scoped lang="scss">
@use '@/styles/responsive.scss' as *;

.layout-top {
  height: 100vh;
  flex-direction: column;
}

.layout-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--app-header-bg);
  box-shadow: var(--app-shadow-card);
  padding: 0 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  .header-left {
    display: flex;
    align-items: center;
    gap: 24px;
    flex: 1;
    min-width: 0;

    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 18px;
      font-weight: bold;
      color: var(--el-text-color-primary);
      flex-shrink: 0;

      img {
        width: 32px;
        height: 32px;
      }
    }

    :deep(.el-menu) {
      flex: 1;
      min-width: 0;
      border-bottom: none;
    }
  }

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
}

.layout-content {
  background: var(--el-bg-color-page);
  padding: 10px;
  overflow: auto;
  flex: 1;
}

// 通知面板（与 LayoutSidebar 一致）
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

// 移动端（顶栏在移动端被 BasicLayout 强制切换为 sidebar，所以这里仅作 fallback）
.is-mobile {
  .layout-header {
    padding: 0 12px;
    height: 50px;
  }
}
</style>
