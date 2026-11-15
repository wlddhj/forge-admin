<template>
  <el-drawer
    v-model="visible"
    :title="`租户授权 - ${plugin?.name ?? ''}`"
    size="880px"
    append-to-body
  >
    <div ref="containerRef" class="drawer-body" style="height: 100%">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="启用需有效 License；停用仅隐藏菜单与拦截接口，业务数据全量保留，重新启用即恢复"
        class="mb-3"
      />

      <!-- spec-guard-disable-r4 租户授权抽屉全量展示（租户数量级小），无分页 -->
      <vxe-table
        id="pluginTenantTable"
        ref="tableRef"
        :data="tenants"
        :height="tableHeight"
        :loading="loading"
        :row-config="{ isHover: true }"
        border
        stripe
        show-overflow="tooltip"
      >
        <vxe-column type="seq" title="序号" width="60" align="center" />

        <vxe-column type="expand" width="40">
          <template #content="{ row }">
            <ScopeCheckGroup
              :tenant-id="row.tenantId"
              :plugin-code="plugin?.pluginCode ?? ''"
              :enabled="row.enabled === 1"
              @changed="loadTenants"
            />
          </template>
        </vxe-column>

        <vxe-column title="租户" min-width="140">
          <template #default="{ row }">
            {{ row.tenantName ?? row.tenantId }}
          </template>
        </vxe-column>

        <vxe-column title="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :active-value="1"
              :inactive-value="0"
              :disabled="!canToggle"
              @change="handleToggle(row)"
            />
          </template>
        </vxe-column>

        <vxe-column title="授权状态" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="row.chargeMode === 0" type="success" size="small">免费开放</el-tag>
            <template v-else-if="row.licenseKey">
              <el-tag :type="row.expiringSoon ? 'warning' : 'success'" size="small">
                {{ row.expiringSoon ? '即将到期' : '已授权' }}
              </el-tag>
              <span class="license-info">
                {{ row.licensee }} · 至 {{ formatDate(row.notAfter) }}
              </span>
            </template>
            <el-tag v-else type="danger" size="small">无有效授权</el-tag>
          </template>
        </vxe-column>

        <vxe-column title="停用原因" width="110">
          <template #default="{ row }">
            {{ reasonText(row.disabledReason) }}
          </template>
        </vxe-column>

        <vxe-column title="数据权限" width="80" align="center">
          <template #default="{ row }">
            <el-tooltip :content="(row.effectiveScopes ?? []).join('、') || '无'">
              <span class="scope-count">{{ row.effectiveScopes?.length ?? 0 }} 项</span>
            </el-tooltip>
          </template>
        </vxe-column>
      </vxe-table>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { VxeTableInstance } from 'vxe-table'
import { pluginApi } from '@/api/system/plugin'
import type { PluginEntity, TenantPluginVO } from '@/api/system/plugin'
import { formatDate } from '@/utils/dateFormat'
import { hasAllPermissions } from '@/directives/permission'
import { useDrawerTableHeight } from '@/composables/useDrawerTableHeight'
import ScopeCheckGroup from './ScopeCheckGroup.vue'

defineOptions({ name: 'TenantAuthDrawer' })

const emit = defineEmits<{ changed: [] }>()

const visible = ref(false)
const loading = ref(false)
const plugin = ref<PluginEntity | null>(null)
const tenants = ref<TenantPluginVO[]>([])
const tableRef = ref<VxeTableInstance | null>(null)
const containerRef = ref<HTMLElement>()

const { tableHeight, updateHeight } = useDrawerTableHeight({
  containerRef,
  excludeSelectors: ['.el-alert']
})

/** 启停开关权限（plugin:enable / plugin:disable），无权限仅可查看 */
const canToggle = computed(() => hasAllPermissions(['plugin:enable', 'plugin:disable']))

const open = async (row: PluginEntity) => {
  plugin.value = row
  visible.value = true
  await nextTick()
  requestAnimationFrame(() => updateHeight())
  await loadTenants()
}

const loadTenants = async () => {
  if (!plugin.value) return
  loading.value = true
  try {
    const res = await pluginApi.tenants(plugin.value.pluginCode)
    tenants.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

const handleToggle = async (row: TenantPluginVO) => {
  const pluginCode = plugin.value?.pluginCode
  if (!pluginCode) return
  try {
    if (row.enabled === 1) {
      await pluginApi.enable(pluginCode, row.tenantId)
      ElMessage.success(`已对「${row.tenantName ?? row.tenantId}」启用`)
    } else {
      await pluginApi.disable(pluginCode, row.tenantId)
      ElMessage.success('已停用（菜单隐藏，数据保留）')
    }
    emit('changed')
    await loadTenants()
  } catch {
    // 失败回滚开关状态（如未授权不可启用）
    row.enabled = row.enabled === 1 ? 0 : 1
  }
}

const reasonText = (reason: string | null) => {
  if (!reason) return '-'
  if (reason === 'license_expired') return 'License 到期'
  if (reason === 'license_revoked') return 'License 撤销'
  return reason
}

defineExpose({ open })
</script>

<style scoped>
.drawer-body {
  display: flex;
  flex-direction: column;
}
.license-info {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.scope-count {
  cursor: default;
  color: var(--el-color-primary);
}
</style>
