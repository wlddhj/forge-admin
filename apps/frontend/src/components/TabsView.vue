<template>
  <div class="tabs-view">
    <el-tabs
      v-model="activeTab"
      type="card"
      @tab-click="handleTabClick"
      @tab-remove="handleTabRemove"
    >
      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.path"
        :label="tab.title"
        :name="tab.path"
        :closable="tab.closable"
      >
        <template #label>
          <span
            class="tab-label"
            @contextmenu.prevent.stop="handleContextMenu($event, tab)"
          >
            {{ tab.title }}
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 标签页操作按钮 -->
    <div class="tabs-actions">
      <el-dropdown @command="handleCommand" trigger="click">
        <el-button :icon="ArrowDown" size="small" circle />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="refresh" :icon="Refresh">
              刷新当前
            </el-dropdown-item>
            <el-dropdown-item command="closeOthers" :icon="Close">
              关闭其他
            </el-dropdown-item>
            <el-dropdown-item command="closeLeft" :icon="Back">
              关闭左侧
            </el-dropdown-item>
            <el-dropdown-item command="closeRight" :icon="Right">
              关闭右侧
            </el-dropdown-item>
            <el-dropdown-item command="closeAll" :icon="CloseBold" divided>
              关闭所有
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 右键菜单 -->
    <teleport to="body">
      <div
        v-show="contextMenuVisible"
        class="context-menu"
        :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
      >
        <div class="context-menu-item" @click="handleContextCommand('refresh')">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </div>
        <div
          class="context-menu-item"
          :class="{ disabled: !selectedTab?.closable }"
          @click="handleContextCommand('close')"
        >
          <el-icon><Close /></el-icon>
          <span>关闭</span>
        </div>
        <div class="context-menu-item" @click="handleContextCommand('closeOthers')">
          <el-icon><Close /></el-icon>
          <span>关闭其他</span>
        </div>
        <div class="context-menu-item" @click="handleContextCommand('closeLeft')">
          <el-icon><Back /></el-icon>
          <span>关闭左侧</span>
        </div>
        <div class="context-menu-item" @click="handleContextCommand('closeRight')">
          <el-icon><Right /></el-icon>
          <span>关闭右侧</span>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ArrowDown,
  Refresh,
  Close,
  CloseBold,
  Back,
  Right
} from '@element-plus/icons-vue'
import { useTabsStore } from '@/stores/tabs'
import type { TabItem } from '@/stores/tabs'

const router = useRouter()
const route = useRoute()
const tabsStore = useTabsStore()

// 右键菜单状态
const contextMenuVisible = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const selectedTab = ref<TabItem | null>(null)

// 当前激活的标签页
const activeTab = computed({
  get: () => tabsStore.activeTab,
  set: (val) => {
    tabsStore.setActiveTab(val)
  }
})

// 标签页列表
const tabs = computed(() => tabsStore.tabs)

// 点击标签页
const handleTabClick = (tab: any) => {
  const path = tab.paneName as string
  if (path !== route.path) {
    router.push(path)
  }
}

// 移除标签页
const handleTabRemove = (path: string) => {
  tabsStore.removeTab(path)

  // 如果移除的是当前路由，跳转到激活的标签页
  if (path === route.path && tabsStore.activeTab) {
    router.push(tabsStore.activeTab)
  } else if (!tabsStore.activeTab && tabs.value.length > 0) {
    // 如果没有激活的标签页，跳转到最后一个
    router.push(tabs.value[tabs.value.length - 1].path)
  }
}

// 右键菜单
const handleContextMenu = (event: MouseEvent, tab: TabItem) => {
  event.preventDefault()
  event.stopPropagation()
  selectedTab.value = tab
  contextMenuPosition.value = { x: event.clientX, y: event.clientY }
  contextMenuVisible.value = true
}

// 点击外部关闭右键菜单
const handleClickOutside = (event: MouseEvent) => {
  if (contextMenuVisible.value) {
    contextMenuVisible.value = false
  }
}

// 右键菜单命令处理
const handleContextCommand = (command: string) => {
  if (!selectedTab.value) return

  const tabPath = selectedTab.value.path

  switch (command) {
    case 'refresh':
      if (tabPath === route.path) {
        router.go(0)
      } else {
        router.push(tabPath)
        setTimeout(() => router.go(0), 100)
      }
      break
    case 'close':
      if (selectedTab.value.closable) {
        tabsStore.removeTab(tabPath)
        if (tabPath === route.path && tabsStore.activeTab) {
          router.push(tabsStore.activeTab)
        }
      }
      break
    case 'closeOthers':
      tabsStore.closeOtherTabs(tabPath)
      if (route.path !== tabPath) {
        router.push(tabPath)
      }
      break
    case 'closeLeft':
      tabsStore.closeLeftTabs(tabPath)
      break
    case 'closeRight':
      tabsStore.closeRightTabs(tabPath)
      break
  }

  contextMenuVisible.value = false
}

// 操作菜单命令处理
const handleCommand = (command: string) => {
  const currentPath = route.path

  switch (command) {
    case 'refresh':
      // 刷新当前页面
      router.go(0)
      break
    case 'closeOthers':
      tabsStore.closeOtherTabs(currentPath)
      break
    case 'closeLeft':
      tabsStore.closeLeftTabs(currentPath)
      break
    case 'closeRight':
      tabsStore.closeRightTabs(currentPath)
      break
    case 'closeAll':
      tabsStore.closeAllTabs()
      // 关闭所有后跳转到首页
      if (tabsStore.activeTab) {
        router.push(tabsStore.activeTab)
      } else {
        router.push('/dashboard')
      }
      break
  }
}

// 监听点击事件关闭右键菜单
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('contextmenu', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('contextmenu', handleClickOutside)
})
</script>

<style scoped lang="scss">
.tabs-view {
  position: relative;
  display: flex;
  align-items: center;
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 10px;
  gap: 8px;

  :deep(.el-tabs) {
    flex: 1;
    min-width: 0;
    overflow: hidden;

    .el-tabs__header {
      margin: 0;
      border-bottom: none;
      padding: 6px 0 0 0;
    }

    .el-tabs__nav-wrap {
      &::after {
        display: none;
      }

      &.is-scrollable {
        padding: 0 20px 0 0;
      }
    }

    .el-tabs__nav-scroll {
      overflow-x: auto;
      overflow-y: hidden;

      &::-webkit-scrollbar {
        height: 4px;
      }

      &::-webkit-scrollbar-thumb {
        background-color: #dcdfe6;
        border-radius: 3px;

        &:hover {
          background-color: #c0c4cc;
        }
      }

      &::-webkit-scrollbar-track {
        background-color: transparent;
      }
    }

    .el-tabs__nav {
      border: none;
      display: inline-flex;
      flex-wrap: nowrap;
    }

    .el-tabs__item {
      height: 34px;
      line-height: 34px;
      padding: 0 16px;
      font-size: 13px;
      color: #606266;
      border: 1px solid #d8dce5;
      border-radius: 3px 3px 0 0;
      margin-right: 4px;
      background-color: #f5f7fa;
      transition: all 0.3s;
      position: relative;
      top: 1px;

      &:hover {
        color: #409eff;
        background-color: #fff;
        border-color: #409eff;
      }

      &.is-active {
        color: #409eff;
        background-color: #fff;
        border-color: #409eff;
        border-bottom-color: #fff;
      }

      .tab-label {
        display: flex;
        align-items: center;
        gap: 4px;
      }

      .el-icon {
        font-size: 12px;
        margin-left: 4px;
        color: #909399;

        &:hover {
          color: #f56c6c;
        }
      }
    }
  }
}

.tabs-actions {
  flex-shrink: 0;
  padding: 6px 0 0 0;

  :deep(.el-button) {
    background-color: #f5f7fa;
    border-color: #dcdfe6;

    &:hover {
      background-color: #ecf5ff;
      border-color: #409eff;
      color: #409eff;
    }
  }
}

// 右键菜单样式
.context-menu {
  position: fixed;
  z-index: 9999;
  background-color: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  padding: 5px 0;
  min-width: 120px;

  .context-menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    font-size: 14px;
    color: #606266;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background-color: #ecf5ff;
      color: #409eff;
    }

    &.disabled {
      color: #c0c4cc;
      cursor: not-allowed;

      &:hover {
        background-color: transparent;
        color: #c0c4cc;
      }
    }

    .el-icon {
      font-size: 16px;
    }
  }
}
</style>
