<template>
  <!-- 仅平台超管（拥有 system:tenant:list 权限）可见 -->
  <el-dropdown v-if="visible" trigger="click" @command="handleSwitch">
    <span class="tenant-switcher">
      <el-icon><OfficeBuilding /></el-icon>
      <span class="tenant-name">{{ currentTenantName }}</span>
      <el-icon class="caret"><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="t in tenants"
          :key="t.id"
          :command="t.id"
          :disabled="t.id === userStore.tenantId"
          :divided="t.id === tenants[0]?.id ? false : false"
        >
          <div class="tenant-item">
            <span>{{ t.name }} <el-tag size="small" type="info">{{ t.code }}</el-tag></span>
            <el-icon v-if="t.id === userStore.tenantId" class="active-check"><Check /></el-icon>
          </div>
        </el-dropdown-item>
        <el-dropdown-item v-if="tenants.length === 0" disabled>暂无租户数据</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { OfficeBuilding, ArrowDown, Check } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { hasPermission } from '@/directives/permission'
import request from '@/utils/request'

interface TenantSimple {
  id: number
  name: string
  code: string
}

const userStore = useUserStore()
const tenants = ref<TenantSimple[]>([])

// 平台超管才显示（拥有 system:tenant:list 权限）
const visible = computed(() => hasPermission('system:tenant:list'))

const currentTenantName = computed(() => {
  const t = tenants.value.find((x) => x.id === userStore.tenantId)
  return t?.name || `租户 #${userStore.tenantId}`
})

const loadTenants = async () => {
  if (!visible.value) return
  try {
    // 平台超管拉租户列表（不分页，最多 100 个）
    const res: any = await request.get('/system/tenant/list', { params: { pageNum: 1, pageSize: 100 } })
    tenants.value = (res?.list || []).map((t: any) => ({
      id: Number(t.id),
      name: t.name,
      code: t.code
    }))
  } catch (e) {
    console.warn('加载租户列表失败', e)
  }
}

const handleSwitch = async (id: number) => {
  if (id === userStore.tenantId) return
  const target = tenants.value.find((t) => t.id === id)
  try {
    await ElMessageBox.confirm(
      `即将切换到「${target?.name}」租户上下文，所有数据将按新租户过滤。`,
      '切换租户',
      { type: 'warning', confirmButtonText: '切换', cancelButtonText: '取消' }
    )
    userStore.setTenantId(id)
    ElMessage.success(`已切换到 ${target?.name}`)
    // 强制刷新页面，清空所有缓存（菜单、用户信息、tabs 等）
    setTimeout(() => {
      window.location.href = '/'
    }, 500)
  } catch {
    // 用户取消
  }
}

onMounted(loadTenants)
</script>

<style scoped lang="scss">
.tenant-switcher {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 4px;
  color: var(--el-text-color-regular);
  transition: background 0.2s;

  &:hover {
    background: var(--el-fill-color-light);
    color: var(--app-color-primary);
  }

  .tenant-name {
    font-size: 13px;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .caret {
    font-size: 12px;
  }
}

.tenant-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 200px;

  .active-check {
    color: var(--app-color-primary);
  }
}
</style>
