<template>
  <el-drawer
    v-model="visible"
    title="页面设置"
    direction="rtl"
    :size="360"
  >
    <div class="settings-panel">
      <!-- 主题套餐 -->
      <div class="setting-section">
        <h3 class="section-title">主题套餐</h3>
        <p class="section-desc">选择整体视觉风格包，包含配色、布局、风格三维度</p>

        <div class="preset-grid">
          <div
            v-for="preset in PRESETS"
            :key="preset.id"
            class="preset-card"
            :class="{ active: localConfig.preset === preset.id }"
            @click="handlePresetChange(preset.id)"
          >
            <div class="preset-thumb" :data-palette="preset.palette" :data-style="preset.style">
              <span class="preset-thumb-sidebar" v-if="preset.layout === 'sidebar'"></span>
              <span class="preset-thumb-topbar" v-else></span>
              <span class="preset-thumb-dot"></span>
            </div>
            <span class="preset-name">{{ preset.name }}</span>
          </div>
        </div>
      </div>

      <el-divider />

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
import { PRESETS } from '@/themes'

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

// 选择套餐
const handlePresetChange = (presetId: string) => {
  localConfig.value.preset = presetId
  pageConfigStore.changePreset(presetId)
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
  ElMessage.success('已恢复默认设置')
}
</script>

<style scoped lang="scss">
.settings-panel {
  padding: 0 20px;

  .section-desc {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin: 0 0 12px 0;
    line-height: 1.5;
  }

  .preset-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    margin-bottom: 8px;
  }

  .preset-card {
    cursor: pointer;
    padding: 8px;
    border: 2px solid var(--el-border-color-lighter);
    border-radius: var(--el-border-radius-base);
    transition: all 0.2s;

    &:hover {
      border-color: var(--app-color-primary);
    }

    &.active {
      border-color: var(--app-color-primary);
      background: var(--el-color-primary-light-9);
    }

    .preset-thumb {
      height: 48px;
      border-radius: 4px;
      margin-bottom: 6px;
      position: relative;
      overflow: hidden;
      background: var(--el-fill-color-lighter);

      // 默认主色映射（每张缩略图反映该套餐主色）
      &[data-palette='blue']    { background: linear-gradient(135deg, #ecf5ff, #409EFF); }
      &[data-palette='purple']  { background: linear-gradient(135deg, #f9f0ff, #722ed1); }
      &[data-palette='green']   { background: linear-gradient(135deg, #f6ffed, #52c41a); }
      &[data-palette='crimson'] { background: linear-gradient(135deg, #fff1f0, #f5222d); }

      // 风格映射：圆角差异
      &[data-style='flat']      { border-radius: 4px; }
      &[data-style='glass']     { border-radius: 12px; }
      &[data-style='card']      { border-radius: 8px; }
      &[data-style='compact']   { border-radius: 2px; }

      .preset-thumb-sidebar,
      .preset-thumb-topbar {
        position: absolute;
        background: rgba(255, 255, 255, 0.6);
      }

      .preset-thumb-sidebar {
        left: 4px;
        top: 4px;
        bottom: 4px;
        width: 12px;
        border-radius: 2px;
      }

      .preset-thumb-topbar {
        left: 4px;
        right: 4px;
        top: 4px;
        height: 10px;
        border-radius: 2px;
      }

      .preset-thumb-dot {
        position: absolute;
        right: 6px;
        bottom: 6px;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.9);
      }
    }

    .preset-name {
      display: block;
      text-align: center;
      font-size: 13px;
      color: var(--el-text-color-primary);
    }
  }

  .setting-section {
    margin-bottom: 24px;

    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
      margin: 0 0 16px 0;
    }

    .setting-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 0;
      border-bottom: 1px solid var(--el-border-color-lighter);

      &:last-child {
        border-bottom: none;
      }

      .item-label {
        flex: 1;
        margin-right: 16px;

        span {
          display: block;
          font-size: 14px;
          color: var(--el-text-color-primary);
          margin-bottom: 4px;
        }

        .item-desc {
          font-size: 12px;
          color: var(--el-text-color-secondary);
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
    border-top: 1px solid var(--el-border-color-lighter);

    .el-button {
      flex: 1;
    }
  }
}

:deep(.el-drawer__header) {
  margin-bottom: 20px;
  padding: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.el-drawer__body) {
  padding: 0;
}

:deep(.el-segmented) {
  --el-segmented-item-selected-color: var(--el-text-color-primary);
  --el-segmented-item-selected-bg-color: var(--el-fill-color-dark);
}
</style>
