<template>
  <el-container class="layout-container layout-fullscreen" :class="{ 'is-mobile': isMobile }">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="logo">
          <img :src="brandStore.logoUrl" alt="logo" />
          <span>{{ brandStore.brand.name }}</span>
        </div>
        <el-tooltip content="打开菜单" placement="bottom">
          <button ref="menuBtnRef" type="button" class="menu-trigger" aria-label="打开菜单" @click="menuVisible = true">
            <el-icon :size="16"><Menu /></el-icon>
            <span>菜单</span>
          </button>
        </el-tooltip>
        <el-breadcrumb v-if="pageConfigStore.config.showBreadcrumb && !isMobile" separator="/">
          <el-breadcrumb-item v-for="(item, i) in breadcrumbs" :key="i">
            {{ item.title }}
          </el-breadcrumb-item>
        </el-breadcrumb>
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

    <FullscreenMenu v-model="menuVisible" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useTabsStore } from '@/stores/tabs'
import { usePageConfigStore } from '@/stores/pageConfig'
import { useBrandStore } from '@/stores/brand'
import { useUserStore } from '@/stores/user'
import { useResponsive } from '@/composables/useResponsive'
import { useTabSync } from '@/composables/useTabSync'
import { joinPath } from '@/composables/useFullscreenMenuData'
import { Menu } from '@element-plus/icons-vue'
import TabsView from '@/components/TabsView.vue'
import SettingsPanel from '@/components/SettingsPanel.vue'
import FullscreenMenu from '@/components/FullscreenMenu.vue'
import AppHeaderRight from '@/themes/layouts/shared/AppHeaderRight.vue'
import type { MenuTree } from '@/types/system'

const route = useRoute()
const tabsStore = useTabsStore()
const pageConfigStore = usePageConfigStore()
const brandStore = useBrandStore()
const userStore = useUserStore()
const { isMobile } = useResponsive()

const menuVisible = ref(false)
const menuBtnRef = ref<HTMLButtonElement | null>(null)

function findChainById(menus: MenuTree[], menuId: number, parents: MenuTree[] = []): MenuTree[] {
  for (const m of menus) {
    if (m.id === menuId) return [...parents, m]
    if (m.children?.length) {
      const found = findChainById(m.children, menuId, [...parents, m])
      if (found.length) return found
    }
  }
  return []
}

function findChainByPath(menus: MenuTree[], target: string, parentPath = '', parents: MenuTree[] = []): MenuTree[] {
  for (const m of menus) {
    if (m.menuType === 2 || m.visible === 0) continue
    const full = joinPath(parentPath, m.routePath)
    const chain = [...parents, m]
    if (m.routePath && full === target) return chain
    if (m.children?.length) {
      const found = findChainByPath(m.children, target, full, chain)
      if (found.length) return found
    }
  }
  return []
}

// 路由经 permission store 拍平后 route.matched 丢失目录层级，面包屑需从菜单树构建完整路径
const breadcrumbs = computed(() => {
  const menus = userStore.menus || []
  const menuId = route.meta?.menuId as number | undefined
  let chain = menuId != null ? findChainById(menus, menuId) : []
  if (!chain.length) chain = findChainByPath(menus, route.path)
  if (chain.length) {
    return chain.map(m => ({ title: m.menuName }))
  }
  return route.matched
    .filter(item => item.meta?.title)
    .map(item => ({ title: item.meta?.title as string }))
})

const shouldShowTabs = computed(() => {
  if (isMobile.value && pageConfigStore.config.autoHideTabsOnMobile) return false
  return pageConfigStore.config.showTabs
})

useTabSync({
  route,
  tabsStore,
  shouldShowTabs,
  config: pageConfigStore.config
})

watch(menuVisible, (visible) => {
  if (!visible) menuBtnRef.value?.focus()
})
</script>

<style scoped lang="scss">
@use '@/styles/responsive.scss' as *;

.layout-fullscreen {
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
    gap: 20px;
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
  }
}

.menu-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 7px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--app-radius-base);
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background-color 0.15s;

  &:hover,
  &:focus-visible {
    color: var(--app-color-primary);
    border-color: var(--app-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.layout-content {
  background: var(--el-bg-color-page);
  padding: 10px;
  overflow: auto;
  flex: 1;
}

// 移动端（fullscreen 布局被 BasicLayout 强制切换为 sidebar，这里仅作 fallback）
.is-mobile {
  .layout-header {
    padding: 0 12px;
    height: 50px;
  }
}
</style>
