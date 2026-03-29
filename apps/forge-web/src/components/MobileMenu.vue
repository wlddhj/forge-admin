<template>
  <el-drawer
    v-model="visible"
    direction="ltr"
    :size="280"
    :with-header="false"
    class="mobile-menu-drawer"
  >
    <div class="mobile-menu">
      <!-- Logo -->
      <div class="mobile-menu-header">
        <div class="logo">
          <img src="/vite.svg" alt="logo" />
          <span>forge-admin</span>
        </div>
        <el-icon class="close-btn" @click="visible = false">
          <Close />
        </el-icon>
      </div>

      <!-- 菜单列表 -->
      <el-scrollbar class="menu-scrollbar">
        <el-menu
          :default-active="activeMenu"
          :unique-opened="true"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          router
          @select="handleMenuSelect"
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

      <!-- 用户信息 -->
      <div class="mobile-menu-footer">
        <div class="user-info">
          <el-avatar :size="40" :src="userStore.userInfo?.avatar">
            {{ userStore.userInfo?.nickname?.charAt(0) }}
          </el-avatar>
          <div class="user-detail">
            <div class="username">{{ userStore.userInfo?.nickname }}</div>
            <div class="user-role">{{ userStore.userInfo?.roles?.[0]?.roleName || '用户' }}</div>
          </div>
        </div>
        <el-button type="danger" text @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useTabsStore } from '@/stores/tabs'
import { resetRouter } from '@/router'
import { HomeFilled, Close, SwitchButton } from '@element-plus/icons-vue'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const tabsStore = useTabsStore()

// 显示状态
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 当前激活菜单
const activeMenu = computed(() => route.path)

// 菜单列表
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

// 菜单选择事件
const handleMenuSelect = () => {
  // 移动端选择菜单后关闭抽屉
  visible.value = false
}

// 退出登录
const handleLogout = async () => {
  try {
    await userStore.logoutAction()
  } catch (e) {
    console.error('退出失败', e)
  } finally {
    visible.value = false
    permissionStore.resetRoutes()
    tabsStore.closeAllTabs()
    resetRouter()
    router.push('/login')
  }
}
</script>

<style scoped lang="scss">
.mobile-menu-drawer {
  :deep(.el-drawer__body) {
    padding: 0;
    display: flex;
    flex-direction: column;
  }
}

.mobile-menu {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #304156;

  .mobile-menu-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 15px 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      color: #fff;
      font-size: 18px;
      font-weight: bold;

      img {
        width: 32px;
        height: 32px;
      }
    }

    .close-btn {
      font-size: 22px;
      color: #bfcbd9;
      cursor: pointer;

      &:hover {
        color: #409eff;
      }
    }
  }

  .menu-scrollbar {
    flex: 1;
    overflow: auto;

    :deep(.el-scrollbar__wrap) {
      overflow-x: hidden;
    }
  }

  .el-menu {
    border-right: none;
  }

  .mobile-menu-footer {
    padding: 15px 20px;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
    background-color: rgba(0, 0, 0, 0.1);

    .user-info {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 15px;

      .user-detail {
        flex: 1;

        .username {
          color: #fff;
          font-size: 16px;
          font-weight: 500;
          margin-bottom: 4px;
        }

        .user-role {
          color: #bfcbd9;
          font-size: 13px;
        }
      }
    }

    .el-button {
      width: 100%;
      justify-content: flex-start;
      color: #f56c6c;

      &:hover {
        background-color: rgba(245, 108, 108, 0.1);
      }

      .el-icon {
        margin-right: 8px;
      }
    }
  }
}
</style>
