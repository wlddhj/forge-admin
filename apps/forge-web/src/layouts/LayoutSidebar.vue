<template>
  <el-container class="layout-container" :class="{ 'is-mobile': isMobile }">
    <!-- 侧边栏（移动端隐藏） -->
    <el-aside v-show="!isMobile" :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <img :src="brandStore.logoUrl" alt="logo" />
        <span v-show="!isCollapse">{{ brandStore.brand.name }}</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :unique-opened="true"
          background-color="var(--app-sidebar-bg)"
          text-color="var(--app-sidebar-text)"
          active-text-color="var(--app-color-primary)"
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
            <!-- 无子菜单 -->
            <el-menu-item v-else :index="menu.routePath || menu.path">
              <IconPreview v-if="menu.icon" :icon="menu.icon" :size="18" />
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
        <AppHeaderRight />
      </el-header>

      <!-- 标签页（移动端自动隐藏） -->
      <TabsView v-if="shouldShowTabs" />

      <!-- 主内容区 -->
      <el-main class="layout-content">
        <router-view v-slot="{ Component }">
          <keep-alive v-if="pageConfigStore.config.keepAlive" :include="tabsStore.cachedViews">
            <component :is="Component" :key="$route.path" />
          </keep-alive>
          <component v-else :is="Component" />
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
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useBrandStore } from '@/stores/brand'
import { useResponsive } from '@/composables/useResponsive'
import { HomeFilled, Menu, Fold, Expand } from '@element-plus/icons-vue'
import TabsView from '@/components/TabsView.vue'
import SettingsPanel from '@/components/SettingsPanel.vue'
import MobileMenu from '@/components/MobileMenu.vue'
import IconPreview from '@/components/IconPreview.vue'
import AppHeaderRight from '@/themes/layouts/shared/AppHeaderRight.vue'

const route = useRoute()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()
const pageConfigStore = usePageConfigStore()
const { isMobile } = useResponsive()

const isCollapse = ref(false)

const brandStore = useBrandStore()
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
    return menus
      .filter((item: any) => item.visible !== 0 && item.menuType !== 2)
      .map((item: any) => {
        // 过滤子菜单中的按钮类型
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

// 获取子菜单完整路径
const getChildPath = (parentPath: string, childPath: string) => {
  if (childPath.startsWith('/')) {
    return childPath
  }
  return `${parentPath}/${childPath}`
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
        closable: path !== '/dashboard', // 首页不可关闭
        routeName: route.name as string // 用于 keep-alive 缓存匹配
      })
    }
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
@use '@/styles/responsive.scss' as *;

.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: var(--app-sidebar-bg);
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
  height: 100vh;

  .logo {
    height: 60px;
    flex-shrink: 0;
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

  .el-scrollbar {
    flex: 1;
    overflow: hidden;
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
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--el-bg-color);
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
      color: var(--el-text-color-primary);
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
  }
}

.layout-content {
  background: var(--el-bg-color-page);
  padding: 10px;
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

// 暗黑主题（已由 EP dark/css-vars + 全局样式接管）

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
