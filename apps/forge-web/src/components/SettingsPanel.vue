<template>
  <el-drawer
    v-model="visible"
    title="页面设置"
    direction="rtl"
    :size="360"
  >
    <div class="settings-panel">
      <!-- 主题设置 -->
      <div class="setting-section">
        <h3 class="section-title">主题设置</h3>

        <div class="setting-item">
          <div class="item-label">
            <span>主题模式</span>
            <p class="item-desc">切换明暗主题</p>
          </div>
          <el-segmented v-model="localConfig.theme" :options="themeOptions" @change="handleConfigChange('theme', $event)" />
        </div>
      </div>

      <el-divider />

      <!-- 标签页设置 -->
      <div class="setting-section">
        <h3 class="section-title">标签页设置</h3>

        <div class="setting-item">
          <div class="item-label">
            <span>显示标签页</span>
            <p class="item-desc">开启后，访问的页面会以标签页形式显示</p>
          </div>
          <el-switch
            v-model="localConfig.showTabs"
            @change="handleConfigChange('showTabs', $event)"
          />
        </div>

        <div class="setting-item" v-if="localConfig.showTabs">
          <div class="item-label">
            <span>最大标签数</span>
            <p class="item-desc">超过此数量时自动关闭最早的标签页</p>
          </div>
          <el-input-number
            v-model="localConfig.maxTabsCount"
            :min="5"
            :max="30"
            :step="5"
            size="small"
            @change="handleConfigChange('maxTabsCount', $event)"
          />
        </div>

        <div class="setting-item" v-if="localConfig.showTabs">
          <div class="item-label">
            <span>移动端隐藏标签</span>
            <p class="item-desc">在移动设备上自动隐藏标签页栏</p>
          </div>
          <el-switch
            v-model="localConfig.autoHideTabsOnMobile"
            @change="handleConfigChange('autoHideTabsOnMobile', $event)"
          />
        </div>
      </div>

      <el-divider />

      <!-- 界面设置 -->
      <div class="setting-section">
        <h3 class="section-title">界面设置</h3>

        <div class="setting-item">
          <div class="item-label">
            <span>显示面包屑</span>
            <p class="item-desc">在顶部显示当前页面的路径</p>
          </div>
          <el-switch
            v-model="localConfig.showBreadcrumb"
            @change="handleConfigChange('showBreadcrumb', $event)"
          />
        </div>

        <div class="setting-item">
          <div class="item-label">
            <span>页面过渡动画</span>
            <p class="item-desc">切换页面时显示过渡动画效果</p>
          </div>
          <el-switch
            v-model="localConfig.showPageTransition"
            @change="handleConfigChange('showPageTransition', $event)"
          />
        </div>
      </div>

      <el-divider />

      <!-- 操作按钮 -->
      <div class="setting-actions">
        <el-button @click="handleReset">
          恢复默认
        </el-button>
        <el-button type="primary" @click="handleSave">
          保存设置
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { usePageConfigStore, type ThemeType } from '@/stores/pageConfig'

const pageConfigStore = usePageConfigStore()

// 主题选项
const themeOptions = [
  { label: '明亮', value: 'light' },
  { label: '暗黑', value: 'dark' }
]

// 对话框显示状态
const visible = computed({
  get: () => pageConfigStore.settingsVisible,
  set: (val) => {
    if (!val) {
      pageConfigStore.closeSettings()
    }
  }
})

// 本地配置（用于编辑）
const localConfig = ref({ ...pageConfigStore.config })

// 监听 store 配置变化，同步到本地
watch(
  () => pageConfigStore.config,
  (newConfig) => {
    localConfig.value = { ...newConfig }
  },
  { deep: true }
)

// 处理配置变化
const handleConfigChange = (key: string, value: any) => {
  if (key === 'theme') {
    pageConfigStore.applyTheme(value as ThemeType)
  }
  pageConfigStore.updateConfig(key as any, value)
}

// 保存设置
const handleSave = () => {
  pageConfigStore.updateMultipleConfig(localConfig.value)
  pageConfigStore.saveConfig()
  ElMessage.success('设置已保存')
  pageConfigStore.closeSettings()
}

// 恢复默认设置
const handleReset = () => {
  pageConfigStore.resetConfig()
  localConfig.value = { ...pageConfigStore.config }
  pageConfigStore.applyTheme(localConfig.value.theme)
  ElMessage.success('已恢复默认设置')
}
</script>

<style scoped lang="scss">
.settings-panel {
  padding: 0 20px;

  .setting-section {
    margin-bottom: 24px;

    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      margin: 0 0 16px 0;
    }

    .setting-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 0;
      border-bottom: 1px solid #f5f7fa;

      &:last-child {
        border-bottom: none;
      }

      .item-label {
        flex: 1;
        margin-right: 16px;

        span {
          display: block;
          font-size: 14px;
          color: #303133;
          margin-bottom: 4px;
        }

        .item-desc {
          font-size: 12px;
          color: #909399;
          margin: 0;
          line-height: 1.5;
        }
      }
    }
  }

  .setting-actions {
    display: flex;
    gap: 12px;
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid #e6e6e6;

    .el-button {
      flex: 1;
    }
  }
}

:deep(.el-drawer__header) {
  margin-bottom: 20px;
  padding: 20px;
  border-bottom: 1px solid #e6e6e6;
}

:deep(.el-drawer__body) {
  padding: 0;
}

:deep(.el-segmented) {
  --el-segmented-item-selected-color: var(--el-text-color-primary);
  --el-segmented-item-selected-bg-color: var(--el-fill-color-dark);
}
</style>
