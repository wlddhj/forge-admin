<template>
  <!-- 工作台相关 -->
  <div class="go-chart">
    <n-layout>
      <layout-header-pro>
        <template #left>
          <header-left-btn></header-left-btn>
        </template>
        <template #center>
          <header-title></header-title>
        </template>
        <template #ri-left>
          <header-right-btn></header-right-btn>
        </template>
      </layout-header-pro>
      <n-layout-content content-style="overflow:hidden; display: flex">
        <div style="overflow:hidden; display: flex">
          <content-charts></content-charts>
          <content-layers></content-layers>
        </div>
        <content-configurations></content-configurations>
      </n-layout-content>
    </n-layout>
  </div>
  <!-- 右键 -->
  <n-dropdown
    placement="bottom-start"
    trigger="manual"
    size="small"
    :x="mousePosition.x"
    :y="mousePosition.y"
    :options="menuOptions"
    :show="chartEditStore.getRightMenuShow"
    :on-clickoutside="onClickOutSide"
    @select="handleMenuSelect"
  ></n-dropdown>
  <!-- 加载蒙层 -->
  <content-load></content-load>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { loadAsyncComponent } from '@/utils'
import { LayoutHeaderPro } from '@/layout/components/LayoutHeaderPro'
import { useContextMenu } from './hooks/useContextMenu.hook'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { useChartHistoryStore } from '@/store/modules/chartHistoryStore/chartHistoryStore'
import { getScreenIdFromUrl } from '@/api/forge/screen'

const chartHistoryStoreStore = useChartHistoryStore()
const chartEditStore = useChartEditStore()


// 从 URL query 读 id，自动从 forge-admin API 加载大屏数据
onMounted(async () => {
  const id = getScreenIdFromUrl()
  if (id) {
    try {
      await chartEditStore.loadProjectById(id)
    } catch (e) {
      console.error('[chart index] 加载大屏失败', e)
    }
  }
})

// 记录初始化
chartHistoryStoreStore.canvasInit(chartEditStore.getEditCanvas)

const HeaderLeftBtn = loadAsyncComponent(() => import('./ContentHeader/headerLeftBtn/index.vue'))
const HeaderRightBtn = loadAsyncComponent(() => import('./ContentHeader/headerRightBtn/index.vue'))
const HeaderTitle = loadAsyncComponent(() => import('./ContentHeader/headerTitle/index.vue'))
const ContentLayers = loadAsyncComponent(() => import('./ContentLayers/index.vue'))
const ContentCharts = loadAsyncComponent(() => import('./ContentCharts/index.vue'))
const ContentConfigurations = loadAsyncComponent(() => import('./ContentConfigurations/index.vue'))
const ContentLoad = loadAsyncComponent(() => import('./ContentLoad/index.vue'))

// 右键
const {
  menuOptions,
  onClickOutSide,
  mousePosition,
  handleMenuSelect
} = useContextMenu()
</script>

<style lang="scss" scoped>
@include go("chart") {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  @include background-image("background-image");
}
</style>
