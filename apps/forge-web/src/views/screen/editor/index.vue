<template>
  <div class="screen-editor">
    <HistoryToolbar :store="store" @preview="handlePreview" />

    <div class="editor-body">
      <CardPanel @add-card="handleAddCard" />

      <div class="editor-canvas">
        <div class="grid-container" @dragover.prevent @drop="handleDrop">
          <GridLayout
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
                  :data="null" :options="item.options || {}"
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
import { GridLayout, GridItem } from 'grid-layout-plus'
import { useScreenEditorStore } from '@/stores/screenEditor'
import { cardRegistry as registry, registerBuiltinCards } from '@/views/screen/cards/registry'
import { getScreenDetail, type ScreenDetailResponse } from '@/api/screen'
import { applyScreenTheme } from '@/themes/screen'
import { getTemplate } from '@/views/screen/templates'
import HistoryToolbar from './HistoryToolbar.vue'
import CardPanel from './CardPanel.vue'
import PropertyPanel from './PropertyPanel.vue'
import TemplateSelector from './TemplateSelector.vue'
import type { ScreenConfig, ScreenCard } from '@/types/screen'

const route = useRoute()
const router = useRouter()
const store = useScreenEditorStore()
const templateRef = ref()

const layout = computed({
  get: () => store.config.cards.map(c => ({ i: c.id, x: c.x, y: c.y, w: c.w, h: c.h, type: c.type, options: c.options, refresh: c.refresh, dataSourceId: c.dataSourceId })),
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
  if (store.screenId) window.open(`/screen/preview/${store.screenCode}`, '_blank')
}

onMounted(async () => {
  try { registerBuiltinCards() } catch { /* 已注册 */ }
  applyScreenTheme(store.config.theme)

  const idStr = route.params.code as string | undefined
  const template = (route.query.template as string) || null
  if (idStr && /^\d+$/.test(idStr)) {
    const detail: ScreenDetailResponse = await getScreenDetail(Number(idStr))
    store.screenId = detail.id
    store.screenCode = detail.code
    const raw = detail.configDraft || detail.config
    if (raw) {
      store.config = JSON.parse(raw)
    } else if (template) {
      store.config = JSON.parse(JSON.stringify(getTemplate(template).config))
    }
  } else if (template) {
    store.config = JSON.parse(JSON.stringify(getTemplate(template).config))
  }
})
</script>

<style scoped>
.screen-editor { position: fixed; inset: 0; background: var(--screen-bg, #000); display: flex; flex-direction: column; }
.editor-body { flex: 1; display: flex; min-height: 0; }
.editor-canvas { flex: 1; background: rgba(8,22,40,0.4); overflow: auto; padding: 16px; }
.grid-container { width: 1920px; min-height: 1080px; background: linear-gradient(0deg, transparent 24%, rgba(30,58,95,0.3) 25%, rgba(30,58,95,0.3) 26%, transparent 27%) 0 0 / 80px 45px; }
.canvas-card {
  height: 100%; background: rgba(8,22,40,0.85); border: 1px solid #1e3a5f; padding: 4px;
}
.canvas-card.active { border-color: #1e88e5; box-shadow: 0 0 12px #1e88e5; }
.canvas-card-header { display: flex; justify-content: space-between; font-size: 12px; color: #8a96a8; padding: 0 4px 4px; }
:deep(.vue-grid-item) { background: transparent !important; }
</style>
