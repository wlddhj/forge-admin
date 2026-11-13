<template>
  <div class="app-container">
    <!-- spec-guard-disable-r4 插件注册表全量展示（启动注册，数量少），无分页 -->
    <div v-loading="loading" class="plugin-card-grid">
      <el-card
        v-for="row in plugins"
        :key="row.id"
        shadow="hover"
        class="plugin-card"
        :class="{ 'is-disabled': row.globalStatus === 0 }"
      >
        <div class="plugin-card__header">
          <div class="plugin-card__title">
            <span class="plugin-card__name" :title="row.name">{{ row.name }}</span>
            <el-tag :type="row.globalStatus === 1 ? 'success' : 'danger'" size="small">
              {{ row.globalStatus === 1 ? '已启用' : '平台已禁用' }}
            </el-tag>
          </div>
          <span class="plugin-card__code">{{ row.pluginCode }}</span>
        </div>

        <p class="plugin-card__desc" :title="row.description ?? ''">
          {{ row.description || '暂无描述' }}
        </p>

        <div class="plugin-card__meta">
          <div class="plugin-card__meta-item">
            <span class="plugin-card__meta-label">运行版本</span>
            <span class="plugin-card__meta-value">{{ row.runningVersion || '—' }}</span>
          </div>
          <div class="plugin-card__meta-item">
            <span class="plugin-card__meta-label">提供方</span>
            <span class="plugin-card__meta-value">{{ row.provider || '—' }}</span>
          </div>
          <div class="plugin-card__meta-item">
            <span class="plugin-card__meta-label">最近变更</span>
            <span class="plugin-card__meta-value">{{ formatDateTime(row.updateTime) }}</span>
          </div>
        </div>

        <div class="plugin-card__footer">
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
        </div>
      </el-card>

      <el-empty
        v-if="!loading && plugins.length === 0"
        description="暂无已注册插件"
        class="plugin-grid-empty"
      />
    </div>

    <!-- 租户授权抽屉（启停/License 状态/scope 勾选） -->
    <TenantAuthDrawer ref="tenantAuthDrawerRef" @changed="loadPlugins" />

    <!-- License 管理抽屉（导入/列表/撤销） -->
    <LicenseDrawer ref="licenseDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pluginApi } from '@/api/system/plugin'
import type { PluginEntity } from '@/api/system/plugin'
import { formatDateTime } from '@/utils/dateFormat'
import TenantAuthDrawer from './components/TenantAuthDrawer.vue'
import LicenseDrawer from './components/LicenseDrawer.vue'

defineOptions({ name: 'SystemPlugin' })

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

<style scoped lang="scss">
.plugin-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  align-items: stretch;
  min-height: 200px;
}

.plugin-card {
  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 16px;
  }

  &.is-disabled {
    .plugin-card__name,
    .plugin-card__desc,
    .plugin-card__meta {
      opacity: 0.6;
    }
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__name {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__code {
    flex-shrink: 0;
    font-family: var(--el-font-family-mono, monospace);
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__desc {
    margin: 10px 0 12px;
    font-size: 13px;
    line-height: 1.5;
    color: var(--el-text-color-regular);
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    overflow: hidden;
    min-height: 39px;
  }

  &__meta {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 10px 12px;
    border-radius: var(--el-border-radius-base);
    background-color: var(--el-fill-color-light);
  }

  &__meta-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    font-size: 13px;
  }

  &__meta-label {
    color: var(--el-text-color-secondary);
    flex-shrink: 0;
  }

  &__meta-value {
    color: var(--el-text-color-regular);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__footer {
    display: flex;
    align-items: center;
    margin-top: auto;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-lighter);

    .el-button + .el-button {
      margin-left: 12px;
    }
  }
}

.plugin-grid-empty {
  grid-column: 1 / -1;
}
</style>
