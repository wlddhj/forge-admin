<template>
  <el-container class="layout-container layout-top" :class="{ 'is-mobile': isMobile }">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="logo">
          <img :src="brandStore.logoUrl" alt="logo" />
          <span>{{ brandStore.brand.name }}</span>
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
      <AppHeaderRight />
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
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useBrandStore } from '@/stores/brand'
import { useResponsive } from '@/composables/useResponsive'
import { HomeFilled } from '@element-plus/icons-vue'
import TabsView from '@/components/TabsView.vue'
import SettingsPanel from '@/components/SettingsPanel.vue'
import IconPreview from '@/components/IconPreview.vue'
import AppHeaderRight from '@/themes/layouts/shared/AppHeaderRight.vue'

const route = useRoute()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()
const pageConfigStore = usePageConfigStore()
const { isMobile } = useResponsive()

const brandStore = useBrandStore()

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
  }
}

.layout-content {
  background: var(--el-bg-color-page);
  padding: 10px;
  overflow: auto;
  flex: 1;
}

// 移动端（顶栏在移动端被 BasicLayout 强制切换为 sidebar，所以这里仅作 fallback）
.is-mobile {
  .layout-header {
    padding: 0 12px;
    height: 50px;
  }
}
</style>
