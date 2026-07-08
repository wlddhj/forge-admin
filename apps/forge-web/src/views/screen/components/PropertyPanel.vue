<script setup lang="ts">
import { computed } from 'vue'
import { useScreenEditorStore } from '@/stores/screenEditor'
import { cardRegistry } from '@/views/screen/cards/registry'
import JsonSchemaForm from './JsonSchemaForm.vue'
import DataSourceBinder from './DataSourceBinder.vue'
import FieldMappingEditor from './FieldMappingEditor.vue'
import { SCREEN_REFRESH_OPTIONS } from '@/constants/screen'
import type { ScreenCard } from '@/types/screen'

const store = useScreenEditorStore()
const card = computed<ScreenCard | null>(() => store.activeCard)
const entry = computed(() => card.value ? cardRegistry.get(card.value.type) : null)

const updateTitle = (v: string) => card.value && store.updateCard(card.value.id, { title: v })
const updateRefresh = (v: number) => card.value && store.updateCard(card.value.id, { refresh: v })
const updateOptions = (options: Record<string, unknown>) => card.value && store.updateCard(card.value.id, { options })
const updateDataSource = (id: number | null) => card.value && store.updateCard(card.value.id, { dataSourceId: id })
</script>

<template>
  <div class="property-panel">
    <h3>属性</h3>
    <el-empty v-if="!card" description="在画布上点击卡片" :image-size="60">
      <template #image>
        <el-icon :size="48" color="#8a96a8"><Pointer /></el-icon>
      </template>
    </el-empty>
    <template v-else>
      <el-form label-width="80px" size="small">
        <el-form-item label="类型">
          <el-tag size="small">{{ entry?.meta.title ?? card.type }}</el-tag>
        </el-form-item>
        <el-form-item label="标题">
          <el-input :model-value="card.title" @update:model-value="updateTitle" />
        </el-form-item>
        <el-form-item label="位置">
          x={{ card.x }} y={{ card.y }} w={{ card.w }} h={{ card.h }}
        </el-form-item>
        <el-form-item label="自动刷新">
          <el-select :model-value="card.refresh ?? 0" @update:model-value="updateRefresh">
            <el-option v-for="o in SCREEN_REFRESH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源">
          <DataSourceBinder :card="card" @bind="updateDataSource" />
        </el-form-item>
      </el-form>

      <el-divider>组件配置</el-divider>
      <JsonSchemaForm
        v-if="entry"
        :schema="entry.meta.configSchema"
        :model-value="card.options || {}"
        @update:model-value="updateOptions"
      />

      <el-divider>字段映射</el-divider>
      <FieldMappingEditor
        v-if="card.dataSourceId && entry"
        :card="card"
        :data-shape="entry.meta.dataShape"
      />
    </template>
  </div>
</template>

<style scoped>
.property-panel { width: 320px; background: rgba(8,22,40,0.6); padding: 12px; border-left: 1px solid #1e3a5f; overflow-y: auto; color: #e0e6f1; }
h3 { margin: 0 0 12px 0; color: #1e88e5; font-size: 14px; }
:deep(.el-form-item__label) { color: #8a96a8 !important; }
</style>
