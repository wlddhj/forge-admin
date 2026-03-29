<template>
  <el-container class="layout-container" :class="{ 'dark-theme': pageConfigStore.config.theme === 'dark', 'is-mobile': isMobile }">
    <!-- 侧边栏（移动端隐藏） -->
    <el-aside v-show="!isMobile" :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <img src="/vite.svg" alt="logo" />
        <span v-show="!isCollapse">forge-admin</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :unique-opened="true"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          router
        >
          <!-- 首页菜单 -->
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>

          <template v-for="menu in menuList" :key="menu.id || menu.path">
            <!-- 有子菜单 -->
            <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.routePath || menu.path">
              <template #title>
                <el-icon><component :is="menu.icon" /></el-icon>
                <span>{{ menu.menuName || menu.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="child in menu.children.filter((c: any) => c.menuType !== 2)"
                :key="child.id"
                :index="getChildPath(menu.routePath || menu.path, child.routePath)"
              >
                <el-icon><component :is="child.icon" /></el-icon>
                <span>{{ child.menuName }}</span>
              </el-menu-item>
            </el-sub-menu>
            <!-- 无子菜单 -->
            <el-menu-item v-else :index="menu.routePath || menu.path">
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.menuName || menu.meta?.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="layout-main">
      <!-- 头部 -->
      <el-header class="layout-header">
        <div class="header-left">
          <!-- 汉堡菜单按钮（仅移动端显示） -->
          <el-icon v-if="isMobile" class="menu-btn" @click="mobileMenuVisible = true">
            <Menu />
          </el-icon>
          <!-- 折叠按钮（仅桌面端显示） -->
          <el-icon v-else class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb v-if="pageConfigStore.config.showBreadcrumb && !isMobile" separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta?.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- 通知铃铛 -->
          <el-tooltip content="通知" placement="bottom">
            <el-badge :value="wsUnreadCount" :hidden="wsUnreadCount === 0" :max="99">
              <el-icon class="header-icon" @click="notificationVisible = !notificationVisible">
                <Bell />
              </el-icon>
            </el-badge>
          </el-tooltip>

          <!-- 主题切换 -->
          <el-tooltip :content="pageConfigStore.config.theme === 'light' ? '切换暗黑模式' : '切换明亮模式'" placement="bottom">
            <el-icon class="header-icon" @click="pageConfigStore.toggleTheme()">
              <Sunny v-if="pageConfigStore.config.theme === 'light'" />
              <Moon v-else />
            </el-icon>
          </el-tooltip>

          <!-- 设置按钮 -->
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

      <!-- 标签页（移动端自动隐藏） -->
      <TabsView v-if="shouldShowTabs" />

      <!-- 主内容区 -->
      <el-main class="layout-content">
        <router-view v-slot="{ Component }">
          <transition :name="pageConfigStore.config.showPageTransition ? 'fade' : ''" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- 移动端菜单 -->
    <MobileMenu v-model="mobileMenuVisible" />

    <!-- 设置面板 -->
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
import { HomeFilled, Sunny, Moon, Setting, Menu, Fold, Expand, Bell } from '@element-plus/icons-vue'
import TabsView from '@/components/TabsView.vue'
import SettingsPanel from '@/components/SettingsPanel.vue'
import MobileMenu from '@/components/MobileMenu.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()
const pageConfigStore = usePageConfigStore()
const { isMobile } = useResponsive()
const { connect: wsConnect, disconnect: wsDisconnect, unreadCount: wsUnreadCount } = useWebSocket()
const notificationVisible = ref(false)

const isCollapse = ref(false)
const mobileMenuVisible = ref(false)

// 当前激活菜单
const activeMenu = computed(() => route.path)

// 面包屑
const breadcrumbs = computed(() => {
  return route.matched.filter(item => item.meta?.title)
})

// 是否显示标签页
const shouldShowTabs = computed(() => {
  // 移动端且配置了自动隐藏时，不显示标签页
  if (isMobile.value && pageConfigStore.config.autoHideTabsOnMobile) {
    return false
  }
  return pageConfigStore.config.showTabs
})

// 菜单列表（优先从后端菜单数据获取，否则从路由获取）
const menuList = computed(() => {
  const menus = userStore.menus
  if (menus && menus.length > 0) {
    return menus.filter((item: any) => item.visible !== 0 && item.menuType !== 2)
  }
  return permissionStore.routes
    .find((r: any) => r.path === '/')?.children
    ?.filter((item: any) => !item.meta?.hidden) || []
})

// 获取子菜单完整路径
const getChildPath = (parentPath: string, childPath: string) => {
  if (childPath.startsWith('/')) {
    return childPath
  }
  return `${parentPath}/${childPath}`
}

// 下拉菜单命令
const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await userStore.logoutAction()
    } catch (e) {
      console.error('退出失败', e)
    } finally {
      permissionStore.resetRoutes()
      tabsStore.closeAllTabs()
      resetRouter()
      router.push('/login')
    }
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

// 监听路由变化，自动添加标签页
watch(
  () => route.path,
  (path) => {
    if (path && route.meta?.title && shouldShowTabs.value) {
      // 检查是否超过最大标签数
      if (tabsStore.tabs.length >= pageConfigStore.config.maxTabsCount) {
        // 找到第一个可关闭的标签（非首页）
        const closableTab = tabsStore.tabs.find(t => t.closable)
        if (closableTab) {
          tabsStore.removeTab(closableTab.path)
        }
      }

      tabsStore.addTab({
        path,
        title: route.meta.title as string,
        icon: route.meta.icon as string,
        closable: path !== '/dashboard' // 首页不可关闭
      })
    }
  },
  { immediate: true }
)

// WebSocket 通知连接
onMounted(() => {
  if (userStore.token) {
    wsConnect()
  }
})

onUnmounted(() => {
  wsDisconnect()
})

// 监听登录状态变化
watch(() => userStore.token, (newToken) => {
  if (newToken) {
    wsConnect()
  } else {
    wsDisconnect()
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/responsive.scss' as *;

.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #304156;
  transition: width 0.3s;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: #fff;
    font-size: 18px;
    font-weight: bold;

    img {
      width: 32px;
      height: 32px;
    }
  }

  .el-menu {
    border-right: none;
  }
}

.layout-main {
  display: flex;
  flex-direction: column;
}

.layout-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 20px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 15px;

    .menu-btn,
    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .header-icon {
      font-size: 20px;
      cursor: pointer;
      color: #606266;

      &:hover {
        color: #409eff;
      }
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;

      .username {
        color: #333;
      }
    }
  }
}

.layout-content {
  background: #f0f2f5;
  padding: 20px;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// 暗黑主题
.dark-theme {
  .layout-header {
    background: #1f1f1f;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);

    .header-left {
      .menu-btn,
      .collapse-btn {
        color: #e5eaf3;
      }
    }

    .header-right {
      .header-icon {
        color: #e5eaf3;

        &:hover {
          color: #409eff;
        }
      }

      .username {
        color: #e5eaf3;
      }
    }

    :deep(.el-breadcrumb__inner) {
      color: #a3a6ad;
    }

    :deep(.el-breadcrumb__separator) {
      color: #a3a6ad;
    }
  }

  .layout-content {
    background: #141414;
  }
}

// 移动端适配
.is-mobile {
  .layout-header {
    padding: 0 15px;

    .header-left {
      gap: 10px;
    }

    .header-right {
      gap: 12px;
    }
  }

  .layout-content {
    padding: 15px;
  }
}

// 响应式断点
@include mobile {
  .layout-header {
    height: 56px;

    .header-left {
      .menu-btn {
        font-size: 22px;
      }
    }

    .header-right {
      .header-icon {
        font-size: 18px;
      }

      .user-info {
        .el-avatar {
          width: 28px;
          height: 28px;
        }
      }
    }
  }

  .layout-content {
    padding: 12px;
  }
}

@include tablet {
  .layout-aside {
    width: 200px !important;

    .logo {
      font-size: 16px;

      img {
        width: 28px;
        height: 28px;
      }
    }
  }
}
</style>
