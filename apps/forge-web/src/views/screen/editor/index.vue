<template>
  <div class="screen-editor">
    <HistoryToolbar :store="store" @preview="handlePreview" />

    <div class="editor-body">
      <CardPanel @add-card="handleAddCard" />

      <div class="editor-canvas">
        <div class="grid-container" @dragover.prevent @drop="handleDrop">
          <el-empty
            v-if="layout.length === 0"
            class="canvas-empty"
            description="画布为空：左侧点选组件，或拖动到画布"
          />
          <GridLayout
            v-else
            v-model:layout="layout"
            :col-num="24" :row-height="45" :margin="[0, 0]"
            is-draggable is-resizable
            @layout-updated="handleLayoutUpdate"
          >
            <GridItem
              v-for="item in layout" :key="item.i"
              :i="item.i" :x="item.x" :y="item.y" :w="item.w" :h="item.h"
              @click="store.activeCardId = item.i"
            >
              <div :class="['canvas-card', { active: store.activeCardId === item.i }]">
                <div class="canvas-card-header">
                  <span>{{ registry.get(item.type)?.meta.title ?? item.type }}</span>
                  <el-button link size="small" @click.stop="store.removeCard(item.i)">×</el-button>
                </div>
                <component
                  :is="registry.get(item.type)?.component"
                  :data="registry.get(item.type)?.meta.dataShape.sample"
                  :options="item.options || {}"
                />
              </div>
            </GridItem>
          </GridLayout>
        </div>
      </div>

      <PropertyPanel />
    </div>

    <TemplateSelector ref="templateRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { GridLayout, GridItem } from 'grid-layout-plus'
import { useScreenEditorStore } from '@/stores/screenEditor'
import { cardRegistry as registry, registerBuiltinCards } from '@/views/screen/cards/registry'
import { getScreenDetail, type ScreenDetailResponse } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import { getTemplate } from '@/views/screen/templates'
import CardPanel from '@/views/screen/components/CardPanel.vue'
import PropertyPanel from '@/views/screen/components/PropertyPanel.vue'
import TemplateSelector from '@/views/screen/components/TemplateSelector.vue'
import HistoryToolbar from '@/views/screen/components/HistoryToolbar.vue'

const route = useRoute()
const router = useRouter()
const store = useScreenEditorStore()
const templateRef = ref()

const layout = computed({
  get: () => store.config.cards.map(c => ({
    i: c.id, x: c.x, y: c.y, w: c.w, h: c.h,
    type: c.type, options: c.options, refresh: c.refresh, dataSourceId: c.dataSourceId
  })),
  set: () => { /* 不直接由 v-model 写回；通过 handleLayoutUpdate */ }
})

const handleLayoutUpdate = (newLayout: any[]) => {
  store.applyChange(d => {
    newLayout.forEach(item => {
      const c = d.cards.find(x => x.id === item.i)
      if (c) { c.x = item.x; c.y = item.y; c.w = item.w; c.h = item.h }
    })
  })
}

const handleAddCard = (type: string) => {
  store.addCard(type, { x: 0, y: 0 })
}

const handleDrop = (e: DragEvent) => {
  const type = e.dataTransfer?.getData('text/plain')
  if (!type) return
  store.addCard(type, { x: 0, y: 0 })
}

const handlePreview = () => {
  if (!store.screenId || !store.screenCode) {
    ElMessage.warning('请先打开一个大屏')
    return
  }
  window.open(`/screen/preview/${store.screenCode}`, '_blank')
}

const applyTemplate = (code: string) => {
  const tpl = getTemplate(code)
  store.config = JSON.parse(JSON.stringify(tpl.config))
  store.isDirty = true
}

const loadFromDetail = (detail: ScreenDetailResponse) => {
  store.screenId = detail.id
  store.screenCode = detail.code
  store.name = detail.name
  const raw = detail.configDraft || detail.config
  if (raw) {
    try { store.config = JSON.parse(raw) } catch { store.config = { version: 1, theme: detail.theme as any, cards: [] } }
  } else {
    store.config = { version: 1, theme: (detail.theme as any) || 'dark-tech', cards: [] }
  }
  if (store.config.theme) applyScreenTheme(store.config.theme)
}

onMounted(async () => {
  try { registerBuiltinCards() } catch { /* 已注册 */ }
  store.reset()
  applyScreenTheme(store.config.theme)

  const idStr = route.params.code as string | undefined
  const template = (route.query.template as string) || null
  if (idStr && /^\d+$/.test(idStr)) {
    try {
      const detail: ScreenDetailResponse = await getScreenDetail(Number(idStr))
      loadFromDetail(detail)
      if ((!detail.configDraft && !detail.config) && template) {
        applyTemplate(template)
      }
    } catch (e) {
      console.error('加载大屏详情失败', e)
      ElMessage.error('加载大屏失败：' + (e instanceof Error ? e.message : String(e)))
    }
  } else if (template) {
    applyTemplate(template)
  }
})
</script>

<style scoped>
.screen-editor { position: fixed; inset: 0; background: var(--screen-bg, #000); display: flex; flex-direction: column; }
.editor-body { flex: 1; display: flex; min-height: 0; }
.editor-canvas { flex: 1; background: rgba(8,22,40,0.4); overflow: auto; padding: 16px; }
.grid-container {
  width: 1920px; min-height: 1080px;
  background: linear-gradient(0deg, transparent 24%, rgba(30,58,95,0.3) 25%, rgba(30,58,95,0.3) 26%, transparent 27%) 0 0 / 80px 45px;
  position: relative;
}
.canvas-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; }
.canvas-card {
  height: 100%; background: rgba(8,22,40,0.85); border: 1px solid #1e3a5f; padding: 4px;
  cursor: pointer;
}
.canvas-card.active { border-color: #1e88e5; box-shadow: 0 0 12px #1e88e5; }
.canvas-card-header { display: flex; justify-content: space-between; font-size: 12px; color: #8a96a8; padding: 0 4px 4px; }
:deep(.vue-grid-item) { background: transparent !important; }
</style>
