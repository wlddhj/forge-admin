<template>
  <div class="app-container">
    <vxe-table
      id="sysPluginTable"
      ref="tableRef"
      :custom-config="{ mode: 'modal' }"
      :data="plugins"
      :height="tableHeight"
      :loading="loading"
      :row-config="{ isCurrent: true, isHover: true }"
      :column-config="{ resizable: true }"
      border="none"
      stripe
      show-overflow="tooltip"
      show-header-overflow="tooltip"
    >
      <vxe-column type="seq" title="序号" width="60" />

      <vxe-column field="name" title="插件名称" min-width="160">
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag v-if="row.globalStatus === 0" type="danger" size="small" class="ml-2">平台已禁用</el-tag>
        </template>
      </vxe-column>

      <vxe-column field="pluginCode" title="编码" width="120" />

      <vxe-column field="runningVersion" title="运行版本" width="100" />

      <vxe-column field="provider" title="提供方" width="120" />

      <vxe-column field="description" title="描述" min-width="200" />

      <vxe-column title="最近变更" width="150">
        <template #default="{ row }">
          {{ formatDateTime(row.updateTime) }}
        </template>
      </vxe-column>

      <vxe-column title="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'plugin:enable'"
            link
            type="primary"
            @click="openTenantAuth(row)"
          >
            租户授权
          </el-button>
          <el-button
            v-permission="'plugin:license:import'"
            link
            type="primary"
            @click="licenseDrawerRef?.open(row)"
          >
            License 管理
          </el-button>
          <el-button
            v-permission="'plugin:global'"
            link
            :type="row.globalStatus === 1 ? 'danger' : 'success'"
            @click="toggleGlobal(row)"
          >
            {{ row.globalStatus === 1 ? '平台禁用' : '平台启用' }}
          </el-button>
        </template>
      </vxe-column>
    </vxe-table>

    <!-- 租户授权抽屉（启停/License 状态/scope 勾选） -->
    <TenantAuthDrawer ref="tenantAuthDrawerRef" @changed="loadPlugins" />

    <!-- License 管理抽屉（导入/列表/撤销） -->
    <LicenseDrawer ref="licenseDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { VxeTableInstance } from 'vxe-table'
import { pluginApi } from '@/api/system/plugin'
import type { PluginEntity } from '@/api/system/plugin'
import { formatDateTime } from '@/utils/dateFormat'
import { useTableHeight } from '@/composables/useTableHeight'
import TenantAuthDrawer from './components/TenantAuthDrawer.vue'
import LicenseDrawer from './components/LicenseDrawer.vue'

defineOptions({ name: 'SystemPlugin' })

const { tableHeight } = useTableHeight()

const tableRef = ref<VxeTableInstance | null>(null)
const loading = ref(false)
const plugins = ref<PluginEntity[]>([])
const tenantAuthDrawerRef = ref<InstanceType<typeof TenantAuthDrawer>>()
const licenseDrawerRef = ref<InstanceType<typeof LicenseDrawer>>()

const loadPlugins = async () => {
  loading.value = true
  try {
    const res = await pluginApi.list()
    plugins.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

const openTenantAuth = (row: PluginEntity) => {
  tenantAuthDrawerRef.value?.open(row)
}

/** 平台总闸：禁用=全部租户瞬时不可访问（fail-closed） */
const toggleGlobal = async (row: PluginEntity) => {
  const disabling = row.globalStatus === 1
  const tip = disabling
    ? `禁用后「${row.name}」对全部租户立即不可访问（菜单隐藏、接口拒绝），插件数据保留。确定禁用？`
    : `重新开放「${row.name}」的平台访问（各租户启停状态不变）。确定启用？`
  await ElMessageBox.confirm(tip, '平台总闸', { type: 'warning' })
  await pluginApi.updateGlobalStatus(row.pluginCode, disabling ? 0 : 1)
  ElMessage.success('操作成功')
  await loadPlugins()
}

onMounted(loadPlugins)
</script>

<style scoped>
.ml-2 {
  margin-left: 8px;
}
</style>
